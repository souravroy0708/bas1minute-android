package com.bas1minute.sync

import android.content.Context

/** Small settings store. The sync key only lets this phone add daily totals to one account. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("bas1minute", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = p.getString("server", null) ?: BuildConfig.DEFAULT_SERVER
        set(v) = p.edit().putString("server", v.trim().trimEnd('/')).apply()

    var syncKey: String
        get() = p.getString("key", "") ?: ""
        set(v) = p.edit().putString("key", v.trim()).apply()

    var autoSync: Boolean
        get() = p.getBoolean("auto", true)
        set(v) = p.edit().putBoolean("auto", v).apply()

    var recordingSubscribed: Boolean
        get() = p.getBoolean("rec_sub", false)
        set(v) = p.edit().putBoolean("rec_sub", v).apply()

    var lastSyncAt: Long
        get() = p.getLong("last_at", 0L)
        set(v) = p.edit().putLong("last_at", v).apply()

    var lastSyncMessage: String
        get() = p.getString("last_msg", "") ?: ""
        set(v) = p.edit().putString("last_msg", v).apply()

    var lastSyncOk: Boolean
        get() = p.getBoolean("last_ok", false)
        set(v) = p.edit().putBoolean("last_ok", v).apply()

    var onboardingDone: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    /** Shown on the phone only; the website keeps the member's real goals. */
    var stepGoal: Int
        get() = p.getInt("step_goal", 7000)
        set(v) = p.edit().putInt("step_goal", v).apply()

    var screenLimitMinutes: Int
        get() = p.getInt("screen_limit", 180)
        set(v) = p.edit().putInt("screen_limit", v).apply()

    /** Login token from the app API (also accepted by the sync endpoint). Stored where syncKey was. */
    var token: String
        get() = syncKey
        set(v) { syncKey = v }

    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) = p.edit().putString("name", v).apply()

    var firstName: String
        get() = p.getString("first_name", "") ?: ""
        set(v) = p.edit().putString("first_name", v).apply()

    var mobile: String
        get() = p.getString("mobile", "") ?: ""
        set(v) = p.edit().putString("mobile", v).apply()

    var burstGoal: Int
        get() = p.getInt("burst_goal", 4)
        set(v) = p.edit().putInt("burst_goal", v).apply()

    /** Weight is off until the person switches it on (private, never shared). */
    var trackWeight: Boolean
        get() = p.getBoolean("track_weight", false)
        set(v) = p.edit().putBoolean("track_weight", v).apply()

    /** When the person tapped "Not now" on the Samsung Health card (shown again after a week). */
    var samsungDismissedAt: Long
        get() = p.getLong("samsung_dismissed", 0L)
        set(v) = p.edit().putLong("samsung_dismissed", v).apply()

    fun clearSession() {
        p.edit().remove("key").remove("name").remove("first_name").remove("mobile")
            .remove("last_at").remove("last_msg").remove("last_ok").apply()
    }

    val isConfigured: Boolean
        get() = syncKey.startsWith("b1m_") && serverUrl.startsWith("http")
}
