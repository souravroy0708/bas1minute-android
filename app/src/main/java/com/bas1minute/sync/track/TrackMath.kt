package com.bas1minute.sync.track

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The GPS engine for walks and runs. Plain Kotlin with no Android imports, so it can be unit tested.
 *
 * Pipeline for every GPS fix:
 *   1. Reject fixes that are too inaccurate (> 30 m) once GPS has locked on.
 *   2. Smooth position with a small Kalman filter (removes the zig-zag GPS draws on a straight road).
 *   3. Reject teleports: implied speed faster than a human can walk/run/cycle.
 *   4. Auto-pause: standing still at a signal doesn't add time or "drift" distance.
 *   5. Only add a route point after real movement (beats stationary jitter).
 *   6. Distance, moving time, 1 km splits, elevation gain and max speed update from accepted points.
 */

enum class ActivityType(val id: String, val label: String, val verb: String, val maxSpeedMs: Double, val kcalPerKgKm: Double) {
    Walk("walk", "Walk", "walked", 4.0, 0.53),
    Run("run", "Run", "ran", 9.0, 1.0),
    Cycle("cycle", "Ride", "rode", 22.0, 0.28);

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Walk }
}

/** One raw GPS reading. elapsedMs is the monotonic clock (Location.elapsedRealtimeNanos / 1e6). */
data class Fix(
    val lat: Double, val lng: Double, val alt: Double?, val accuracy: Float,
    val speed: Float?, val timeMs: Long, val elapsedMs: Long,
)

/** One accepted point on the route. movingMs and distM are the totals at this point, so stats can be rebuilt. */
data class TrackPoint(
    val lat: Double, val lng: Double, val alt: Double?, val timeMs: Long,
    val movingMs: Long, val distM: Double, val segment: Int,
)

data class LatLng(val lat: Double, val lng: Double)

data class TrackStats(
    val distanceM: Double = 0.0,
    val movingMs: Long = 0,
    val elapsedMs: Long = 0,
    val elevGainM: Double = 0.0,
    val maxSpeedMs: Double = 0.0,
    /** Rolling speed over the last ~20 s, for "current pace". */
    val currentSpeedMs: Double = 0.0,
    val splitsMs: List<Long> = emptyList(),
    val autoPaused: Boolean = false,
    /** Without GPS: steps counted and steps per minute (0 when recording with GPS). */
    val steps: Long = 0,
    val cadenceSpm: Int = 0,
) {
    val avgSpeedMs: Double get() = if (movingMs <= 0) 0.0 else distanceM / (movingMs / 1000.0)
}

object Geo {
    private const val R = 6_371_000.0

    /** Great-circle distance in metres. */
    fun distance(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2).let { it * it }
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    fun distance(a: LatLng, b: LatLng) = distance(a.lat, a.lng, b.lat, b.lng)
}

/**
 * Minimal Kalman filter for lat/lng with accuracy as measurement noise. q = expected speed (m/s):
 * higher q follows turns faster, lower q smooths more.
 */
class KalmanLatLng(private val qMs: Double) {
    private var lat = 0.0
    private var lng = 0.0
    private var variance = -1.0
    private var lastMs = 0L

    fun reset() { variance = -1.0 }

    fun process(fLat: Double, fLng: Double, accuracy: Float, elapsedMs: Long): LatLng {
        val acc = max(accuracy.toDouble(), 1.0)
        if (variance < 0) {
            lat = fLat; lng = fLng; variance = acc * acc; lastMs = elapsedMs
        } else {
            val dt = elapsedMs - lastMs
            if (dt > 0) { variance += dt * qMs * qMs / 1000.0; lastMs = elapsedMs }
            val k = variance / (variance + acc * acc)
            lat += k * (fLat - lat)
            lng += k * (fLng - lng)
            variance *= (1 - k)
        }
        return LatLng(lat, lng)
    }
}

/**
 * The live tracking state machine. Not thread safe: call from one thread (the service's main thread).
 * Times are monotonic milliseconds (SystemClock.elapsedRealtime), except TrackPoint.timeMs (wall clock, for GPX).
 */
class Tracker(val type: ActivityType, private val autoPauseOn: Boolean = true) {
    companion object {
        const val MAX_ACCURACY_M = 30f
        const val SPLIT_M = 1000.0
        private const val STILL_WINDOW_MS = 10_000L
        private const val STILL_RADIUS_M = 6.0
        private const val RESUME_RADIUS_M = 10.0
        private const val SPEED_WINDOW_MS = 20_000L
        private const val ELEV_STEP_M = 4.0
    }

    var running = false; private set
    var segment = 0; private set
    val points = ArrayList<TrackPoint>()

    // Tuned on simulated routes with 3 m GPS noise: within ~1-2% of true distance for walks and runs.
    private val kalman = KalmanLatLng(when (type) { ActivityType.Walk -> 1.5; ActivityType.Run -> 2.5; ActivityType.Cycle -> 4.0 })
    private var distanceM = 0.0
    private var movingMs = 0L
    private var startedMono = 0L
    private var lastTickMono = 0L
    private var pausedTotalMs = 0L
    private var elevGain = 0.0
    private var elevBase: Double? = null
    private var altSmooth: Double? = null
    private var maxSpeed = 0.0
    private var autoPaused = false
    private var stillAnchor: Pair<Long, LatLng>? = null
    private var lastSmoothed: Pair<Long, LatLng>? = null
    private val recent = ArrayDeque<Pair<Long, Double>>()   // (mono, distanceM) for rolling speed
    private val splits = ArrayList<Long>()
    private var newSegmentPending = false
    /** Called with (splitIndex starting at 1, splitMs) when a kilometre is completed. */
    var onSplit: ((Int, Long) -> Unit)? = null

    fun start(nowMono: Long) {
        running = true; startedMono = nowMono; lastTickMono = nowMono
    }

    /** Rebuild after the app was closed by Android: points come from the database. */
    fun restore(saved: List<TrackPoint>, nowMono: Long) {
        points.clear(); points.addAll(saved)
        val last = saved.lastOrNull()
        distanceM = last?.distM ?: 0.0
        movingMs = last?.movingMs ?: 0L
        segment = (last?.segment ?: -1) + 1          // resume on a new segment: no straight line across the gap
        splits.clear(); splits.addAll(TrackSummary.splits(saved))
        saved.forEach { p -> p.alt?.let { addAltitude(it) } }
        startedMono = nowMono - movingMs
        lastTickMono = nowMono
        running = true
        newSegmentPending = true
        kalman.reset()
    }

    /** Advance the clock. Moving time counts only while running and not auto-paused. */
    fun tick(nowMono: Long) {
        if (nowMono <= lastTickMono) return
        val dt = nowMono - lastTickMono
        if (running && !autoPaused) movingMs += dt else if (!running) pausedTotalMs += dt
        lastTickMono = nowMono
    }

    fun pause(nowMono: Long) {
        tick(nowMono); running = false; autoPaused = false; stillAnchor = null
    }

    fun resume(nowMono: Long) {
        tick(nowMono); running = true; newSegmentPending = true; kalman.reset(); stillAnchor = null
    }

    /** Returns the accepted point, or null if the fix was filtered out. */
    fun onFix(f: Fix): TrackPoint? {
        tick(f.elapsedMs)
        if (!running) return null
        if (f.accuracy > MAX_ACCURACY_M) return null

        val p = kalman.process(f.lat, f.lng, f.accuracy, f.elapsedMs)

        // Teleport check against the previous smoothed position
        lastSmoothed?.let { (t, prev) ->
            val dt = (f.elapsedMs - t) / 1000.0
            if (dt > 0 && Geo.distance(prev, p) / dt > type.maxSpeedMs * 1.6) {
                kalman.reset()
                return null
            }
        }
        lastSmoothed = f.elapsedMs to p

        if (autoPauseOn && updateAutoPause(f, p)) return null

        val last = points.lastOrNull()
        val startSegment = newSegmentPending || last == null
        val step = if (last == null || startSegment) 0.0 else Geo.distance(last.lat, last.lng, p.lat, p.lng)
        // Movement must beat the GPS noise before it counts
        val minStep = max(4.0, min(f.accuracy.toDouble(), 15.0) * 0.8)
        if (!startSegment && step < minStep) return null

        if (startSegment && last != null) segment = last.segment + 1
        newSegmentPending = false

        val before = distanceM
        distanceM += step
        f.alt?.let { addAltitude(it) }
        f.speed?.takeIf { f.accuracy <= 15f && it <= type.maxSpeedMs }?.let { maxSpeed = max(maxSpeed, it.toDouble()) }

        // Kilometre splits, interpolated to the exact crossing
        val lastMoving = last?.movingMs ?: 0L
        while (distanceM >= (splits.size + 1) * SPLIT_M) {
            val mark = (splits.size + 1) * SPLIT_M
            val frac = if (distanceM > before) (mark - before) / (distanceM - before) else 1.0
            val at = lastMoving + ((movingMs - lastMoving) * frac).roundToLong()
            val split = at - splits.sum()
            splits.add(split)
            onSplit?.invoke(splits.size, split)
        }

        recent.addLast(f.elapsedMs to distanceM)
        while (recent.size > 2 && f.elapsedMs - recent.first().first > SPEED_WINDOW_MS) recent.removeFirst()

        val tp = TrackPoint(p.lat, p.lng, f.alt, f.timeMs, movingMs, distanceM, segment)
        points.add(tp)
        return tp
    }

    /** True while standing still (fix should not add distance). */
    private fun updateAutoPause(f: Fix, p: LatLng): Boolean {
        val anchor = stillAnchor
        if (anchor == null) { stillAnchor = f.elapsedMs to p; return autoPaused }
        val moved = Geo.distance(anchor.second, p)
        if (autoPaused) {
            if (moved > RESUME_RADIUS_M || (f.speed ?: 0f) > 1.2f) {
                autoPaused = false
                stillAnchor = f.elapsedMs to p
                return false
            }
            return true
        }
        if (moved > STILL_RADIUS_M) { stillAnchor = f.elapsedMs to p; return false }
        if (f.elapsedMs - anchor.first >= STILL_WINDOW_MS && (f.speed ?: 0f) < 0.6f) {
            autoPaused = true
            recent.clear()
            return true
        }
        return false
    }

    private fun addAltitude(alt: Double) {
        val s = altSmooth?.let { it + 0.25 * (alt - it) } ?: alt
        altSmooth = s
        val base = elevBase
        if (base == null) { elevBase = s; return }
        if (s - base > ELEV_STEP_M) { elevGain += s - base; elevBase = s }
        else if (base - s > ELEV_STEP_M) elevBase = s
    }

    fun stats(nowMono: Long): TrackStats {
        tick(nowMono)
        val speed = if (autoPaused || !running || recent.size < 2) 0.0 else {
            val a = recent.first(); val b = recent.last()
            val dt = (b.first - a.first) / 1000.0
            if (dt > 3) (b.second - a.second) / dt else 0.0
        }
        return TrackStats(
            distanceM = distanceM, movingMs = movingMs, elapsedMs = movingMs + pausedTotalMs,
            elevGainM = elevGain, maxSpeedMs = maxSpeed, currentSpeedMs = speed,
            splitsMs = splits.toList(), autoPaused = autoPaused,
        )
    }
}

/** Things worked out from a finished route. */
object TrackSummary {
    /** Kilometre split times from stored points (same interpolation as live). */
    fun splits(points: List<TrackPoint>): List<Long> {
        val out = ArrayList<Long>()
        var used = 0L
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            while (b.distM >= (out.size + 1) * Tracker.SPLIT_M) {
                val mark = (out.size + 1) * Tracker.SPLIT_M
                val frac = if (b.distM > a.distM) ((mark - a.distM) / (b.distM - a.distM)).coerceIn(0.0, 1.0) else 1.0
                val at = a.movingMs + ((b.movingMs - a.movingMs) * frac).roundToLong()
                out.add(at - used); used = at
            }
        }
        return out
    }

    /** A loop (round trip) ends close to where it started. */
    fun isLoop(points: List<TrackPoint>): Boolean {
        if (points.size < 3) return false
        val a = points.first(); val b = points.last()
        return b.distM >= 300 && Geo.distance(a.lat, a.lng, b.lat, b.lng) <= 75.0
    }

    fun kcal(type: ActivityType, distanceM: Double, weightKg: Double): Int =
        (type.kcalPerKgKm * weightKg * distanceM / 1000.0).roundToInt()

    /** "Morning run", "Evening walk" from the start hour. */
    fun title(type: ActivityType, hour: Int): String {
        val part = when (hour) { in 5..10 -> "Morning"; in 11..15 -> "Afternoon"; in 16..19 -> "Evening"; else -> "Night" }
        return "$part ${type.label.lowercase(Locale.ENGLISH)}"
    }

    /**
     * Privacy: drop the first and last [meters] of the route so a shared picture doesn't show the
     * exact door you left from. Short routes keep at least their middle third.
     */
    fun trimEnds(points: List<TrackPoint>, meters: Double = 200.0): List<TrackPoint> {
        if (points.size < 4) return points
        val total = points.last().distM
        val cut = min(meters, total / 3)
        val kept = points.filter { it.distM >= cut && it.distM <= total - cut }
        return if (kept.size >= 2) kept else points
    }

    /** Points split into drawable segments (a pause starts a new line). */
    fun segments(points: List<TrackPoint>): List<List<LatLng>> =
        points.groupBy { it.segment }.toSortedMap().values.map { seg -> seg.map { LatLng(it.lat, it.lng) } }
}

/** Douglas–Peucker simplification, for thumbnails and the share card. */
object Simplify {
    fun dp(points: List<LatLng>, toleranceM: Double): List<LatLng> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size); keep[0] = true; keep[points.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>(); stack.addLast(0 to points.lastIndex)
        val lat0 = points[0].lat
        val kx = 111_320.0 * cos(Math.toRadians(lat0)); val ky = 110_540.0
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var maxD = 0.0; var idx = -1
            val ax = points[s].lng * kx; val ay = points[s].lat * ky
            val bx = points[e].lng * kx; val by = points[e].lat * ky
            for (i in s + 1 until e) {
                val d = segDist(points[i].lng * kx, points[i].lat * ky, ax, ay, bx, by)
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx > 0 && maxD > toleranceM) { keep[idx] = true; stack.addLast(s to idx); stack.addLast(idx to e) }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** Simplify until at most [maxPoints] remain. */
    fun toMax(points: List<LatLng>, maxPoints: Int): List<LatLng> {
        var tol = 3.0
        var out = dp(points, tol)
        while (out.size > maxPoints && tol < 5000) { tol *= 1.8; out = dp(points, tol) }
        return out
    }

    private fun segDist(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        val len = dx * dx + dy * dy
        val t = if (len == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len).coerceIn(0.0, 1.0)
        val cx = ax + t * dx - px; val cy = ay + t * dy - py
        return sqrt(cx * cx + cy * cy)
    }
}

/** Google's encoded polyline format: a compact route preview stored with each activity. */
object Polyline5 {
    fun encode(points: List<LatLng>): String {
        val sb = StringBuilder()
        var pLat = 0L; var pLng = 0L
        for (p in points) {
            val lat = Math.round(p.lat * 1e5); val lng = Math.round(p.lng * 1e5)
            enc(lat - pLat, sb); enc(lng - pLng, sb)
            pLat = lat; pLng = lng
        }
        return sb.toString()
    }

    private fun enc(v: Long, sb: StringBuilder) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar()); x = x shr 5 }
        sb.append((x + 63).toInt().toChar())
    }

    fun decode(s: String): List<LatLng> {
        val out = ArrayList<LatLng>()
        var i = 0; var lat = 0L; var lng = 0L
        fun next(): Long {
            var result = 0L; var shift = 0
            while (i < s.length) {
                val b = s[i++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
                if (b < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        while (i < s.length) {
            lat += next()
            lng += next()
            out.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return out
    }
}

/** GPX 1.1: opens in Strava, Google Earth, Komoot, Garmin Connect and most map apps. */
object Gpx {
    fun write(name: String, type: ActivityType, points: List<TrackPoint>): String {
        val iso = java.time.format.DateTimeFormatter.ISO_INSTANT
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Bas 1 Minute\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        points.firstOrNull()?.let { sb.append("  <metadata><time>${iso.format(java.time.Instant.ofEpochMilli(it.timeMs))}</time></metadata>\n") }
        sb.append("  <trk>\n    <name>${esc(name)}</name>\n    <type>${type.id}</type>\n")
        for ((_, seg) in points.groupBy { it.segment }.toSortedMap()) {
            sb.append("    <trkseg>\n")
            for (p in seg) {
                sb.append(String.format(Locale.US, "      <trkpt lat=\"%.7f\" lon=\"%.7f\">", p.lat, p.lng))
                p.alt?.let { sb.append(String.format(Locale.US, "<ele>%.1f</ele>", it)) }
                sb.append("<time>${iso.format(java.time.Instant.ofEpochMilli(p.timeMs))}</time></trkpt>\n")
            }
            sb.append("    </trkseg>\n")
        }
        sb.append("  </trk>\n</gpx>\n")
        return sb.toString()
    }
}

/** Display helpers. */
object Fmt {
    /** "2.48" (km, two decimals under 100). */
    fun km(m: Double): String = if (m < 100_000) String.format(Locale.US, "%.2f", m / 1000) else String.format(Locale.US, "%.1f", m / 1000)

    /** "42:07" or "1:05:33". */
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** Pace in min/km: "6:42". "–" when not moving. */
    fun pace(speedMs: Double): String {
        if (speedMs < 0.3) return "–"
        val secPerKm = (1000 / speedMs).roundToInt()
        if (secPerKm >= 60 * 60) return "–"
        return String.format(Locale.US, "%d:%02d", secPerKm / 60, secPerKm % 60)
    }

    fun paceOfSplit(ms: Long, meters: Double = 1000.0): String = pace(if (ms <= 0) 0.0 else meters / (ms / 1000.0))

    /** km/h with one decimal. */
    fun kmh(speedMs: Double): String = String.format(Locale.US, "%.1f", speedMs * 3.6)

    /** Spoken split: "1 kilometre. Time 6 minutes 12 seconds. Pace 6 minutes 12 per kilometre." */
    fun spokenSplit(index: Int, splitMs: Long, totalMs: Long): String {
        fun spoken(ms: Long): String {
            val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
            return listOfNotNull(
                h.takeIf { it > 0 }?.let { "$it hour${if (it == 1L) "" else "s"}" },
                m.takeIf { it > 0 }?.let { "$it minute${if (it == 1L) "" else "s"}" },
                "$sec second${if (sec == 1L) "" else "s"}",
            ).joinToString(" ")
        }
        val km = if (index == 1) "1 kilometre" else "$index kilometres"
        return "$km. Time ${spoken(totalMs)}. Last kilometre ${spoken(splitMs)}."
    }
}
