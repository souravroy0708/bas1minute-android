package com.bas1minute.sync.sync

import android.content.Context
import com.bas1minute.sync.api.Api
import com.bas1minute.sync.detox.DetoxStore
import com.bas1minute.sync.detox.Outcome
import com.bas1minute.sync.diary.DiaryStore
import com.bas1minute.sync.diary.EntryType
import com.bas1minute.sync.goal.GoalKind
import com.bas1minute.sync.goal.GoalStore
import com.bas1minute.sync.goal.LifeGoalPhoto
import com.bas1minute.sync.goal.LifeGoalStore
import com.bas1minute.sync.diary.DiaryPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

/**
 * One-way upload: phone → website, for members who turn on "Sync my goals, diary and detox" in Profile.
 * Sends the full current local state as a plain upsert (see apps.journal.views.sync on the server, keyed by
 * each row's own client uuid), then uploads any photo the server doesn't have yet. There's no download —
 * the phone stays the source of truth, and the website is a read-only mirror for viewing and backup.
 */
object JournalSync {

    /** Result for the UI: how many rows of each kind were accepted, or null if it couldn't run at all. */
    data class SyncResult(val ok: Boolean, val message: String)

    suspend fun run(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val prefs = com.bas1minute.sync.Prefs(context)
        if (!prefs.syncPersonal) return@withContext SyncResult(false, "Sync is off.")
        val api = Api(context)

        val zone = ZoneId.systemDefault()
        fun epochMs(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

        val goalStore = GoalStore(context)
        val lifeStore = LifeGoalStore(context)
        val diaryStore = DiaryStore(context)
        val detoxStore = DetoxStore.get(context)

        val body = JSONObject()

        val goals = goalStore.all()
        body.put("habit_goals", JSONArray().apply {
            goals.forEach { g ->
                put(JSONObject().apply {
                    put("client_id", g.uuid); put("kind", g.kind.id); put("title", g.title); put("target", g.target)
                    put("created_at_ms", g.createdAt)
                    g.archivedAt?.let { put("archived_at_ms", it) }
                })
            }
        })
        body.put("checkins", JSONArray().apply {
            goals.filter { it.kind == GoalKind.Custom }.forEach { g ->
                val from = java.time.Instant.ofEpochMilli(g.createdAt).atZone(zone).toLocalDate()
                goalStore.checkins(g.id, from, LocalDate.now().plusDays(1)).forEach { (date, c) ->
                    put(JSONObject().apply { put("goal_client_id", g.uuid); put("date", date.toString()); put("done", c.done) })
                }
            }
        })

        val lifeGoals = lifeStore.all()
        body.put("life_goals", JSONArray().apply {
            lifeGoals.forEach { g ->
                put(JSONObject().apply {
                    put("client_id", g.uuid); put("horizon", g.horizon.id); put("title", g.title)
                    put("details", g.details); put("why", g.why); put("obstacle", g.obstacle); put("plan", g.plan)
                    put("started_at_ms", g.startedAt); put("target_date", g.targetDate.toString())
                    g.achievedAt?.let { put("achieved_at_ms", it) }
                    g.archivedAt?.let { put("archived_at_ms", it) }
                })
            }
        })
        body.put("milestones", JSONArray().apply {
            lifeGoals.forEach { g ->
                lifeStore.milestones(g.id).forEach { m ->
                    put(JSONObject().apply {
                        put("client_id", m.uuid); put("life_goal_client_id", g.uuid); put("title", m.title)
                        put("done", m.done); put("sort_order", m.order)
                        m.doneAt?.let { put("done_at_ms", it) }
                    })
                }
            }
        })

        val diaryEntries = diaryStore.timeline()
        body.put("diary_entries", JSONArray().apply {
            diaryEntries.forEach { e ->
                put(JSONObject().apply {
                    put("client_id", e.uuid); put("type", if (e.type == EntryType.Milestone) "milestone" else "reflection")
                    put("date", e.date.toString()); put("title", e.title); put("category", e.category.id)
                    put("story", e.story); put("created_at_ms", e.createdAt)
                    e.effort?.let { put("effort", it) }
                    e.satisfaction?.let { put("satisfaction", it) }
                })
            }
        })

        val sessions = detoxStore.sessions().filter { it.outcome != Outcome.ACTIVE }
        body.put("detox_sessions", JSONArray().apply {
            sessions.forEach { s ->
                put(JSONObject().apply {
                    put("client_id", s.id.toString()); put("started_at_ms", s.startMs); put("planned_end_at_ms", s.plannedEndMs)
                    put("outcome", s.outcome.name); put("blocked_attempts", s.blockedAttempts); put("why", s.why)
                    s.endMs?.let { put("ended_at_ms", it) }
                    s.reason?.let { put("reason", it.name) }
                    s.brokenBy?.let { put("broken_by", it) }
                })
            }
        })

        val reply = api.post("journal/sync/", body)
        if (!reply.ok) {
            return@withContext SyncResult(false, reply.error ?: "Couldn't reach the website. Try again later.")
        }

        // Photos: small, separate requests, so one bad photo never fails the metadata sync above.
        // Plain for-loops, not .forEach { } — suspend calls (uploadPhoto) aren't allowed inside a forEach lambda.
        var photoFailures = 0
        for (g in lifeGoals) {
            val path = LifeGoalPhoto.path(context, g.id)
            if (path != null && needsUpload(prefs, g.uuid, path)) {
                val ok = uploadPhoto(api, "journal/life-goals/${g.uuid}/photo/", path)
                if (ok) markUploaded(prefs, g.uuid, path) else photoFailures++
            }
        }
        for (e in diaryEntries) {
            val path = DiaryPhoto.path(context, e.id)
            if (path != null && needsUpload(prefs, e.uuid, path)) {
                val ok = uploadPhoto(api, "journal/diary/${e.uuid}/photo/", path)
                if (ok) markUploaded(prefs, e.uuid, path) else photoFailures++
            }
        }

        prefs.journalLastSyncAt = System.currentTimeMillis()
        val counts = reply.json?.optJSONObject("saved")
        SyncResult(true, if (photoFailures > 0) "Synced ($photoFailures photo${if (photoFailures == 1) "" else "s"} will retry next time)." else "Synced.")
    }

    private suspend fun uploadPhoto(api: Api, path: String, filePath: String): Boolean {
        val bytes = runCatching { File(filePath).readBytes() }.getOrNull() ?: return false
        val body = JSONObject().put("data_base64", Base64.getEncoder().encodeToString(bytes))
        return api.post(path, body).ok
    }

    // ---- avoid re-uploading an unchanged photo every sync: remember "uuid:lastModified" once it succeeds ----

    private fun needsUpload(prefs: com.bas1minute.sync.Prefs, uuid: String, path: String): Boolean {
        val mtime = File(path).lastModified()
        return prefs.syncedPhotoMarks["$uuid:$mtime"] != true
    }

    private fun markUploaded(prefs: com.bas1minute.sync.Prefs, uuid: String, path: String) {
        val mtime = File(path).lastModified()
        val marks = prefs.syncedPhotoMarks.toMutableMap()
        marks.keys.removeAll { it.startsWith("$uuid:") }   // drop any stale mark for an older version of this photo
        marks["$uuid:$mtime"] = true
        prefs.syncedPhotoMarks = marks
    }
}
