package com.bas1minute.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Copies what the phone knows about recent days into the local database. Works offline. */
object Recorder {
    /** 10 days covers the Recording API's memory; older days are already saved. */
    suspend fun captureRecent(context: Context, days: Int = 10) {
        val steps = StepsReader(context)
        val screen = ScreenTimeReader(context)
        val db = LocalDb.get(context)
        val today = LocalDate.now()
        for (i in (days - 1) downTo 0) {
            val day = today.minusDays(i.toLong())
            val s = steps.readDay(day)
            val minutes = withContext(Dispatchers.Default) { screen.minutesFor(day) }
            withContext(Dispatchers.IO) { db.saveDay(day, s?.count, s?.source, minutes) }
        }
        withContext(Dispatchers.IO) { db.prune(today) }
    }
}
