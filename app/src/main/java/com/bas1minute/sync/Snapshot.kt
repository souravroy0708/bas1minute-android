package com.bas1minute.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class DayValue(val date: LocalDate, val steps: Long?, val screenMinutes: Int?)

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
) {
    val today: DayValue? get() = week.lastOrNull()
    val stepsReady: Boolean get() = hcGranted || (activityGranted && recordingAvailable)
}

suspend fun loadSnapshot(context: Context): Snapshot {
    val steps = StepsReader(context)
    val screen = ScreenTimeReader(context)
    if (steps.activityPermissionGranted() && !Prefs(context).recordingSubscribed) steps.subscribeRecording()

    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    var todaySource: String? = null
    val week = days.map { day ->
        val s = steps.readDay(day)
        if (day == today) todaySource = s?.source
        val minutes = withContext(Dispatchers.Default) { screen.minutesFor(day) }
        DayValue(day, s?.count, minutes)
    }
    return Snapshot(
        hcStatus = steps.healthConnectStatus(),
        hcGranted = steps.healthConnectGranted(),
        activityGranted = steps.activityPermissionGranted(),
        recordingAvailable = steps.recordingApiAvailable(),
        usageAccess = screen.hasAccess(),
        todaySource = todaySource,
        week = week,
        loaded = true,
    )
}
