package com.bas1minute.sync.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Run: ./gradlew test */
class StepMathTest {
    private val base = 10_000_000L   // any monotonic start

    /** Walk at [spm] steps per minute for [seconds], one counter reading per second (like a real phone). */
    private fun StepTracker.walk(fromMono: Long, counterStart: Long, spm: Int, seconds: Int): Pair<Long, Long> {
        var t = fromMono; var c = counterStart
        repeat(seconds) {
            t += 1000
            c += if (spm % 60 == 0) (spm / 60).toLong() else Math.round(spm / 60.0 * (it + 1)) - Math.round(spm / 60.0 * it)
            onStepCounter(c, t)
            tick(t)
        }
        return t to c
    }

    @Test fun stride_growsWithCadenceForRunsOnly() {
        assertEquals(0.76, Stride.meters(76, 110.0, ActivityType.Walk), 1e-9)
        assertTrue(Stride.meters(76, 170.0, ActivityType.Run) > 1.0)
        assertTrue(Stride.meters(76, 170.0, ActivityType.Walk) < 0.9)
        assertTrue(Stride.meters(76, 300.0, ActivityType.Run) <= 0.76 * 1.6 + 1e-9)
    }

    @Test fun firstReadingIsOnlyABaseline() {
        val t = StepTracker(ActivityType.Walk, 76)
        t.start(base)
        assertEquals(0L, t.onStepCounter(52_000, base + 1000))   // steps since the phone started
        assertEquals(0L, t.stats(base + 1000).steps)
    }

    @Test fun walk10Minutes_distanceTimePaceAndCadence() {
        val t = StepTracker(ActivityType.Walk, 76)
        t.start(base)
        t.onStepCounter(1000, base)
        val (end, _) = t.walk(base, 1000, 110, 600)
        val s = t.stats(end)
        assertEquals(1100, s.steps.toInt(), 5)
        // 1,100 steps × 0.76 m ≈ 836 m (the first few seconds use the base step length too)
        assertTrue("distance ${s.distanceM}", abs(s.distanceM - 836) < 25)
        assertEquals(600_000L, s.movingMs)
        assertTrue("cadence ${s.cadenceSpm}", s.cadenceSpm in 105..115)
        assertTrue("speed ${s.currentSpeedMs}", abs(s.currentSpeedMs - 110 / 60.0 * 0.76) < 0.1)
        assertFalse(s.autoPaused)
    }

    @Test fun autoPause_stopsTheClockWhenNoStepsAndResumesWithSteps() {
        val t = StepTracker(ActivityType.Walk, 76)
        t.start(base)
        t.onStepCounter(0, base)
        val (walked, c) = t.walk(base, 0, 120, 60)
        // Stand at a signal for 2 minutes
        var now = walked
        repeat(120) { now += 1000; t.tick(now) }
        val waiting = t.stats(now)
        assertTrue(waiting.autoPaused)
        assertEquals(60_000L, waiting.movingMs)        // the 12 s spent deciding is taken back too
        // Walk on
        val (end, _) = t.walk(now, c, 120, 30)
        val s = t.stats(end)
        assertFalse(s.autoPaused)
        assertTrue("moving ${s.movingMs}", s.movingMs in 88_000L..91_000L)
    }

    @Test fun manualPause_ignoresStepsTakenWhilePaused() {
        val t = StepTracker(ActivityType.Walk, 76)
        t.start(base)
        t.onStepCounter(0, base)
        val (a, c) = t.walk(base, 0, 120, 60)
        t.pause(a)
        t.onStepCounter(c + 500, a + 30_000)          // walking to the car, paused
        t.resume(a + 60_000)
        t.onStepCounter(c + 500, a + 60_000)          // new baseline after resume
        val (end, _) = t.walk(a + 60_000, c + 500, 120, 60)
        val s = t.stats(end)
        assertEquals(240, s.steps.toInt(), 3)
        assertEquals(120_000L, s.movingMs)
        assertEquals(180_000L, s.elapsedMs)
    }

    @Test fun counterResetAndGlitchesAreIgnored() {
        val t = StepTracker(ActivityType.Walk, 76)
        t.start(base)
        t.onStepCounter(5_000, base)
        t.onStepCounter(5_002, base + 1000)
        assertEquals(0L, t.onStepCounter(3, base + 2000))         // phone restarted: counter back near 0
        assertEquals(0L, t.onStepCounter(90_000, base + 3000))    // impossible jump
        assertEquals(2L, t.stats(base + 3000).steps)
    }

    @Test fun run_longerStepsAndKilometreSplits() {
        val t = StepTracker(ActivityType.Run, 76)
        val splits = ArrayList<Pair<Int, Long>>()
        t.onSplit = { i, ms -> splits += i to ms }
        t.start(base)
        t.onStepCounter(0, base)
        val (end, _) = t.walk(base, 0, 165, 900)      // 15 minutes at 165 spm
        val s = t.stats(end)
        assertTrue("distance ${s.distanceM}", s.distanceM in 2_400.0..3_000.0)
        assertEquals(s.splitsMs.size, splits.size)
        assertTrue(splits.size >= 2)
        assertTrue("split ${splits[1].second}", splits[1].second in 300_000L..400_000L)   // ~5:00–6:40 per km
    }

    @Test fun timer_neverAutoPauses() {
        val t = StepTracker(ActivityType.Cycle, 76, autoPauseOn = false)
        t.start(base)
        var now = base
        repeat(300) { now += 1000; t.tick(now) }
        val s = t.stats(now)
        assertEquals(300_000L, s.movingMs)
        assertEquals(0.0, s.distanceM, 0.0)
        assertFalse(s.autoPaused)
    }

    @Test fun restore_continuesFromSavedTotals() {
        val t = StepTracker(ActivityType.Walk, 76)
        t.restore(1500.0, 900_000L, 2000, listOf(600_000L), base)
        t.onStepCounter(10, base + 1000)              // new baseline (counter reset after reboot)
        val (end, _) = t.walk(base + 1000, 10, 120, 10)
        val s = t.stats(end)
        assertEquals(2020, s.steps.toInt(), 2)
        assertTrue(s.distanceM > 1500)
        assertEquals(listOf(600_000L), s.splitsMs)
    }

    @Test fun calibration_andSplitsText() {
        assertEquals(80, Stride.calibratedCm(4000.0, 5000))
        assertEquals(null, Stride.calibratedCm(50.0, 5000))
        assertEquals(listOf(1L, 22L, 333L), SplitsText.read(SplitsText.write(listOf(1, 22, 333))))
        assertEquals(null, SplitsText.write(emptyList()))
        assertEquals(TrackSource.Steps, TrackSource.of("steps"))
        assertEquals(TrackSource.Gps, TrackSource.of(null))
    }
}
