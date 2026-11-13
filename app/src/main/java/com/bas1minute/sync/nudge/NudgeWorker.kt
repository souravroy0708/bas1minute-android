package com.bas1minute.sync.nudge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.Prefs
import com.bas1minute.sync.R
import com.bas1minute.sync.Recorder
import com.bas1minute.sync.detox.DetoxController
import com.bas1minute.sync.detox.DetoxService
import com.bas1minute.sync.detox.DetoxStore
import com.bas1minute.sync.detox.Outcome
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Sends the three daily nudges (see Nudges.kt). One-time work chained to the next slot, so it survives restarts
 * (WorkManager keeps it) and lands near 8:30, 13:30 and 19:30 even with Android's battery saving. Everything is
 * worked out on the phone; nothing is sent anywhere.
 */
class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val slot = NudgeSlot.of(inputData.getString(KEY_SLOT))
        try {
            send(ctx, slot)
        } catch (e: Exception) {
            // Never crash in the background: the next slot still gets scheduled
        } finally {
            schedule(ctx, replace = true)
        }
        return Result.success()
    }

    private suspend fun send(ctx: Context, slot: NudgeSlot) {
        val prefs = Prefs(ctx)
        if (!prefs.dailyNudges || !prefs.setupDone) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        // Late by more than 3 hours (phone was off): skip rather than say "good morning" at lunch
        val now = LocalDateTime.now()
        val due = now.toLocalDate().atTime(slot.hour, slot.minute)
        if (now.isBefore(due.minusMinutes(30)) || now.isAfter(due.plusHours(3))) return
        val key = "${now.toLocalDate()}:${slot.id}"
        if (prefs.lastNudge == key) return

        runCatching { Recorder.captureRecent(ctx, days = 2) }   // fresh steps and screen time
        val db = LocalDb.get(ctx)
        val today = LocalDate.now()
        val row = db.day(today)
        val yesterday = db.day(today.minusDays(1))
        val last30 = db.days(today.minusDays(29), today).associateBy { it.date }
        val steps30 = (29 downTo 0).map { last30[today.minusDays(it.toLong())]?.steps }
        val detox = DetoxStore.get(ctx)
        val dayStart = DetoxController.dayStart(System.currentTimeMillis())
        val state = NudgeState(
            slot = slot,
            name = prefs.firstName.ifBlank { "dost" },
            steps = row?.steps,
            goal = prefs.stepGoal,
            socialToday = row?.socialMinutes,
            socialYesterday = yesterday?.socialMinutes,
            socialLimit = prefs.screenLimitMinutes,
            detoxActive = detox.active() != null,
            detoxKeptToday = detox.sessions().any { it.startMs >= dayStart && it.outcome == Outcome.KEPT },
            streak = com.bas1minute.sync.ui.TwentyOneStats.streak(steps30, prefs.stepGoal),
            stepLengthCm = prefs.stepLengthCm,
            dayOfYear = today.dayOfYear,
        )
        val nudge = Nudges.pick(state) ?: return
        show(ctx, nudge, slot)
        prefs.lastNudge = key
    }

    companion object {
        private const val NAME = "bas1minute-daily-nudge"
        private const val KEY_SLOT = "slot"
        private const val CHANNEL = "daily_nudges"

        /**
         * Queue the next slot. replace=false (app start) keeps an already queued one, so opening the app often
         * never shifts or doubles the nudges.
         */
        fun schedule(ctx: Context, replace: Boolean = false) {
            val now = LocalDateTime.now()
            val zone = ZoneId.systemDefault()
            val next = (0..1).flatMap { d -> NudgeSlot.entries.map { it to now.toLocalDate().plusDays(d.toLong()).atTime(it.hour, it.minute) } }
                .first { (_, at) -> at.isAfter(now.plusMinutes(1)) }
            // Up to 10 minutes later, so phones don't all buzz at the same second
            val delayMs = next.second.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis() + Random.nextLong(0, 10 * 60_000L)
            val req = OneTimeWorkRequestBuilder<NudgeWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SLOT to next.first.id))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }

        private fun show(ctx: Context, n: Nudge, slot: NudgeSlot) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Three a day: steps, screen time and digital detox, from your own numbers"
                })
            }
            val open = PendingIntent.getActivity(
                ctx, 7400 + slot.ordinal,
                Intent(ctx, MainActivity::class.java).putExtra(DetoxService.EXTRA_OPEN, n.target.open)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(n.title)
                .setContentText(n.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            // One id for all three: a new nudge replaces an unread one instead of piling up
            NotificationManagerCompat.from(ctx).notify(7400, notification)
        }
    }
}
