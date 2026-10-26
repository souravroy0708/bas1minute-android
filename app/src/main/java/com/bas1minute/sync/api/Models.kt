package com.bas1minute.sync.api

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it)?.let(f) }
}

data class Me(
    val name: String, val firstName: String, val mobile: String, val city: String,
    val stepGoal: Int, val burstGoal: Int, val screenGoal: Int, val memberSince: String,
) {
    companion object {
        fun from(j: JSONObject) = Me(
            j.optString("name"), j.optString("first_name"), j.optString("mobile"), j.optString("city"),
            j.optInt("step_goal", 7000), j.optInt("burst_goal", 4), j.optInt("screen_goal_minutes", 180),
            j.optString("member_since"),
        )
    }
}

data class ServerDay(
    val date: LocalDate, val steps: Int?, val stepsSource: String, val screen: Int?,
    val bursts: Int, val mindful: Int, val mood: Int?,
) {
    companion object {
        fun from(j: JSONObject) = ServerDay(
            LocalDate.parse(j.optString("date")), j.intOrNull("steps"), j.optString("steps_source"),
            j.intOrNull("screen_minutes"), j.optInt("bursts"), j.optInt("mindful_minutes"), j.intOrNull("mood"),
        )
    }
}

data class CheerIn(val from: String, val kind: String, val circle: String)

data class TodayData(val me: Me, val today: ServerDay, val week: List<ServerDay>, val cheers: List<CheerIn>, val circleCount: Int) {
    companion object {
        fun from(j: JSONObject) = TodayData(
            Me.from(j.getJSONObject("me")),
            ServerDay.from(j.getJSONObject("today")),
            j.optJSONArray("week").mapObjects { ServerDay.from(it) },
            j.optJSONArray("cheers").mapObjects { CheerIn(it.optString("from"), it.optString("kind"), it.optString("circle")) },
            j.optInt("circle_count"),
        )
    }
}

data class CircleSummary(
    val id: Int, val name: String, val kind: String, val kindLabel: String, val size: Int,
    val myRank: Int, val myPoints: Int, val teamPoints: Int, val teamTarget: Int, val teamPct: Int, val isOwner: Boolean,
) {
    companion object {
        fun from(j: JSONObject) = CircleSummary(
            j.optInt("id"), j.optString("name"), j.optString("kind"), j.optString("kind_label"), j.optInt("size"),
            j.optInt("my_rank"), j.optInt("my_points"), j.optInt("team_points"), j.optInt("team_target"),
            j.optInt("team_pct"), j.optBoolean("is_owner"),
        )
    }
}

data class CirclesData(val circles: List<CircleSummary>, val kinds: List<Pair<String, String>>) {
    companion object {
        fun from(j: JSONObject) = CirclesData(
            j.optJSONArray("circles").mapObjects { CircleSummary.from(it) },
            j.optJSONArray("kinds").mapObjects { it.optString("value") to it.optString("label") },
        )
    }
}

data class BoardRow(
    val userId: Int, val name: String, val isMe: Boolean, val isOwner: Boolean, val rank: Int, val points: Int,
    val pointsToday: Int, val stepsToday: Int, val todayPct: Int, val burstsToday: Int, val calmToday: Boolean,
    val screenToday: Int?, val cheered: Boolean, val nudged: Boolean,
)

data class FeedItem(val from: String, val to: String, val kind: String, val at: String)

data class CircleDetail(
    val id: Int, val name: String, val kind: String, val kindLabel: String,
    val isOwner: Boolean, val shareScreen: Boolean, val board: List<BoardRow>,
    val teamPoints: Int, val teamTarget: Int, val teamPct: Int, val daysLeft: Int, val pointsPerDay: Int,
    val feed: List<FeedItem>, val inviteUrl: String, val inviteCode: String, val whatsappUrl: String, val full: Boolean,
) {
    companion object {
        fun from(j: JSONObject): CircleDetail {
            val c = j.getJSONObject("circle")
            val me = j.getJSONObject("me")
            val team = j.getJSONObject("team")
            val invite = j.getJSONObject("invite")
            return CircleDetail(
                c.optInt("id"), c.optString("name"), c.optString("kind"), c.optString("kind_label"),
                me.optBoolean("is_owner"), me.optBoolean("share_screen_time"),
                j.optJSONArray("board").mapObjects {
                    BoardRow(
                        it.optInt("user_id"), it.optString("name"), it.optBoolean("is_me"), it.optBoolean("is_owner"),
                        it.optInt("rank"), it.optInt("points"), it.optInt("points_today"), it.optInt("steps_today"),
                        it.optInt("today_pct"), it.optInt("bursts_today"), it.optBoolean("calm_today"),
                        it.intOrNull("screen_today"), it.optBoolean("cheered"), it.optBoolean("nudged"),
                    )
                },
                team.optInt("points"), team.optInt("target"), team.optInt("pct"), j.optInt("days_left"), j.optInt("points_per_day", 3),
                j.optJSONArray("feed").mapObjects { FeedItem(it.optString("from"), it.optString("to"), it.optString("kind"), it.optString("at")) },
                invite.optString("url"), invite.optString("code"), invite.optString("whatsapp_url"), invite.optBoolean("full"),
            )
        }
    }
}
