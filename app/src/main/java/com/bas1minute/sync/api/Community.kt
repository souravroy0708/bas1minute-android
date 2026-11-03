package com.bas1minute.sync.api

import android.content.Context
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * Global ranking, circle search and join requests, from the website's /api/v1/ (docs/COMMUNITY_API.md).
 * Every read is saved on the phone (LocalDb cache), so these screens show the last synced copy offline.
 */

// ---------------------------------------------------------------- models

data class GlobalRow(
    val rank: Int,
    /** Masked by the server ("Ra*** S."). Your own row carries your real first name. */
    val name: String,
    val city: String?,
    val points: Int,
    val steps: Long,
    val isMe: Boolean,
)

data class GlobalMe(
    /** Null when not ranked (not sharing, or hidden). */
    val rank: Int?,
    val points: Int,
    val steps: Long,
    /** Sharing steps with the server is on. */
    val shares: Boolean,
    /** "Show me in global ranking" is on. */
    val visible: Boolean,
) {
    val ranked: Boolean get() = rank != null
}

data class GlobalBoard(
    val name: String,
    val period: String,
    /** People in the global circle (everyone with the app). */
    val members: Int,
    /** People with a rank (sharing and visible). */
    val ranked: Int,
    val top: List<GlobalRow>,
    val me: GlobalMe,
    val daysLeft: Int,
    /** When this copy came from the server (shown as "Saved copy from …" offline). */
    val updatedAt: Long,
) {
    /** "Top 12%" style, for people outside the top 100. */
    val topPercent: Int? get() = me.rank?.let { r -> if (ranked <= 0) null else ((r * 100.0 / ranked).toInt()).coerceIn(1, 100) }

    companion object {
        fun from(j: JSONObject, at: Long = System.currentTimeMillis()): GlobalBoard {
            val m = j.optJSONObject("me") ?: JSONObject()
            return GlobalBoard(
                j.optString("name", "Bas 1 Minute India"), j.optString("period", "week"),
                j.optInt("members"), j.optInt("ranked"),
                j.optJSONArray("top").objects().map {
                    GlobalRow(it.optInt("rank"), it.optString("name"), it.optString("city").takeIf { c -> c.isNotBlank() && c != "null" },
                        it.optInt("points"), it.optLong("steps"), it.optBoolean("is_me"))
                },
                GlobalMe(if (m.isNull("rank") || !m.has("rank")) null else m.optInt("rank"), m.optInt("points"), m.optLong("steps"),
                    m.optBoolean("shares"), m.optBoolean("visible", true)),
                j.optInt("days_left"), at,
            )
        }
    }
}

/** A circle found in search. status: none, pending, member, declined. */
data class FoundCircle(
    val id: Int, val name: String, val kind: String, val kindLabel: String, val city: String?,
    val size: Int, val full: Boolean, val owner: String, val about: String?, val status: String, val weekPoints: Int,
) {
    companion object {
        fun from(j: JSONObject) = FoundCircle(
            j.optInt("id"), j.optString("name"), j.optString("kind"), j.optString("kind_label"),
            j.optString("city").takeIf { it.isNotBlank() && it != "null" }, j.optInt("size"), j.optBoolean("full"),
            j.optString("owner"), j.optString("about").takeIf { it.isNotBlank() && it != "null" },
            j.optString("status", "none"), j.optInt("week_points"),
        )
    }
}

/** Someone asking to join a circle you own. */
data class JoinRequest(val id: Int, val name: String, val message: String?, val ago: String, val shares: Boolean, val weekPoints: Int?) {
    companion object {
        fun from(j: JSONObject) = JoinRequest(j.optInt("id"), j.optString("name"), j.optString("message").takeIf { it.isNotBlank() && it != "null" },
            j.optString("ago"), j.optBoolean("shares"), if (j.isNull("week_points") || !j.has("week_points")) null else j.optInt("week_points"))
    }
}

/** A request you sent. status: pending, approved, declined. */
data class MyRequest(val circleId: Int, val name: String, val status: String, val ago: String) {
    companion object {
        fun from(j: JSONObject) = MyRequest(j.optInt("circle_id"), j.optString("name"), j.optString("status"), j.optString("ago"))
    }
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** Everything the screens need. Every call is safe to make offline (returns a Fail or saved data). */
interface CommunitySource {
    suspend fun global(period: String): Loaded<GlobalBoard>
    suspend fun setGlobalVisible(on: Boolean): Res<Unit>
    suspend fun search(query: String): Res<List<FoundCircle>>
    suspend fun requestJoin(circleId: Int, message: String): Res<String>
    suspend fun cancelRequest(circleId: Int): Res<Unit>
    suspend fun myRequests(): Res<List<MyRequest>>
    suspend fun joinRequests(circleId: Int): Res<List<JoinRequest>>
    suspend fun decide(circleId: Int, requestId: Int, approve: Boolean): Res<Unit>
    suspend fun discoverable(circleId: Int): Boolean
    suspend fun setDiscoverable(circleId: Int, on: Boolean, about: String?): Res<Unit>
}

object Community {
    @Volatile private var instance: CommunitySource? = null
    fun get(ctx: Context): CommunitySource = instance ?: synchronized(this) {
        instance ?: HttpCommunity(ctx.applicationContext).also { instance = it }
    }

    /** "Sourav Kumar" → "So*** K." (what the server does for everyone except you). */
    fun mask(full: String): String {
        val parts = full.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "Member"
        val first = parts[0]
        val shown = first.take(if (first.length <= 3) 1 else 2)
        val last = parts.getOrNull(1)?.take(1)?.uppercase()?.let { " $it." } ?: ""
        return shown.replaceFirstChar { it.uppercase() } + "***" + last
    }
}

// ---------------------------------------------------------------- real server (contract)

class HttpCommunity(private val ctx: Context) : CommunitySource {
    private val api = Api(ctx)
    private val db = LocalDb.get(ctx)

    private fun fail(r: ApiReply, what: String): Res.Fail = when {
        r.code == null -> Res.Fail("You’re offline. $what needs the internet.")
        r.code == 401 -> Res.Fail("Please log in again.", authLost = true)
        r.code == 404 -> Res.Fail("$what needs the latest website update.")
        else -> Res.Fail(r.error ?: "Something went wrong (${r.code}).", r.fields)
    }

    override suspend fun global(period: String): Loaded<GlobalBoard> {
        val key = "global:$period"
        val r = api.post("global/", JSONObject().put("period", period))
        if (r.ok && r.json != null) {
            withContext(Dispatchers.IO) { db.putCache(key, r.json.toString()) }
            return Loaded(runCatching { GlobalBoard.from(r.json) }.getOrNull(), offline = false)
        }
        val saved = db.getCache(key)?.let { runCatching { GlobalBoard.from(JSONObject(it), db.getCacheTime(key) ?: 0L) }.getOrNull() }
        return Loaded(saved, offline = true, authLost = r.code == 401)
    }

    override suspend fun setGlobalVisible(on: Boolean): Res<Unit> {
        val r = api.post("me/", JSONObject().put("global_visible", on))
        return if (r.ok) Res.Ok(Unit) else fail(r, "Changing this")
    }

    override suspend fun search(query: String): Res<List<FoundCircle>> {
        val r = api.post("circles/search/", JSONObject().put("q", query))
        return if (r.ok) Res.Ok(r.json?.optJSONArray("results").objects().map { FoundCircle.from(it) }) else fail(r, "Search")
    }

    override suspend fun requestJoin(circleId: Int, message: String): Res<String> {
        val r = api.post("circles/$circleId/request/", JSONObject().put("message", message))
        return if (r.ok) Res.Ok(r.json?.optString("status", "pending") ?: "pending") else fail(r, "Asking to join")
    }

    override suspend fun cancelRequest(circleId: Int): Res<Unit> {
        val r = api.post("circles/$circleId/request/cancel/")
        return if (r.ok) Res.Ok(Unit) else fail(r, "Cancelling")
    }

    /** Network first; the saved copy when offline. */
    private suspend fun <T> cachedList(path: String, key: String, what: String, parse: (JSONObject) -> T): Res<List<T>> {
        val r = api.post(path)
        if (r.ok && r.json != null) {
            withContext(Dispatchers.IO) { db.putCache(key, r.json.toString()) }
            return Res.Ok(r.json.optJSONArray("requests").objects().map(parse))
        }
        if (r.code == null || r.code >= 500) {
            db.getCache(key)?.let { saved -> return Res.Ok(JSONObject(saved).optJSONArray("requests").objects().map(parse)) }
        }
        return fail(r, what)
    }

    override suspend fun myRequests(): Res<List<MyRequest>> =
        cachedList("circles/requests/mine/", "requests:mine", "Requests") { MyRequest.from(it) }

    override suspend fun joinRequests(circleId: Int): Res<List<JoinRequest>> =
        cachedList("circles/$circleId/requests/", "requests:$circleId", "Join requests") { JoinRequest.from(it) }

    override suspend fun decide(circleId: Int, requestId: Int, approve: Boolean): Res<Unit> {
        val r = api.post("circles/$circleId/requests/$requestId/${if (approve) "approve" else "decline"}/")
        return if (r.ok) Res.Ok(Unit) else fail(r, "This")
    }

    override suspend fun discoverable(circleId: Int): Boolean =
        db.getCache("circle:$circleId")?.let { runCatching { JSONObject(it).optJSONObject("circle")?.optBoolean("discoverable") }.getOrNull() } ?: false

    override suspend fun setDiscoverable(circleId: Int, on: Boolean, about: String?): Res<Unit> {
        val r = api.post("circles/$circleId/visibility/", JSONObject().put("discoverable", on).put("about", about ?: ""))
        return if (r.ok) Res.Ok(Unit) else fail(r, "Changing this")
    }
}
