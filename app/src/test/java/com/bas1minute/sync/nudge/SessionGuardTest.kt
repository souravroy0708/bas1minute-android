package com.bas1minute.sync.nudge

import com.bas1minute.sync.detox.Interval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGuardTest {
    private val min = 60_000L
    private val pkg = "com.instagram.android"

    @Test fun mergeContinuous_joinsIntervalsWithinTolerance() {
        val intervals = listOf(
            Interval(pkg, 0, 5 * min),
            Interval(pkg, 5 * min + 30_000, 10 * min),   // 30s gap: a notification glance, same session
            Interval("com.whatsapp", 5 * min, 5 * min + 20_000),   // a different app in between: irrelevant to this pkg's merge
        )
        val merged = mergeContinuous(intervals = intervals, pkg = pkg, tolerance = 90_000L)
        assertEquals(1, merged.size)
        assertEquals(0L, merged[0].start)
        assertEquals(10 * min, merged[0].end)
    }

    @Test fun mergeContinuous_keepsSeparateIntervalsBeyondTolerance() {
        val intervals = listOf(Interval(pkg, 0, 5 * min), Interval(pkg, 10 * min, 12 * min))   // 5-minute real gap
        val merged = mergeContinuous(intervals, pkg, tolerance = 90_000L)
        assertEquals(2, merged.size)
    }

    @Test fun currentSessionMs_zeroWhenNotCurrentlyInTheApp() {
        val intervals = listOf(Interval(pkg, 0, 5 * min))
        val now = 10 * min   // 5 minutes since they left — well past the 90s tolerance
        assertEquals(0L, SessionGuard.currentSessionMs(intervals, pkg, now))
    }

    @Test fun currentSessionMs_countsFromTheMergedSessionStart() {
        val intervals = listOf(Interval(pkg, 0, 5 * min), Interval(pkg, 5 * min + 30_000, 20 * min))
        val now = 20 * min + 10_000   // 10s after the last event: clearly still in the session
        assertEquals(20 * min + 10_000, SessionGuard.currentSessionMs(intervals, pkg, now))
    }

    @Test fun dueNudgeCount_crossesEveryFifteenMinutes_cappedAtThree() {
        assertEquals(0, SessionGuard.dueNudgeCount(14 * min))
        assertEquals(1, SessionGuard.dueNudgeCount(15 * min))
        assertEquals(1, SessionGuard.dueNudgeCount(29 * min))
        assertEquals(2, SessionGuard.dueNudgeCount(30 * min))
        assertEquals(3, SessionGuard.dueNudgeCount(45 * min))
        assertEquals(3, SessionGuard.dueNudgeCount(120 * min))   // capped: silence for the rest of a long session
    }

    @Test fun sessionKey_changesWhenTheSessionActuallyRestarts() {
        assertEquals(SessionGuard.sessionKey(pkg, 1000L), SessionGuard.sessionKey(pkg, 1000L))
        assertNotEquals(SessionGuard.sessionKey(pkg, 1000L), SessionGuard.sessionKey(pkg, 2000L))
    }

    @Test fun message_escalatesAndOffersADetoxByTheSecondNudge() {
        val first = SessionGuard.message("Instagram", 1, "Sourav")
        val second = SessionGuard.message("Instagram", 2, "Sourav")
        val third = SessionGuard.message("Instagram", 3, "Sourav")
        assertTrue(first.body.isNotBlank())
        assertEquals(NudgeTarget.Detox, second.target)   // the direct "stop it" offer arrives by the 2nd nudge
        assertNotEquals(first.body, second.body)
        assertNotEquals(second.body, third.body)
    }

    @Test fun message_neverUsesShamingLanguage() {
        val shamingWords = listOf("addict", "lazy", "waste", "pathetic", "shameful")
        (1..3).forEach { n ->
            val m = SessionGuard.message("Chrome", n, "Priya")
            shamingWords.forEach { word -> assertFalse("nudge $n shouldn't say '$word'", (m.title + " " + m.body).contains(word, ignoreCase = true)) }
        }
    }

    private fun mergeContinuous(intervals: List<Interval>, pkg: String, tolerance: Long) =
        SessionGuard.mergeContinuous(intervals, pkg, tolerance)

    private fun assertFalse(msg: String, c: Boolean) { org.junit.Assert.assertFalse(msg, c) }
}
