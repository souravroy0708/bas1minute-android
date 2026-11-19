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
import java.time.LocalTime

/**
 * "Your steps aren't counting yet" — for people who skipped the step permission (or never opened the app again
 * after installing). At most [MAX] reminders, at least a day apart, only between 10:00 and 20:00, and never once
 * steps are counting. Built and sent on the phone; nothing is sent anywhere. Runs from the 15-minute tick.
 */
object StepsReminder {
    private const val MAX = 3
    private const val GAP_MS = 22 * 60 * 60 * 1000L
    private const val CHANNEL = "setup"
    private const val NOTIF = 7600

    suspend fun check(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.setupDone || prefs.stepsReminderCount >= MAX) return
        val now = System.currentTimeMillis()
        if (now - prefs.stepsReminderAt < GAP_MS) return
        if (LocalTime.now().hour !in 10..19) return
        val reader = StepsReader(ctx)
        val counting = reader.healthConnectGranted() || (reader.activityPermissionGranted() && reader.recordingApiAvailable())
        if (counting) { prefs.stepsReminderCount = MAX; return }   // done for good
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Finish setting up", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder if step counting isn’t switched on yet"
            })
        }
        val (title, text) = when (prefs.stepsReminderCount) {
            0 -> "Your steps aren’t counting yet 👟" to "One tap in Bas 1 Minute and your phone counts them for you — even the ones you’ve already walked today."
            1 -> "You’ve been walking — let’s count it" to "Switch on step counting and today’s steps show up in your ring and your circle."
            else -> "Last reminder: count your steps" to "Open Bas 1 Minute and tap Start counting. We won’t ask again."
        }
        val open = PendingIntent.getActivity(ctx, NOTIF, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true)
            .build()
        if (runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF, n) }.isSuccess) {
            prefs.stepsReminderCount = prefs.stepsReminderCount + 1
            prefs.stepsReminderAt = now
        }
    }
}
