package com.bas1minute.sync.track

import kotlin.math.max
import kotlin.math.roundToLong

/*
 * Recording without GPS. Plain Kotlin with no Android imports, so it can be unit tested.
 *
 * Three ways to record, best first. The person chooses whether GPS is used; the app falls back on its own when
 * something isn't available, so pressing Start always records something:
 *
 *   Gps    – location while recording: route on a map, distance, pace, splits, elevation (TrackingService + Tracker).
 *   Steps  – no location: the phone's step counter. Distance = steps × step length, where the step length starts
 *            from Profile → Step length and grows with cadence when running. Time, pace, speed, splits, steps
 *            and cadence. Auto-pause when no steps arrive for a few seconds.
 *   Timer  – no location and no step counter (a ride, a phone without the sensor, or Physical activity not
 *            allowed): moving time only. The distance can be typed in after finishing.
 */

enum class TrackSource(val id: String) {
    Gps("gps"), Steps("steps"), Timer("timer");

    val hasMap: Boolean get() = this == Gps

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Gps }
}

/** Step length from the profile, adjusted for how fast the steps come (steps per minute). */
object Stride {
    /** Metres per step. [baseCm] is the walking step length from Profile (default 76 cm). */
    fun meters(baseCm: Int, cadenceSpm: Double, type: ActivityType): Double {
        val factor = when {
            cadenceSpm <= 0.0 -> 1.0
            cadenceSpm < 90 -> 0.85                       // slow, short steps (indoors, shopping)
            cadenceSpm < 125 -> 1.0                       // normal walk
            cadenceSpm < 145 -> 1.08                      // brisk walk
            type == ActivityType.Walk -> 1.12             // very fast walk
            else -> (1.25 + (cadenceSpm - 145) * 0.006).coerceAtMost(1.6)   // running: longer steps the faster you go
        }
        return baseCm / 100.0 * factor
    }

    /** Step length (cm) that would have given [distanceM] over [steps]: used when the person corrects a walk. */
    fun calibratedCm(distanceM: Double, steps: Long): Int? =
        if (steps < 200 || distanceM < 100) null else (distanceM * 100 / steps).roundToLong().toInt().coerceIn(40, 120)
}

/**
 * Live recording without GPS. Same contract as [Tracker]: call from one thread; times are monotonic ms
 * (SystemClock.elapsedRealtime). Feed it the step counter's running total; it works out everything else.
 */
class StepTracker(
    val type: ActivityType,
    private val baseStepCm: Int,
    /** Steps: stop the clock when no steps come. Timer: always off (there's nothing to detect stillness with). */
    private val autoPauseOn: Boolean = true,
) {
    companion object {
        /** No steps for this long counts as standing still. Long enough for a red light, short enough to be fair. */
        const val STILL_MS = 12_000L
        private const val CADENCE_WINDOW_MS = 30_000L
        private const val SPEED_WINDOW_MS = 20_000L
        /** A counter jump bigger than this between two readings is a sensor glitch, not steps. */
        private const val MAX_STEPS_PER_SECOND = 6.0
    }

    var running = false; private set
    private var distanceM = 0.0
    private var movingMs = 0L
    private var pausedMs = 0L
    private var steps = 0L
    private var lastTickMono = 0L
    private var lastStepMono = 0L
    private var autoPaused = false
    private var lastCounter: Long? = null
    private var lastCounterMono = 0L
    private var maxSpeed = 0.0
    private var movingAtLastStep = 0L
    private val stepWindow = ArrayDeque<Pair<Long, Long>>()       // (mono, total steps) for cadence
    private val distWindow = ArrayDeque<Pair<Long, Double>>()     // (mono, distance) for current speed
    private val splits = ArrayList<Long>()
    /** Called with (splitIndex starting at 1, splitMs) when a kilometre is completed. */
    var onSplit: ((Int, Long) -> Unit)? = null

    fun start(nowMono: Long) {
        running = true; lastTickMono = nowMono; lastStepMono = nowMono
    }

    /** Continue after Android closed the app: totals come from the database. */
    fun restore(distanceM: Double, movingMs: Long, steps: Long, splitsMs: List<Long>, nowMono: Long) {
        this.distanceM = distanceM; this.movingMs = movingMs; this.steps = steps; movingAtLastStep = movingMs
        splits.clear(); splits.addAll(splitsMs)
        lastCounter = null                    // the counter may have reset (phone restarted): take a new baseline
        start(nowMono)
    }

    fun tick(nowMono: Long) {
        if (nowMono <= lastTickMono) return
        val dt = nowMono - lastTickMono
        lastTickMono = nowMono
        if (!running) { pausedMs += dt; return }
        if (autoPaused) return
        movingMs += dt
        // Standing still: stop the clock, and take back the seconds spent waiting to be sure.
        if (autoPauseOn && nowMono - lastStepMono >= STILL_MS) {
            val idle = nowMono - lastStepMono
            movingMs = max(0L, movingMs - idle)
            autoPaused = true
            distWindow.clear()
        }
    }

    fun pause(nowMono: Long) { tick(nowMono); running = false; autoPaused = false }

    fun resume(nowMono: Long) {
        tick(nowMono); running = true; autoPaused = false; lastStepMono = nowMono; movingAtLastStep = movingMs
        lastCounter = null                    // steps taken while paused don't count
        distWindow.clear(); stepWindow.clear()
    }

    /**
     * The step counter's total (steps since the phone started) at [eventMono]. The first reading is only a
     * baseline. Returns the steps added.
     */
    fun onStepCounter(total: Long, eventMono: Long): Long {
        val prev = lastCounter
        lastCounter = total
        val prevMono = lastCounterMono
        lastCounterMono = eventMono
        if (prev == null || !running) return 0
        var delta = total - prev
        if (delta < 0) delta = 0                          // counter reset (phone restarted)
        val seconds = max(1.0, (eventMono - prevMono) / 1000.0)
        if (delta > MAX_STEPS_PER_SECOND * seconds + 20) return 0   // glitch
        if (delta == 0L) return 0

        tick(eventMono)
        if (autoPaused) { autoPaused = false }
        lastStepMono = eventMono
        steps += delta

        stepWindow.addLast(eventMono to steps)
        while (stepWindow.size > 2 && eventMono - stepWindow.first().first > CADENCE_WINDOW_MS) stepWindow.removeFirst()

        val before = distanceM
        distanceM += delta * Stride.meters(baseStepCm, cadence(), type)
        recordSplits(before, movingAtLastStep)
        movingAtLastStep = movingMs

        distWindow.addLast(eventMono to distanceM)
        while (distWindow.size > 2 && eventMono - distWindow.first().first > SPEED_WINDOW_MS) distWindow.removeFirst()
        val speed = windowSpeed()
        if (speed > 0 && speed <= type.maxSpeedMs) maxSpeed = max(maxSpeed, speed)
        return delta
    }

    /** Steps per minute over the last ~30 s (0 until there's enough to tell). */
    fun cadence(): Double {
        if (stepWindow.size < 2) return 0.0
        val a = stepWindow.first(); val b = stepWindow.last()
        val minutes = (b.first - a.first) / 60_000.0
        return if (minutes < 5.0 / 60) 0.0 else (b.second - a.second) / minutes
    }

    private fun windowSpeed(): Double {
        if (distWindow.size < 2) return 0.0
        val a = distWindow.first(); val b = distWindow.last()
        val dt = (b.first - a.first) / 1000.0
        return if (dt < 8) 0.0 else (b.second - a.second) / dt
    }

    /** Kilometre splits, interpolated between the previous step reading and this one (same as GPS). */
    private fun recordSplits(before: Double, movingBefore: Long) {
        while (distanceM >= (splits.size + 1) * Tracker.SPLIT_M) {
            val mark = (splits.size + 1) * Tracker.SPLIT_M
            val done = splits.sum()
            val frac = if (distanceM > before) ((mark - before) / (distanceM - before)).coerceIn(0.0, 1.0) else 1.0
            val at = (movingBefore + (movingMs - movingBefore) * frac).roundToLong().coerceAtLeast(done)
            val split = at - done
            splits.add(split)
            onSplit?.invoke(splits.size, split)
        }
    }

    fun stats(nowMono: Long): TrackStats {
        tick(nowMono)
        val live = !autoPaused && running && nowMono - lastStepMono < SPEED_WINDOW_MS
        val avgCadence = if (movingMs < 30_000) 0 else (steps / (movingMs / 60_000.0)).toInt()
        return TrackStats(
            distanceM = distanceM, movingMs = movingMs, elapsedMs = movingMs + pausedMs,
            elevGainM = 0.0, maxSpeedMs = maxSpeed, currentSpeedMs = if (live) windowSpeed() else 0.0,
            splitsMs = splits.toList(), autoPaused = autoPaused,
            steps = steps, cadenceSpm = if (live && cadence() > 0) cadence().toInt() else avgCadence,
        )
    }
}

/** Splits are stored as "312000,298500,..." on step and timer activities (GPS ones rebuild them from points). */
object SplitsText {
    fun write(splits: List<Long>): String? = splits.takeIf { it.isNotEmpty() }?.joinToString(",")
    fun read(s: String?): List<Long> = s?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
}
