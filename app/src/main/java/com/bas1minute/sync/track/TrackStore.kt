package com.bas1minute.sync.track

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.bas1minute.sync.LocalDb

/** A saved walk/run (summary row). ended == null means it was still recording when Android closed the app. */
data class ActivityRow(
    val id: Long,
    val type: ActivityType,
    val title: String,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceM: Double,
    val movingMs: Long,
    val elapsedMs: Long,
    val elevGainM: Double,
    val maxSpeedMs: Double,
    val kcal: Int?,
    val preview: List<LatLng>,
    /** gps (route and map), steps (distance from steps, no location) or timer (time only). */
    val source: TrackSource = TrackSource.Gps,
    val steps: Long? = null,
    /** Kilometre splits for step activities (GPS ones are rebuilt from points). */
    val splitsMs: List<Long> = emptyList(),
    /** The distance was typed or corrected by the person. */
    val distanceEdited: Boolean = false,
) {
    val avgSpeedMs: Double get() = if (movingMs <= 0) 0.0 else distanceM / (movingMs / 1000.0)
    val avgCadence: Int? get() = steps?.takeIf { it > 0 && movingMs >= 30_000 }?.let { (it / (movingMs / 60_000.0)).toInt() }
}

/** Activities live in the app's own SQLite (bas1minute.db, v5 + v7). They never leave the phone unless shared. */
class TrackStore(context: Context) {
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val helper = LocalDb.get(context)

    companion object {
        private const val COLS = "id, type, title, started_at, ended_at, distance_m, moving_ms, elapsed_ms, elev_gain_m, max_speed, kcal, preview, " +
            "source, steps, splits, distance_edited"
        private const val DEFAULT_WEIGHT_KG = 65.0
    }

    fun create(type: ActivityType, startedAt: Long, source: TrackSource = TrackSource.Gps): Long =
        db.insert("activities", null, ContentValues().apply {
            put("type", type.id); put("started_at", startedAt); put("source", source.id)
        })

    fun addPoints(activityId: Long, firstSeq: Int, points: List<TrackPoint>) {
        if (points.isEmpty()) return
        val d = db
        d.beginTransaction()
        try {
            points.forEachIndexed { i, p ->
                d.insertWithOnConflict("activity_points", null, ContentValues().apply {
                    put("activity_id", activityId); put("seq", firstSeq + i)
                    put("lat", p.lat); put("lng", p.lng); p.alt?.let { put("alt", it) }
                    put("t", p.timeMs); put("moving_ms", p.movingMs); put("dist_m", p.distM); put("seg", p.segment)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    /** Running totals, saved every few seconds so nothing is lost if Android closes the app. */
    fun updateLive(id: Long, s: TrackStats) {
        db.update("activities", ContentValues().apply {
            put("distance_m", s.distanceM); put("moving_ms", s.movingMs); put("elapsed_ms", s.elapsedMs)
            put("elev_gain_m", s.elevGainM); put("max_speed", s.maxSpeedMs)
            putStepTotals(s)
        }, "id = ?", arrayOf(id.toString()))
    }

    /** Steps and splits only exist on activities without a route (GPS ones keep them in their points). */
    private fun ContentValues.putStepTotals(s: TrackStats) {
        if (s.steps > 0) put("steps", s.steps)
        SplitsText.write(s.splitsMs)?.let { put("splits", it) }
    }

    /** Mark finished: title, calories estimate and a small route preview for the list. */
    fun finish(id: Long, type: ActivityType, s: TrackStats?, endedAt: Long) {
        val pts = points(id)
        val saved = row(id)
        val distance = s?.distanceM ?: pts.lastOrNull()?.distM ?: saved?.distanceM ?: 0.0
        val started = saved?.startedAt ?: endedAt
        val hour = java.time.Instant.ofEpochMilli(started).atZone(java.time.ZoneId.systemDefault()).hour
        val preview = Polyline5.encode(Simplify.toMax(pts.map { LatLng(it.lat, it.lng) }, 80))
        db.update("activities", ContentValues().apply {
            put("ended_at", endedAt)
            put("title", TrackSummary.title(type, hour))
            put("kcal", TrackSummary.kcal(type, distance, latestWeightKg()))
            put("preview", preview)
            if (s != null) {
                put("distance_m", s.distanceM); put("moving_ms", s.movingMs); put("elapsed_ms", s.elapsedMs)
                put("elev_gain_m", s.elevGainM); put("max_speed", s.maxSpeedMs)
                putStepTotals(s)
            } else if (pts.isEmpty()) {
                // Recovered step or timer activity: the live totals saved every few seconds are already in the row
            } else {
                // Recovered after the app was closed: rebuild totals from the saved points
                put("distance_m", distance)
                put("moving_ms", pts.lastOrNull()?.movingMs ?: 0L)
                put("elapsed_ms", (pts.lastOrNull()?.timeMs ?: started) - started)
            }
        }, "id = ?", arrayOf(id.toString()))
    }

    /**
     * The person typed the real distance (a ride without GPS, or a corrected walk). Splits are dropped because
     * they no longer match; calories follow the new distance.
     */
    fun editDistance(id: Long, distanceM: Double) {
        val r = row(id) ?: return
        db.update("activities", ContentValues().apply {
            put("distance_m", distanceM); put("distance_edited", 1); putNull("splits")
            put("kcal", TrackSummary.kcal(r.type, distanceM, latestWeightKg()))
        }, "id = ?", arrayOf(id.toString()))
    }

    fun rename(id: Long, title: String) {
        db.update("activities", ContentValues().apply { put("title", title.trim().take(60)) }, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        val d = db
        d.beginTransaction()
        try {
            d.delete("activity_points", "activity_id = ?", arrayOf(id.toString()))
            d.delete("activities", "id = ?", arrayOf(id.toString()))
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    fun row(id: Long): ActivityRow? =
        db.rawQuery("SELECT $COLS FROM activities WHERE id = ?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) read(c) else null }

    /** Finished activities, newest first. */
    fun list(limit: Int = 100): List<ActivityRow> =
        db.rawQuery("SELECT $COLS FROM activities WHERE ended_at IS NOT NULL ORDER BY started_at DESC LIMIT $limit", null)
            .use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() }

    /** An activity that was recording when Android closed the app (null if none). */
    fun unfinished(): ActivityRow? =
        db.rawQuery("SELECT $COLS FROM activities WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1", null)
            .use { c -> if (c.moveToFirst()) read(c) else null }

    fun points(id: Long): List<TrackPoint> =
        db.rawQuery("SELECT lat, lng, alt, t, moving_ms, dist_m, seg FROM activity_points WHERE activity_id = ? ORDER BY seq", arrayOf(id.toString()))
            .use { c ->
                generateSequence {
                    if (c.moveToNext()) TrackPoint(c.getDouble(0), c.getDouble(1), if (c.isNull(2)) null else c.getDouble(2),
                        c.getLong(3), c.getLong(4), c.getDouble(5), c.getInt(6)) else null
                }.toList()
            }

    /** Distance and count since a moment (for "This week"). */
    fun totalsSince(fromMs: Long): Pair<Int, Double> =
        db.rawQuery("SELECT COUNT(*), COALESCE(SUM(distance_m), 0) FROM activities WHERE ended_at IS NOT NULL AND started_at >= ?", arrayOf(fromMs.toString()))
            .use { c -> if (c.moveToFirst()) c.getInt(0) to c.getDouble(1) else 0 to 0.0 }

    /** Longest single activity per type, for "Personal best" badges. */
    fun longest(type: ActivityType): Double =
        db.rawQuery("SELECT COALESCE(MAX(distance_m), 0) FROM activities WHERE ended_at IS NOT NULL AND type = ?", arrayOf(type.id))
            .use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }

    /** Weight for the calorie estimate: the latest one saved (Health Connect or typed), else a typical adult. */
    private fun latestWeightKg(): Double =
        db.rawQuery("SELECT weight_kg FROM days WHERE weight_kg IS NOT NULL ORDER BY date DESC LIMIT 1", null)
            .use { c -> if (c.moveToFirst()) c.getDouble(0) else DEFAULT_WEIGHT_KG }

    private fun read(c: android.database.Cursor) = ActivityRow(
        id = c.getLong(0), type = ActivityType.of(c.getString(1)),
        title = c.getString(2) ?: ActivityType.of(c.getString(1)).label,
        startedAt = c.getLong(3), endedAt = if (c.isNull(4)) null else c.getLong(4),
        distanceM = c.getDouble(5), movingMs = c.getLong(6), elapsedMs = c.getLong(7),
        elevGainM = c.getDouble(8), maxSpeedMs = c.getDouble(9), kcal = if (c.isNull(10)) null else c.getInt(10),
        preview = c.getString(11)?.let { Polyline5.decode(it) } ?: emptyList(),
        source = TrackSource.of(c.getString(12)),
        steps = if (c.isNull(13)) null else c.getLong(13),
        splitsMs = SplitsText.read(c.getString(14)),
        distanceEdited = c.getInt(15) == 1,
    )
}
