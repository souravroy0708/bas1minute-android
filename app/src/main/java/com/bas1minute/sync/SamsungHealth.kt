package com.bas1minute.sync

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Samsung Health shares with Health Connect only after the user switches it on in Samsung Health.
 * We check two things: is Samsung Health installed, and have any steps from it reached Health Connect lately.
 */
object SamsungHealth {
    const val PACKAGE = "com.sec.android.app.shealth"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * true  = Samsung Health steps from the last 7 days are in Health Connect (sharing works)
     * false = Health Connect is readable but nothing from Samsung Health (sharing is probably off)
     * null  = can't tell (not installed, or Health Connect not available/allowed)
     */
    suspend fun sharing(context: Context, hcGranted: Boolean): Boolean? {
        if (!hcGranted || !installed(context)) return null
        return try {
            val end = Instant.now()
            val result = HealthConnectClient.getOrCreate(context).aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(end.minus(7, ChronoUnit.DAYS), end),
                )
            )
            result.dataOrigins.any { it.packageName == PACKAGE }
        } catch (e: Exception) {
            null
        }
    }

    /** Opens Samsung Health. Returns false if it can't be opened. */
    fun open(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }
}
