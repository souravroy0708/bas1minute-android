package com.bas1minute.sync.detox

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bas1minute.sync.Prefs
import com.bas1minute.sync.R
import java.time.LocalDate

/**
 * If the app in the foreground right now is on the member's App Timer list and Android's own usage stats say
 * its total time today is at or past the daily budget, a reminder notification appears with a one-tap
 * "Close it". Nothing is drawn over the app (no "Display over other apps").
 *
 * No periodic job of its own: this rides on the single shared 15-minute tick in phone/BatteryLog.kt
 * (BatteryWorker). A lag of up to ~15 minutes past the exact budget is accepted on purpose — the trade for not
 * running anything in the background all the time. Capped at AppTimerLogic.MAX_REMINDERS_PER_APP_PER_DAY.
 */
object AppTimerChecker {
    private const val CHANNEL = "app_timer"
    private const val NOTIF = 7470

    fun check(ctx: Context, reader: UsageReader, fg: String?, now: Long) {
        val prefs = Prefs(ctx)
        if (!prefs.appTimerEnabled || !prefs.setupDone) return
        if (fg == null || fg !in prefs.appTimerApps) return

        val day = LocalDate.now().toString()
        val sent = prefs.appTimerReminders(day, fg)
        val usedMs = reader.totalTodayMs(fg, now)
        if (!AppTimerLogic.shouldRemind(usedMs, prefs.appTimerBudgetMinutes, sent)) return

        val label = AppRules(ctx, emptySet(), emptySet()).label(fg)   // only .label(pkg) is used here
        if (show(ctx, label, prefs.appTimerBudgetMinutes, usedMs)) prefs.setAppTimerReminders(day, fg, sent + 1)
    }

    private fun show(ctx: Context, label: String, budgetMin: Int, usedMs: Long): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App Timers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When an app on your App Timers list has used up today's minutes"
            })
        }
        val usedMin = usedMs / 60_000L
        val body = "You've had ${usedMin} min today (your limit is $budgetMin). Close it and come back tomorrow?"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Today's time on $label is up")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(DetoxWatch.openDetox(ctx, 7471))
            .addAction(0, "Close it", DetoxWatch.goHome(ctx, 7472))
            .setAutoCancel(true)
            .build()
        return runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF, n) }.isSuccess
    }
}
