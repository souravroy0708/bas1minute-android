package com.bas1minute.sync.schedule

/*
 * "Every weeknight 10 PM to 7 AM, automatically." The one feature every well-known paid focus app treats as
 * core — Freedom's Scheduled Sessions, Opal's Schedules, Digital Wellbeing's own Bedtime mode — and the one
 * clear gap against them here: until now, Detox only ever ran if someone remembered to start it. A schedule
 * is just that same Detox, auto-started and auto-ended at chosen recurring times, using the block choices
 * (categories, individual apps, exemptions) the member already set up in Detox itself — nothing new to
 * configure twice, nothing about what gets blocked changes.
 *
 * Plain Kotlin, no Android imports, so the actual time-window math — the part most likely to have an
 * off-by-one bug, especially overnight windows that cross midnight — is fully unit tested.
 */

data class Schedule(
    val id: Long,
    val label: String,
    /** ISO day-of-week values, 1 (Monday) through 7 (Sunday) — java.time.DayOfWeek.getValue()'s own numbering. */
    val days: Set<Int>,
    /** Minutes since midnight, 0..1439. */
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
) {
    /** True when the window runs past midnight (e.g. 22:00 \u2192 07:00), needing the two-day check below. */
    val wrapsPastMidnight: Boolean get() = endMinute <= startMinute
}

object ScheduleLogic {
    const val MINUTES_PER_DAY = 1440
    const val MAX_SCHEDULES = 10

    private fun previousIsoDay(day: Int): Int = if (day == 1) 7 else day - 1

    /** Whether [schedule] is active at this exact moment — [dayOfWeek] is 1..7 (Monday..Sunday), [minuteOfDay] is 0..1439. */
    fun isActiveNow(schedule: Schedule, dayOfWeek: Int, minuteOfDay: Int): Boolean {
        if (!schedule.enabled) return false
        return if (!schedule.wrapsPastMidnight) {
            dayOfWeek in schedule.days && minuteOfDay >= schedule.startMinute && minuteOfDay < schedule.endMinute
        } else {
            // Started yesterday and still running (e.g. it's 2 AM Tuesday, schedule set for Monday 22:00-07:00)...
            (previousIsoDay(dayOfWeek) in schedule.days && minuteOfDay < schedule.endMinute) ||
                // ...or starts today and runs past midnight.
                (dayOfWeek in schedule.days && minuteOfDay >= schedule.startMinute)
        }
    }

    /** The first enabled schedule that's active right now, if any — the one DetoxController should be running for. */
    fun activeSchedule(schedules: List<Schedule>, dayOfWeek: Int, minuteOfDay: Int): Schedule? =
        schedules.firstOrNull { isActiveNow(it, dayOfWeek, minuteOfDay) }

    /** How many minutes remain in the CURRENT active window — the duration to hand DetoxController.start(). */
    fun minutesUntilEnd(schedule: Schedule, dayOfWeek: Int, minuteOfDay: Int): Int {
        if (!schedule.wrapsPastMidnight) return (schedule.endMinute - minuteOfDay).coerceAtLeast(0)
        return if (minuteOfDay >= schedule.startMinute) {
            (MINUTES_PER_DAY - minuteOfDay) + schedule.endMinute   // rest of today, plus tomorrow's portion
        } else {
            (schedule.endMinute - minuteOfDay).coerceAtLeast(0)    // already into the next day's portion
        }
    }

    fun formatTimeOfDay(minuteOfDay: Int): String {
        val h24 = (minuteOfDay / 60) % 24
        val m = minuteOfDay % 60
        val h12 = when { h24 == 0 -> 12; h24 > 12 -> h24 - 12; else -> h24 }
        val suffix = if (h24 < 12) "AM" else "PM"
        return "%d:%02d %s".format(h12, m, suffix)
    }

    private val DAY_LABELS = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    fun dayLabel(isoDay: Int): String = DAY_LABELS[isoDay] ?: "?"

    /** "Mon, Wed, Fri" or "Every day" or "Weekdays"/"Weekends" when the set matches exactly. */
    fun daysSummary(days: Set<Int>): String = when (days) {
        setOf(1, 2, 3, 4, 5, 6, 7) -> "Every day"
        setOf(1, 2, 3, 4, 5) -> "Weekdays"
        setOf(6, 7) -> "Weekends"
        else -> days.sorted().joinToString(", ") { dayLabel(it) }
    }

    fun timeRangeSummary(schedule: Schedule): String = "${formatTimeOfDay(schedule.startMinute)} \u2013 ${formatTimeOfDay(schedule.endMinute)}"
}
