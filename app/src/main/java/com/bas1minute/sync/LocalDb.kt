package com.bas1minute.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** One saved day on the phone. `dirty` = changed since the website last confirmed it. */
data class DayRow(
    val date: LocalDate,
    val steps: Long?,
    val stepsSource: String?,
    val screenMinutes: Int?,
    val dirty: Boolean,
)

/**
 * The phone's own copy of every day, using Android's built-in SQLite (no extra libraries).
 * The app reads and shows data from here, so it works with no internet and when the website is down.
 * Rows stay "dirty" until a sync succeeds, which makes this table the sync outbox too.
 */
class LocalDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "bas1minute.db", null, 1) {

    companion object {
        private const val KEEP_DAYS = 400L

        @Volatile private var instance: LocalDb? = null

        fun get(context: Context): LocalDb =
            instance ?: synchronized(this) { instance ?: LocalDb(context).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE days (
                date TEXT PRIMARY KEY,
                steps INTEGER,
                steps_source TEXT,
                screen_minutes INTEGER,
                updated_at INTEGER NOT NULL,
                synced_at INTEGER,
                dirty INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX days_dirty ON days(dirty)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes go here, one `if (oldVersion < N)` block per version. Never drop user data.
    }

    /**
     * Save what the phone reports for a day. A missing value never erases a saved one
     * (for example after the Recording API has forgotten an old day). Marks the day dirty only if it changed.
     */
    @Synchronized
    fun saveDay(date: LocalDate, steps: Long?, stepsSource: String?, screenMinutes: Int?) {
        if (steps == null && screenMinutes == null) return
        val db = writableDatabase
        val key = date.toString()
        val old = db.rawQuery("SELECT steps, steps_source, screen_minutes FROM days WHERE date = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) Triple(
                if (c.isNull(0)) null else c.getLong(0),
                if (c.isNull(1)) null else c.getString(1),
                if (c.isNull(2)) null else c.getInt(2),
            ) else null
        }
        val newSteps = steps ?: old?.first
        val newSource = if (steps != null) stepsSource else old?.second
        val newScreen = screenMinutes ?: old?.third
        if (old != null && old.first == newSteps && old.second == newSource && old.third == newScreen) return

        val values = ContentValues().apply {
            put("date", key)
            if (newSteps != null) put("steps", newSteps) else putNull("steps")
            if (newSource != null) put("steps_source", newSource) else putNull("steps_source")
            if (newScreen != null) put("screen_minutes", newScreen) else putNull("screen_minutes")
            put("updated_at", System.currentTimeMillis())
            put("dirty", 1)
        }
        db.insertWithOnConflict("days", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun days(from: LocalDate, to: LocalDate): List<DayRow> =
        query("SELECT date, steps, steps_source, screen_minutes, dirty FROM days WHERE date BETWEEN ? AND ? ORDER BY date",
            arrayOf(from.toString(), to.toString()))

    /** Days waiting for the website, oldest first, within the window the website accepts. */
    fun pending(since: LocalDate, limit: Int): List<DayRow> =
        query("SELECT date, steps, steps_source, screen_minutes, dirty FROM days WHERE dirty = 1 AND date >= ? ORDER BY date LIMIT $limit",
            arrayOf(since.toString()))

    fun pendingCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM days WHERE dirty = 1", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun savedDayCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM days", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Only clears days that haven't changed since they were sent. */
    @Synchronized
    fun markSynced(sent: List<DayRow>) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (row in sent) {
                db.execSQL(
                    "UPDATE days SET dirty = 0, synced_at = ? WHERE date = ? AND steps IS ? AND screen_minutes IS ?",
                    arrayOf<Any?>(now, row.date.toString(), row.steps, row.screenMinutes),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Days older than the website accepts can never sync; stop counting them as waiting. */
    fun expire(before: LocalDate) {
        writableDatabase.execSQL("UPDATE days SET dirty = 0 WHERE dirty = 1 AND date < ?", arrayOf(before.toString()))
    }

    fun prune(today: LocalDate) {
        writableDatabase.execSQL("DELETE FROM days WHERE date < ?", arrayOf(today.minusDays(KEEP_DAYS).toString()))
    }

    private fun query(sql: String, args: Array<String>): List<DayRow> =
        readableDatabase.rawQuery(sql, args).use { c ->
            val out = ArrayList<DayRow>()
            while (c.moveToNext()) {
                out += DayRow(
                    date = LocalDate.parse(c.getString(0)),
                    steps = if (c.isNull(1)) null else c.getLong(1),
                    stepsSource = if (c.isNull(2)) null else c.getString(2),
                    screenMinutes = if (c.isNull(3)) null else c.getInt(3),
                    dirty = c.getInt(4) == 1,
                )
            }
            out
        }
}
