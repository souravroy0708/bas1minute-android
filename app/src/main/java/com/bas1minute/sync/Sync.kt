package com.bas1minute.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Offline-first sync:
 *  1. Save the latest phone readings into the local database (works with no internet).
 *  2. Send every day still waiting (the outbox), oldest first, up to what the website accepts.
 *  3. Clear only the days the website confirmed. Anything else waits for the next sync.
 */
object Sync {
    data class Outcome(val ok: Boolean, val message: String, val retry: Boolean = false)

    /** The website accepts days up to 31 days old, and up to 31 items per request. */
    private const val MAX_AGE_DAYS = 30L
    private const val DAYS_PER_REQUEST = 7   // each day can be up to 4 items; the website takes 31 per request

    suspend fun run(context: Context): Outcome {
        val prefs = Prefs(context)
        val db = LocalDb.get(context)

        Recorder.captureRecent(context)

        // Privacy first: nothing leaves the phone unless sharing is on.
        if (!prefs.shareActivity) return remember(prefs, Outcome(true, "Saved on your phone only. Sharing is off."))
        val repo = com.bas1minute.sync.api.Repo(context)
        if (!repo.ensureAccount()) return remember(prefs, Outcome(false, "Saved on your phone. Will share when you’re online.", retry = true))

        // Actions taken offline (logged days, mood, calm, cheers) go first, in the order they happened.
        repo.flushOutbox()

        val today = LocalDate.now()
        val oldest = today.minusDays(MAX_AGE_DAYS)
        withContext(Dispatchers.IO) { db.expire(oldest) }

        var sentDays = 0
        var skipped = 0
        var rounds = 0
        while (rounds++ < 5) {  // at most 5 × 15 days per sync, and never an endless loop
            val batch = withContext(Dispatchers.IO) { db.pending(oldest, DAYS_PER_REQUEST) }
            if (batch.isEmpty()) break

            val items = JSONArray()
            for (row in batch) items(row).forEach { items.put(it) }
            if (items.length() == 0) {  // nothing shareable in these days (e.g. only typed data)
                withContext(Dispatchers.IO) { db.markSynced(batch) }
                if (batch.size < DAYS_PER_REQUEST) break else continue
            }

            val reply = try {
                com.bas1minute.sync.api.Api(context).post("activity/", JSONObject().put("days", items))
                    .let { SyncClient.Reply(it.code ?: throw java.io.IOException("offline"), it.json?.toString() ?: "") }
            } catch (e: Exception) {
                return remember(prefs, offline(sentDays, db))
            }

            when (reply.code) {
                200, 207 -> {
                    // 207 = the website rejected some items (for example a bad date). Those can't
                    // succeed on retry, so they're cleared too; the reply lists them.
                    if (reply.code == 207) skipped += countErrors(reply.body)
                    withContext(Dispatchers.IO) { db.markSynced(batch) }
                    sentDays += batch.size
                    if (batch.size < DAYS_PER_REQUEST) break
                }
                401 -> return remember(prefs, Outcome(false, "Please log in again."))
                400 -> return remember(prefs, Outcome(false, "The server didn’t understand the data. Update the app."))
                else -> return remember(prefs, offline(sentDays, db, server = reply.code))
            }
        }

        val message = when {
            sentDays == 0 -> "Up to date."
            skipped > 0 -> "Synced $sentDays day${if (sentDays == 1) "" else "s"}. $skipped item${if (skipped == 1) " was" else "s were"} skipped by the server."
            else -> "Synced $sentDays day${if (sentDays == 1) "" else "s"}."
        }
        return remember(prefs, Outcome(true, message))
    }

    /** Only phone-measured values go here; typed ones travel through the action outbox. */
    /** Only what sharing promises: daily steps and screen time (social, messaging, browser apps). Nothing else. */
    private fun items(row: DayRow): List<JSONObject> {
        val date = row.date.toString()
        val out = ArrayList<JSONObject>()
        if (row.steps != null && row.stepsSource != "manual") {
            out += JSONObject().put("date", date).put("steps", row.steps).put("source", row.stepsSource ?: "health_connect")
        }
        row.socialMinutes?.let { out += JSONObject().put("date", date).put("social_minutes", it).put("source", "usage_stats") }
        return out
    }

    private suspend fun offline(sentDays: Int, db: LocalDb, server: Int? = null): Outcome {
        val waiting = withContext(Dispatchers.IO) { db.pendingCount() }
        val reason = if (server != null) "Bas 1 Minute servers are having trouble ($server)." else "Couldn’t reach Bas 1 Minute."
        val done = if (sentDays > 0) " Sent $sentDays day${if (sentDays == 1) "" else "s"} first." else ""
        return Outcome(false, "$reason$done $waiting day${if (waiting == 1) "" else "s"} saved on your phone, will send later.", retry = true)
    }

    private fun countErrors(body: String): Int =
        try { JSONObject(body).optJSONArray("errors")?.length() ?: 0 } catch (e: Exception) { 0 }

    private fun remember(prefs: Prefs, outcome: Outcome): Outcome {
        prefs.lastSyncAt = System.currentTimeMillis()
        prefs.lastSyncMessage = outcome.message
        prefs.lastSyncOk = outcome.ok
        return outcome
    }
}
