package com.bas1minute.sync

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
import java.time.LocalDate
import java.time.LocalTime

/**
 * First-two-weeks tips: at most ONE notification a day, in the evening, about a feature the member hasn't tried
 * yet (AppFeature.TIPS order: Circles, Goals, Walk, Detox, Diary…). Each feature is suggested only once, and never
 * once it's been used. Static text, built on the phone; nothing is sent anywhere. Tapping opens that feature.
 * Follows the "Nudges and reminders" switch. Runs from the 15-minute tick.
 */
object FeatureTips {
    const val EXTRA_FEATURE = "open_feature"
    private const val CHANNEL = "tips"
    private const val NOTIF = 7700
    private const val DAYS = 14

    fun check(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.setupDone || !prefs.dailyNudges) return
        val today = LocalDate.now()
        if (prefs.lastTipDay == today.toString()) return
        if (LocalTime.now().hour !in 18..20) return
        val installed = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime }.getOrDefault(0L)
        val days = (System.currentTimeMillis() - installed) / (24 * 3600_000L)
        if (days < 1 || days > DAYS) return                  // not on install day; stop after two weeks
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val used = prefs.usedFeatures; val sent = prefs.tipsSent
        val tip = AppFeature.TIPS.firstOrNull { it.key !in used && it.key !in sent } ?: return

        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tips for new members", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One tip a day in your first two weeks, about a feature you haven’t tried"
            })
        }
        val open = PendingIntent.getActivity(ctx, NOTIF, Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_FEATURE, tip.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tip.tipTitle).setContentText(tip.tipText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tip.tipText))
            .setContentIntent(open).setAutoCancel(true)
            .build()
        if (runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF, n) }.isSuccess) {
            prefs.tipsSent = sent + tip.key
            prefs.lastTipDay = today.toString()
        }
    }
}
