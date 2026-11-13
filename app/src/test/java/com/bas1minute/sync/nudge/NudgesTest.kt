package com.bas1minute.sync.nudge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Run: ./gradlew test */
class NudgesTest {
    private fun state(
        slot: NudgeSlot, steps: Long? = 3000, social: Int? = 60, yesterday: Int? = 60,
        detoxActive: Boolean = false, detoxKept: Boolean = false, streak: Int = 0, day: Int = 0,
    ) = NudgeState(slot, "Sourav", steps, 7000, social, yesterday, 120, detoxActive, detoxKept, streak, 76, day)

    @Test fun silentDuringADetox() {
        NudgeSlot.entries.forEach { assertNull(Nudges.pick(state(it, detoxActive = true))) }
    }

    @Test fun morning_heavyScrollingYesterdayChallengesToADetox() {
        val n = Nudges.pick(state(NudgeSlot.Morning, yesterday = 200))!!
        assertEquals(NudgeTarget.Detox, n.target)
        assertTrue(n.body, n.body.contains("3h 20m"))
    }

    @Test fun morning_streakComesNext_thenRotatingMessages() {
        assertTrue(Nudges.pick(state(NudgeSlot.Morning, streak = 4))!!.title.startsWith("Day 5"))
        val titles = (0..2).map { Nudges.pick(state(NudgeSlot.Morning, day = it))!!.title }.toSet()
        assertEquals(3, titles.size)
    }

    @Test fun afternoon_overTheScreenLimitBeatsSteps() {
        val n = Nudges.pick(state(NudgeSlot.Afternoon, steps = 500, social = 150))!!
        assertEquals(NudgeTarget.Detox, n.target)
        assertTrue(n.title.contains("2h 30m"))
    }

    @Test fun afternoon_behindSendsAWalk_onTrackSendsABurst_doneSendsADetox() {
        val behind = Nudges.pick(state(NudgeSlot.Afternoon, steps = 1000))!!
        assertEquals(NudgeTarget.Move, behind.target)
        assertTrue(behind.title, behind.title.startsWith("6,000 steps"))
        assertTrue(behind.body, behind.body.contains("30-minute"))
        assertEquals(NudgeTarget.Today, Nudges.pick(state(NudgeSlot.Afternoon, steps = 4000))!!.target)
        assertEquals(NudgeTarget.Detox, Nudges.pick(state(NudgeSlot.Afternoon, steps = 8000))!!.target)
    }

    @Test fun evening_stepsLeftGivesMinutesAndKm() {
        val n = Nudges.pick(state(NudgeSlot.Evening, steps = 5200))!!
        assertEquals(NudgeTarget.Move, n.target)
        assertTrue(n.title, n.title.contains("1,800 steps left (1.4 km)"))
        assertTrue(n.body, n.body.contains("20-minute"))
    }

    @Test fun evening_goalDoneSuggestsADetoxUnlessAlreadyKept() {
        assertEquals(NudgeTarget.Detox, Nudges.pick(state(NudgeSlot.Evening, steps = 9000))!!.target)
        assertEquals(NudgeTarget.Today, Nudges.pick(state(NudgeSlot.Evening, steps = 9000, detoxKept = true))!!.target)
    }

    @Test fun noStepDataStillSaysSomethingTrue() {
        assertEquals(NudgeTarget.Today, Nudges.pick(state(NudgeSlot.Afternoon, steps = null, social = null))!!.target)
        assertEquals(NudgeTarget.Detox, Nudges.pick(state(NudgeSlot.Evening, steps = null, social = 200))!!.target)
        assertEquals(NudgeTarget.Today, Nudges.pick(state(NudgeSlot.Evening, steps = null, social = null))!!.target)
    }

    @Test fun slotsAndFormatting() {
        assertEquals(NudgeSlot.Evening, NudgeSlot.of("evening"))
        assertEquals(NudgeSlot.Morning, NudgeSlot.of(null))
        assertEquals("1h 5m", Nudges.hm(65))
        assertEquals("45m", Nudges.hm(45))
    }
}
