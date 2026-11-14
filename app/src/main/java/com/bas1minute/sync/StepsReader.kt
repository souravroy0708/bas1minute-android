package com.bas1minute.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.LocalRecordingClient
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.request.LocalDataReadRequest
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class Steps(val count: Long, val source: String)

/**
 * Steps for one day, taking the larger number at every level rather than adding anything up — every source
 * here (a watch, Google Fit, Samsung Health, the phone's own sensor) is counting the SAME walking, so summing
 * them over-counts:
 *  1. Health Connect: read per app that wrote steps there, and keep only the largest app's total for the day
 *     (see readHealthConnect) — not Health Connect's own aggregate, which can add overlapping apps together.
 *  2. Recording API: Google Play services counting with the phone's step sensor. Fallback for phones
 *     where nothing writes to Health Connect. Keeps 10 days, only from the moment we subscribe.
 * The larger of (1) and (2) is what's shown; see readDay.
 */
class StepsReader(private val context: Context) {

    companion object {
        const val HC_PACKAGE = "com.google.android.apps.healthdata"
        // A plain string so older Health Connect versions simply don't grant it.
        const val READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
        // Optional extras: allowed one by one on Health Connect's screen
        val HC_PERMISSIONS: Set<String> = setOf(READ_STEPS, READ_IN_BACKGROUND) + HealthExtras.PERMISSIONS
    }

    fun healthConnectStatus(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun healthConnectGranted(): Boolean {
        if (healthConnectStatus() != HealthConnectClient.SDK_AVAILABLE) return false
        return try {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().contains(READ_STEPS)
        } catch (e: Exception) {
            false
        }
    }

    fun activityPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun recordingApiAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
            context, LocalRecordingClient.LOCAL_RECORDING_CLIENT_MIN_VERSION_CODE
        ) == ConnectionResult.SUCCESS

    /** Start background step counting by Google Play services. Safe to call more than once. */
    suspend fun subscribeRecording(): Boolean {
        if (!activityPermissionGranted() || !recordingApiAvailable()) return false
        return try {
            FitnessLocal.getLocalRecordingClient(context).subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA).await()
            Prefs(context).recordingSubscribed = true
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readDay(date: LocalDate): Steps? {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val end = if (dayEnd.isBefore(now)) dayEnd else now
        if (!end.isAfter(start)) return null

        val hc = readHealthConnect(start, end)
        val rec = readRecording(start, end)
        return when {
            hc == null && rec == null -> null
            rec == null || (hc != null && hc >= rec) -> Steps(hc!!, "health_connect")
            else -> Steps(rec!!, "phone_sensor")
        }
    }

    /**
     * Health Connect's own COUNT_TOTAL aggregate is NOT safe to use here: when more than one app writes
     * steps for the same walking — a watch's companion app, Google Fit, Samsung Health, and (Android 14+)
     * the phone itself can all write to Health Connect at once — its aggregate often just adds every
     * contributing app's records together for the overlapping time range, rather than reliably recognising
     * they're the same steps. That's the exact "more steps than I actually walked" bug: someone wearing a
     * watch alongside their phone would see roughly double.
     *
     * The fix: read the raw records instead of the aggregate, group them by which app wrote them
     * (metadata.dataOrigin), sum each app's own records for the day, and take the LARGEST of those per-app
     * totals — never the sum across apps. Different apps are alternative measurements of the same walking,
     * not additional steps; this mirrors the same "take the larger, not the total" rule this class already
     * applies one level up, between Health Connect and the phone's own sensor.
     */
    private suspend fun readHealthConnect(start: Instant, end: Instant): Long? {
        if (!healthConnectGranted()) return null
        return try {
            val records = HealthConnectClient.getOrCreate(context).readRecords(
                ReadRecordsRequest(StepsRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
            ).records
            if (records.isEmpty()) return 0L
            records.groupBy { it.metadata.dataOrigin.packageName }
                .mapValues { (_, recs) -> recs.sumOf { clippedCount(it, start, end) } }
                .values.maxOrNull() ?: 0L
        } catch (e: Exception) {
            null // for example a background read without the background permission
        }
    }

    /** A record's step count, scaled down if only part of its time span falls inside [start, end]. */
    private fun clippedCount(record: StepsRecord, start: Instant, end: Instant): Long {
        val overlapStart = maxOf(record.startTime, start)
        val overlapEnd = minOf(record.endTime, end)
        val overlapMs = (overlapEnd.toEpochMilli() - overlapStart.toEpochMilli()).coerceAtLeast(0)
        val fullMs = (record.endTime.toEpochMilli() - record.startTime.toEpochMilli())
        if (fullMs <= 0 || overlapMs >= fullMs) return record.count
        return ((record.count * overlapMs) / fullMs)
    }

    private suspend fun readRecording(start: Instant, end: Instant): Long? {
        if (!Prefs(context).recordingSubscribed || !activityPermissionGranted() || !recordingApiAvailable()) return null
        return try {
            val request = LocalDataReadRequest.Builder()
                .aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(start.epochSecond, end.epochSecond, TimeUnit.SECONDS)
                .build()
            val response = FitnessLocal.getLocalRecordingClient(context).readData(request).await()
            var total = 0L
            for (bucket in response.buckets) {
                for (dataSet in bucket.dataSets) {
                    for (point in dataSet.dataPoints) {
                        for (field in point.dataType.fields) {
                            total += point.getValue(field).asInt()
                        }
                    }
                }
            }
            total
        } catch (e: Exception) {
            null
        }
    }
}
