package com.bas1minute.sync.achieve

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Badges and personal records, worked out on the phone from the daily step history (LocalDb). Nothing is
 * stored separately — they're recomputed from the days, so they're always right, even after importing older
 * steps. Plain Kotlin, no Android imports, so it's unit tested.
 */
data class StepDay(val date: LocalDate, val steps: Long)

data class Badge(
    val id: String, val emoji: String, val title: String, val detail: String,
    val earnedOn: LocalDate?,          // null = not yet
    val progress: Float,               // 0..1 toward it (1 when earned)
)

data class Records(
    val bestDay: StepDay?, val bestWeekStart: LocalDate?, val bestWeekSteps: Long,
    val longestStreak: Int, val currentStreak: Int, val lifetimeSteps: Long, val goalDays: Int,
)

object Achievements {

    private data class Def(val id: String, val emoji: String, val title: String, val detail: String, val target: Long, val kind: Kind)
    private enum class Kind { DAY_STEPS, STREAK, LIFETIME, GOAL_DAYS, FIRST_GOAL }

    private val DEFS = listOf(
        Def("first_goal", "🎯", "First goal day", "Reach your daily step goal once", 1, Kind.FIRST_GOAL),
        Def("day10k", "🔟", "10K day", "10,000 steps in one day", 10_000, Kind.DAY_STEPS),
        Def("day15k", "🚀", "15K day", "15,000 steps in one day", 15_000, Kind.DAY_STEPS),
        Def("day20k", "🏔️", "20K day", "20,000 steps in one day", 20_000, Kind.DAY_STEPS),
        Def("streak7", "🔥", "7-day streak", "Goal 7 days in a row", 7, Kind.STREAK),
        Def("streak30", "💪", "30-day streak", "Goal 30 days in a row", 30, Kind.STREAK),
        Def("streak100", "👑", "100-day streak", "Goal 100 days in a row", 100, Kind.STREAK),
        Def("goal50", "⭐", "50 goal days", "Reach your goal on 50 days", 50, Kind.GOAL_DAYS),
        Def("goal100", "🌟", "100 goal days", "Reach your goal on 100 days", 100, Kind.GOAL_DAYS),
        Def("life100k", "👟", "1 lakh steps", "100,000 steps in total", 100_000, Kind.LIFETIME),
        Def("life1m", "🥇", "10 lakh steps", "1,000,000 steps in total", 1_000_000, Kind.LIFETIME),
        Def("life5m", "🏆", "50 lakh steps", "5,000,000 steps in total", 5_000_000, Kind.LIFETIME),
    )

    /** [days] in any order; days with no steps may be missing. [goal] is today's step goal. */
    fun badges(days: List<StepDay>, goal: Int): List<Badge> {
        val sorted = days.filter { it.steps > 0 }.sortedBy { it.date }
        val earned = HashMap<String, LocalDate>()
        var lifetime = 0L; var goalDays = 0; var streak = 0; var prev: LocalDate? = null
        var bestDaySteps = 0L; var bestStreak = 0
        for (d in sorted) {
            lifetime += d.steps
            bestDaySteps = maxOf(bestDaySteps, d.steps)
            val hit = d.steps >= goal
            streak = when { !hit -> 0; prev != null && d.date == prev!!.plusDays(1) && streak > 0 -> streak + 1; else -> 1 }
            if (!hit) streak = 0
            bestStreak = maxOf(bestStreak, streak)
            if (hit) goalDays++
            prev = d.date
            for (def in DEFS) {
                if (def.id in earned) continue
                val ok = when (def.kind) {
                    Kind.DAY_STEPS -> d.steps >= def.target
                    Kind.STREAK -> streak >= def.target
                    Kind.LIFETIME -> lifetime >= def.target
                    Kind.GOAL_DAYS -> goalDays >= def.target
                    Kind.FIRST_GOAL -> hit
                }
                if (ok) earned[def.id] = d.date
            }
        }
        return DEFS.map { def ->
            val on = earned[def.id]
            val now = when (def.kind) {
                Kind.DAY_STEPS -> bestDaySteps
                Kind.STREAK -> bestStreak.toLong()
                Kind.LIFETIME -> lifetime
                Kind.GOAL_DAYS -> goalDays.toLong()
                Kind.FIRST_GOAL -> if (goalDays > 0) 1L else 0L
            }
            Badge(def.id, def.emoji, def.title, def.detail, on, if (on != null) 1f else (now.toFloat() / def.target).coerceIn(0f, 0.99f))
        }
    }

    fun records(days: List<StepDay>, goal: Int, today: LocalDate): Records {
        val sorted = days.filter { it.steps > 0 }.sortedBy { it.date }
        val bestDay = sorted.maxByOrNull { it.steps }
        val weeks = sorted.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.mapValues { e -> e.value.sumOf { it.steps } }
        val bestWeek = weeks.maxByOrNull { it.value }
        var longest = 0; var run = 0; var prev: LocalDate? = null
        for (d in sorted) {
            run = if (d.steps >= goal) (if (prev != null && d.date == prev!!.plusDays(1) && run > 0) run + 1 else 1) else 0
            longest = maxOf(longest, run); prev = d.date
        }
        // Current streak: counts back from today (today still in progress, so it doesn't break the streak)
        val byDate = sorted.associateBy { it.date }
        var current = 0
        var day = if ((byDate[today]?.steps ?: 0) >= goal) today else today.minusDays(1)
        while ((byDate[day]?.steps ?: 0) >= goal) { current++; day = day.minusDays(1) }
        return Records(bestDay, bestWeek?.key, bestWeek?.value ?: 0L, longest, current, sorted.sumOf { it.steps }, sorted.count { it.steps >= goal })
    }

    /**
     * Suggested daily goal, like Google Fit's: about 10% above the last 4 weeks' average, rounded to 500, between
     * 3,000 and 20,000. Null until there are 14 days of data, or when it would barely differ from today's goal.
     */
    fun suggestedGoal(days: List<StepDay>, goal: Int, today: LocalDate): Int? {
        val recent = days.filter { it.steps > 0 && !it.date.isAfter(today.minusDays(1)) && it.date.isAfter(today.minusDays(29)) }
        if (recent.size < 14) return null
        val avg = recent.sumOf { it.steps } / recent.size
        val target = (((avg * 1.1) / 500).toLong() * 500).toInt().coerceIn(3_000, 20_000)
        return if (kotlin.math.abs(target - goal) < 500) null else target
    }
}
