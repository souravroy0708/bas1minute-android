package com.bas1minute.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Fills older days from Health Connect, a little at a time, so "All time" really is all time.
 *
 * Each run walks [CHUNK_DAYS] further back from the oldest day already checked and saves any steps it finds in the
 * local database (never sent anywhere unless the member turned on Online features). It stops once it has seen
 * [STOP_AFTER_EMPTY] days in a row with nothing — that's the start of the member's history. Granting the optional
 * "older steps" permission resets that stop, so the walk continues past the 30-day limit.
 *
 * Runs from Recorder (app open and the 15-minute tick); a chunk is a few hundred milliseconds of reading.
 */
object HistoryImport {
    const val CHUNK_DAYS = 30
    const val STOP_AFTER_EMPTY = 120
    private const val RECENT_DAYS = 10L   // Recorder already keeps these up to date

    /** Returns how many days got steps this run. */
    suspend fun step(context: Context): Int {
        val prefs = Prefs(context)
        if (prefs.backfillEmptyRun >= STOP_AFTER_EMPTY) return 0
        val reader = StepsReader(context)
        if (!reader.healthConnectGranted()) return 0
        val db = LocalDb.get(context)
        val today = LocalDate.now()
        var day = prefs.backfillOldest.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: today.minusDays(RECENT_DAYS)
        var empty = prefs.backfillEmptyRun
        var filled = 0
        repeat(CHUNK_DAYS) {
            if (empty >= STOP_AFTER_EMPTY) return@repeat
            day = day.minusDays(1)
            val saved = withContext(Dispatchers.IO) { db.day(day) }?.steps
            if (saved != null && saved > 0) { empty = 0; return@repeat }
            val s = runCatching { reader.readDay(day) }.getOrNull()
            if (s != null && s.count > 0) {
                withContext(Dispatchers.IO) { db.saveDay(day, PhoneDay(steps = s.count, stepsSource = s.source)) }
                filled++; empty = 0
            } else empty++
        }
        prefs.backfillOldest = day.toString()
        prefs.backfillEmptyRun = empty
        return filled
    }

    /** After the "older steps" permission is granted: keep walking back from where the import stopped. */
    fun continueFurther(context: Context) { Prefs(context).backfillEmptyRun = 0 }
}
