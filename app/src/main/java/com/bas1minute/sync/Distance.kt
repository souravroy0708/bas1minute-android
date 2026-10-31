package com.bas1minute.sync

import java.util.Locale

/** Steps → kilometres using the person's step length (Profile → Step length). */
object Distance {
    fun km(steps: Long, stepLengthCm: Int): Double = steps * stepLengthCm / 100_000.0

    /** "0.8 km", "4.2 km", "12 km" */
    fun format(km: Double): String =
        if (km < 10) String.format(Locale.US, "%.1f km", km) else String.format(Locale.US, "%.0f km", km)

    fun stepsPerKm(stepLengthCm: Int): Int = (100_000.0 / stepLengthCm).toInt()
}
