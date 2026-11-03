package com.bas1minute.sync.phone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.R
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Battery condition right now, straight from Android (no permission needed). */
data class BatteryNow(
    val level: Int?, val charging: Boolean, val tempC: Float?, val health: String?, val cycles: Int?,
    val plug: String?, val currentMa: Int?,
)

/** Battery level history: Android doesn't keep one for other apps, so the app samples it itself. */
object BatteryLog {
    private const val KEEP_MS = 8L * 24 * 60 * 60 * 1000

    fun now(ctx: Context): BatteryNow {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryNow(null, false, null, null, null, null, null)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Worn out"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Problem"
            else -> null
        }
        // Charge cycles: Android 14+, on phones that report it
        val cycles = if (Build.VERSION.SDK_INT >= 34) i.getIntExtra("android.os.extra.CYCLE_COUNT", -1).takeIf { it >= 0 } else null
        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Charger"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"; else -> null
        }
        // Instant current draw; units differ by maker (µA or mA), so only plausible values are shown
        val current = try {
            val bm = ctx.getSystemService(BatteryManager::class.java)
            val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (raw == Int.MIN_VALUE || raw == 0) null else {
                val ma = if (kotlin.math.abs(raw) > 20_000) raw / 1000 else raw
                kotlin.math.abs(ma).takeIf { it in 10..8000 }
            }
        } catch (e: Exception) { null }
        return BatteryNow(if (level >= 0) level * 100 / scale else null, charging, temp, health, cycles, plug, current)
    }

    private fun db(ctx: Context): SQLiteDatabase = LocalDb.get(ctx).writableDatabase

    /** Save the current level (at most one row per minute). */
    fun record(ctx: Context) {
        val n = now(ctx)
        val level = n.level ?: return
        val t = System.currentTimeMillis() / 60_000L * 60_000L
        db(ctx).insertWithOnConflict("battery_samples", null, ContentValues().apply {
            put("t", t); put("level", level); put("charging", if (n.charging) 1 else 0); n.tempC?.let { put("temp", it) }
        }, SQLiteDatabase.CONFLICT_REPLACE)
        db(ctx).delete("battery_samples", "t < ?", arrayOf((t - KEEP_MS).toString()))
    }

    fun samples(ctx: Context, from: Long): List<BatterySample> =
        db(ctx).rawQuery("SELECT t, level, charging, temp FROM battery_samples WHERE t >= ? ORDER BY t", arrayOf(from.toString())).use { c ->
            generateSequence { if (c.moveToNext()) BatterySample(c.getLong(0), c.getInt(1), c.getInt(2) == 1, if (c.isNull(3)) null else c.getFloat(3)) else null }.toList()
        }

    /** Hours of history saved so far (the screen says "measuring" until there's a day of it). */
    fun firstSampleAt(ctx: Context): Long? =
        db(ctx).rawQuery("SELECT MIN(t) FROM battery_samples", null).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
}

/**
 * Every ~15 minutes (Android's shortest repeat; it waits longer while the phone sleeps): save the battery level.
 * Once each evening, if alerts are on, check for something unusual and send one notification.
 */
class BatteryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try { BatteryLog.record(ctx) } catch (e: Exception) { /* storage busy: next run */ }
        try { maybeAlert(ctx) } catch (e: Exception) { /* never crash in the background */ }
        return Result.success()
    }

    private fun maybeAlert(ctx: Context) {
        val prefs = ctx.getSharedPreferences("bas1minute", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("phone_alerts", true)) return
        val today = LocalDate.now().toString()
        if (prefs.getString("phone_alert_day", "") == today || LocalTime.now().hour < 19) return
        prefs.edit().putString("phone_alert_day", today).apply()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val report = PhoneCheckReader(ctx).report() ?: return
        // Only the serious, new ones; never the same thing two days running
        val seen = prefs.getStringSet("phone_alert_seen", emptySet()) ?: emptySet()
        val top = report.findings.firstOrNull { it.severity == Severity.High && it.id !in seen } ?: return
        prefs.edit().putStringSet("phone_alert_seen", (seen + top.id).toList().takeLast(40).toSet()).apply()

        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Phone check", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Once a day at most: an app draining battery or data, or a risky permission"
            })
        }
        val open = PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(4712, NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(top.title)
            .setContentText(top.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(top.detail))
            .setContentIntent(open).setAutoCancel(true).build())
    }

    companion object {
        private const val NAME = "bas1minute-battery"
        private const val CHANNEL = "phone_check"
        const val EXTRA_OPEN = "open_phone_check"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
