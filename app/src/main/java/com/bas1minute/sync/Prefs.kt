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

    /** Step length for distance. 76 cm ≈ 1,300 steps per km (typical adult). Editable in Profile. */
    var stepLengthCm: Int
        get() = p.getInt("step_cm", 76)
        set(v) = p.edit().putInt("step_cm", v.coerceIn(40, 120)).apply()

    // ---- GPS walk / run (phone only) ----
    /** Use GPS for a map of the route. Off: km, time and pace from steps, no location used. */
    var trackUseGps: Boolean
        get() = p.getBoolean("track_use_gps", true)
        set(v) = p.edit().putBoolean("track_use_gps", v).apply()

    var trackType: String
        get() = p.getString("track_type", "walk") ?: "walk"
        set(v) = p.edit().putString("track_type", v).apply()

    var trackAutoPause: Boolean
        get() = p.getBoolean("track_autopause", true)
        set(v) = p.edit().putBoolean("track_autopause", v).apply()

    var trackVoice: Boolean
        get() = p.getBoolean("track_voice", false)
        set(v) = p.edit().putBoolean("track_voice", v).apply()

    /** Move tab: weekly distance goal in km. */
    var weeklyKmGoal: Int
        get() = p.getInt("weekly_km_goal", 10)
        set(v) = p.edit().putInt("weekly_km_goal", v.coerceIn(1, 500)).apply()

    /** Shared pictures leave out the first and last 200 m by default (privacy first). */
    var trackHideEnds: Boolean
        get() = p.getBoolean("track_hide_ends", true)
        set(v) = p.edit().putBoolean("track_hide_ends", v).apply()

    /** Appear (masked) in the global ranking when sharing steps. Everyone can always see it. */
    var globalVisible: Boolean
        get() = p.getBoolean("global_visible", true)
        set(v) = p.edit().putBoolean("global_visible", v).apply()

    /** Phone check: one evening notification when something serious is found (read by BatteryWorker). */
    var phoneAlerts: Boolean
        get() = p.getBoolean("phone_alerts", true)
        set(v) = p.edit().putBoolean("phone_alerts", v).apply()

    // ---- Health wheel (stays on this phone; not account data) ----
    var spinDay: String
        get() = p.getString("spin_day", "") ?: ""
        set(v) = p.edit().putString("spin_day", v).apply()

    var spinCount: Int
        get() = p.getInt("spin_count", 0)
        set(v) = p.edit().putInt("spin_count", v).apply()

    /** Ids of wheel tips this person has discovered. */
    var discoveredTips: Set<String>
        get() = p.getStringSet("tips_found", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("tips_found", v).apply()

    // ---- Privacy-first setup (v4) ----

    /** Name and PIN chosen on first launch. */
    var setupDone: Boolean
        get() = p.getBoolean("setup_done", false)
        set(v) = p.edit().putBoolean("setup_done", v).apply()

    /** SHA-256 of the PIN with a per-phone salt. A convenience lock, not security (it can be reset). */
    var pinHash: String
        get() = p.getString("pin_hash", "") ?: ""
        set(v) = p.edit().putString("pin_hash", v).apply()

    private val salt: String
        get() = p.getString("pin_salt", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("pin_salt", it).apply() }

    fun hashPin(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun checkPin(pin: String) = pinHash.isNotBlank() && hashPin(pin) == pinHash

    /** Ask for the PIN when the app opens. Off by default. */
    var appLock: Boolean
        get() = p.getBoolean("app_lock", false)
        set(v) = p.edit().putBoolean("app_lock", v).apply()

    /**
     * When on, only daily steps and screen time are sent (global ranking, challenges, circles, backup).
     * Set on at setup for new members (they're told on the name screen); stored off for older installs until they join.
     */
    var shareActivity: Boolean
        get() = p.getBoolean("share_activity", false)
        set(v) = p.edit().putBoolean("share_activity", v).apply()

    /** Invite code from a link tapped before setup was finished. */
    var pendingJoin: String
        get() = p.getString("pending_join", "") ?: ""
        set(v) = p.edit().putString("pending_join", v).apply()

    /** Motivation pushes from Bas 1 Minute (on by default; Android 13+ also needs the system permission). */
    var pushEnabled: Boolean
        get() = p.getBoolean("push_on", true)
        set(v) = p.edit().putBoolean("push_on", v).apply()

    /** Playful taunts ("Tumse na ho payega?") can be switched off; other messages still come. */
    var allowTaunts: Boolean
        get() = p.getBoolean("allow_taunts", true)
        set(v) = p.edit().putBoolean("allow_taunts", v).apply()

    /** Three nudges a day (morning, afternoon, evening) built on the phone from steps, screen time and detox. */
    var dailyNudges: Boolean
        get() = p.getBoolean("daily_nudges", true)
        set(v) = p.edit().putBoolean("daily_nudges", v).apply()

    /** "2026-11-13:evening": the last nudge sent, so a slot is never sent twice. */
    var lastNudge: String
        get() = p.getString("last_nudge", "") ?: ""
        set(v) = p.edit().putString("last_nudge", v).apply()

    /** Members from before the global circle was opt-out have been invited once (they weren't sharing). */
    var globalInviteShown: Boolean
        get() = p.getBoolean("global_invite_shown", false)
        set(v) = p.edit().putBoolean("global_invite_shown", v).apply()

    /** One daily notification with yesterday's total screen and social-media time. Off by default. */
    var dailyUsageAlert: Boolean
        get() = p.getBoolean("daily_usage_alert", false)
        set(v) = p.edit().putBoolean("daily_usage_alert", v).apply()

    /** "2026-11-13": the last usage-alert day sent, so it's never sent twice. */
    var lastUsageAlert: String
        get() = p.getString("last_usage_alert", "") ?: ""
        set(v) = p.edit().putString("last_usage_alert", v).apply()

    /** Shown once, right after setup: a short, mandatory walkthrough of the app's main features. */
    var tourDone: Boolean
        get() = p.getBoolean("tour_done", false)
        set(v) = p.edit().putBoolean("tour_done", v).apply()

    /** One-time migration flag: decides tourDone's starting value for members who already existed when the
     *  tour shipped (see AppController), so it never forces an already-onboarded member through it. */
    var tourMigrated: Boolean
        get() = p.getBoolean("tour_migrated", false)
        set(v) = p.edit().putBoolean("tour_migrated", v).apply()

    /** Asked for the notification permission once already. */
    var notifAsked: Boolean
        get() = p.getBoolean("notif_asked", false)
        set(v) = p.edit().putBoolean("notif_asked", v).apply()

    /** Latest Firebase token for this phone. */
    var pushToken: String
        get() = p.getString("push_token", "") ?: ""
        set(v) = p.edit().putString("push_token", v).apply()

    /** Day (yyyy-mm-dd) the goal celebration was last shown: once a day. */
    var celebratedDay: String
        get() = p.getString("celebrated_day", "") ?: ""
        set(v) = p.edit().putString("celebrated_day", v).apply()

    /** Week key (e.g. 2026-W44) whose review was already opened. */
    var reviewSeenWeek: String
        get() = p.getString("review_seen", "") ?: ""
        set(v) = p.edit().putString("review_seen", v).apply()

    /** "system", "light" or "dark". */
    var theme: String
        get() = p.getString("theme", "system") ?: "system"
        set(v) = p.edit().putString("theme", v).apply()

    /** "en" or "hi". */
    var language: String
        get() = p.getString("language", "en") ?: "en"
        set(v) = p.edit().putString("language", v).apply()

    /** Short bio shown on Home and Profile (phone only). */
    var bio: String
        get() = p.getString("bio", "") ?: ""
        set(v) = p.edit().putString("bio", v.take(120)).apply()

    /** Days (yyyy-mm-dd) on which the one manual burst / one manual calm minute was used. Timers are unlimited. */
    var manualBurstDays: Set<String>
        get() = p.getStringSet("manual_burst_days", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("manual_burst_days", v.sorted().takeLast(60).toSet()).apply()

    var manualCalmDays: Set<String>
        get() = p.getStringSet("manual_calm_days", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("manual_calm_days", v.sorted().takeLast(60).toSet()).apply()

    /** Wipe all settings (Delete everything). */
    fun wipeAll() { p.edit().clear().apply() }

    fun clearSession() {
        p.edit().remove("key").remove("name").remove("first_name").remove("mobile")
            .remove("last_at").remove("last_msg").remove("last_ok").apply()
    }

    val isConfigured: Boolean
        get() = syncKey.startsWith("b1m_") && serverUrl.startsWith("http")
}
