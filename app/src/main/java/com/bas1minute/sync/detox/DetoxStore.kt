package com.bas1minute.sync.detox

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Detox sessions and settings, kept only on this phone (SharedPreferences, JSON).
 * Nothing here is sent to the server, so Data safety does not change.
 */
class DetoxStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("bas1minute_detox", Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun changed() = listeners.forEach { it() }

    // ---- sessions -------------------------------------------------------------------------------------

    @Synchronized fun sessions(): List<DetoxSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun active(): DetoxSession? = sessions().lastOrNull { it.outcome == Outcome.ACTIVE }

    @Synchronized fun start(nowMs: Long, minutes: Int): DetoxSession {
        val list = sessions().map {
            // Never two at once: an older ACTIVE one left behind is closed as broken.
            if (it.outcome == Outcome.ACTIVE) DetoxLogic.breakNow(it, nowMs, BreakReason.ENDED_EARLY) else it
        }
        val s = DetoxSession(
            id = nowMs, startMs = nowMs,
            plannedEndMs = nowMs + DetoxLogic.clampMinutes(minutes) * DetoxLogic.MINUTE,
        )
        save(list + s)
        return s
    }

    @Synchronized fun update(session: DetoxSession) {
        save(sessions().map { if (it.id == session.id) session else it })
    }

    @Synchronized fun recordBlockedAttempt(sessionId: Long) {
        save(sessions().map { if (it.id == sessionId) it.copy(blockedAttempts = it.blockedAttempts + 1) else it })
    }

    @Synchronized fun clearHistory() {
        val keep = active()
        prefs.edit().putString(KEY_SESSIONS, JSONArray(listOfNotNull(keep).map(::toJson)).toString()).apply()
        changed()
    }

    private fun save(list: List<DetoxSession>) {
        val trimmed = list.sortedBy { it.startMs }.takeLast(MAX_SESSIONS)
        prefs.edit().putString(KEY_SESSIONS, JSONArray(trimmed.map(::toJson)).toString()).apply()
        changed()
    }

    // ---- settings -------------------------------------------------------------------------------------

    /** Up to 3 extra apps the user allows during detox (maps, UPI, a work app). Chosen before starting. */
    var extraAllowed: Set<String>
        get() = prefs.getStringSet(KEY_EXTRA, emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_EXTRA, v.take(DetoxLogic.MAX_EXTRA_ALLOWED).toSet()).apply(); changed() }

    /** Clean hours are counted from this hour of the day (default 6 AM) to midnight. */
    var dayFirstHour: Int
        get() = prefs.getInt(KEY_FIRST_HOUR, 6)
        set(v) { prefs.edit().putInt(KEY_FIRST_HOUR, v.coerceIn(0, 12)).apply(); changed() }

    var lastMinutes: Int
        get() = prefs.getInt(KEY_LAST_MIN, 60)
        set(v) { prefs.edit().putInt(KEY_LAST_MIN, DetoxLogic.clampMinutes(v)).apply() }

    companion object {
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_EXTRA = "extra_allowed"
        private const val KEY_FIRST_HOUR = "day_first_hour"
        private const val KEY_LAST_MIN = "last_minutes"
        private const val MAX_SESSIONS = 400

        @Volatile private var instance: DetoxStore? = null
        fun get(context: Context): DetoxStore =
            instance ?: synchronized(this) { instance ?: DetoxStore(context).also { instance = it } }

        private fun toJson(s: DetoxSession) = JSONObject().apply {
            put("id", s.id); put("start", s.startMs); put("plannedEnd", s.plannedEndMs)
            s.endMs?.let { put("end", it) }
            put("outcome", s.outcome.name)
            s.reason?.let { put("reason", it.name) }
            put("attempts", s.blockedAttempts)
            s.brokenBy?.let { put("brokenBy", it) }
        }

        private fun fromJson(o: JSONObject) = DetoxSession(
            id = o.getLong("id"),
            startMs = o.getLong("start"),
            plannedEndMs = o.getLong("plannedEnd"),
            endMs = if (o.has("end")) o.getLong("end") else null,
            outcome = Outcome.valueOf(o.optString("outcome", Outcome.KEPT.name)),
            reason = o.optString("reason", "").takeIf { it.isNotEmpty() }?.let { BreakReason.valueOf(it) },
            blockedAttempts = o.optInt("attempts", 0),
            brokenBy = o.optString("brokenBy", "").takeIf { it.isNotEmpty() },
        )
    }
}
