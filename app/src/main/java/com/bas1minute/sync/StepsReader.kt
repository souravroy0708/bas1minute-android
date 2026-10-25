package com.bas1minute.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
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
 * Steps for one day from two places, taking the larger number (they count the same walking):
 *  1. Health Connect: what Google Fit, Samsung Health, watches and (Android 14+) the phone itself recorded.
 *  2. Recording API: Google Play services counting with the phone's step sensor. Fallback for phones
 *     where nothing writes to Health Connect. Keeps 10 days, only from the moment we subscribe.
 */
class StepsReader(private val context: Context) {

    companion object {
        const val HC_PACKAGE = "com.google.android.apps.healthdata"
        // A plain string so older Health Connect versions simply don't grant it.
        const val READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
        val HC_PERMISSIONS: Set<String> = setOf(READ_STEPS, READ_IN_BACKGROUND)
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

    private suspend fun readHealthConnect(start: Instant, end: Instant): Long? {
        if (!healthConnectGranted()) return null
        return try {
            val result = HealthConnectClient.getOrCreate(context).aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            result[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            null // for example a background read without the background permission
        }
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
