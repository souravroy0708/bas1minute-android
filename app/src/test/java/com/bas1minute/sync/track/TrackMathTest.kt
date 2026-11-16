package com.bas1minute.sync.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos

/** Simulated GPS (with realistic 3–5 m noise) checks for the walk/run engine. Run: ./gradlew test */
class TrackMathTest {
    private val k = 111_000.0

    private fun walkNorth(t: Tracker, secs: Int, speed: Double, noise: Double, lat0: Double, t0: Int, rnd: Random): Double {
        var lat = lat0
        for (i in 0 until secs) {
            lat += speed / k
            t.onFix(Fix(lat + rnd.nextGaussian() * noise / k, 78.48 + rnd.nextGaussian() * noise / (k * cos(Math.toRadians(lat))),
                500.0 + rnd.nextGaussian() * 2, 6f, speed.toFloat(), 1_700_000_000_000L + (t0 + i) * 1000L, (t0 + i) * 1000L))
        }
        return lat
    }

    /** Two laps of a 250 m square (2 km) — corners are where over-smoothing loses distance. */
    private fun square(type: ActivityType, speed: Double, seed: Long): Double {
        val rnd = Random(seed); val t = Tracker(type); t.start(0)
        val side = 250.0; val per = (4 * side / speed).toInt(); val kx = k * cos(Math.toRadians(17.4))
        for (i in 0..per * 2) {
            val d = (i * speed) % (4 * side); val sd = d % side
            val (x, y) = when ((d / side).toInt()) { 0 -> sd to 0.0; 1 -> side to sd; 2 -> side - sd to side; else -> 0.0 to side - sd }
            t.onFix(Fix(17.4 + (y + rnd.nextGaussian() * 3) / k, 78.4 + (x + rnd.nextGaussian() * 3) / kx, null, 6f, speed.toFloat(), 0, i * 1000L))
        }
        return t.stats(per * 2 * 1000L + 1000).distanceM
    }

    @Test fun walkDistanceTimeAndSplits() {
        val t = Tracker(ActivityType.Walk); t.start(0)
        val secs = (3000 / 1.4).toInt()
        walkNorth(t, secs, 1.4, 3.0, 17.385, 0, Random(42))
        val s = t.stats(secs * 1000L)
        assertTrue("distance ${s.distanceM}", abs(s.distanceM - 3000) < 100)
        assertTrue(abs(s.movingMs / 1000 - secs) < 5)
        assertEquals(3, s.splitsMs.size)
        assertTrue(s.elevGainM < 15)
    }

    @Test fun cornersStayAccurate() {
        for ((type, speed) in listOf(ActivityType.Walk to 1.4, ActivityType.Run to 3.0, ActivityType.Run to 4.5, ActivityType.Cycle to 6.0))
            for (seed in 1L..3L) {
                val d = square(type, speed, seed)
                assertTrue("$type $speed: $d", abs(d - 2000) < 60)
            }
    }

    @Test fun standingStillAutoPauses() {
        val rnd = Random(7); val t = Tracker(ActivityType.Run); t.start(0)
        for (i in 0 until 300) t.onFix(Fix(17.4 + rnd.nextGaussian() * 5 / k, 78.4 + rnd.nextGaussian() * 5 / 106_000, null, 8f, 0.1f, 0, i * 1000L))
        val s = t.stats(300_000)
        assertTrue(s.autoPaused); assertTrue(s.distanceM < 30); assertTrue(s.movingMs < 30_000)
    }

    @Test fun rejectsTeleportsAndBadFixes() {
        val t = Tracker(ActivityType.Walk, autoPauseOn = false); t.start(0)
        walkNorth(t, 100, 1.4, 1.0, 17.385, 0, Random(1))
        val before = t.stats(100_000).distanceM
        t.onFix(Fix(17.5, 78.48, null, 5f, null, 0, 101_000))
        assertTrue(t.stats(101_000).distanceM - before < 5)
        assertEquals(null, Tracker(ActivityType.Walk).also { it.start(0) }.onFix(Fix(17.0, 78.0, null, 80f, null, 0, 1000)))
    }

    @Test fun manualPauseMakesNewSegmentAndRestoreMatches() {
        val rnd = Random(3); val t = Tracker(ActivityType.Walk, autoPauseOn = false); t.start(0)
        var lat = walkNorth(t, 300, 1.4, 1.0, 17.385, 0, rnd)
        val first = t.stats(300_000).distanceM
        t.pause(300_000); lat = walkNorth(t, 300, 1.4, 1.0, lat, 300, rnd); t.resume(600_000)
        walkNorth(t, 300, 1.4, 1.0, lat, 600, rnd)
        val s = t.stats(900_000)
        assertTrue(abs(s.distanceM - 2 * first) < 40)
        assertTrue(abs(s.movingMs - 600_000) < 3000)
        assertEquals(2, TrackSummary.segments(t.points).size)
        val r = Tracker(ActivityType.Walk); r.restore(t.points, 5_000_000)
        assertEquals(s.distanceM, r.stats(5_000_000).distanceM, 0.01)
        assertEquals(TrackSummary.splits(t.points), r.stats(5_000_000).splitsMs)
    }

    @Test fun loopPolylineGpxAndFormatting() {
        val t = Tracker(ActivityType.Run, autoPauseOn = false); t.start(0)
        var lat = 17.385
        for (i in 0 until 400) { lat += (if (i < 200) 3.0 else -3.0) / k; t.onFix(Fix(lat, 78.48, null, 5f, 3f, 0, i * 1000L)) }
        assertTrue(TrackSummary.isLoop(t.points))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Polyline5.encode(listOf(LatLng(38.5, -120.2), LatLng(40.7, -120.95), LatLng(43.252, -126.453))))
        val dec = Polyline5.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(43.252, dec.last().lat, 1e-5)
        assertTrue(Gpx.write("A <b>", ActivityType.Run, t.points).contains("A &lt;b&gt;"))
        assertEquals("1:05:33", Fmt.duration(3_933_000)); assertEquals("6:42", Fmt.pace(1000.0 / 402)); assertEquals("2.48", Fmt.km(2484.0))
        assertEquals("Morning run", TrackSummary.title(ActivityType.Run, 6))
        assertEquals(350, TrackSummary.kcal(ActivityType.Run, 5000.0, 70.0))
    }
}
