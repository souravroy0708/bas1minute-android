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
 * Water reminders: at 11:00, 14:00 and 17:00 (one per slot), only if today's water is behind where it should be
 * for a 2-litre day. Static text, built on the phone. Follows the "Nudges and reminders" switch.
 */
object WaterReminder {
    private const val CHANNEL = "water"
    private const val NOTIF = 7900
    private val SLOTS = listOf(11, 14, 17)
    private const val DAY_ML = 2000

    fun check(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.setupDone || !prefs.dailyNudges || !prefs.waterReminders) return
        val hour = LocalTime.now().hour
        val slot = SLOTS.lastOrNull { hour >= it && hour < it + 2 } ?: return
        val key = "${LocalDate.now()}:$slot"
        if (prefs.lastWaterReminder == key) return
        val water = LocalDb.get(ctx).day(LocalDate.now())?.waterMl ?: 0
        val expected = DAY_ML * (slot - 8) / 13            // roughly on pace for 08:00–21:00
        prefs.lastWaterReminder = key
        if (water >= expected) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A gentle reminder to drink water, at most three a day"
            })
        }
        val open = PendingIntent.getActivity(ctx, NOTIF, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (water == 0) "Nothing logged yet today. Have a glass now and tap Water on Home." else
            "$water ml so far. One more glass keeps you on track — tap Water on Home."
        runCatching {
            NotificationManagerCompat.from(ctx).notify(NOTIF, NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Time for some water 💧").setContentText(text)
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
