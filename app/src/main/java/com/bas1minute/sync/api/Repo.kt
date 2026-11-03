package com.bas1minute.sync.api

import android.content.Context
import android.os.Build
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** Result of an action the user took. */
sealed class Res<out T> {
    data class Ok<T>(val value: T) : Res<T>()
    /** Saved on the phone; will be sent when the website is reachable. */
    data class Queued(val message: String = "Saved on your phone. It will be sent when you’re online.") : Res<Nothing>()
    data class Fail(val message: String, val fields: Map<String, String> = emptyMap(), val authLost: Boolean = false) : Res<Nothing>()
}

/** A screen's data plus whether it came from the phone's saved copy. */
data class Loaded<T>(val value: T?, val offline: Boolean, val authLost: Boolean = false)

class Repo(context: Context) {
    private val ctx = context.applicationContext
    private val api = Api(ctx)
    private val db = LocalDb.get(ctx)
    private val prefs = Prefs(ctx)
    private val secrets = SecretStore(ctx)

    // ---------- anonymous account (name only; needed for circles) ----------

    /** Creates the name-only account if this phone doesn't have one yet. False when offline. */
    suspend fun ensureAccount(): Boolean {
        if (prefs.token.isNotBlank()) {
            if (secrets.secret.isNotBlank()) return true
            val up = api.post("auth/upgrade/")   // installs from before request signing
            if (up.ok) up.json?.optString("secret")?.takeIf { it.isNotBlank() }?.let { secrets.secret = it }
            return secrets.secret.isNotBlank() || up.code == null
        }
        if (prefs.name.isBlank()) return false
        val r = api.post("auth/device/", JSONObject().put("name", prefs.name).put("device", device()))
        if (!r.ok || r.json == null) return false
        prefs.token = r.json.optString("token")
        secrets.secret = r.json.optString("secret")
        r.json.optJSONObject("me")?.let { saveMe(Me.from(it)) }
        if (prefs.shareActivity) api.post("me/", JSONObject().put("share_activity", true))
        return true
    }

    /** Turn sharing of steps and screen time on or off on the server. */
    suspend fun setShare(on: Boolean): Res<Unit> {
        if (!ensureAccount()) return Res.Fail("You’re offline. Try again when connected.")
        val r = api.post("me/", JSONObject().put("share_activity", on))
        return if (r.ok) Res.Ok(Unit) else Res.Fail(r.error ?: "Couldn’t update sharing.")
    }

    /** Delete every day this phone shared with the server. Account and circles stay. */
    suspend fun deleteSharedData(): Res<Int> {
        if (prefs.token.isBlank()) return Res.Ok(0)
        val r = api.post("me/data/delete/")
        return when {
            r.code == null -> Res.Fail("You’re offline. Try again when connected.")
            r.ok -> Res.Ok(r.json?.optInt("deleted_days") ?: 0)
            else -> Res.Fail(r.error ?: "Couldn’t delete shared data.")
        }
    }

    suspend fun logout() {
        api.post("auth/logout/")  // best effort; the phone forgets the token either way
        prefs.clearSession()
        withContext(Dispatchers.IO) { db.clearAccountData() }
    }

    suspend fun updateMe(fields: JSONObject): Res<Me> {
        val r = api.post("me/", fields)
        return when {
            r.code == null -> Res.Fail("You’re offline. Goals can be changed when you’re connected.")
            r.code == 401 -> Res.Fail("Please log in again.", authLost = true)
            r.ok -> Me.from(r.json!!.getJSONObject("me")).also { saveMe(it) }.let { Res.Ok(it) }
            else -> Res.Fail(r.error ?: "Couldn’t save.", r.fields)
        }
    }

    /** Delete everything: the server account (if any) and all data on this phone. */
    suspend fun deleteEverything(): Res<Unit> {
        if (prefs.token.isNotBlank()) {
            val r = api.post("me/delete/", JSONObject().put("confirm", "DELETE"))
            if (r.code == null) return Res.Fail("You’re offline. Connect to the internet so your circle account is deleted too.")
        }
        withContext(Dispatchers.IO) { db.wipeAll() }
        prefs.wipeAll()
        secrets.clear()
        return Res.Ok(Unit)
    }

    private fun saveMe(me: Me) {
        prefs.name = me.name
        prefs.firstName = me.firstName
        prefs.mobile = me.mobile.takeIf { it != "null" } ?: ""
        prefs.stepGoal = me.stepGoal
        prefs.burstGoal = me.burstGoal
        prefs.screenLimitMinutes = me.screenGoal
    }

    // ---------- screens (cached for offline) ----------

    suspend fun today(): Loaded<TodayData> = load("today/", "today") { TodayData.from(it) }.also { l ->
        l.value?.let { saveMe(it.me) }
    }
    fun cachedToday(): TodayData? = cached("today") { TodayData.from(it) }

    suspend fun circles(): Loaded<CirclesData> = load("circles/list/", "circles") { CirclesData.from(it) }
    fun cachedCircles(): CirclesData? = cached("circles") { CirclesData.from(it) }

    suspend fun circle(id: Int): Loaded<CircleDetail> = load("circles/$id/", "circle:$id") { CircleDetail.from(it) }
    fun cachedCircle(id: Int): CircleDetail? = cached("circle:$id") { CircleDetail.from(it) }

    private suspend fun <T> load(path: String, key: String, parse: (JSONObject) -> T): Loaded<T> {
        val r = api.get(path)
        return when {
            r.ok && r.json != null -> {
                withContext(Dispatchers.IO) { db.putCache(key, r.json.toString()) }
                Loaded(runCatching { parse(r.json) }.getOrNull(), offline = false)
            }
            r.code == 401 -> Loaded(cached(key, parse), offline = true, authLost = true)
            else -> Loaded(cached(key, parse), offline = true)
        }
    }

    private fun <T> cached(key: String, parse: (JSONObject) -> T): T? =
        db.getCache(key)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }

    // ---------- actions that work offline (queued) ----------

    /** Everything typed in Log a day stays on this phone. */
    suspend fun logDay(
        date: LocalDate, steps: Int?, bursts: Int?, screenMinutes: Int?, mindful: Int?,
        waterMl: Int? = null, weightKg: Double? = null, sleepMinutes: Int? = null,
    ): Res<JSONObject> = withContext(Dispatchers.IO) {
        if (steps != null || screenMinutes != null) {
            // Typed steps and screen time are replaced automatically when the phone measures them.
            db.saveDay(date, com.bas1minute.sync.PhoneDay(steps = steps?.toLong(), stepsSource = "manual", socialMinutes = screenMinutes))
        }
        db.savePrivate(date, bursts = bursts, calmMinutes = mindful)
        db.saveTyped(date, waterMl, weightKg, sleepMinutes)
        Res.Ok(JSONObject())
    }

    suspend fun mood(value: Int): Res<JSONObject> = withContext(Dispatchers.IO) {
        db.savePrivate(LocalDate.now(), mood = value); Res.Ok(JSONObject())
    }

    suspend fun calmMinute(): Res<JSONObject> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        db.savePrivate(today, calmMinutes = (db.day(today)?.calmMinutes ?: 0) + 1); Res.Ok(JSONObject())
    }

    /** A burst finished with the 1-minute timer (unlimited). */
    suspend fun addBurst(date: LocalDate = LocalDate.now()): Res<JSONObject> = withContext(Dispatchers.IO) {
        db.savePrivate(date, bursts = (db.day(date)?.bursts ?: 0) + 1); Res.Ok(JSONObject())
    }

    /** A burst done without the timer: once per day, so typed numbers can’t be stacked. */
    suspend fun addManualBurst(date: LocalDate = LocalDate.now()): Res<JSONObject> {
        if (date.toString() in prefs.manualBurstDays) return Res.Fail("You’ve added your one manual burst for this day. Use the 1-minute timer for more.")
        prefs.manualBurstDays = prefs.manualBurstDays + date.toString()
        return addBurst(date)
    }

    /** One manual calm minute per day; the Calm screen adds as many as you do. */
    suspend fun addManualCalm(date: LocalDate = LocalDate.now()): Res<JSONObject> = withContext(Dispatchers.IO) {
        if (date.toString() in prefs.manualCalmDays) return@withContext Res.Fail("You’ve added your one manual calm minute for this day. Use the Calm timer for more.")
        prefs.manualCalmDays = prefs.manualCalmDays + date.toString()
        db.savePrivate(date, calmMinutes = (db.day(date)?.calmMinutes ?: 0) + 1); Res.Ok(JSONObject())
    }

    suspend fun cheer(circleId: Int, userId: Int, kind: String): Res<JSONObject> =
        write("circles/$circleId/cheer/", JSONObject().put("user_id", userId).put("kind", kind), queueable = true)

    // ---------- actions that need the website ----------

    /** listed = anyone can find it and ask to join; private = invite only. Changeable later. */
    suspend fun createCircle(name: String, kind: String, listed: Boolean = false, about: String = "") =
        write("circles/", JSONObject().put("name", name).put("kind", kind).put("discoverable", listed).put("about", about), queueable = false)
    suspend fun joinCircle(code: String) = write("circles/join/", JSONObject().put("code", code), queueable = false)
    suspend fun shareScreen(circleId: Int, on: Boolean) = write("circles/$circleId/settings/", JSONObject().put("share_screen_time", on), queueable = false)
    suspend fun leaveCircle(circleId: Int) = write("circles/$circleId/leave/", JSONObject(), queueable = false)
    suspend fun manageCircle(circleId: Int, action: String, body: JSONObject = JSONObject()) =
        write("circles/$circleId/$action/", body, queueable = false)

    private suspend fun write(path: String, body: JSONObject, queueable: Boolean): Res<JSONObject> {
        val r = api.post(path, body)
        return when {
            r.code == null && queueable -> {
                withContext(Dispatchers.IO) { db.enqueue(path, body.toString()) }
                Res.Queued()
            }
            r.code == null -> Res.Fail("You’re offline. This needs the internet; try again when connected.")
            r.code == 401 -> Res.Fail("Please log in again.", authLost = true)
            r.ok -> Res.Ok(r.json ?: JSONObject())
            else -> Res.Fail(r.error ?: "Something went wrong (${r.code}).", r.fields)
        }
    }

    /** Send actions saved while offline, oldest first. Returns how many are still waiting. */
    suspend fun flushOutbox(): Int {
        val items = withContext(Dispatchers.IO) { db.queued() }
        for (item in items) {
            val r = api.post(item.path, runCatching { JSONObject(item.body) }.getOrDefault(JSONObject()))
            when {
                r.code == null || r.code >= 500 || r.code == 401 -> break   // try again later
                else -> withContext(Dispatchers.IO) { db.dequeue(item.id) } // done, or rejected for good (4xx)
            }
        }
        return withContext(Dispatchers.IO) { db.queuedCount() }
    }

    fun waitingActions(): Int = db.queuedCount()

    // ---------- circles clone: keep every circle, the global board and requests on the phone ----------

    /** When the circles list was last downloaded (null = never). */
    fun circlesSavedAt(): Long? = db.getCacheTime("circles")
    fun circleSavedAt(id: Int): Long? = db.getCacheTime("circle:$id")

    /**
     * Download everything the Circles tab shows, so it all opens offline with the last synced data:
     * the list, every circle's board, the global board (week and today), your requests and, for circles you
     * own, their join requests. Best effort: stops quietly when offline. Returns true when it completed.
     */
    suspend fun syncCommunity(): Boolean {
        if (prefs.token.isBlank()) return false
        val list = circles()
        if (list.offline) return false
        val community = Community.get(ctx)
        list.value?.circles?.forEach { c ->
            if (circle(c.id).offline) return false
            if (c.isOwner) community.joinRequests(c.id)
        }
        community.global("week")
        community.global("today")
        community.myRequests()
        (community.competitions().value ?: emptyList()).filter { it.status != "ended" }.forEach { community.competition(it.id) }
        return true
    }

    // ---------- website sign-in, approved on this phone ----------

    data class WebRequest(val browser: String, val ip: String, val ago: String)
    data class WebSession(val id: Int, val browser: String, val ip: String, val since: String, val lastSeen: String)

    private fun webFail(r: ApiReply): Res.Fail = when {
        r.code == null -> Res.Fail("You’re offline. Connect to sign in on the website.")
        r.code == 401 -> Res.Fail("Please try again in a moment.", authLost = r.error == "Please sign in again.")
        r.code == 404 -> Res.Fail("Website sign-in needs the latest website update.")
        r.code == 429 -> Res.Fail("Too many tries. Wait a few minutes.")
        else -> Res.Fail(r.error ?: "Something went wrong (${r.code}).")
    }

    private fun webRequest(j: JSONObject?) = WebRequest(j?.optString("browser") ?: "A browser", j?.optString("ip") ?: "", j?.optString("ago") ?: "")

    suspend fun webCheck(code: String): Res<WebRequest> {
        if (!ensureAccount()) return Res.Fail("You’re offline. Connect to sign in on the website.")
        val r = api.post("web-login/check/", JSONObject().put("code", code))
        return if (r.ok) Res.Ok(webRequest(r.json?.optJSONObject("request"))) else webFail(r)
    }

    suspend fun webApprove(code: String, allow: Boolean): Res<WebRequest> {
        val r = api.post("web-login/approve/", JSONObject().put("code", code).put("allow", allow))
        return if (r.ok) Res.Ok(webRequest(r.json?.optJSONObject("request"))) else webFail(r)
    }

    suspend fun webSessions(): Res<List<WebSession>> {
        if (!ensureAccount()) return Res.Fail("You’re offline.")
        val r = api.post("web-login/sessions/")
        if (!r.ok) return webFail(r)
        val arr = r.json?.optJSONArray("sessions") ?: return Res.Ok(emptyList())
        return Res.Ok((0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
            WebSession(it.optInt("id"), it.optString("browser"), it.optString("ip"), it.optString("since"), it.optString("last_seen"))
        })
    }

    suspend fun webSignOut(id: Int?): Res<Int> {
        val r = api.post(if (id == null) "web-login/sessions/signout-all/" else "web-login/sessions/$id/signout/")
        return if (r.ok) Res.Ok(r.json?.optInt("signed_out") ?: 0) else webFail(r)
    }

    /** Tell the server this phone's push token ("" turns pushes off). Needs the name-only account. */
    suspend fun registerPush(token: String): Boolean {
        if (!ensureAccount()) return false
        return api.post("me/push-token/", JSONObject().put("token", token)).ok
    }

    /** Download up to 90 days from the server into this phone (restore). Null if offline. */
    suspend fun pullHistory(days: Int = 90): Int? {
        val r = api.post("history/", JSONObject().put("days", days))
        if (!r.ok || r.json == null) return null
        val arr = r.json.optJSONArray("days") ?: return 0
        val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        return withContext(Dispatchers.IO) { db.mergeFromServer(list) }
    }

    private fun device(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}
