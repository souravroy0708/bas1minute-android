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

    @Synchronized fun start(nowMs: Long, minutes: Int, why: String = ""): DetoxSession {
        val list = sessions().map {
            // Never two at once: an older ACTIVE one left behind is closed as broken.
            if (it.outcome == Outcome.ACTIVE) DetoxLogic.breakNow(it, nowMs, BreakReason.ENDED_EARLY) else it
        }
        val s = DetoxSession(
            id = nowMs, startMs = nowMs,
            plannedEndMs = nowMs + DetoxLogic.clampMinutes(minutes) * DetoxLogic.MINUTE,
            why = why.trim().take(140),
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

    /** Which built-in categories are blocked. Both on by default: this is what "just block social media and
     * browsers" means on a fresh install. Stored as the literal strings "social" / "browsers". */
    var blockCategories: Set<String>
        get() = prefs.getStringSet(KEY_CATEGORIES, null)?.toSet() ?: setOf("social", "browsers")
        set(v) { prefs.edit().putStringSet(KEY_CATEGORIES, v.toSet()).apply(); changed() }

    /** Individual apps the member added to block, beyond the two categories above. */
    var blockedApps: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_EXTRA, emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_BLOCKED_EXTRA, v.take(DetoxLogic.MAX_BLOCKED_EXTRA).toSet()).apply(); changed() }

    /** Individual apps carved OUT of an on category — e.g. keep LinkedIn open while still blocking Instagram. */
    var allowedOverride: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_OVERRIDE, emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_ALLOWED_OVERRIDE, v.toSet()).apply(); changed() }

    /** Clean hours are counted from this hour of the day (default 6 AM) to midnight. */
    var dayFirstHour: Int
        get() = prefs.getInt(KEY_FIRST_HOUR, 6)
        set(v) { prefs.edit().putInt(KEY_FIRST_HOUR, v.coerceIn(0, 12)).apply(); changed() }

    var lastMinutes: Int
        get() = prefs.getInt(KEY_LAST_MIN, 60)
        set(v) { prefs.edit().putInt(KEY_LAST_MIN, DetoxLogic.clampMinutes(v)).apply() }

    /** The person's own past commitment phrases, shown as suggestions next time (never sent anywhere). */
    var recentWhys: List<String>
        get() = prefs.getString(KEY_WHYS, null)?.let { JSONArray(it) }?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        set(v) { prefs.edit().putString(KEY_WHYS, JSONArray(v.takeLast(5)).toString()).apply() }

    /**
     * Optional accountability contact: a name and a phone number or handle, used only to pre-fill a message the
     * person reviews and sends themselves if they end a detox early. Never sent automatically.
     */
    var accountabilityName: String
        get() = prefs.getString(KEY_ACC_NAME, "") ?: ""
        set(v) { prefs.edit().putString(KEY_ACC_NAME, v.trim().take(60)).apply() }

    var accountabilityContact: String
        get() = prefs.getString(KEY_ACC_CONTACT, "") ?: ""
        set(v) { prefs.edit().putString(KEY_ACC_CONTACT, v.trim().take(60)).apply() }

    companion object {
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_CATEGORIES = "block_categories"
        private const val KEY_BLOCKED_EXTRA = "blocked_extra"
        private const val KEY_ALLOWED_OVERRIDE = "allowed_override"
        private const val KEY_FIRST_HOUR = "day_first_hour"
        private const val KEY_LAST_MIN = "last_minutes"
        private const val KEY_WHYS = "recent_whys"
        private const val KEY_ACC_NAME = "accountability_name"
        private const val KEY_ACC_CONTACT = "accountability_contact"
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
            if (s.why.isNotBlank()) put("why", s.why)
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
            why = o.optString("why", ""),
        )
    }
}
