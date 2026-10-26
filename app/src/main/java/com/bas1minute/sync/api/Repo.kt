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

    // ---------- account ----------

    suspend fun login(mobile: String, password: String): Res<Me> =
        session(api.post("auth/login/", JSONObject().put("mobile", mobile).put("password", password).put("device", device())))

    suspend fun signup(name: String, mobile: String, password: String, consent: Boolean): Res<Me> =
        session(api.post("auth/signup/", JSONObject().put("name", name).put("mobile", mobile).put("password", password)
            .put("consent", consent).put("device", device())))

    private suspend fun session(r: ApiReply): Res<Me> = when {
        r.code == null -> Res.Fail("Couldn’t reach Bas 1 Minute. Check your internet and try again.")
        r.ok -> {
            val me = Me.from(r.json!!.getJSONObject("me"))
            prefs.token = r.json.optString("token")
            saveMe(me)
            Res.Ok(me)
        }
        else -> Res.Fail(r.error ?: "Something went wrong (${r.code}).", r.fields)
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

    suspend fun deleteAccount(): Res<Unit> {
        val r = api.post("me/delete/", JSONObject().put("confirm", "DELETE"))
        return when {
            r.code == null -> Res.Fail("You’re offline. Connect to the internet to delete your account.")
            r.ok -> { prefs.clearSession(); withContext(Dispatchers.IO) { db.clearAccountData() }; Res.Ok(Unit) }
            else -> Res.Fail(r.error ?: "Couldn’t delete the account.")
        }
    }

    private fun saveMe(me: Me) {
        prefs.name = me.name
        prefs.firstName = me.firstName
        prefs.mobile = me.mobile
        prefs.stepGoal = me.stepGoal
        prefs.burstGoal = me.burstGoal
        prefs.screenLimitMinutes = me.screenGoal
    }

    // ---------- screens (cached for offline) ----------

    suspend fun today(): Loaded<TodayData> = load("today/", "today") { TodayData.from(it) }.also { l ->
        l.value?.let { saveMe(it.me) }
    }
    fun cachedToday(): TodayData? = cached("today") { TodayData.from(it) }

    suspend fun circles(): Loaded<CirclesData> = load("circles/", "circles") { CirclesData.from(it) }
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

    suspend fun logDay(
        date: LocalDate, steps: Int?, bursts: Int?, screenMinutes: Int?, mindful: Int?,
        waterMl: Int? = null, weightKg: Double? = null, sleepMinutes: Int? = null,
    ): Res<JSONObject> {
        val body = JSONObject().put("date", date.toString())
        steps?.let { body.put("steps", it) }
        bursts?.let { body.put("bursts", it) }
        screenMinutes?.let { body.put("screen_minutes", it) }
        mindful?.let { body.put("mindful_minutes", it) }
        waterMl?.let { body.put("water_ml", it) }
        weightKg?.let { body.put("weight_kg", Math.round(it * 10) / 10.0) }
        sleepMinutes?.let { body.put("sleep_minutes", it) }
        // Show typed values straight away, even offline
        if (waterMl != null || weightKg != null || sleepMinutes != null) {
            withContext(Dispatchers.IO) { db.saveTyped(date, waterMl, weightKg, sleepMinutes) }
        }
        return write("day/", body, queueable = true)
    }

    suspend fun mood(value: Int): Res<JSONObject> =
        write("mood/", JSONObject().put("date", LocalDate.now().toString()).put("mood", value), queueable = true)

    suspend fun calmMinute(): Res<JSONObject> =
        write("calm/", JSONObject().put("date", LocalDate.now().toString()).put("minutes", 1), queueable = true)

    suspend fun cheer(circleId: Int, userId: Int, kind: String): Res<JSONObject> =
        write("circles/$circleId/cheer/", JSONObject().put("user_id", userId).put("kind", kind), queueable = true)

    // ---------- actions that need the website ----------

    suspend fun createCircle(name: String, kind: String) = write("circles/", JSONObject().put("name", name).put("kind", kind), queueable = false)
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

    /** Download up to 90 days from the server into this phone (restore). Null if offline. */
    suspend fun pullHistory(days: Int = 90): Int? {
        val r = api.get("history/?days=$days")
        if (!r.ok || r.json == null) return null
        val arr = r.json.optJSONArray("days") ?: return 0
        val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        return withContext(Dispatchers.IO) { db.mergeFromServer(list) }
    }

    private fun device(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}
