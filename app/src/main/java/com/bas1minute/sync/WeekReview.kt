package com.bas1minute.sync

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** Numbers for the weekly story: last 7 days vs the 7 before, all from this phone. */
data class WeekReview(
    val steps: Long, val prevSteps: Long, val km: String, val goalDays: Int, val streak: Int,
    val bestDay: LocalDate?, val bestSteps: Long, val screenAvg: Int?, val prevScreenAvg: Int?,
    val bursts: Int, val calm: Int,
) {
    companion object {
        /** "2026-W44", so the review is shown once per week. */
        fun weekKey(d: LocalDate = LocalDate.now()) = "${d.get(IsoFields.WEEK_BASED_YEAR)}-W${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

        /** Ready on Sunday and Monday. */
        fun isReviewDay(d: LocalDate = LocalDate.now()) = d.dayOfWeek == DayOfWeek.SUNDAY || d.dayOfWeek == DayOfWeek.MONDAY

        fun build(db: LocalDb, prefs: Prefs, today: LocalDate = LocalDate.now()): WeekReview {
            // The week that just ended: Monday..Sunday (on Monday, last week; on Sunday, this week so far)
            val end = if (today.dayOfWeek == DayOfWeek.SUNDAY) today else today.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY))
            val start = end.minusDays(6)
            val week = db.days(start, end)
            val prev = db.days(start.minusDays(7), start.minusDays(1))
            val steps = week.sumOf { it.steps ?: 0L }
            val best = week.maxByOrNull { it.steps ?: 0L }
            val screen = week.mapNotNull { it.socialMinutes }
            val prevScreen = prev.mapNotNull { it.socialMinutes }
            val last = db.days(end.minusDays(29), end).associateBy { it.date }
            return WeekReview(
                steps = steps, prevSteps = prev.sumOf { it.steps ?: 0L },
                km = Distance.format(Distance.km(steps, prefs.stepLengthCm)),
                goalDays = week.count { (it.steps ?: 0L) >= prefs.stepGoal },
                streak = com.bas1minute.sync.ui.TwentyOneStats.streak((29 downTo 0).map { last[end.minusDays(it.toLong())]?.steps }, prefs.stepGoal),
                bestDay = best?.takeIf { (it.steps ?: 0L) > 0 }?.date, bestSteps = best?.steps ?: 0L,
                screenAvg = screen.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                prevScreenAvg = prevScreen.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                bursts = week.sumOf { it.bursts }, calm = week.sumOf { it.calmMinutes },
            )
        }
    }
}
