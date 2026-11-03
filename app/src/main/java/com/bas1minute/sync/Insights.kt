package com.bas1minute.sync

import java.time.LocalDate
import kotlin.math.roundToInt

/** If today's average continues for N years. */
data class Forecast(val years: Int, val hours: Long, val days: Long, val wakingDays: Long, val walkKm: Long)

/** What a small daily swap adds up to in a year. */
data class Reclaim(val minutesPerDay: Int, val hoursPerYear: Int, val walkKm: Int, val workouts30: Int, val bursts: Int)

data class Insights(
    val socialToday: Int?,
    val socialAvg: Int?,          // minutes a day, last 7 days with data
    val screenAvg: Int?,
    val socialShare: Int?,        // % of screen time spent in social apps
    val daysMeasured: Int,
    val forecasts: List<Forecast>,
    val reclaim: List<Reclaim>,
    val topApps: List<Pair<String, Int>>, // 7-day minutes per social app (this phone only)
    val lastNight: DayRow?,
    val avgLastUse: String?,
    val avgFirstUse: String?,
    val avgSleep: Int?,
    val water: List<Pair<LocalDate, Int?>>,
    val weightLatest: Double?,
    val weightChange: Double?,    // vs about 30 days ago
)

object InsightMath {
    /** A brisk walk is about 5 km an hour. */
    const val WALK_KMH = 5.0
    /** Waking hours in a day, for "days of your waking life". */
    const val WAKING_HOURS = 16.0

    fun forecast(minutesPerDay: Int, years: Int): Forecast {
        val hours = minutesPerDay * 365L * years / 60
        return Forecast(
            years = years,
            hours = hours,
            days = hours / 24,
            wakingDays = (hours / WAKING_HOURS).roundToInt().toLong(),
            walkKm = (hours * WALK_KMH).roundToInt().toLong(),
        )
    }

    fun reclaim(minutesPerDay: Int): Reclaim {
        val minutesYear = minutesPerDay * 365
        return Reclaim(
            minutesPerDay = minutesPerDay,
            hoursPerYear = minutesYear / 60,
            walkKm = (minutesYear / 60.0 * WALK_KMH).roundToInt(),
            workouts30 = minutesYear / 30,
            bursts = minutesYear,
        )
    }

    /** Average clock time. Evening times are averaged around midnight, so 23:30 and 00:30 give 00:00. */
    fun averageTime(times: List<String>, evening: Boolean): String? {
        val mins = times.mapNotNull { t ->
            val parts = t.split(":")
            val m = (parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
            if (evening) (m - 12 * 60 + 24 * 60) % (24 * 60) else m
        }
        if (mins.isEmpty()) return null
        var avg = mins.average().roundToInt()
        if (evening) avg = (avg + 12 * 60) % (24 * 60)
        return "%02d:%02d".format(avg / 60, avg % 60)
    }

    fun build(db: LocalDb, today: LocalDate): Insights {
        val week = db.days(today.minusDays(6), today)
        val month = db.days(today.minusDays(35), today)
        val social = week.mapNotNull { it.socialMinutes }
        val screen = week.mapNotNull { it.screenMinutes }
        val socialAvg = if (social.isEmpty()) null else social.average().roundToInt()
        val screenAvg = if (screen.isEmpty()) null else screen.average().roundToInt()
        val weights = month.filter { it.weightKg != null }
        val latest = weights.lastOrNull()?.weightKg
        val earliest = weights.firstOrNull { it.date <= today.minusDays(21) }?.weightKg ?: weights.firstOrNull()?.weightKg
        val byDate = week.associateBy { it.date }
        return Insights(
            socialToday = byDate[today]?.socialMinutes,
            socialAvg = socialAvg,
            screenAvg = screenAvg,
            socialShare = if (socialAvg != null && screenAvg != null && screenAvg > 0) (socialAvg * 100 / screenAvg) else null,
            daysMeasured = social.size,
            forecasts = if (socialAvg == null) emptyList() else listOf(1, 3, 10).map { forecast(socialAvg, it) },
            reclaim = listOf(15, 30).map { reclaim(it) },
            topApps = db.appTotals(today.minusDays(6), today).take(6),
            lastNight = byDate[today],
            avgLastUse = averageTime(week.mapNotNull { it.lastUse }, evening = true),
            avgFirstUse = averageTime(week.mapNotNull { it.firstUse }, evening = false),
            avgSleep = week.mapNotNull { it.sleepMinutes }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            water = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to byDate[it]?.waterMl },
            weightLatest = latest,
            weightChange = if (latest != null && earliest != null && weights.size > 1) Math.round((latest - earliest) * 10) / 10.0 else null,
        )
    }
}
