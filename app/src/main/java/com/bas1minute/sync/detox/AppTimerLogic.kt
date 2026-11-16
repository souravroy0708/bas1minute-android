package com.bas1minute.sync.detox

/*
 * "App Timers": a small daily time budget per APP (Instagram, TikTok, Facebook — the member's own list),
 * blocked once it's spent, resetting at midnight — the same promise Site Guard already makes for specific
 * WEBSITES, now for the apps themselves. This is Google's own Digital Wellbeing flagship feature (and
 * Opal's, and AppBlock's); the gap it closes here is that Session Guard could only ever NUDGE someone using
 * Instagram's native app past 15 minutes, never actually stop them the way Site Guard already stops
 * facebook.com in a browser.
 *
 * Deliberately thin: Android's own UsageStatsManager already tracks exact foreground time per app
 * (UsageReader.totalTodayMs), so unlike Site Guard — which has to build its own per-website total one small
 * sample at a time, because Android has no concept of "time on one website" — there's nothing to accumulate
 * here. The budget math itself (is a total over budget, how much is left) is the same either way, so it's
 * shared with Site Guard's own SiteGuardLogic rather than duplicated.
 */
object AppTimerLogic {
    const val DEFAULT_DAILY_BUDGET_MIN = 30
    const val MAX_APPS = 15

    /** A smaller, curated starting point than AppRules.SOCIAL's full list — App Timers is meant to be a
     * deliberate, short per-app list a member actually reviews, not every app the Social category recognises. */
    val DEFAULT_APPS = setOf(
        "com.instagram.android", "com.facebook.katana", "com.google.android.youtube", "com.twitter.android",
        "com.zhiliaoapp.musically", "com.snapchat.android", "com.reddit.frontpage", "com.linkedin.android",
    )

    fun clampBudget(minutes: Int): Int = com.bas1minute.sync.siteguard.SiteGuardLogic.clampBudget(minutes)
    fun isOverBudget(usedMs: Long, budgetMinutes: Int): Boolean =
        com.bas1minute.sync.siteguard.SiteGuardLogic.isOverBudget(usedMs, budgetMinutes)
    fun remainingLabel(usedMs: Long, budgetMinutes: Int): String =
        com.bas1minute.sync.siteguard.SiteGuardLogic.remainingLabel(usedMs, budgetMinutes)
}
