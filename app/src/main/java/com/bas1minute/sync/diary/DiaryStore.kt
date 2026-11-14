package com.bas1minute.sync.diary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.bas1minute.sync.LocalDb
import java.time.LocalDate

/** Diary entries — milestones and daily reflections — kept only on this phone. */
class DiaryStore(context: Context) {
    private val ctx = context.applicationContext
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val helper = LocalDb.get(ctx)

    companion object {
        private const val COLS = "id, uuid, type, date, title, category, story, effort, satisfaction, has_photo, created_at"
    }

    fun addMilestone(date: LocalDate, title: String, category: Category, story: String, nowMs: Long = System.currentTimeMillis()): Long =
        db.insert("diary_entries", null, ContentValues().apply {
            put("uuid", java.util.UUID.randomUUID().toString())
            put("type", EntryType.Milestone.name); put("date", date.toString())
            put("title", DiaryLogic.clampText(title, DiaryLogic.MAX_TITLE)); put("category", category.id)
            put("story", DiaryLogic.clampText(story, DiaryLogic.MAX_STORY)); put("created_at", nowMs)
        })

    fun updateMilestone(id: Long, date: LocalDate, title: String, category: Category, story: String) {
        db.update("diary_entries", ContentValues().apply {
            put("date", date.toString()); put("title", DiaryLogic.clampText(title, DiaryLogic.MAX_TITLE))
            put("category", category.id); put("story", DiaryLogic.clampText(story, DiaryLogic.MAX_STORY))
        }, "id = ? AND type = ?", arrayOf(id.toString(), EntryType.Milestone.name))
    }

    /** Today's reflection: one row per day, replacing anything already saved for today. */
    fun setTodayReflection(effort: Int, satisfaction: Int, note: String, today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()) {
        val existing = reflectionOn(today)
        if (existing != null) {
            db.update("diary_entries", ContentValues().apply {
                put("effort", DiaryLogic.clampRating(effort)); put("satisfaction", DiaryLogic.clampRating(satisfaction))
                put("story", DiaryLogic.clampText(note, DiaryLogic.MAX_STORY))
            }, "id = ?", arrayOf(existing.id.toString()))
        } else {
            db.insert("diary_entries", null, ContentValues().apply {
                put("uuid", java.util.UUID.randomUUID().toString())
                put("type", EntryType.Reflection.name); put("date", today.toString()); put("title", "Reflection")
                put("category", Category.Other.id); put("story", DiaryLogic.clampText(note, DiaryLogic.MAX_STORY))
                put("effort", DiaryLogic.clampRating(effort)); put("satisfaction", DiaryLogic.clampRating(satisfaction))
                put("created_at", nowMs)
            })
        }
    }

    fun reflectionOn(date: LocalDate): DiaryEntry? =
        db.rawQuery("SELECT $COLS FROM diary_entries WHERE type = ? AND date = ? LIMIT 1", arrayOf(EntryType.Reflection.name, date.toString()))
            .use { c -> if (c.moveToFirst()) read(c) else null }

    fun delete(id: Long) {
        db.delete("diary_entries", "id = ?", arrayOf(id.toString()))
        DiaryPhoto.remove(ctx, id)
    }

    fun setPhoto(id: Long, has: Boolean) {
        db.update("diary_entries", ContentValues().apply { put("has_photo", if (has) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    /** Every entry, newest-dated first, for the timeline. */
    fun timeline(limit: Int = 500): List<DiaryEntry> =
        db.rawQuery("SELECT $COLS FROM diary_entries ORDER BY date DESC, created_at DESC LIMIT $limit", null)
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    fun milestones(limit: Int = 500): List<DiaryEntry> =
        db.rawQuery("SELECT $COLS FROM diary_entries WHERE type = ? ORDER BY date DESC LIMIT $limit", arrayOf(EntryType.Milestone.name))
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    fun reflections(from: LocalDate, to: LocalDate): List<DiaryEntry> =
        db.rawQuery("SELECT $COLS FROM diary_entries WHERE type = ? AND date BETWEEN ? AND ? ORDER BY date",
            arrayOf(EntryType.Reflection.name, from.toString(), to.toString()))
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    fun reflectionDates(): Set<LocalDate> =
        db.rawQuery("SELECT date FROM diary_entries WHERE type = ?", arrayOf(EntryType.Reflection.name))
            .use { c -> generateSequence { if (c.moveToNext()) LocalDate.parse(c.getString(0)) else null }.toSet() }

    private fun read(c: Cursor) = DiaryEntry(
        id = c.getLong(0), uuid = c.getString(1) ?: c.getLong(0).toString(), type = EntryType.valueOf(c.getString(2)),
        date = LocalDate.parse(c.getString(3)), title = c.getString(4), category = Category.of(c.getString(5)), story = c.getString(6),
        effort = if (c.isNull(7)) null else c.getInt(7), satisfaction = if (c.isNull(8)) null else c.getInt(8),
        photoPath = if (c.getInt(9) == 1) DiaryPhoto.path(ctx, c.getLong(0)) else null, createdAt = c.getLong(10),
    )
}
