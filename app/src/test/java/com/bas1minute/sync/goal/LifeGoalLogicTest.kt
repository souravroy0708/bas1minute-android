package com.bas1minute.sync.goal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LifeGoalLogicTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val startMs = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun goal(horizon: Horizon, target: LocalDate) =
        LifeGoal(1, "gu1", horizon, "Run a marathon", startedAt = startMs, targetDate = target)

    private fun milestone(id: Long, done: Boolean) = Milestone(id, "mu$id", 1, "Step $id", done, null, id.toInt())

    @Test fun suggestedTargetDate_matchesEachHorizon() {
        assertEquals(start.plusMonths(2), LifeGoalLogic.suggestedTargetDate(Horizon.Short, start))
        assertEquals(start.plusMonths(24), LifeGoalLogic.suggestedTargetDate(Horizon.Mid, start))
        assertEquals(start.plusMonths(84), LifeGoalLogic.suggestedTargetDate(Horizon.Long, start))
    }

    @Test fun clampTargetDate_keepsWithinHorizonRange() {
        // Short-term (1-3 months): a date 10 days out is too soon, a date 2 years out is too far
        assertEquals(start.plusMonths(1), LifeGoalLogic.clampTargetDate(Horizon.Short, start, start.plusDays(10)))
        assertEquals(start.plusMonths(3), LifeGoalLogic.clampTargetDate(Horizon.Short, start, start.plusYears(2)))
        // A date already inside range is left alone
        val ok = start.plusMonths(2)
        assertEquals(ok, LifeGoalLogic.clampTargetDate(Horizon.Short, start, ok))
    }

    @Test fun progress_timeAndMilestoneFractions() {
        val g = goal(Horizon.Short, start.plusDays(100))
        val milestones = listOf(milestone(1, true), milestone(2, true), milestone(3, false), milestone(4, false))
        val p = LifeGoalLogic.progress(g, milestones, today = start.plusDays(50))
        assertEquals(50, p.daysElapsed)
        assertEquals(100, p.daysTotal)
        assertEquals(0.5f, p.timeFraction, 0.001f)
        assertEquals(0.5f, p.milestoneFraction, 0.001f)
        assertEquals(Pace.OnTrack, p.pace)
    }

    @Test fun progress_pace_aheadAndBehind() {
        val g = goal(Horizon.Short, start.plusDays(100))
        val ahead = LifeGoalLogic.progress(g, listOf(milestone(1, true), milestone(2, true)), today = start.plusDays(20))
        assertEquals(Pace.Ahead, ahead.pace)   // 100% done at 20% of the time
        val behind = LifeGoalLogic.progress(g, listOf(milestone(1, false), milestone(2, false), milestone(3, true)), today = start.plusDays(90))
        assertEquals(Pace.Behind, behind.pace)   // 33% done at 90% of the time
    }

    @Test fun progress_noMilestonesIsUnknownPace_notBehind() {
        val g = goal(Horizon.Short, start.plusDays(100))
        val p = LifeGoalLogic.progress(g, emptyList(), today = start.plusDays(90))
        assertEquals(Pace.Unknown, p.pace)
    }

    @Test fun progress_clampsElapsedToTotalRange() {
        val g = goal(Horizon.Short, start.plusDays(30))
        val p = LifeGoalLogic.progress(g, emptyList(), today = start.plusDays(500))
        assertEquals(30, p.daysElapsed)
        assertEquals(1.0f, p.timeFraction, 0.001f)
    }

    @Test fun daysLeft_countsDownToTargetDate() {
        val g = goal(Horizon.Short, start.plusDays(30))
        assertEquals(30, LifeGoalLogic.daysLeft(g, start))
        assertEquals(0, LifeGoalLogic.daysLeft(g, start.plusDays(30)))
        assertEquals(-5, LifeGoalLogic.daysLeft(g, start.plusDays(35)))
    }

    @Test fun dayLabel_firstAndLastDay() {
        val g = goal(Horizon.Short, start.plusDays(90))
        assertEquals("Day 1 of 90", LifeGoalLogic.dayLabel(g, start))
        assertEquals("Day 46 of 90", LifeGoalLogic.dayLabel(g, start.plusDays(45)))
        assertEquals("Day 90 of 90", LifeGoalLogic.dayLabel(g, start.plusDays(200)))   // overdue: clamp, don't overshoot
    }

    @Test fun clampText_trimsAndLimitsLength() {
        assertEquals("hello", LifeGoalLogic.clampText("  hello  ", 10))
        assertEquals("hello", LifeGoalLogic.clampText("hello world", 5))
    }

    @Test fun nextMilestoneOrder_appendsAfterHighest() {
        assertEquals(0, LifeGoalLogic.nextMilestoneOrder(emptyList()))
        assertEquals(3, LifeGoalLogic.nextMilestoneOrder(listOf(milestone(1, false).copy(order = 2), milestone(2, false).copy(order = 0))))
    }

    @Test fun horizon_rangeLabelsAndLookup() {
        assertEquals("1–3 months", Horizon.Short.rangeLabel)
        assertEquals("1–3 years", Horizon.Mid.rangeLabel)
        assertEquals("5–10 years", Horizon.Long.rangeLabel)
        assertEquals(Horizon.Mid, Horizon.of("mid"))
        assertEquals(Horizon.Short, Horizon.of("nonsense"))
    }
}
