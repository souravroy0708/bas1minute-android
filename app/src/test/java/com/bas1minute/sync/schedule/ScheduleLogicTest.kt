package com.bas1minute.sync.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLogicTest {
    private fun sameDay(days: Set<Int> = setOf(1, 2, 3, 4, 5), start: Int = 9 * 60, end: Int = 17 * 60) =
        Schedule(1, "Work hours", days, start, end)

    private fun overnight(days: Set<Int> = setOf(1, 2, 3, 4, 5), start: Int = 22 * 60, end: Int = 7 * 60) =
        Schedule(2, "Bedtime", days, start, end)

    @Test fun sameDayWindow_activeOnlyInsideItsOwnHoursOnItsOwnDays() {
        val s = sameDay()   // Mon-Fri 9am-5pm
        assertTrue(ScheduleLogic.isActiveNow(s, dayOfWeek = 1, minuteOfDay = 9 * 60))        // exactly at start
        assertTrue(ScheduleLogic.isActiveNow(s, dayOfWeek = 3, minuteOfDay = 12 * 60))       // midday, Wednesday
        assertFalse(ScheduleLogic.isActiveNow(s, dayOfWeek = 1, minuteOfDay = 17 * 60))      // exactly at end: over
        assertFalse(ScheduleLogic.isActiveNow(s, dayOfWeek = 1, minuteOfDay = 8 * 60 + 59))  // 1 min before start
        assertFalse(ScheduleLogic.isActiveNow(s, dayOfWeek = 6, minuteOfDay = 12 * 60))      // Saturday: not a listed day
    }

    @Test fun overnightWindow_spansMidnightCorrectly() {
        val s = overnight(days = setOf(1), start = 22 * 60, end = 7 * 60)   // Monday 10pm - Tuesday 7am
        assertTrue(ScheduleLogic.isActiveNow(s, dayOfWeek = 1, minuteOfDay = 23 * 60))       // Monday 11pm: active
        assertTrue(ScheduleLogic.isActiveNow(s, dayOfWeek = 2, minuteOfDay = 2 * 60))        // Tuesday 2am: still active
        assertTrue(ScheduleLogic.isActiveNow(s, dayOfWeek = 2, minuteOfDay = 6 * 60 + 59))   // Tuesday 6:59am: last minute
        assertFalse(ScheduleLogic.isActiveNow(s, dayOfWeek = 2, minuteOfDay = 7 * 60))       // Tuesday 7am exactly: over
        assertFalse(ScheduleLogic.isActiveNow(s, dayOfWeek = 1, minuteOfDay = 21 * 60 + 59)) // Monday just before start
        assertFalse(ScheduleLogic.isActiveNow(s, dayOfWeek = 3, minuteOfDay = 2 * 60))       // Wednesday 2am: not from Tuesday's (unset) start
    }

    @Test fun disabledSchedule_isNeverActive() {
        val s = sameDay().copy(enabled = false)
        assertFalse(ScheduleLogic.isActiveNow(s, dayOfWeek = 1, minuteOfDay = 12 * 60))
    }

    @Test fun activeSchedule_returnsTheFirstMatchOrNull() {
        val work = sameDay()
        val bed = overnight()
        assertEquals(work, ScheduleLogic.activeSchedule(listOf(work, bed), dayOfWeek = 3, minuteOfDay = 12 * 60))
        assertNull(ScheduleLogic.activeSchedule(listOf(work, bed), dayOfWeek = 6, minuteOfDay = 12 * 60))
    }

    @Test fun minutesUntilEnd_sameDayWindow() {
        val s = sameDay(start = 9 * 60, end = 17 * 60)
        assertEquals(60, ScheduleLogic.minutesUntilEnd(s, dayOfWeek = 1, minuteOfDay = 16 * 60))
        assertEquals(0, ScheduleLogic.minutesUntilEnd(s, dayOfWeek = 1, minuteOfDay = 17 * 60))
    }

    @Test fun minutesUntilEnd_overnightWindow_beforeAndAfterMidnight() {
        val s = overnight(start = 22 * 60, end = 7 * 60)
        // At 11pm: 1 hour left today (to midnight) + 7 hours tomorrow = 8 hours = 480 min
        assertEquals(480, ScheduleLogic.minutesUntilEnd(s, dayOfWeek = 1, minuteOfDay = 23 * 60))
        // At 2am (already past midnight, in the tail end): 5 hours left until 7am
        assertEquals(300, ScheduleLogic.minutesUntilEnd(s, dayOfWeek = 2, minuteOfDay = 2 * 60))
    }

    @Test fun formatTimeOfDay_handlesMidnightNoonAndPm() {
        assertEquals("12:00 AM", ScheduleLogic.formatTimeOfDay(0))
        assertEquals("12:00 PM", ScheduleLogic.formatTimeOfDay(12 * 60))
        assertEquals("10:00 PM", ScheduleLogic.formatTimeOfDay(22 * 60))
        assertEquals("7:30 AM", ScheduleLogic.formatTimeOfDay(7 * 60 + 30))
    }

    @Test fun daysSummary_recognisesCommonPatterns() {
        assertEquals("Every day", ScheduleLogic.daysSummary(setOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("Weekdays", ScheduleLogic.daysSummary(setOf(1, 2, 3, 4, 5)))
        assertEquals("Weekends", ScheduleLogic.daysSummary(setOf(6, 7)))
        assertEquals("Mon, Wed, Fri", ScheduleLogic.daysSummary(setOf(1, 3, 5)))
    }

    @Test fun timeRangeSummary_formatsBothEnds() {
        val s = overnight(start = 22 * 60, end = 7 * 60)
        assertEquals("10:00 PM \u2013 7:00 AM", ScheduleLogic.timeRangeSummary(s))
    }
}
