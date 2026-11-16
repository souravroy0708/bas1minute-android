package com.bas1minute.sync.schedule

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Schedules (recurring auto-detox windows), kept only on this phone — same pattern as DetoxStore's own
 * sessions. Nothing here is sent to the server. */
class ScheduleStore private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("bas1minute_schedule", Context.MODE_PRIVATE)

    @Synchronized fun all(): List<Schedule> {
        val raw = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized fun add(label: String, days: Set<Int>, startMinute: Int, endMinute: Int): Schedule {
        val s = Schedule(id = System.currentTimeMillis(), label = label, days = days, startMinute = startMinute, endMinute = endMinute)
        save(all() + s)
        return s
    }

    @Synchronized fun update(schedule: Schedule) = save(all().map { if (it.id == schedule.id) schedule else it })

    @Synchronized fun remove(id: Long) = save(all().filter { it.id != id })

    /** The id of the schedule currently responsible for an active detox, if any — so the worker knows which
     * running session (if any) is its own to end automatically, versus one the member started by hand. */
    var activeScheduleId: Long
        get() = prefs.getLong(KEY_ACTIVE_ID, -1L)
        set(v) = prefs.edit().putLong(KEY_ACTIVE_ID, v).apply()

    private fun save(list: List<Schedule>) {
        prefs.edit().putString(KEY_SCHEDULES, JSONArray(list.take(ScheduleLogic.MAX_SCHEDULES).map(::toJson)).toString()).apply()
    }

    private fun toJson(s: Schedule) = JSONObject().apply {
        put("id", s.id); put("label", s.label); put("days", JSONArray(s.days.toList()))
        put("start", s.startMinute); put("end", s.endMinute); put("enabled", s.enabled)
    }

    private fun fromJson(o: JSONObject): Schedule {
        val daysArr = o.optJSONArray("days") ?: JSONArray()
        val days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
        return Schedule(
            id = o.getLong("id"), label = o.optString("label", "Schedule"), days = days,
            startMinute = o.getInt("start"), endMinute = o.getInt("end"), enabled = o.optBoolean("enabled", true),
        )
    }

    companion object {
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_ACTIVE_ID = "active_schedule_id"
        @Volatile private var instance: ScheduleStore? = null
        fun get(context: Context): ScheduleStore =
            instance ?: synchronized(this) { instance ?: ScheduleStore(context).also { instance = it } }
    }
}
