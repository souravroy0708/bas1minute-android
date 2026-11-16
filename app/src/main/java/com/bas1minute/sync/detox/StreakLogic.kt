package com.bas1minute.sync.detox

import java.util.Calendar
import java.util.TimeZone

/**
 * Streaks and badges for focused detox, kept separate from DetoxLogic's kept/guilty bookkeeping.
 *
 * A "focus day" is a single kept detox session of at least FOCUS_MINUTES (3 h), entirely inside the
 * WINDOW_START_HOUR–WINDOW_END_HOUR window (10 AM–10 PM) of one local calendar day. A session that
 * crosses midnight or starts/ends outside the window doesn't qualify, even if it's long and kept.
 * The streak counts consecutive calendar days, counting back from today, that have a qualifying session.
 */
object StreakLogic {

    data class Badge(val days: Int, val id: String)

    fun qualifies(s: DetoxSession, zone: TimeZone = TimeZone.getDefault()): Boolean {
        if (s.outcome != Outcome.KEPT) return false
        if (s.plannedMinutes < DetoxLogic.FOCUS_MINUTES) return false
        val startCal = Calendar.getInstance(zone).apply { timeInMillis = s.startMs }
        val endCal = Calendar.getInstance(zone).apply { timeInMillis = s.plannedEndMs }
        if (startCal.get(Calendar.YEAR) != endCal.get(Calendar.YEAR) ||
            startCal.get(Calendar.DAY_OF_YEAR) != endCal.get(Calendar.DAY_OF_YEAR)
        ) return false
        val startHour = startCal.get(Calendar.HOUR_OF_DAY) + startCal.get(Calendar.MINUTE) / 60.0
        val endHour = endCal.get(Calendar.HOUR_OF_DAY) + endCal.get(Calendar.MINUTE) / 60.0
        return startHour >= DetoxLogic.WINDOW_START_HOUR && endHour <= DetoxLogic.WINDOW_END_HOUR
    }

    private fun localDayKey(ms: Long, zone: TimeZone): Int {
        val c = Calendar.getInstance(zone).apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Current streak as of [nowMs]: consecutive days (today backwards) with at least one qualifying session.
     * Today without one yet doesn't break the streak (the day isn't over), but yesterday without one does.
     */
    fun currentStreak(sessions: List<DetoxSession>, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val qualifyingDays = sessions.filter { qualifies(it, zone) }.map { localDayKey(it.startMs, zone) }.toSet()
        val cursor = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        var streak = 0
        var isToday = true
        repeat(400) {   // hard cap: nobody has a 400-day run to scan, this just bounds the loop
            val key = cursor.get(Calendar.YEAR) * 1000 + cursor.get(Calendar.DAY_OF_YEAR)
            val has = key in qualifyingDays
            if (has) {
                streak++
            } else if (!isToday) {
                return streak   // yesterday (or earlier) with no qualifying day ends the streak
            }
            // today with no session yet isn't a break: the day isn't over
            isToday = false
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    /** Longest streak ever reached, scanning all qualifying days on record. */
    fun bestStreak(sessions: List<DetoxSession>, zone: TimeZone = TimeZone.getDefault()): Int {
        val days = sessions.filter { qualifies(it, zone) }.map { localDayKey(it.startMs, zone) }.toSortedSet()
        if (days.isEmpty()) return 0
        var best = 1; var run = 1
        val list = days.toList()
        for (i in 1 until list.size) {
            // Local day keys are YEAR*1000+DAY_OF_YEAR, so +1 is consecutive except across a year boundary,
            // which bestStreak tolerates by falling back to a day-count check.
            val consecutive = list[i] - list[i - 1] == 1 || isNextCalendarDay(list[i - 1], list[i])
            run = if (consecutive) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }

    private fun isNextCalendarDay(a: Int, b: Int): Boolean {
        val yearA = a / 1000; val dayA = a % 1000
        val yearB = b / 1000; val dayB = b % 1000
        if (yearB != yearA + 1) return false
        val cal = Calendar.getInstance().apply { clear(); set(Calendar.YEAR, yearA); set(Calendar.DAY_OF_YEAR, dayA) }
        val lastDayOfYearA = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        return dayA == lastDayOfYearA && dayB == 1
    }

    /** Total number of distinct qualifying days ever, for "all-time focus days" and badges based on total. */
    fun totalFocusDays(sessions: List<DetoxSession>, zone: TimeZone = TimeZone.getDefault()): Int =
        sessions.filter { qualifies(it, zone) }.map { localDayKey(it.startMs, zone) }.toSet().size

    /** Badge ids reached by this streak length, highest first. Empty below the first threshold. */
    fun badgesFor(streak: Int): List<Badge> =
        DetoxLogic.BADGE_DAYS.filter { streak >= it }.sortedDescending().map { Badge(it, "streak_$it") }

    /** The next badge not yet reached, and how many more days to it. Null once every badge is earned. */
    fun nextBadge(streak: Int): Pair<Badge, Int>? =
        DetoxLogic.BADGE_DAYS.firstOrNull { it > streak }?.let { Badge(it, "streak_$it") to (it - streak) }

    fun badgeLabel(days: Int): String = when (days) {
        3 -> "3-Day Focus"
        7 -> "1-Week Focus"
        21 -> "21-Day Focus"
        50 -> "50-Day Focus"
        100 -> "100-Day Focus"
        else -> "$days-Day Focus"
    }
}
