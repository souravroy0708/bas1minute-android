package com.bas1minute.sync.detox

/*
 * "App Timers": a small daily time budget per APP (Instagram, YouTube, Facebook — the member's own list),
 * resetting at midnight. Android's own UsageStatsManager already tracks exact foreground time per app
 * (UsageReader.totalTodayMs), so there's nothing to accumulate here — only the budget math.
 *
 * What happens when the budget is spent changed in the minimal-permission redesign: instead of covering the
 * app with a full-screen window ("Display over other apps"), the member gets a reminder notification with a
 * one-tap "Close it" (goes to the home screen) — at most [MAX_REMINDERS_PER_APP_PER_DAY] times per app per
 * day. Anyone who wants a real lock is sent to Android's own Digital Wellbeing app timers, which grey the app
 * out at the OS level and need no permission from Bas 1 Minute at all (see DigitalWellbeing.kt).
 *
 * Plain Kotlin, no Android imports, so it's unit tested.
 */
object AppTimerLogic {
    const val DEFAULT_DAILY_BUDGET_MIN = 30
    const val MIN_BUDGET_MIN = 5
    const val MAX_BUDGET_MIN = 120
    const val MAX_APPS = 15
    /** Checks ride on the shared 15-minute tick, so this means "at the limit, and twice more if still on it". */
    const val MAX_REMINDERS_PER_APP_PER_DAY = 3

    /** A smaller, curated starting point than AppRules.SOCIAL's full list — App Timers is meant to be a
     * deliberate, short per-app list a member actually reviews, not every app the Social category recognises. */
    val DEFAULT_APPS = setOf(
        "com.instagram.android", "com.facebook.katana", "com.google.android.youtube", "com.twitter.android",
        "com.zhiliaoapp.musically", "com.snapchat.android", "com.reddit.frontpage", "com.linkedin.android",
    )

    fun clampBudget(minutes: Int): Int = minutes.coerceIn(MIN_BUDGET_MIN, MAX_BUDGET_MIN)

    fun isOverBudget(usedMs: Long, budgetMinutes: Int): Boolean = usedMs >= budgetMinutes * 60_000L

    fun remainingLabel(usedMs: Long, budgetMinutes: Int): String {
        val remainingMin = ((budgetMinutes * 60_000L - usedMs) / 60_000L).coerceAtLeast(0)
        return if (remainingMin <= 0) "Used up for today" else "$remainingMin of $budgetMinutes min left today"
    }

    /** Whether another "time's up" reminder is due, given how many this app has already had today. */
    fun shouldRemind(usedMs: Long, budgetMinutes: Int, remindersSentToday: Int): Boolean =
        isOverBudget(usedMs, budgetMinutes) && remindersSentToday < MAX_REMINDERS_PER_APP_PER_DAY
}
