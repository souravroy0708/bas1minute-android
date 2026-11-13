package com.bas1minute.sync.detox

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/** Entry points the UI, the service and the boot receiver share. */
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

    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun isReady(ctx: Context) = hasUsageAccess(ctx) && canDrawOverlays(ctx)

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    /** Starts a promise for [minutes]. Returns null if the permissions the block needs are missing. */
    fun start(ctx: Context, minutes: Int): DetoxSession? {
        if (!isReady(ctx)) return null
        val store = DetoxStore.get(ctx)
        store.lastMinutes = minutes
        val s = store.start(System.currentTimeMillis(), minutes)
        startService(ctx)
        return s
    }

    /** User chose to stop before time: counted as a broken promise. */
    fun endEarly(ctx: Context) {
        val store = DetoxStore.get(ctx)
        val s = store.active() ?: return
        store.update(DetoxLogic.breakNow(s, System.currentTimeMillis(), BreakReason.ENDED_EARLY))
        ctx.stopService(Intent(ctx, DetoxService::class.java))
    }

    /**
     * Call on app open, on boot and from the service. Closes a session whose time is up (checking real usage,
     * so force-stopping the app doesn't fake a kept promise), breaks it if permissions were removed, and
     * restarts the blocking service if Android killed it.
     */
    fun reconcile(ctx: Context) {
        val store = DetoxStore.get(ctx)
        val s = store.active() ?: return
        val now = System.currentTimeMillis()
        when {
            now >= s.plannedEndMs -> finishWithHistory(ctx, s, now)
            !isReady(ctx) -> store.update(DetoxLogic.breakNow(s, now, BreakReason.PERMISSION_REVOKED))
            !isServiceRunning(ctx) -> startService(ctx)
        }
    }

    fun finishWithHistory(ctx: Context, s: DetoxSession, now: Long): DetoxSession {
        val rules = AppRules(ctx, DetoxStore.get(ctx).extraAllowed)
        val blocked = if (hasUsageAccess(ctx)) {
            UsageReader(ctx).intervals(s.startMs, minOf(now, s.plannedEndMs), rules::isBlocked)
        } else emptyList()
        val done = DetoxLogic.finish(s, now, blocked)
        DetoxStore.get(ctx).update(done)
        return done
    }

    fun startService(ctx: Context) {
        val i = Intent(ctx, DetoxService::class.java)
        runCatching { ctx.startForegroundService(i) }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // getRunningServices still reports the caller's own services.
        return am.getRunningServices(50).any { it.service.className == DetoxService::class.java.name }
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
        val rules = AppRules(ctx, store.extraAllowed)
        val to = minOf(nowMs, dayStartMs + DetoxLogic.DAY)
        val distracting = UsageReader(ctx).intervals(dayStartMs, to, rules::isDistracting)
        return DetoxLogic.hourCells(dayStartMs, store.dayFirstHour, 24, nowMs, distracting)
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
