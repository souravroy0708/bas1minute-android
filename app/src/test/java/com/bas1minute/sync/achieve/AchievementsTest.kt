package com.bas1minute.sync.achieve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AchievementsTest {
    private val d0 = LocalDate.of(2026, 11, 1)
    private fun days(vararg steps: Long) = steps.mapIndexed { i, s -> StepDay(d0.plusDays(i.toLong()), s) }

    @Test fun firstGoalAnd10k() {
        val b = Achievements.badges(days(3000, 7500, 10200), 7000).associateBy { it.id }
        assertEquals(d0.plusDays(1), b.getValue("first_goal").earnedOn)
        assertEquals(d0.plusDays(2), b.getValue("day10k").earnedOn)
        assertNull(b.getValue("day15k").earnedOn)
    }

    @Test fun sevenDayStreak_needsConsecutiveDays() {
        val ok = Achievements.badges(days(*LongArray(7) { 8000 }), 7000).first { it.id == "streak7" }
        assertNotNull(ok.earnedOn)
        val broken = Achievements.badges(days(8000, 8000, 8000, 100, 8000, 8000, 8000, 8000), 7000).first { it.id == "streak7" }
        assertNull(broken.earnedOn)
        assertTrue(broken.progress > 0.5f)
    }

    @Test fun lifetimeLakh() {
        val b = Achievements.badges(days(*LongArray(12) { 9000 }), 7000).first { it.id == "life100k" }
        assertEquals(d0.plusDays(11), b.earnedOn)   // 12 × 9,000 = 1,08,000 crosses on day 12
    }

    @Test fun records_bestDayWeekAndStreaks() {
        val r = Achievements.records(days(8000, 9000, 12000, 2000, 7500), 7000, d0.plusDays(4))
        assertEquals(12000L, r.bestDay?.steps)
        assertEquals(3, r.longestStreak)
        assertEquals(1, r.currentStreak)
        assertEquals(38500L, r.lifetimeSteps)
        assertEquals(4, r.goalDays)
    }

    @Test fun currentStreak_todayInProgressDoesNotBreakIt() {
        val r = Achievements.records(days(8000, 8000, 1000), 7000, d0.plusDays(2))
        assertEquals(2, r.currentStreak)
    }

    @Test fun suggestedGoal_tenPercentAboveRecentAverage() {
        val today = LocalDate.of(2026, 12, 1)
        val days = (1..28).map { StepDay(today.minusDays(it.toLong()), 8000) }
        assertEquals(8500, Achievements.suggestedGoal(days, 7000, today))
        assertNull(Achievements.suggestedGoal(days, 8500, today))          // already there
        assertNull(Achievements.suggestedGoal(days.take(10), 7000, today))  // not enough data
    }
}
