package com.bas1minute.sync.goal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.detox.DetoxStore
import com.bas1minute.sync.detox.Outcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Goals, their check-offs, and the day-signals they're judged against. Everything stays on this phone. */
class GoalStore(context: Context) {
    private val ctx = context.applicationContext
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val helper = LocalDb.get(ctx)

    fun create(kind: GoalKind, title: String, target: Int, nowMs: Long = System.currentTimeMillis()): Long =
        db.insert("goals", null, ContentValues().apply {
            put("kind", kind.id); put("title", title.trim().take(GoalLogic.MAX_TITLE_LEN))
            put("target", target); put("created_at", nowMs)
        })

    fun archive(id: Long, nowMs: Long = System.currentTimeMillis()) {
        db.update("goals", ContentValues().apply { put("archived_at", nowMs) }, "id = ?", arrayOf(id.toString()))
    }

    fun restore(id: Long) {
        db.update("goals", ContentValues().apply { putNull("archived_at") }, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        val d = db
        d.beginTransaction()
        try {
            d.delete("goal_checkins", "goal_id = ?", arrayOf(id.toString()))
            d.delete("goals", "id = ?", arrayOf(id.toString()))
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
    }

    fun active(): List<Goal> =
        db.rawQuery("SELECT id, kind, title, target, created_at, archived_at FROM goals WHERE archived_at IS NULL ORDER BY created_at", null)
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    fun all(): List<Goal> =
        db.rawQuery("SELECT id, kind, title, target, created_at, archived_at FROM goals ORDER BY created_at", null)
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    fun setCheckin(goalId: Long, date: LocalDate, done: Boolean) {
        db.insertWithOnConflict("goal_checkins", null, ContentValues().apply {
            put("goal_id", goalId); put("date", date.toString()); put("done", if (done) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun checkin(goalId: Long, date: LocalDate): Checkin? =
        db.rawQuery("SELECT done FROM goal_checkins WHERE goal_id = ? AND date = ?", arrayOf(goalId.toString(), date.toString()))
            .use { c -> if (c.moveToFirst()) Checkin(goalId, date, c.getInt(0) == 1) else null }

    /** Every check-in for [goalId] from [from] to [to] inclusive, keyed by date. */
    fun checkins(goalId: Long, from: LocalDate, to: LocalDate): Map<LocalDate, Checkin> =
        db.rawQuery("SELECT date, done FROM goal_checkins WHERE goal_id = ? AND date BETWEEN ? AND ?",
            arrayOf(goalId.toString(), from.toString(), to.toString()))
            .use { c -> generateSequence { if (c.moveToNext()) LocalDate.parse(c.getString(0)) to Checkin(goalId, LocalDate.parse(c.getString(0)), c.getInt(1) == 1) else null }.toMap() }

    /** Builds the measurable signal for one day from the days table and detox history. */
    fun signal(date: LocalDate): DaySignal {
        val row = helper.day(date)
        val dayStartMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEndMs = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val bestKept = DetoxStore.get(ctx).sessions()
            .filter { it.outcome == Outcome.KEPT && it.startMs >= dayStartMs && it.startMs < dayEndMs }
            .maxOfOrNull { it.plannedMinutes } ?: 0
        return DaySignal(date, steps = row?.steps, sleepMinutes = row?.sleepMinutes, socialMinutes = row?.socialMinutes, bestKeptDetoxMinutes = bestKept)
    }

    private fun read(c: android.database.Cursor) = Goal(
        id = c.getLong(0), kind = GoalKind.of(c.getString(1)), title = c.getString(2), target = c.getInt(3),
        createdAt = c.getLong(4), archivedAt = if (c.isNull(5)) null else c.getLong(5),
    )
}
