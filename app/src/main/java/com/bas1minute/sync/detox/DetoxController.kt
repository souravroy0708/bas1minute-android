package com.bas1minute.sync.detox

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/** Entry points the UI, the detox watcher (DetoxWatch) and the 15-minute tick share. */
object DetoxController {

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The only special access detox needs. No "Display over other apps", no Accessibility, no always-on service. */
    fun isReady(ctx: Context) = hasUsageAccess(ctx)

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Starts a promise for [minutes]. Returns null if Usage access is missing. */
    fun start(ctx: Context, minutes: Int, why: String = ""): DetoxSession? {
        if (!isReady(ctx)) return null
        val store = DetoxStore.get(ctx)
        store.lastMinutes = minutes
        if (why.isNotBlank()) store.recentWhys = (listOf(why.trim()) + store.recentWhys).distinct()
        val s = store.start(System.currentTimeMillis(), minutes, why)
        DetoxWatch.begin(ctx, s)
        return s
    }

    /** User chose to stop before time: counted as a broken promise. */
    fun endEarly(ctx: Context) {
        val store = DetoxStore.get(ctx)
        val s = store.active() ?: return
        store.update(DetoxLogic.breakNow(s, System.currentTimeMillis(), BreakReason.ENDED_EARLY))
        DetoxWatch.stop(ctx)
    }

    /**
     * Call on app open and from the shared 15-minute tick. Closes a session whose time is up (checking real
     * usage, so force-stopping the app doesn't fake a kept promise), breaks it if Usage access was removed, and
     * otherwise makes sure the reminder jobs exist (e.g. after the phone restarted mid-detox).
     */
    fun reconcile(ctx: Context) {
        val store = DetoxStore.get(ctx)
        val s = store.active() ?: return
        val now = System.currentTimeMillis()
        when {
            now >= s.plannedEndMs -> { finishWithHistory(ctx, s, now); DetoxWatch.stop(ctx) }
            !isReady(ctx) -> { store.update(DetoxLogic.breakNow(s, now, BreakReason.PERMISSION_REVOKED)); DetoxWatch.stop(ctx) }
            else -> DetoxWatch.ensure(ctx, s)
        }
    }

    fun finishWithHistory(ctx: Context, s: DetoxSession, now: Long): DetoxSession {
        val store = DetoxStore.get(ctx)
        val rules = AppRules(ctx, store.blockCategories, store.blockedApps, store.allowedOverride)
        val blocked = if (hasUsageAccess(ctx)) {
            UsageReader(ctx).intervals(s.startMs, minOf(now, s.plannedEndMs), rules::isBlocked)
        } else emptyList()
        val done = DetoxLogic.finish(s, now, blocked)
        DetoxStore.get(ctx).update(done)
        return done
    }

    /** Local midnight of the day containing [ms]. */
    fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Clean-hour cells for the day starting at [dayStartMs]. Needs Usage access; empty without it. */
    fun hourCells(ctx: Context, dayStartMs: Long, nowMs: Long): List<HourCell> {
        if (!hasUsageAccess(ctx)) return emptyList()
        val store = DetoxStore.get(ctx)
        // isDistracting never looks at block choices, only the Social/Browsers reference lists — empty sets are fine.
        val rules = AppRules(ctx, emptySet(), emptySet())
        val to = minOf(nowMs, dayStartMs + DetoxLogic.DAY)
        val distracting = UsageReader(ctx).intervals(dayStartMs, to, rules::isDistracting)
        // The full calendar day (12am-11pm), not just store.dayFirstHour onward — a member specifically
        // asked to see the overnight hours too, since phone use between 11pm and 6am is exactly what the
        // UI should be calling out (see HourBox's sleep-window highlighting in DetoxScreen.kt).
        return DetoxLogic.hourCells(dayStartMs, 0, 24, nowMs, distracting)
    }

    /** Clean hours for each of the last [days] days, oldest first (Android keeps about a week of events). */
    fun cleanHoursByDay(ctx: Context, nowMs: Long, days: Int = 7): List<Pair<Long, Int>> {
        val today = dayStart(nowMs)
        return (days - 1 downTo 0).map { back ->
            val start = dayStart(today - back * DetoxLogic.DAY + DetoxLogic.HOUR) // +1h guards DST shifts
            start to DetoxLogic.cleanHours(hourCells(ctx, start, nowMs))
        }
    }
}
