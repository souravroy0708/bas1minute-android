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
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.Prefs
import com.bas1minute.sync.R
import com.bas1minute.sync.detox.DetoxWatch
import com.bas1minute.sync.detox.DetoxStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * One extra nudge a day, at a time nobody could predict — unlike the three fixed check-ins in NudgeWorker,
 * this isn't tied to steps, screen time or a schedule the person could set their watch by. Same restraint as
 * the fixed ones, though: silent during a detox, silent without notification permission, and the message
 * pool (see Nudges.motivational) never repeats itself back-to-back. Shares the "Daily nudges" toggle rather
 * than adding a second setting — turning that off turns this off too.
 */
class MotivationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try { send(applicationContext) } catch (e: Exception) { /* never crash in the background */ }
        finally { schedule(applicationContext, replace = true) }
        return Result.success()
    }

    private fun send(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.dailyNudges || !prefs.setupDone) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (DetoxStore.get(ctx).active() != null) return   // never interrupt a detox, same rule as the fixed nudges
        val today = LocalDate.now().toString()
        if (prefs.lastMotivationalDate == today) return    // already sent today — including by an earlier, delayed run

        val (nudge, idx) = Nudges.motivational(prefs.firstName.ifBlank { "" }, Random.nextInt(8), prefs.lastMotivationalIndex)
        show(ctx, nudge)
        prefs.lastMotivationalDate = today
        prefs.lastMotivationalIndex = idx
    }

    companion object {
        private const val NAME = "bas1minute-motivation"
        private const val CHANNEL = "daily_nudges"   // same channel as the fixed nudges: one place to mute both together
        private const val WINDOW_START_HOUR = 11
        private const val WINDOW_END_HOUR = 20

        /** Queues tomorrow's random moment once today's has fired (or been skipped). A fresh random pick
         * every call, so the time is never the same two days running. */
        fun schedule(ctx: Context, replace: Boolean = false) {
            val now = LocalDateTime.now()
            val zone = ZoneId.systemDefault()
            var target = now.toLocalDate().atTime(WINDOW_START_HOUR, 0)
                .plusMinutes(Random.nextLong(0, (WINDOW_END_HOUR - WINDOW_START_HOUR) * 60L))
            if (!target.isAfter(now.plusMinutes(5))) {
                target = now.toLocalDate().plusDays(1).atTime(WINDOW_START_HOUR, 0)
                    .plusMinutes(Random.nextLong(0, (WINDOW_END_HOUR - WINDOW_START_HOUR) * 60L))
            }
            val delayMs = target.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis()
            val req = OneTimeWorkRequestBuilder<MotivationWorker>().setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }

        private fun show(ctx: Context, n: Nudge) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Three a day, from your numbers, plus the occasional surprise message to keep you going"
                })
            }
            val open = PendingIntent.getActivity(
                ctx, 7450,
                Intent(ctx, MainActivity::class.java).putExtra(DetoxWatch.EXTRA_OPEN, n.target.open)
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
            // Its own notification id (7450), separate from the fixed nudges' 7400: a surprise message
            // shouldn't silently replace an unread check-in, or vice versa.
            NotificationManagerCompat.from(ctx).notify(7450, notification)
        }
    }
}
