package com.bas1minute.sync.sync

import android.content.Context
import com.bas1minute.sync.Prefs
import com.bas1minute.sync.api.Api
import com.bas1minute.sync.detox.BreakReason
import com.bas1minute.sync.detox.DetoxSession
import com.bas1minute.sync.detox.DetoxStore
import com.bas1minute.sync.detox.Outcome
import com.bas1minute.sync.diary.Category
import com.bas1minute.sync.diary.DiaryPhoto
import com.bas1minute.sync.diary.DiaryStore
import com.bas1minute.sync.diary.EntryType
import com.bas1minute.sync.goal.GoalKind
import com.bas1minute.sync.goal.GoalStore
import com.bas1minute.sync.goal.Horizon
import com.bas1minute.sync.goal.LifeGoalPhoto
import com.bas1minute.sync.goal.LifeGoalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Base64

/**
 * The other half of JournalSync: downloads everything previously backed up to the website and writes it
 * back into local storage — for the moment auth/device/ recognises a fresh install as the SAME phone that
 * synced before (see Repo.ensureAccount's Welcome.restoreOffer) and there's nothing local to lose yet.
 *
 * Deliberately simple rather than a general two-way sync: this only ever runs when every local Goals, Life
 * goals, Diary and Detox table is genuinely empty (see [localStorageIsEmpty]), so it's always a plain
 * "write everything" with no merge decisions to get wrong. Each row keeps the uuid it already had on the
 * server, so a normal JournalSync push right after restoring recognises every row as already-known rather
 * than creating duplicates.
 */
object JournalRestore {
    data class RestoreResult(val ok: Boolean, val message: String, val restoredAnything: Boolean = false)

    /** Whether it's even safe to offer this — see the class doc for why an empty local store is required. */
    suspend fun localStorageIsEmpty(context: Context): Boolean = withContext(Dispatchers.IO) {
        GoalStore(context).all().isEmpty() &&
            LifeGoalStore(context).all().isEmpty() &&
            DiaryStore(context).timeline(limit = 1).isEmpty() &&
            DetoxStore.get(context).sessions().isEmpty()
    }

    suspend fun run(context: Context): RestoreResult = withContext(Dispatchers.IO) {
        if (!localStorageIsEmpty(context)) {
            return@withContext RestoreResult(
                false,
                "You already have goals, diary or detox history on this phone, so nothing was restored — " +
                    "this only runs when that's empty, to avoid duplicating anything.",
            )
        }
        val api = Api(context)
        val reply = api.get("journal/restore/")
        val body = reply.json
        if (!reply.ok || body == null) {
            return@withContext RestoreResult(false, reply.error ?: "Couldn't reach the website. Try again later.")
        }

        val goalStore = GoalStore(context)
        val lifeStore = LifeGoalStore(context)
        val diaryStore = DiaryStore(context)
        val detoxStore = DetoxStore.get(context)
        var restored = 0

        for (g in each(body, "habit_goals")) {
            runCatching {
                goalStore.restore(
                    uuid = g.getString("client_id"), kind = GoalKind.of(g.getString("kind")), title = g.getString("title"),
                    target = g.optInt("target", 0), createdAt = g.getLong("created_at_ms"), archivedAt = g.optLongOrNull("archived_at_ms"),
                )
                restored++
            }
        }
        for (c in each(body, "checkins")) {
            runCatching {
                val goalId = goalStore.idForUuid(c.getString("goal_client_id")) ?: continue
                goalStore.restoreCheckin(goalId, LocalDate.parse(c.getString("date")), c.getBoolean("done"))
            }
        }
        // A plain for-loop, not .forEach { } — downloadPhoto below is a suspend call, which a forEach lambda
        // (a non-suspend function type, even though forEach itself is inline) can't contain.
        for (g in each(body, "life_goals")) {
            val uuid = g.getString("client_id")
            runCatching {
                lifeStore.restore(
                    uuid = uuid, horizon = Horizon.of(g.getString("horizon")), title = g.getString("title"),
                    details = g.optString("details", ""), why = g.optString("why", ""), obstacle = g.optString("obstacle", ""),
                    plan = g.optString("plan", ""), startedAt = g.getLong("started_at_ms"),
                    targetDate = LocalDate.parse(g.getString("target_date")),
                    achievedAt = g.optLongOrNull("achieved_at_ms"), archivedAt = g.optLongOrNull("archived_at_ms"),
                )
                restored++
            }
            if (g.optBoolean("has_photo", false)) {
                val localId = lifeStore.idForUuid(uuid)
                if (localId != null) {
                    val bytes = downloadPhoto(api, "journal/life-goals/$uuid/photo/download/")
                    if (bytes != null) { LifeGoalPhoto.saveBytes(context, localId, bytes); lifeStore.setPhoto(localId, true) }
                }
            }
        }
        for (m in each(body, "milestones")) {
            runCatching {
                val goalId = lifeStore.idForUuid(m.getString("life_goal_client_id")) ?: continue
                lifeStore.restoreMilestone(
                    uuid = m.getString("client_id"), goalId = goalId, title = m.getString("title"),
                    done = m.optBoolean("done", false), doneAt = m.optLongOrNull("done_at_ms"), sortOrder = m.optInt("sort_order", 0),
                )
            }
        }
        for (e in each(body, "diary_entries")) {
            val uuid = e.getString("client_id")
            runCatching {
                diaryStore.restore(
                    uuid = uuid, type = if (e.getString("type") == "milestone") EntryType.Milestone else EntryType.Reflection,
                    date = LocalDate.parse(e.getString("date")), title = e.getString("title"),
                    category = Category.of(e.optString("category", "other")), story = e.optString("story", ""),
                    effort = if (e.has("effort")) e.getInt("effort") else null,
                    satisfaction = if (e.has("satisfaction")) e.getInt("satisfaction") else null,
                    createdAt = e.getLong("created_at_ms"),
                )
                restored++
            }
            if (e.optBoolean("has_photo", false)) {
                val localId = diaryStore.timeline().firstOrNull { it.uuid == uuid }?.id
                if (localId != null) {
                    val bytes = downloadPhoto(api, "journal/diary/$uuid/photo/download/")
                    if (bytes != null) { DiaryPhoto.saveBytes(context, localId, bytes); diaryStore.setPhoto(localId, true) }
                }
            }
        }
        for (s in each(body, "detox_sessions")) {
            runCatching {
                detoxStore.restoreSession(DetoxSession(
                    id = s.getString("client_id").toLong(), startMs = s.getLong("started_at_ms"), plannedEndMs = s.getLong("planned_end_at_ms"),
                    endMs = s.optLongOrNull("ended_at_ms"), outcome = runCatching { Outcome.valueOf(s.optString("outcome", "ACTIVE")) }.getOrDefault(Outcome.ACTIVE),
                    reason = if (s.has("reason")) runCatching { BreakReason.valueOf(s.getString("reason")) }.getOrNull() else null,
                    blockedAttempts = s.optInt("blocked_attempts", 0), brokenBy = if (s.has("broken_by")) s.getString("broken_by") else null,
                    why = s.optString("why", ""),
                ))
                restored++
            }
        }

        val prefs = Prefs(context)
        prefs.syncPersonal = true   // they clearly had this on before; keep it on so new activity keeps syncing
        prefs.journalLastSyncAt = System.currentTimeMillis()

        RestoreResult(true, if (restored > 0) "Restored your goals, diary and detox history from the website." else "No backup found for this account.", restored > 0)
    }

    private fun each(body: JSONObject, key: String): List<JSONObject> {
        val arr = body.optJSONArray(key) ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

    private suspend fun downloadPhoto(api: Api, path: String): ByteArray? {
        val reply = api.get(path)
        val b64 = reply.json?.optString("data_base64")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Base64.getDecoder().decode(b64) }.getOrNull()
    }
}
