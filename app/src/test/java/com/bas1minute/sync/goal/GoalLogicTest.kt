package com.bas1minute.sync.goal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalLogicTest {
    private val day0 = LocalDate.of(2026, 11, 9)   // a Monday

    private fun stepsGoal(target: Int = 7000) = Goal(1, "u1", GoalKind.Steps, "Steps", target, createdAt = 0)
    private fun sleepGoal(target: Int = 420) = Goal(2, "u2", GoalKind.Sleep, "Sleep", target, createdAt = 0)
    private fun screenGoal(target: Int = 120) = Goal(3, "u3", GoalKind.ScreenTime, "Screen", target, createdAt = 0)
    private fun detoxGoal(target: Int = 120) = Goal(4, "u4", GoalKind.Detox, "Detox", target, createdAt = 0)
    private fun customGoal() = Goal(5, "u5", GoalKind.Custom, "Study", createdAt = 0)

    @Test fun steps_metAtOrAboveTarget() {
        val g = stepsGoal(7000)
        assertTrue(GoalLogic.isMet(g, DaySignal(day0, steps = 7000), null))
        assertTrue(GoalLogic.isMet(g, DaySignal(day0, steps = 9000), null))
        assertFalse(GoalLogic.isMet(g, DaySignal(day0, steps = 6999), null))
        assertFalse(GoalLogic.isTrackable(g, DaySignal(day0, steps = null), null))
    }

    @Test fun sleep_metAtOrAboveTargetMinutes() {
        val g = sleepGoal(420)
        assertTrue(GoalLogic.isMet(g, DaySignal(day0, sleepMinutes = 420), null))
        assertFalse(GoalLogic.isMet(g, DaySignal(day0, sleepMinutes = 419), null))
        assertFalse(GoalLogic.isTrackable(g, DaySignal(day0, sleepMinutes = null), null))
    }

    @Test fun screenTime_metAtOrBelowTarget_lowerIsBetter() {
        val g = screenGoal(120)
        assertTrue(GoalLogic.isMet(g, DaySignal(day0, socialMinutes = 90), null))
        assertTrue(GoalLogic.isMet(g, DaySignal(day0, socialMinutes = 120), null))
        assertFalse(GoalLogic.isMet(g, DaySignal(day0, socialMinutes = 121), null))
        assertFalse(GoalLogic.isTrackable(g, DaySignal(day0, socialMinutes = null), null))
    }

    @Test fun detox_metByBestKeptSessionOfTheDay_alwaysTrackable() {
        val g = detoxGoal(120)
        assertTrue(GoalLogic.isMet(g, DaySignal(day0, bestKeptDetoxMinutes = 150), null))
        assertFalse(GoalLogic.isMet(g, DaySignal(day0, bestKeptDetoxMinutes = 90), null))
        assertTrue(GoalLogic.isTrackable(g, DaySignal(day0, bestKeptDetoxMinutes = 0), null))
    }

    @Test fun custom_metOnlyByCheckin_untrackedWithoutOne() {
        val g = customGoal()
        assertFalse(GoalLogic.isTrackable(g, null, null))
        assertTrue(GoalLogic.isTrackable(g, null, Checkin(g.id, day0, done = false)))
        assertTrue(GoalLogic.isMet(g, null, Checkin(g.id, day0, done = true)))
        assertFalse(GoalLogic.isMet(g, null, Checkin(g.id, day0, done = false)))
    }

    @Test fun streak_countsBackAndTodayWithNoDataIsNotABreak() {
        val g = stepsGoal(7000)
        val met = setOf(day0, day0.minusDays(1), day0.minusDays(2))
        fun statusFor(d: LocalDate) = GoalLogic.dayStatus(g, d, DaySignal(d, steps = if (d in met) 7000 else if (d == day0) null else 0), null)
        assertEquals(3, GoalLogic.streak(g, day0, ::statusFor))
    }

    @Test fun streak_gapEndsIt() {
        val g = stepsGoal(7000)
        val met = setOf(day0, day0.minusDays(2))   // yesterday missing
        fun statusFor(d: LocalDate) = GoalLogic.dayStatus(g, d, DaySignal(d, steps = if (d in met) 7000 else 0), null)
        assertEquals(1, GoalLogic.streak(g, day0, ::statusFor))
    }

    @Test fun week_countsOnlyTrackableDaysFromMonday() {
        val g = stepsGoal(7000)
        // Monday..Wednesday tracked (2 met, 1 not), Thursday has no data (not trackable), today = Wednesday
        val wed = day0.plusDays(2)
        fun statusFor(d: LocalDate): GoalDayStatus = when {
            d == day0 -> GoalLogic.dayStatus(g, d, DaySignal(d, steps = 7000), null)
            d == day0.plusDays(1) -> GoalLogic.dayStatus(g, d, DaySignal(d, steps = 3000), null)
            d == wed -> GoalLogic.dayStatus(g, d, DaySignal(d, steps = 8000), null)
            else -> GoalLogic.dayStatus(g, d, DaySignal(d, steps = null), null)
        }
        val w = GoalLogic.week(g, wed, ::statusFor)
        assertEquals(3, w.daysTracked)
        assertEquals(2, w.daysMet)
    }

    @Test fun month_countsMetDaysUpToToday() {
        val g = customGoal()
        val metDates = setOf(3, 4, 5)
        val today = LocalDate.of(2026, 11, 10)
        fun statusFor(d: LocalDate) = GoalLogic.dayStatus(g, d, null, Checkin(g.id, d, done = d.dayOfMonth in metDates))
        val m = GoalLogic.month(g, today, ::statusFor)
        assertEquals(3, m.daysMet)
        assertEquals(10, m.daysInMonth)
    }

    @Test fun markBest_flagsTheHighestNonZero() {
        val a = GoalMonth(stepsGoal(), daysMet = 5, daysInMonth = 10, best = false)
        val b = GoalMonth(sleepGoal(), daysMet = 8, daysInMonth = 10, best = false)
        val c = GoalMonth(customGoal(), daysMet = 0, daysInMonth = 10, best = false)
        val marked = GoalLogic.markBest(listOf(a, b, c))
        assertTrue(marked[1].best)
        assertFalse(marked[0].best)
        assertFalse(marked[2].best)
    }

    @Test fun markBest_noneMetMarksNothing() {
        val a = GoalMonth(stepsGoal(), daysMet = 0, daysInMonth = 10, best = false)
        assertFalse(GoalLogic.markBest(listOf(a)).first().best)
    }

    @Test fun presetsAndClamp() {
        val p = GoalLogic.presets(stepGoal = 8000, screenLimitMinutes = 90)
        assertEquals(8000, p.first { it.kind == GoalKind.Steps }.defaultTarget)
        assertEquals(90, p.first { it.kind == GoalKind.ScreenTime }.defaultTarget)
        assertEquals(1_000, GoalLogic.clampTarget(GoalKind.Steps, 0))
        assertEquals(30_000, GoalLogic.clampTarget(GoalKind.Steps, 100_000))
        assertEquals(0, GoalLogic.clampTarget(GoalKind.Custom, 500))
    }

    @Test fun goalKindOfUnknownFallsBackToCustom() {
        assertEquals(GoalKind.Custom, GoalKind.of("nonsense"))
        assertEquals(GoalKind.Steps, GoalKind.of("steps"))
    }
}
