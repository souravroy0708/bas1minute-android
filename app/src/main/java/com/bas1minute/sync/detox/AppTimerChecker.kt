package com.bas1minute.sync.detox

import com.bas1minute.sync.Prefs
import android.content.Context

/**
 * If the app in the foreground right now is on the member's App Timer list and Android's own usage stats
 * say its total time today is already at or past the daily budget, AppTimerOverlay covers it.
 *
 * No periodic job of its own: this rides on the single shared 15-minute tick in phone/BatteryLog.kt
 * (BatteryWorker) — see its own doc for why, and SessionGuardChecker for the same pattern. [reader] and [fg]
 * are the same already-looked-up values that checker also uses, so the foreground app is only queried once
 * per tick, not once per feature. A short lag (up to ~15 minutes past the exact budget) is an accepted
 * trade-off here, same as for Session Guard and Schedules — a persistent always-on service could catch it
 * immediately, but only at the cost of its own permanent notification.
 */
object AppTimerChecker {
    fun check(ctx: Context, reader: UsageReader, fg: String?, now: Long) {
        val prefs = Prefs(ctx)
        if (!prefs.appTimerEnabled || !prefs.setupDone) return
        if (fg == null || fg !in prefs.appTimerApps) return

        val usedMs = reader.totalTodayMs(fg, now)
        if (!AppTimerLogic.isOverBudget(usedMs, prefs.appTimerBudgetMinutes)) return

        val rules = AppRules(ctx, emptySet(), emptySet())   // only .label(pkg) is used here; block choices don't apply
        AppTimerOverlay.show(ctx, fg, rules.label(fg))
    }
}
