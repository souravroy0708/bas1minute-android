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
        private const val COLS = "id, horizon, title, details, why, obstacle, plan, has_photo, started_at, target_date, achieved_at, archived_at"
    }

    fun create(
        horizon: Horizon, title: String, details: String, why: String, obstacle: String, plan: String,
        targetDate: LocalDate, nowMs: Long = System.currentTimeMillis(),
    ): Long = db.insert("life_goals", null, ContentValues().apply {
        put("horizon", horizon.id)
        put("title", LifeGoalLogic.clampText(title, LifeGoalLogic.MAX_TITLE))
        put("details", LifeGoalLogic.clampText(details, LifeGoalLogic.MAX_DETAILS))
        put("why", LifeGoalLogic.clampText(why, LifeGoalLogic.MAX_WHY))
        put("obstacle", LifeGoalLogic.clampText(obstacle, LifeGoalLogic.MAX_OBSTACLE))
        put("plan", LifeGoalLogic.clampText(plan, LifeGoalLogic.MAX_PLAN))
        put("started_at", nowMs); put("target_date", targetDate.toString())
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

    // ---- milestones ----

    fun addMilestone(goalId: Long, title: String, order: Int): Long =
        db.insert("life_goal_milestones", null, ContentValues().apply {
            put("goal_id", goalId); put("title", LifeGoalLogic.clampText(title, LifeGoalLogic.MAX_MILESTONE_TITLE)); put("sort_order", order)
        })

    fun setMilestoneDone(id: Long, done: Boolean, nowMs: Long = System.currentTimeMillis()) {
        db.update("life_goal_milestones", ContentValues().apply {
            put("done", if (done) 1 else 0); if (done) put("done_at", nowMs) else putNull("done_at")
        }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteMilestone(id: Long) { db.delete("life_goal_milestones", "id = ?", arrayOf(id.toString())) }

    fun milestones(goalId: Long): List<Milestone> =
        db.rawQuery("SELECT id, goal_id, title, done, done_at, sort_order FROM life_goal_milestones WHERE goal_id = ? ORDER BY sort_order",
            arrayOf(goalId.toString())).use { c ->
            generateSequence {
                if (c.moveToNext()) Milestone(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3) == 1,
                    if (c.isNull(4)) null else c.getLong(4), c.getInt(5)) else null
            }.toList()
        }

    private fun read(c: Cursor) = LifeGoal(
        id = c.getLong(0), horizon = Horizon.of(c.getString(1)), title = c.getString(2), details = c.getString(3),
        why = c.getString(4), obstacle = c.getString(5), plan = c.getString(6),
        photoPath = if (c.getInt(7) == 1) LifeGoalPhoto.path(ctx, c.getLong(0)) else null,
        startedAt = c.getLong(8), targetDate = LocalDate.parse(c.getString(9)),
        achievedAt = if (c.isNull(10)) null else c.getLong(10), archivedAt = if (c.isNull(11)) null else c.getLong(11),
    )
}
