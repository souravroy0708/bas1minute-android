package com.bas1minute.sync.detox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StreakLogicTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear(); set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun kept(startMs: Long, minutes: Int) =
        DetoxSession(id = startMs, startMs = startMs, plannedEndMs = startMs + minutes * DetoxLogic.MINUTE, outcome = Outcome.KEPT)

    @Test fun qualifies_needsThreeHoursInsideTenToTen() {
        assertTrue(StreakLogic.qualifies(kept(at(2026, 11, 10, 10, 0), 180), zone))
        assertTrue(StreakLogic.qualifies(kept(at(2026, 11, 10, 18, 0), 240), zone))
        assertFalse(StreakLogic.qualifies(kept(at(2026, 11, 10, 10, 0), 179), zone))      // just under 3h
        assertFalse(StreakLogic.qualifies(kept(at(2026, 11, 10, 9, 0), 180), zone))       // starts before 10 AM
        assertFalse(StreakLogic.qualifies(kept(at(2026, 11, 10, 20, 0), 180), zone))      // ends after 10 PM
        assertFalse(StreakLogic.qualifies(kept(at(2026, 11, 10, 21, 30), 180), zone))     // crosses midnight
    }

    @Test fun qualifies_mustBeKept_notGuilty() {
        val broken = kept(at(2026, 11, 10, 10, 0), 180).copy(outcome = Outcome.BROKEN, endMs = at(2026, 11, 10, 11, 0))
        assertFalse(StreakLogic.qualifies(broken, zone))
    }

    @Test fun streak_consecutiveDaysCountBack() {
        val sessions = listOf(
            kept(at(2026, 11, 10, 10, 0), 180),
            kept(at(2026, 11, 11, 10, 0), 180),
            kept(at(2026, 11, 12, 10, 0), 180),
        )
        val now = at(2026, 11, 12, 20, 0)
        assertEquals(3, StreakLogic.currentStreak(sessions, now, zone))
    }

    @Test fun streak_todayWithoutOneYetDoesNotBreakIt() {
        val sessions = listOf(kept(at(2026, 11, 11, 10, 0), 180), kept(at(2026, 11, 12, 10, 0), 180))
        val now = at(2026, 11, 13, 9, 0)   // the 13th hasn't had a session yet
        assertEquals(2, StreakLogic.currentStreak(sessions, now, zone))
    }

    @Test fun streak_gapResetsIt() {
        val sessions = listOf(kept(at(2026, 11, 8, 10, 0), 180), kept(at(2026, 11, 10, 10, 0), 180))
        val now = at(2026, 11, 10, 20, 0)
        assertEquals(1, StreakLogic.currentStreak(sessions, now, zone))   // the 9th is missing
    }

    @Test fun streak_twoSessionsSameDayStillCountOnce() {
        val sessions = listOf(kept(at(2026, 11, 10, 10, 0), 180), kept(at(2026, 11, 10, 18, 0), 180))
        assertEquals(1, StreakLogic.currentStreak(sessions, at(2026, 11, 10, 22, 0), zone))
    }

    @Test fun bestStreak_findsLongestEvenAfterABreak() {
        val sessions = listOf(
            kept(at(2026, 10, 1, 10, 0), 180), kept(at(2026, 10, 2, 10, 0), 180), kept(at(2026, 10, 3, 10, 0), 180),
            kept(at(2026, 10, 4, 10, 0), 180), kept(at(2026, 10, 5, 10, 0), 180),
            // gap
            kept(at(2026, 11, 10, 10, 0), 180), kept(at(2026, 11, 11, 10, 0), 180),
        )
        assertEquals(5, StreakLogic.bestStreak(sessions, zone))
    }

    @Test fun totalFocusDays_countsDistinctDaysNotSessions() {
        val sessions = listOf(kept(at(2026, 11, 10, 10, 0), 180), kept(at(2026, 11, 10, 18, 0), 180), kept(at(2026, 11, 11, 10, 0), 180))
        assertEquals(2, StreakLogic.totalFocusDays(sessions, zone))
    }

    @Test fun badges_thresholdsAndNext() {
        assertEquals(emptyList<StreakLogic.Badge>(), StreakLogic.badgesFor(2))
        assertEquals(listOf(3), StreakLogic.badgesFor(5).map { it.days })
        assertEquals(listOf(21, 7, 3), StreakLogic.badgesFor(25).map { it.days })
        val (next, remaining) = StreakLogic.nextBadge(5)!!
        assertEquals(7, next.days); assertEquals(2, remaining)
        assertNull(StreakLogic.nextBadge(100))
        assertEquals("1-Week Focus", StreakLogic.badgeLabel(7))
    }
}
