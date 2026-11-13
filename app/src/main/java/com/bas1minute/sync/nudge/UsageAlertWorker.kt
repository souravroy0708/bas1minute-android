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
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.Prefs
import com.bas1minute.sync.R
import com.bas1minute.sync.Recorder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * One notification a day at ~9 PM: yesterday's total screen time and social-media time, plainly stated.
 * No "you wasted" language, no guilt, no exaggeration — the honest number is the point; what the person does
 * with it is theirs. A separate, optional setting from the three daily nudges.
 */
class UsageAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try { send(applicationContext) } catch (e: Exception) { /* never crash in the background */ }
        finally { schedule(applicationContext, replace = true) }
        return Result.success()
    }

    private suspend fun send(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.dailyUsageAlert || !prefs.setupDone) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val today = LocalDate.now()
        val key = today.toString()
        if (prefs.lastUsageAlert == key) return

        runCatching { Recorder.captureRecent(ctx, days = 2) }
        val row = LocalDb.get(ctx).day(today.minusDays(1)) ?: return   // nothing measured yet: say nothing false
        val screen = row.screenMinutes
        val social = row.socialMinutes
        if (screen == null && social == null) return

        val body = when {
            screen != null && social != null -> "${hm(screen)} on your phone, ${hm(social)} of it on social media or a browser."
            screen != null -> "${hm(screen)} on your phone."
            else -> "${hm(social!!)} on social media or a browser."
        }
        show(ctx, body)
        prefs.lastUsageAlert = key
    }

    private fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

    companion object {
        private const val NAME = "bas1minute-usage-alert"
        private const val CHANNEL = "usage_alert"
        private const val HOUR = 21
        private const val MINUTE = 0

        fun schedule(ctx: Context, replace: Boolean = false) {
            val now = LocalDateTime.now()
            val zone = ZoneId.systemDefault()
            var next = now.toLocalDate().atTime(HOUR, MINUTE)
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            val delayMs = next.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis() + Random.nextLong(0, 10 * 60_000L)
            val req = OneTimeWorkRequestBuilder<UsageAlertWorker>().setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }

        private fun show(ctx: Context, body: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily phone-time reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "One evening notification with yesterday's screen and social-media time"
                })
            }
            val open = PendingIntent.getActivity(
                ctx, 7500,
                Intent(ctx, MainActivity::class.java).putExtra(com.bas1minute.sync.detox.DetoxService.EXTRA_OPEN, "today")
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Yesterday on your phone")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(7500, notification)
        }
    }
}
