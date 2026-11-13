package com.bas1minute.sync.detox

import com.bas1minute.sync.detox.DetoxLogic.HOUR
import com.bas1minute.sync.detox.DetoxLogic.MINUTE
import com.bas1minute.sync.detox.DetoxLogic.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetoxLogicTest {
    private val day = 1_700_000_000_000L - (1_700_000_000_000L % DAY) // a "midnight"

    private fun session(startH: Int, minutes: Int) =
        DetoxSession(id = 1, startMs = day + startH * HOUR, plannedEndMs = day + startH * HOUR + minutes * MINUTE)

    @Test fun intervals_pairResumeAndPause() {
        val ev = listOf(FgEvent("a", 100, true), FgEvent("a", 300, false), FgEvent("b", 400, true), FgEvent("b", 900, false))
        assertEquals(listOf(Interval("a", 100, 300), Interval("b", 400, 900)), DetoxLogic.buildIntervals(ev, 0, 1000))
    }

    @Test fun intervals_switchingAppsEndsPreviousOne() {
        val ev = listOf(FgEvent("a", 100, true), FgEvent("b", 200, true), FgEvent("b", 500, false))
        assertEquals(listOf(Interval("a", 100, 200), Interval("b", 200, 500)), DetoxLogic.buildIntervals(ev, 0, 1000))
    }

    @Test fun intervals_openAtStartAndStillOpenAtEnd() {
        val ev = listOf(FgEvent("a", 300, false), FgEvent("b", 800, true))
        assertEquals(listOf(Interval("a", 0, 300), Interval("b", 800, 1000)), DetoxLogic.buildIntervals(ev, 0, 1000))
    }

    @Test fun cleanHours_countOnlyFinishedHoursWithoutDistraction() {
        val now = day + 10 * HOUR + 30 * MINUTE
        val used = listOf(
            Interval("com.instagram.android", day + 7 * HOUR + 10 * MINUTE, day + 7 * HOUR + 20 * MINUTE),
            Interval("com.android.chrome", day + 9 * HOUR + 59 * MINUTE, day + 10 * HOUR + 2 * MINUTE),
        )
        val cells = DetoxLogic.hourCells(day, 6, 24, now, used)
        // 6 clean, 7 used, 8 clean, 9 used, 10 used (chrome crossed into 10), 11+ future
        assertEquals(HourState.CLEAN, cells[0].state)
        assertEquals(HourState.USED, cells[1].state)
        assertEquals("com.instagram.android", cells[1].usedBy)
        assertEquals(HourState.CLEAN, cells[2].state)
        assertEquals(HourState.USED, cells[3].state)
        assertEquals(HourState.USED, cells[4].state)
        assertEquals(HourState.FUTURE, cells[5].state)
        assertEquals(2, DetoxLogic.cleanHours(cells))
    }

    @Test fun cleanHours_blockScreenFlashDoesNotSpoilTheHour() {
        val flash = Interval("com.whatsapp", day + 8 * HOUR + 5 * MINUTE, day + 8 * HOUR + 5 * MINUTE + 1_500)
        val cells = DetoxLogic.hourCells(day, 8, 9, day + 9 * HOUR, listOf(flash))
        assertEquals(HourState.CLEAN, cells.single().state)
    }

    @Test fun cleanHours_currentHourIsInProgressUntilItEnds() {
        val cells = DetoxLogic.hourCells(day, 8, 9, day + 8 * HOUR + 20 * MINUTE, emptyList())
        assertEquals(HourState.IN_PROGRESS, cells.single().state)
    }

    @Test fun finish_keptWhenOnlyFlashesWereCaught() {
        val s = session(14, 120)
        val flashes = listOf(Interval("com.whatsapp", s.startMs + 10 * MINUTE, s.startMs + 10 * MINUTE + 1_200))
        val done = DetoxLogic.finish(s, s.plannedEndMs + 5_000, flashes)
        assertEquals(Outcome.KEPT, done.outcome)
        assertEquals(120, done.keptMinutes(done.plannedEndMs + HOUR))
    }

    @Test fun finish_brokenWhenBlockedAppReallyUsed() {
        // e.g. the service was force-stopped and YouTube was watched for 20 minutes
        val s = session(14, 120)
        val yt = listOf(Interval("com.google.android.youtube", s.startMs + 45 * MINUTE, s.startMs + 65 * MINUTE))
        val done = DetoxLogic.finish(s, s.plannedEndMs, yt)
        assertEquals(Outcome.BROKEN, done.outcome)
        assertEquals(BreakReason.USED_BLOCKED_APP, done.reason)
        assertEquals("com.google.android.youtube", done.brokenBy)
        assertEquals(45, done.keptMinutes(s.plannedEndMs))
    }

    @Test fun finish_ignoresUseOutsideTheSession() {
        val s = session(14, 60)
        val before = listOf(Interval("com.instagram.android", s.startMs - 30 * MINUTE, s.startMs + 2_000))
        assertNull(DetoxLogic.firstViolation(s, s.plannedEndMs, before))
    }

    @Test fun endEarly_countsAsBrokenWithMinutesSoFar() {
        val s = session(20, 120)
        val broken = DetoxLogic.breakNow(s, s.startMs + 50 * MINUTE, BreakReason.ENDED_EARLY)
        assertEquals(Outcome.BROKEN, broken.outcome)
        assertEquals(50, broken.keptMinutes(s.plannedEndMs))
    }

    @Test fun daySummary_countsKeptBrokenAndMinutes() {
        val kept = DetoxLogic.finish(session(9, 60), day + 11 * HOUR, emptyList())
        val broken = DetoxLogic.breakNow(session(14, 120), day + 14 * HOUR + 30 * MINUTE, BreakReason.ENDED_EARLY)
        val cells = DetoxLogic.hourCells(day, 6, 24, day + 16 * HOUR, emptyList())
        val sum = DetoxLogic.daySummary(day, cells, listOf(kept, broken), day + 16 * HOUR)
        assertEquals(1, sum.keptSessions)
        assertEquals(1, sum.brokenSessions)
        assertEquals(60, sum.detoxMinutesKept)
        assertEquals(10, sum.cleanHours) // 6..15 finished
    }

    @Test fun weekSummary_streakSkipsEmptyTodayAndStopsAtBrokenDay() {
        fun keptOn(dayOffset: Int) = DetoxLogic.finish(
            DetoxSession(dayOffset.toLong(), day - dayOffset * DAY + 9 * HOUR, day - dayOffset * DAY + 10 * HOUR),
            day, emptyList())
        val brokenThree = DetoxLogic.breakNow(
            DetoxSession(99, day - 3 * DAY + 9 * HOUR, day - 3 * DAY + 11 * HOUR), day - 3 * DAY + 10 * HOUR,
            BreakReason.ENDED_EARLY)
        val w = DetoxLogic.weekSummary(listOf(keptOn(1), keptOn(2), brokenThree, keptOn(4)), day, day + 8 * HOUR)
        assertEquals(2, w.keptDaysStreak)
        assertEquals(3, w.kept)
        assertEquals(1, w.broken)
        assertEquals(180, w.minutesKept)
    }

    @Test fun clampAndFormat() {
        assertEquals(15, DetoxLogic.clampMinutes(5))
        assertEquals(720, DetoxLogic.clampMinutes(5000))
        assertEquals("1:05:09", DetoxLogic.formatDuration(HOUR + 5 * MINUTE + 9_000))
        assertEquals("2 h", DetoxLogic.formatMinutes(120))
        assertEquals("1 h 30 min", DetoxLogic.formatMinutes(90))
        assertEquals("6 PM", DetoxLogic.hourLabel(18))
    }
}
