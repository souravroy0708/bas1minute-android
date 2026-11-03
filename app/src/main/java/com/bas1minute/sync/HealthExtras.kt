package com.bas1minute.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sleep, water and weight, read automatically from Health Connect when another app or device
 * saves them there (a watch or Samsung Health for sleep, a water app, a smart scale).
 * Each returns null when there's nothing, so typed values and estimates are never overwritten.
 */
class HealthExtras(private val context: Context) {

    companion object {
        val SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val WATER: String = HealthPermission.getReadPermission(HydrationRecord::class)
        val WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)
        val PERMISSIONS: Set<String> = setOf(SLEEP, WATER, WEIGHT)
    }

    data class Granted(val sleep: Boolean, val water: Boolean, val weight: Boolean)

    suspend fun granted(): Granted {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return Granted(false, false, false)
        return try {
            val g = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            Granted(SLEEP in g, WATER in g, WEIGHT in g)
        } catch (e: Exception) {
            Granted(false, false, false)
        }
    }

    private fun client() = HealthConnectClient.getOrCreate(context)

    /** Sleep that ended on this day's morning (noon yesterday to noon today), in minutes. */
    suspend fun sleepMinutes(date: LocalDate, g: Granted): Int? {
        if (!g.sleep) return null
        val zone = ZoneId.systemDefault()
        val start = date.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
        val end = date.atTime(12, 0).atZone(zone).toInstant()
        return try {
            val sessions = client().readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
            ).records
            if (sessions.isEmpty()) null
            else sessions.sumOf { Duration.between(maxOf(it.startTime, start), minOf(it.endTime, end)).toMinutes() }.toInt()
                .coerceIn(0, 18 * 60)
        } catch (e: Exception) {
            null
        }
    }

    /** Water logged in any app for the day, in ml. */
    suspend fun waterMl(date: LocalDate, g: Granted): Int? {
        if (!g.water) return null
        val (start, end) = dayRange(date)
        return try {
            val r = client().aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), TimeRangeFilter.between(start, end)))
            r[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.toInt()?.takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    /** The day's last weight reading (smart scale or health app), in kg. */
    suspend fun weightKg(date: LocalDate, g: Granted): Double? {
        if (!g.weight) return null
        val (start, end) = dayRange(date)
        return try {
            client().readRecords(ReadRecordsRequest(WeightRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.time }?.weight?.inKilograms
        } catch (e: Exception) {
            null
        }
    }

    private fun dayRange(date: LocalDate): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        return start to (if (dayEnd.isBefore(now)) dayEnd else now)
    }
}
