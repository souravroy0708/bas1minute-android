package com.bas1minute.sync.goal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/*
 * The fourth pillar: Goals. Not a new kind of tracking — a thin layer on top of what Bas 1 Minute already
 * measures (steps, sleep, screen time, detox) plus simple yes/no goals for things it can't measure (study,
 * read, no phone after 10). One goal is "met" or not for a given day; a streak and a weekly/monthly story
 * come from that. Plain Kotlin, no Android imports, so it's unit tested.
 */

enum class GoalKind(val id: String) {
    Steps("steps"),       // met when day.steps >= target
    Sleep("sleep"),       // met when day.sleepMinutes >= target (minutes)
    ScreenTime("screen"), // met when day.socialMinutes <= target (lower is better)
    Detox("detox"),       // met when a kept detox session that day is >= target minutes
    Custom("custom");     // met only by the person's own daily check-off

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Custom }
}

data class Goal(
    val id: Long,
    val kind: GoalKind,
    val title: String,
    /** Meaning depends on kind: steps count, minutes of sleep, minutes of screen time, minutes of detox. Ignored for Custom. */
    val target: Int = 0,
    val createdAt: Long,
    val archivedAt: Long? = null,
) {
    val active: Boolean get() = archivedAt == null
}

/** One day's inputs the measurable goal kinds read from. Everything optional: a day with no data meets nothing. */
data class DaySignal(
    val date: LocalDate,
    val steps: Long? = null,
    val sleepMinutes: Int? = null,
    val socialMinutes: Int? = null,
    /** Minutes of the longest kept detox session that started this day, 0 if none. */
    val bestKeptDetoxMinutes: Int = 0,
)

/** A custom goal's own per-day check-off (ignored for every other kind). */
data class Checkin(val goalId: Long, val date: LocalDate, val done: Boolean)

data class GoalDayStatus(val goal: Goal, val date: LocalDate, val met: Boolean, val trackable: Boolean)

data class GoalWeek(val goal: Goal, val daysMet: Int, val daysTracked: Int, val streak: Int)

data class GoalMonth(val goal: Goal, val daysMet: Int, val daysInMonth: Int, val best: Boolean)

object GoalLogic {
    const val MAX_ACTIVE_GOALS = 5
    const val MAX_TITLE_LEN = 60

    /** Suggested presets, with a sensible default target. The person can still change the target or title. */
    data class Preset(val kind: GoalKind, val title: String, val defaultTarget: Int)

    fun presets(stepGoal: Int, screenLimitMinutes: Int): List<Preset> = listOf(
        Preset(GoalKind.Steps, "Reach my step goal", stepGoal),
        Preset(GoalKind.Sleep, "Sleep 7+ hours", 7 * 60),
        Preset(GoalKind.ScreenTime, "Stay under my screen limit", screenLimitMinutes),
        Preset(GoalKind.Detox, "A 2-hour focus session", 120),
        Preset(GoalKind.Custom, "Study for my exam", 0),
        Preset(GoalKind.Custom, "No phone after 10 PM", 0),
        Preset(GoalKind.Custom, "Read for 20 minutes", 0),
    )

    /** Whether [goal] has any signal to judge on [date] at all (false ≠ failed; it means "no data yet"). */
    fun isTrackable(goal: Goal, signal: DaySignal?, checkin: Checkin?): Boolean = when (goal.kind) {
        GoalKind.Steps -> signal?.steps != null
        GoalKind.Sleep -> signal?.sleepMinutes != null
        GoalKind.ScreenTime -> signal?.socialMinutes != null
        GoalKind.Detox -> true   // 0 kept minutes is a real, trackable "not yet" for detox
        GoalKind.Custom -> checkin != null
    }

    /** Was [goal] met on the day described by [signal] (and, for Custom, [checkin])? */
    fun isMet(goal: Goal, signal: DaySignal?, checkin: Checkin?): Boolean = when (goal.kind) {
        GoalKind.Steps -> (signal?.steps ?: 0) >= goal.target
        GoalKind.Sleep -> (signal?.sleepMinutes ?: -1) >= goal.target
        GoalKind.ScreenTime -> signal?.socialMinutes?.let { it <= goal.target } ?: false
        GoalKind.Detox -> (signal?.bestKeptDetoxMinutes ?: 0) >= goal.target
        GoalKind.Custom -> checkin?.done == true
    }

    fun dayStatus(goal: Goal, date: LocalDate, signal: DaySignal?, checkin: Checkin?): GoalDayStatus =
        GoalDayStatus(goal, date, isMet(goal, signal, checkin), isTrackable(goal, signal, checkin))

    /**
     * Current streak for one goal: consecutive days (today backwards) that were met. A day with no data yet
     * (not trackable) is skipped only if it's today — the day isn't over. Any earlier untracked or unmet day
     * ends the streak.
     */
    fun streak(goal: Goal, today: LocalDate, statusFor: (LocalDate) -> GoalDayStatus): Int {
        var streak = 0
        var day = today
        var isToday = true
        repeat(400) {
            val s = statusFor(day)
            when {
                s.met -> streak++
                isToday && !s.trackable -> { /* today, nothing yet: not a break */ }
                else -> return streak
            }
            isToday = false
            day = day.minusDays(1)
        }
        return streak
    }

    /** This calendar week (Monday start) up to and including [today]. */
    fun week(goal: Goal, today: LocalDate, statusFor: (LocalDate) -> GoalDayStatus): GoalWeek {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var met = 0; var tracked = 0
        var d = monday
        while (!d.isAfter(today)) {
            val s = statusFor(d)
            if (s.trackable) { tracked++; if (s.met) met++ }
            d = d.plusDays(1)
        }
        return GoalWeek(goal, met, tracked, streak(goal, today, statusFor))
    }

    /** This calendar month up to and including [today], for the monthly reflection. */
    fun month(goal: Goal, today: LocalDate, statusFor: (LocalDate) -> GoalDayStatus): GoalMonth {
        var met = 0
        var d = today.withDayOfMonth(1)
        while (!d.isAfter(today)) { if (statusFor(d).met) met++; d = d.plusDays(1) }
        return GoalMonth(goal, met, today.dayOfMonth, best = false)
    }

    /** Marks the goal(s) with the most days met this month as "best", for the reflection screen. */
    fun markBest(months: List<GoalMonth>): List<GoalMonth> {
        val top = months.maxOfOrNull { it.daysMet } ?: return months
        return months.map { if (it.daysMet == top && top > 0) it.copy(best = true) else it }
    }

    fun clampTarget(kind: GoalKind, value: Int): Int = when (kind) {
        GoalKind.Steps -> value.coerceIn(1_000, 30_000)
        GoalKind.Sleep -> value.coerceIn(4 * 60, 10 * 60)
        GoalKind.ScreenTime -> value.coerceIn(15, 8 * 60)
        GoalKind.Detox -> value.coerceIn(15, 8 * 60)
        GoalKind.Custom -> 0
    }
}
