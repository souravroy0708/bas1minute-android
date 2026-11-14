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

    /**
     * The app currently in the foreground, worked out two ways and cross-checked. On several OEM builds
     * (OnePlus's OxygenOS confirmed) Android's fine-grained usage EVENTS can be delivered several seconds
     * late — or occasionally not at all — even though the aggregate usage stats (lastTimeUsed) stay accurate.
     * This replays the whole [lookbackMs] window fresh on every call, rather than advancing a cursor forward:
     * an advancing cursor permanently loses any event that becomes queryable only after the cursor has already
     * moved past its timestamp, which is exactly the failure mode "nothing ever blocks" on those phones.
     */
    fun currentForegroundApp(nowMs: Long, lookbackMs: Long = 20_000L): String? {
        val from = nowMs - lookbackMs
        var fromEvents: String? = null
        runCatching {
            val ev = usm.queryEvents(from, nowMs)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                when (e.eventType) {
                    RESUMED -> fromEvents = e.packageName
                    PAUSED -> if (e.packageName == fromEvents) fromEvents = null
                }
            }
        }
        if (fromEvents != null) return fromEvents

        // Fallback: aggregate stats, which some phones keep accurate even when the event stream above is empty.
        val fromStats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, from, nowMs)
                ?.filter { it.lastTimeUsed > from }
                ?.maxByOrNull { it.lastTimeUsed }?.packageName
        }.getOrNull()
        return fromStats
    }

    companion object {
        // ACTIVITY_RESUMED / ACTIVITY_PAUSED (API 29) share values with MOVE_TO_FOREGROUND / BACKGROUND (API 21).
        const val RESUMED = 1
        const val PAUSED = 2
    }
}
