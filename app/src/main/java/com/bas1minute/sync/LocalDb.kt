package com.bas1minute.sync

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** What the phone measured for a day. Null means "nothing new", never "erase". */
data class PhoneDay(
    val steps: Long? = null,
    val stepsSource: String? = null,
    val screenMinutes: Int? = null,
    val socialMinutes: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepSource: String? = null,   // health_connect or phone_estimate
    val firstUse: String? = null,      // "06:52", first use this morning
    val lastUse: String? = null,       // "23:48", last use the night before
    val waterMl: Int? = null,          // from Health Connect
    val weightKg: Double? = null,      // from Health Connect
)

/** One saved day on the phone. `dirty` = phone data changed since the website confirmed it. */
data class DayRow(
    val date: LocalDate,
    val steps: Long?,
    val stepsSource: String?,
    val screenMinutes: Int?,
    val socialMinutes: Int?,
    val sleepMinutes: Int?,
    val sleepSource: String?,
    val firstUse: String?,
    val lastUse: String?,
    val waterMl: Int?,
    val waterSource: String?,
    val weightKg: Double?,
    val weightSource: String?,
    val dirty: Boolean,
    val updatedAt: Long,
)

/**
 * The phone's own copy of every day, in Android's built-in SQLite (no extra libraries).
 * Screens read from here, so the app works with no internet. Rows stay dirty until the website
 * confirms them (the sync outbox). Typed values (water, weight, sleep) are stored here too for
 * display; they reach the website through the action outbox.
 */
class LocalDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "bas1minute.db", null, 3) {

    companion object {
        private const val KEEP_DAYS = 400L
        private val PHONE = setOf("health_connect", "phone_sensor", "phone_estimate", "usage_stats")
        private const val COLS = "date, steps, steps_source, screen_minutes, social_minutes, sleep_minutes, sleep_source, " +
            "first_use, last_use, water_ml, water_source, weight_kg, weight_source, dirty, updated_at"

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
        createV2(db)
        createV3(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // One block per version. Never drop user data.
        if (oldVersion < 2) createV2(db)
        if (oldVersion < 3) createV3(db)
    }

    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS cache (key TEXT PRIMARY KEY, body TEXT NOT NULL, fetched_at INTEGER NOT NULL)")
    }

    /** v3: social media, sleep, phone habits, water, weight, and the per-app breakdown (never sent). */
    private fun createV3(db: SQLiteDatabase) {
        for (col in listOf(
            "social_minutes INTEGER", "sleep_minutes INTEGER", "sleep_source TEXT", "first_use TEXT", "last_use TEXT",
            "water_ml INTEGER", "water_source TEXT", "weight_kg REAL", "weight_source TEXT",
        )) db.execSQL("ALTER TABLE days ADD COLUMN $col")
        db.execSQL("CREATE TABLE IF NOT EXISTS app_usage (date TEXT NOT NULL, app TEXT NOT NULL, minutes INTEGER NOT NULL, PRIMARY KEY (date, app))")
    }

    // ---------- days ----------

    private fun row(c: Cursor) = DayRow(
        date = LocalDate.parse(c.getString(0)),
        steps = c.longOrNull(1), stepsSource = c.stringOrNull(2), screenMinutes = c.intOrNull(3),
        socialMinutes = c.intOrNull(4), sleepMinutes = c.intOrNull(5), sleepSource = c.stringOrNull(6),
        firstUse = c.stringOrNull(7), lastUse = c.stringOrNull(8), waterMl = c.intOrNull(9), waterSource = c.stringOrNull(10),
        weightKg = if (c.isNull(11)) null else c.getDouble(11), weightSource = c.stringOrNull(12),
        dirty = c.getInt(13) == 1, updatedAt = c.getLong(14),
    )

    private fun Cursor.longOrNull(i: Int) = if (isNull(i)) null else getLong(i)
    private fun Cursor.intOrNull(i: Int) = if (isNull(i)) null else getInt(i)
    private fun Cursor.stringOrNull(i: Int) = if (isNull(i)) null else getString(i)

    fun day(date: LocalDate): DayRow? =
        readableDatabase.rawQuery("SELECT $COLS FROM days WHERE date = ?", arrayOf(date.toString())).use { c -> if (c.moveToFirst()) row(c) else null }

    /** Save phone measurements. Missing values keep what's saved; phone values replace typed ones. */
    @Synchronized
    fun saveDay(date: LocalDate, p: PhoneDay) {
        val old = day(date)
        val v = ContentValues()
        fun <T> set(col: String, new: T?, oldValue: T?) {
            if (new != null && new != oldValue) when (new) {
                is Long -> v.put(col, new); is Int -> v.put(col, new); is Double -> v.put(col, new); is String -> v.put(col, new)
                else -> v.put(col, new.toString())
            }
        }
        set("steps", p.steps, old?.steps)
        if (p.steps != null) set("steps_source", p.stepsSource, old?.stepsSource)
        set("screen_minutes", p.screenMinutes, old?.screenMinutes)
        set("social_minutes", p.socialMinutes, old?.socialMinutes)
        // Health Connect sleep beats the phone's estimate; both beat typed sleep.
        val sleepWins = p.sleepMinutes != null && !(p.sleepSource == "phone_estimate" && old?.sleepSource == "health_connect")
        if (sleepWins) { set("sleep_minutes", p.sleepMinutes, old?.sleepMinutes); set("sleep_source", p.sleepSource, old?.sleepSource) }
        set("first_use", p.firstUse, old?.firstUse)
        set("last_use", p.lastUse, old?.lastUse)
        if (p.waterMl != null) { set("water_ml", p.waterMl, old?.waterMl); set("water_source", "health_connect", old?.waterSource) }
        if (p.weightKg != null) { set("weight_kg", p.weightKg, old?.weightKg); set("weight_source", "health_connect", old?.weightSource) }
        if (v.size() == 0) return

        v.put("updated_at", System.currentTimeMillis())
        v.put("dirty", 1)
        if (old == null) {
            v.put("date", date.toString())
            writableDatabase.insert("days", null, v)
        } else {
            writableDatabase.update("days", v, "date = ?", arrayOf(date.toString()))
        }
    }

    /** Values typed in the app. Shown right away; sent through the action outbox. Never replace phone values. */
    @Synchronized
    fun saveTyped(date: LocalDate, waterMl: Int? = null, weightKg: Double? = null, sleepMinutes: Int? = null) {
        val old = day(date)
        val v = ContentValues()
        if (waterMl != null && old?.waterSource !in PHONE) { v.put("water_ml", waterMl); v.put("water_source", "manual") }
        if (weightKg != null && old?.weightSource !in PHONE) { v.put("weight_kg", weightKg); v.put("weight_source", "manual") }
        if (sleepMinutes != null && old?.sleepSource !in PHONE) { v.put("sleep_minutes", sleepMinutes); v.put("sleep_source", "manual") }
        if (v.size() == 0) return
        if (old == null) {
            v.put("date", date.toString()); v.put("updated_at", System.currentTimeMillis()); v.put("dirty", 0)
            writableDatabase.insert("days", null, v)
        } else {
            writableDatabase.update("days", v, "date = ?", arrayOf(date.toString()))
        }
    }

    /**
     * Restore from the server (new phone, reinstall). Fills only what this phone doesn't have yet,
     * and never marks rows dirty, so restored days aren't sent straight back.
     */
    @Synchronized
    fun mergeFromServer(days: List<org.json.JSONObject>): Int {
        var filled = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (j in days) {
                val date = LocalDate.parse(j.optString("date"))
                val old = day(date)
                val src = j.optJSONObject("sources")
                val v = ContentValues()
                fun intOf(k: String): Int? = if (!j.has(k) || j.isNull(k)) null else j.optInt(k)
                fun strOf(k: String): String? = if (!j.has(k) || j.isNull(k)) null else j.optString(k).takeIf { it.isNotBlank() }
                fun srcOf(k: String): String = src?.optString(k)?.takeIf { it.isNotBlank() } ?: "manual"
                if (old?.steps == null) intOf("steps")?.let { v.put("steps", it.toLong()); v.put("steps_source", strOf("steps_source") ?: "manual") }
                if (old?.screenMinutes == null) intOf("screen_minutes")?.let { v.put("screen_minutes", it) }
                if (old?.socialMinutes == null) intOf("social_minutes")?.let { v.put("social_minutes", it) }
                if (old?.sleepMinutes == null) intOf("sleep_minutes")?.let { v.put("sleep_minutes", it); v.put("sleep_source", srcOf("sleep_minutes")) }
                if (old?.firstUse == null) strOf("first_use_at")?.let { v.put("first_use", it) }
                if (old?.lastUse == null) strOf("last_use_at")?.let { v.put("last_use", it) }
                if (old?.waterMl == null) intOf("water_ml")?.let { v.put("water_ml", it); v.put("water_source", srcOf("water_ml")) }
                if (old?.weightKg == null && j.has("weight_kg") && !j.isNull("weight_kg")) {
                    v.put("weight_kg", j.optDouble("weight_kg")); v.put("weight_source", srcOf("weight_kg"))
                }
                if (v.size() == 0) continue
                filled++
                if (old == null) {
                    v.put("date", date.toString()); v.put("updated_at", System.currentTimeMillis()); v.put("dirty", 0)
                    db.insert("days", null, v)
                } else {
                    db.update("days", v, "date = ?", arrayOf(date.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return filled
    }

    fun days(from: LocalDate, to: LocalDate): List<DayRow> =
        readableDatabase.rawQuery("SELECT $COLS FROM days WHERE date BETWEEN ? AND ? ORDER BY date", arrayOf(from.toString(), to.toString()))
            .use { c -> generateSequence<DayRow> { if (c.moveToNext()) row(c) else null }.toList() }

    fun pending(since: LocalDate, limit: Int): List<DayRow> =
        readableDatabase.rawQuery("SELECT $COLS FROM days WHERE dirty = 1 AND date >= ? ORDER BY date LIMIT $limit", arrayOf(since.toString()))
            .use { c -> generateSequence<DayRow> { if (c.moveToNext()) row(c) else null }.toList() }

    fun pendingCount(): Int = count("SELECT COUNT(*) FROM days WHERE dirty = 1")
    fun savedDayCount(): Int = count("SELECT COUNT(*) FROM days")

    /** Clears only days that haven't changed since they were read for sending. */
    @Synchronized
    fun markSynced(sent: List<DayRow>) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (r in sent) db.execSQL("UPDATE days SET dirty = 0, synced_at = ? WHERE date = ? AND updated_at = ?",
                arrayOf<Any?>(now, r.date.toString(), r.updatedAt))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun expire(before: LocalDate) {
        writableDatabase.execSQL("UPDATE days SET dirty = 0 WHERE dirty = 1 AND date < ?", arrayOf(before.toString()))
    }

    fun prune(today: LocalDate) {
        val cutoff = today.minusDays(KEEP_DAYS).toString()
        writableDatabase.execSQL("DELETE FROM days WHERE date < ?", arrayOf(cutoff))
        writableDatabase.execSQL("DELETE FROM app_usage WHERE date < ?", arrayOf(cutoff))
    }

    // ---------- per-app social breakdown (this phone only) ----------

    @Synchronized
    fun saveApps(date: LocalDate, apps: Map<String, Int>) {
        if (apps.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("app_usage", "date = ?", arrayOf(date.toString()))
            for ((app, minutes) in apps) db.insert("app_usage", null, ContentValues().apply {
                put("date", date.toString()); put("app", app); put("minutes", minutes)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Total minutes per social app over a date range, biggest first. */
    fun appTotals(from: LocalDate, to: LocalDate): List<Pair<String, Int>> =
        readableDatabase.rawQuery("SELECT app, SUM(minutes) m FROM app_usage WHERE date BETWEEN ? AND ? GROUP BY app ORDER BY m DESC",
            arrayOf(from.toString(), to.toString())).use { c -> generateSequence<Pair<String, Int>> { if (c.moveToNext()) c.getString(0) to c.getInt(1) else null }.toList() }

    // ---------- outbox: API writes made offline, replayed in order ----------

    data class QueuedCall(val id: Long, val path: String, val body: String)

    fun enqueue(path: String, body: String) {
        writableDatabase.insert("outbox", null, ContentValues().apply {
            put("path", path); put("body", body); put("created_at", System.currentTimeMillis())
        })
    }

    fun queued(): List<QueuedCall> =
        readableDatabase.rawQuery("SELECT id, path, body FROM outbox ORDER BY id", null)
            .use { c -> generateSequence<QueuedCall> { if (c.moveToNext()) QueuedCall(c.getLong(0), c.getString(1), c.getString(2)) else null }.toList() }

    fun queuedCount(): Int = count("SELECT COUNT(*) FROM outbox")

    fun dequeue(id: Long) { writableDatabase.delete("outbox", "id = ?", arrayOf(id.toString())) }

    // ---------- cache: last good response per screen ----------

    fun putCache(key: String, body: String) {
        writableDatabase.insertWithOnConflict("cache", null, ContentValues().apply {
            put("key", key); put("body", body); put("fetched_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getCache(key: String): String? =
        readableDatabase.rawQuery("SELECT body FROM cache WHERE key = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** On log out: forget account data. Phone-measured days stay (they belong to this phone). */
    fun clearAccountData() {
        writableDatabase.delete("outbox", null, null)
        writableDatabase.delete("cache", null, null)
        writableDatabase.execSQL("UPDATE days SET dirty = 1")
    }

    private fun count(sql: String): Int = readableDatabase.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
}
