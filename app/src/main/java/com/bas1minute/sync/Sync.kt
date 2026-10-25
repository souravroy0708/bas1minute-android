package com.bas1minute.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Reads yesterday and today on the phone and sends the daily totals to the website. */
object Sync {
    data class Outcome(val ok: Boolean, val message: String, val retry: Boolean = false)

    suspend fun run(context: Context): Outcome {
        val prefs = Prefs(context)
        if (!prefs.isConfigured) return Outcome(false, "Paste your sync key first.")

        val stepsReader = StepsReader(context)
        val screenReader = ScreenTimeReader(context)
        val today = LocalDate.now()
        val days = JSONArray()
        for (day in listOf(today.minusDays(1), today)) {
            stepsReader.readDay(day)?.let {
                days.put(JSONObject().put("date", day.toString()).put("steps", it.count).put("source", it.source))
            }
            screenReader.minutesFor(day)?.let {
                days.put(JSONObject().put("date", day.toString()).put("screen_minutes", it).put("source", "usage_stats"))
            }
        }
        if (days.length() == 0) {
            return remember(prefs, Outcome(false, "Nothing to send yet. Allow steps or screen-time access below."))
        }

        val outcome = try {
            val reply = SyncClient.post(prefs.serverUrl, prefs.syncKey, JSONObject().put("days", days))
            when (reply.code) {
                200 -> Outcome(true, "Synced.")
                207 -> Outcome(true, "Synced. Some days were skipped by the server.")
                401 -> Outcome(false, "Sync key not accepted. Make a new one on the website: Profile, then Connect your phone.")
                400 -> Outcome(false, "The server didn’t understand the data. Update the app.")
                else -> Outcome(false, "Server error ${reply.code}. We’ll try again later.", retry = true)
            }
        } catch (e: Exception) {
            Outcome(false, "Couldn’t reach the website. Check your internet.", retry = true)
        }
        return remember(prefs, outcome)
    }

    private fun remember(prefs: Prefs, outcome: Outcome): Outcome {
        prefs.lastSyncAt = System.currentTimeMillis()
        prefs.lastSyncMessage = outcome.message
        prefs.lastSyncOk = outcome.ok
        return outcome
    }
}
