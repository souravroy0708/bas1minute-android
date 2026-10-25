package com.bas1minute.sync

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/**
 * Total minutes apps were on screen for one day: the same idea as Digital Wellbeing's screen time.
 * Reads only "app came to the front / went away" events and adds up the time. Nothing about which
 * apps were used leaves the phone; only the daily total is sent.
 */
class ScreenTimeReader(private val context: Context) {

    private companion object {
        const val RESUMED = 1   // ACTIVITY_RESUMED (API 29+), same value as MOVE_TO_FOREGROUND before
        const val PAUSED = 2    // ACTIVITY_PAUSED (API 29+), same value as MOVE_TO_BACKGROUND before
        const val STOPPED = 23  // ACTIVITY_STOPPED (API 29+)
    }

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Minutes for the given day. Null when usage access isn't granted, or when Android has no usage
     * data for that day at all (it trims old history), so a saved number is never replaced by 0.
     */
    fun minutesFor(date: LocalDate): Int? {
        if (!hasAccess()) return null
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        if (end <= start) return 0

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ignored = setOfNotNull(context.packageName, homePackage(), "com.android.systemui")
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val openSince = HashMap<String, Long>()
        var totalMs = 0L
        var seenAny = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            seenAny = true
            val pkg = event.packageName ?: continue
            if (pkg in ignored) continue
            val key = pkg + "/" + (event.className ?: "")
            when (event.eventType) {
                RESUMED -> openSince[key] = event.timeStamp
                PAUSED, STOPPED -> openSince.remove(key)?.let { totalMs += event.timeStamp - it }
            }
        }
        if (!seenAny) return null
        // Apps still on screen right now
        openSince.values.forEach { totalMs += end - it }
        val minutes = (totalMs / 60_000L).toInt()
        return minutes.coerceIn(0, 24 * 60)
    }

    private fun homePackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }
}
