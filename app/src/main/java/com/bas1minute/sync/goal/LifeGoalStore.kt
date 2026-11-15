package com.bas1minute.sync.goal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.bas1minute.sync.LocalDb
import java.time.LocalDate

/** Life goals, their milestones, and the photo that goes with each. Everything stays on this phone. */
class LifeGoalStore(context: Context) {
    private val ctx = context.applicationContext
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val helper = LocalDb.get(ctx)

    companion object {
        private const val COLS = "id, uuid, horizon, title, details, why, obstacle, plan, has_photo, started_at, target_date, achieved_at, archived_at"
    }

    fun create(
        horizon: Horizon, title: String, details: String, why: String, obstacle: String, plan: String,
        targetDate: LocalDate, nowMs: Long = System.currentTimeMillis(),
    ): Long = db.insert("life_goals", null, ContentValues().apply {
        put("uuid", java.util.UUID.randomUUID().toString())
        put("horizon", horizon.id)
        put("title", LifeGoalLogic.clampText(title, LifeGoalLogic.MAX_TITLE))
        put("details", LifeGoalLogic.clampText(details, LifeGoalLogic.MAX_DETAILS))
        put("why", LifeGoalLogic.clampText(why, LifeGoalLogic.MAX_WHY))
        put("obstacle", LifeGoalLogic.clampText(obstacle, LifeGoalLogic.MAX_OBSTACLE))
        put("plan", LifeGoalLogic.clampText(plan, LifeGoalLogic.MAX_PLAN))
        put("started_at", nowMs); put("target_date", targetDate.toString())
    })

    /** Re-creates a life goal downloaded from the website backup, keeping its ORIGINAL uuid. See JournalRestore.kt. */
    fun restore(
        uuid: String, horizon: Horizon, title: String, details: String, why: String, obstacle: String, plan: String,
        startedAt: Long, targetDate: LocalDate, achievedAt: Long?, archivedAt: Long?,
    ): Long = db.insert("life_goals", null, ContentValues().apply {
        put("uuid", uuid); put("horizon", horizon.id); put("title", title.take(LifeGoalLogic.MAX_TITLE))
        put("details", details.take(LifeGoalLogic.MAX_DETAILS)); put("why", why.take(LifeGoalLogic.MAX_WHY))
        put("obstacle", obstacle.take(LifeGoalLogic.MAX_OBSTACLE)); put("plan", plan.take(LifeGoalLogic.MAX_PLAN))
        put("started_at", startedAt); put("target_date", targetDate.toString())
        if (achievedAt != null) put("achieved_at", achievedAt) else putNull("achieved_at")
        if (archivedAt != null) put("archived_at", archivedAt) else putNull("archived_at")
    })

    /** The local life goal id for a given uuid, needed to attach restored milestones to the right goal. */
    fun idForUuid(uuid: String): Long? =
        db.rawQuery("SELECT id FROM life_goals WHERE uuid = ?", arrayOf(uuid)).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    /** Re-creates a milestone downloaded from the backup, keeping its ORIGINAL uuid, against the already-restored
     * parent goal's LOCAL id (its own id changed on restore even though its uuid didn't). */
    fun restoreMilestone(uuid: String, goalId: Long, title: String, done: Boolean, doneAt: Long?, sortOrder: Int): Long =
        db.insert("life_goal_milestones", null, ContentValues().apply {
            put("uuid", uuid); put("goal_id", goalId); put("title", title.take(LifeGoalLogic.MAX_MILESTONE_TITLE))
            put("done", if (done) 1 else 0); put("sort_order", sortOrder)
            if (doneAt != null) put("done_at", doneAt) else putNull("done_at")
        })

    fun setPhoto(id: Long, has: Boolean) {
        db.update("life_goals", ContentValues().apply { put("has_photo", if (has) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    fun update(id: Long, title: String, details: String, why: String, obstacle: String, plan: String, targetDate: LocalDate) {
        db.update("life_goals", ContentValues().apply {
            put("title", LifeGoalLogic.clampText(title, LifeGoalLogic.MAX_TITLE))
            put("details", LifeGoalLogic.clampText(details, LifeGoalLogic.MAX_DETAILS))
            put("why", LifeGoalLogic.clampText(why, LifeGoalLogic.MAX_WHY))
            put("obstacle", LifeGoalLogic.clampText(obstacle, LifeGoalLogic.MAX_OBSTACLE))
            put("plan", LifeGoalLogic.clampText(plan, LifeGoalLogic.MAX_PLAN))
            put("target_date", targetDate.toString())
        }, "id = ?", arrayOf(id.toString()))
    }

    fun markAchieved(id: Long, nowMs: Long = System.currentTimeMillis()) {
        db.update("life_goals", ContentValues().apply { put("achieved_at", nowMs) }, "id = ?", arrayOf(id.toString()))
    }

    fun archive(id: Long, nowMs: Long = System.currentTimeMillis()) {
        db.update("life_goals", ContentValues().apply { put("archived_at", nowMs) }, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        val d = db
        d.beginTransaction()
        try {
            d.delete("life_goal_milestones", "goal_id = ?", arrayOf(id.toString()))
            d.delete("life_goals", "id = ?", arrayOf(id.toString()))
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
        LifeGoalPhoto.remove(ctx, id)
    }

    fun row(id: Long): LifeGoal? =
        db.rawQuery("SELECT $COLS FROM life_goals WHERE id = ?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) read(c) else null }

    /** Active goals for one horizon, newest first. */
    fun active(horizon: Horizon): List<LifeGoal> =
        db.rawQuery("SELECT $COLS FROM life_goals WHERE horizon = ? AND achieved_at IS NULL AND archived_at IS NULL ORDER BY started_at DESC",
            arrayOf(horizon.id)).use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    fun activeCount(horizon: Horizon): Int =
        db.rawQuery("SELECT COUNT(*) FROM life_goals WHERE horizon = ? AND achieved_at IS NULL AND archived_at IS NULL", arrayOf(horizon.id))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Goals reached, newest first — for a small "achieved" wall. */
    fun achieved(): List<LifeGoal> =
        db.rawQuery("SELECT $COLS FROM life_goals WHERE achieved_at IS NOT NULL ORDER BY achieved_at DESC", null)
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    /** Every life goal regardless of horizon or status — for syncing to the website. */
    fun all(): List<LifeGoal> =
        db.rawQuery("SELECT $COLS FROM life_goals ORDER BY started_at DESC", null)
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    // ---- milestones ----

    fun addMilestone(goalId: Long, title: String, order: Int): Long =
        db.insert("life_goal_milestones", null, ContentValues().apply {
            put("uuid", java.util.UUID.randomUUID().toString()); put("goal_id", goalId)
            put("title", LifeGoalLogic.clampText(title, LifeGoalLogic.MAX_MILESTONE_TITLE)); put("sort_order", order)
        })

    fun setMilestoneDone(id: Long, done: Boolean, nowMs: Long = System.currentTimeMillis()) {
        db.update("life_goal_milestones", ContentValues().apply {
            put("done", if (done) 1 else 0); if (done) put("done_at", nowMs) else putNull("done_at")
        }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteMilestone(id: Long) { db.delete("life_goal_milestones", "id = ?", arrayOf(id.toString())) }

    fun milestones(goalId: Long): List<Milestone> =
        db.rawQuery("SELECT id, uuid, goal_id, title, done, done_at, sort_order FROM life_goal_milestones WHERE goal_id = ? ORDER BY sort_order",
            arrayOf(goalId.toString())).use { c ->
            generateSequence {
                if (c.moveToNext()) Milestone(c.getLong(0), c.getString(1) ?: c.getLong(0).toString(), c.getLong(2), c.getString(3), c.getInt(4) == 1,
                    if (c.isNull(5)) null else c.getLong(5), c.getInt(6)) else null
            }.toList()
        }

    private fun read(c: Cursor) = LifeGoal(
        id = c.getLong(0), uuid = c.getString(1) ?: c.getLong(0).toString(), horizon = Horizon.of(c.getString(2)),
        title = c.getString(3), details = c.getString(4),
        why = c.getString(5), obstacle = c.getString(6), plan = c.getString(7),
        photoPath = if (c.getInt(8) == 1) LifeGoalPhoto.path(ctx, c.getLong(0)) else null,
        startedAt = c.getLong(9), targetDate = LocalDate.parse(c.getString(10)),
        achievedAt = if (c.isNull(11)) null else c.getLong(11), archivedAt = if (c.isNull(12)) null else c.getLong(12),
    )
}
