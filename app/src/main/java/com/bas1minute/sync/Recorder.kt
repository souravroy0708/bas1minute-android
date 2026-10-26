package com.bas1minute.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Copies everything the phone knows about recent days into the local database. Works offline. */
object Recorder {
    /** 10 days covers the Recording API's memory and Android's usage history. Older days are already saved. */
    suspend fun captureRecent(context: Context, days: Int = 10) {
        val steps = StepsReader(context)
        val usage = ScreenTimeReader(context)
        val health = HealthExtras(context)
        val granted = health.granted()
        val db = LocalDb.get(context)
        val today = LocalDate.now()
        for (i in (days - 1) downTo 0) {
            val day = today.minusDays(i.toLong())
            val s = steps.readDay(day)
            val u = withContext(Dispatchers.Default) { usage.dayUsage(day) }
            val night = withContext(Dispatchers.Default) { usage.night(day) }
            val hcSleep = health.sleepMinutes(day, granted)
            val phoneDay = PhoneDay(
                steps = s?.count, stepsSource = s?.source,
                screenMinutes = u?.screenMinutes, socialMinutes = u?.socialMinutes,
                sleepMinutes = hcSleep ?: night?.phoneFreeMinutes,
                sleepSource = if (hcSleep != null) "health_connect" else if (night != null) "phone_estimate" else null,
                firstUse = night?.firstUse?.toString(), lastUse = night?.lastUse?.toString(),
                waterMl = health.waterMl(day, granted), weightKg = health.weightKg(day, granted),
            )
            withContext(Dispatchers.IO) {
                db.saveDay(day, phoneDay)
                u?.let { db.saveApps(day, it.socialApps) }
            }
        }
        withContext(Dispatchers.IO) { db.prune(today) }
    }
}
