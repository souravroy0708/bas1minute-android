package com.bas1minute.sync.detox

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Reads which app was on screen, from Android's usage events (needs Usage access). Nothing leaves the phone. */
class UsageReader(context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun events(from: Long, to: Long): List<FgEvent> {
        val out = ArrayList<FgEvent>()
        val ev = runCatching { usm.queryEvents(from, to) }.getOrNull() ?: return out
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                RESUMED -> out += FgEvent(e.packageName, e.timeStamp, resumed = true)
                PAUSED -> out += FgEvent(e.packageName, e.timeStamp, resumed = false)
            }
        }
        return out
    }

    fun intervals(from: Long, to: Long, keep: (String) -> Boolean): List<Interval> =
        DetoxLogic.buildIntervals(events(from, to), from, to).filter { keep(it.pkg) }

    companion object {
        // ACTIVITY_RESUMED / ACTIVITY_PAUSED (API 29) share values with MOVE_TO_FOREGROUND / BACKGROUND (API 21).
        const val RESUMED = 1
        const val PAUSED = 2
    }
}
