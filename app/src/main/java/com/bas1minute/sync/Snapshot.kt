package com.bas1minute.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class DayValue(val date: LocalDate, val steps: Long?, val screenMinutes: Int?, val row: DayRow? = null)

/** Everything the screens show, read from the phone in one go. */
data class Snapshot(
    val hcStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val hcGranted: Boolean = false,
    val activityGranted: Boolean = false,
    val recordingAvailable: Boolean = false,
    val usageAccess: Boolean = false,
    val todaySource: String? = null,
    val week: List<DayValue> = emptyList(),
    val loaded: Boolean = false,
    val pendingDays: Int = 0,
    val savedDays: Int = 0,
    val insights: Insights? = null,
    val extras: HealthExtras.Granted = HealthExtras.Granted(false, false, false),
) {
    val today: DayValue? get() = week.lastOrNull()
    val stepsReady: Boolean get() = hcGranted || (activityGranted && recordingAvailable)
}

/**
 * Reads the phone into the local database, then builds the screen from the database.
 * No network involved, so this works offline and when the website is down.
 */
suspend fun loadSnapshot(context: Context): Snapshot {
    val steps = StepsReader(context)
    val screen = ScreenTimeReader(context)
    if (steps.activityPermissionGranted() && !Prefs(context).recordingSubscribed) steps.subscribeRecording()

    Recorder.captureRecent(context)

    val db = LocalDb.get(context)
    val today = LocalDate.now()
    val saved = withContext(Dispatchers.IO) { db.days(today.minusDays(6), today) }.associateBy { it.date }
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { day ->
        val row = saved[day]
        DayValue(day, row?.steps, row?.screenMinutes, row)
    }
    val todaySource = saved[today]?.stepsSource
    val pending = withContext(Dispatchers.IO) { db.pendingCount() }
    val savedCount = withContext(Dispatchers.IO) { db.savedDayCount() }
    val insights = withContext(Dispatchers.IO) { InsightMath.build(db, today) }
    val extras = HealthExtras(context).granted()
    return Snapshot(
        hcStatus = steps.healthConnectStatus(),
        hcGranted = steps.healthConnectGranted(),
        activityGranted = steps.activityPermissionGranted(),
        recordingAvailable = steps.recordingApiAvailable(),
        usageAccess = screen.hasAccess(),
        todaySource = todaySource,
        week = week,
        loaded = true,
        pendingDays = pending,
        savedDays = savedCount,
        insights = insights,
        extras = extras,
    )
}
