package com.bas1minute.sync

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Douglas–Peucker line simplification for GPS routes: keeps the points that matter for the route's shape and
 * drops the rest. A 4 m tolerance is invisible on a phone-sized map but removes most points from a typical walk
 * or run. Plain Kotlin, no Android imports, so it's unit tested.
 */
object RouteThinning {
    /** For each point, whether to keep it. First and last are always kept. Iterative, so long routes can't overflow. */
    fun keep(lat: List<Double>, lng: List<Double>, toleranceM: Double): BooleanArray {
        val n = lat.size
        val keep = BooleanArray(n)
        if (n <= 2) { keep.fill(true); return keep }
        // Local flat projection in metres around the first point (accurate over any walk/run distance)
        val lat0 = Math.toRadians(lat[0])
        val x = DoubleArray(n) { (lng[it] - lng[0]) * 111_320.0 * cos(lat0) }
        val y = DoubleArray(n) { (lat[it] - lat[0]) * 110_540.0 }
        keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            var maxD = -1.0; var idx = -1
            for (i in a + 1 until b) {
                val d = distToSegment(x[i], y[i], x[a], y[a], x[b], y[b])
                if (d > maxD) { maxD = d; idx = i }
            }
            if (maxD > toleranceM) {
                keep[idx] = true
                stack.addLast(a to idx); stack.addLast(idx to b)
            }
        }
        return keep
    }

    private fun distToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx - px; val cy = ay + t * dy - py
        return sqrt(cx * cx + cy * cy)
    }
}
