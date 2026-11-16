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
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.Prefs
import com.bas1minute.sync.R
import com.bas1minute.sync.detox.AppRules
import com.bas1minute.sync.detox.DetoxService
import com.bas1minute.sync.detox.DetoxStore
import com.bas1minute.sync.detox.UsageReader

/**
 * Catches a long, unbroken stretch on one distracting app (see AppRules.isDistracting — Instagram, Facebook,
 * YouTube, any browser), sending a nudge: 1st gentle, 2nd offering a detox outright, 3rd the last one for
 * that session. See SessionGuard.kt for the session-detection rules.
 *
 * No periodic job of its own: this rides on the single shared 15-minute tick in phone/BatteryLog.kt
 * (BatteryWorker), which already runs for core battery/phone-health logging — one OS wake-up covers this,
 * Schedules, and App Timers together, rather than four separate ones (see BatteryWorker's own doc for why).
 * [reader] and [fg] (the already-looked-up foreground app) are passed in so that single shared tick only
 * ever queries UsageStatsManager once per 15 minutes, not once per feature.
 */
object SessionGuardChecker {
    private const val CHANNEL = "session_nudges"

    fun check(ctx: Context, reader: UsageReader, fg: String?, now: Long) {
        val prefs = Prefs(ctx)
        if (!prefs.longSessionNudges || !prefs.setupDone) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (fg == null) return
        val rules = AppRules(ctx, emptySet(), emptySet())   // only isDistracting/label are used here; block choices don't apply
        if (!rules.isDistracting(fg)) return

        val windowStart = now - 40 * 60_000L
        val intervals = runCatching { reader.intervals(windowStart, now, rules::isDistracting) }.getOrNull() ?: return
        val merged = SessionGuard.mergeContinuous(intervals, fg)
        val last = merged.lastOrNull() ?: return
        if (now - last.end > SessionGuard.GAP_TOLERANCE_MS) return   // not actually an ongoing session
        val sessionMs = now - last.start
        val due = SessionGuard.dueNudgeCount(sessionMs)
        if (due == 0) return

        val key = SessionGuard.sessionKey(fg, last.start)
        val already = if (prefs.lastSessionGuardKey == key) prefs.lastSessionGuardCount else 0
        if (due <= already) return   // this session's already had everything it's earned so far

        val nudge = SessionGuard.message(rules.label(fg), due, prefs.firstName.ifBlank { "" })
        show(ctx, nudge)
        prefs.lastSessionGuardKey = key
        prefs.lastSessionGuardCount = due
    }

    private fun show(ctx: Context, n: Nudge) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Long-session nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When you've been on one social app or browser a while, in one sitting — separate from the 4 daily check-ins"
            })
        }
        val open = PendingIntent.getActivity(
            ctx, 7460,
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
        NotificationManagerCompat.from(ctx).notify(7460, notification)
    }
}
