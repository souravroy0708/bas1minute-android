package com.bas1minute.sync

import android.content.Context
import java.time.LocalDate
import java.util.Locale

/**
 * Fills an admin template with THIS phone's own numbers. Nothing personal comes from the server.
 * Returns null when the message isn't for this person (audience) or a placeholder has no value.
 */
object Personalizer {
    data class Values(
        val name: String, val steps: Long?, val goal: Int, val screen: Int?, val limit: Int,
        val rank: Int?, val circle: String?, val circleSize: Int?, val stepLengthCm: Int,
        val streak: Int = 0,
    )

    fun values(context: Context): Values {
        val prefs = Prefs(context)
        val db = LocalDb.get(context)
        val today = LocalDate.now()
        val row = db.day(today)
        val last = db.days(today.minusDays(29), today).associateBy { it.date }
        val steps30 = (29 downTo 0).map { last[today.minusDays(it.toLong())]?.steps }
        val best = runCatching { com.bas1minute.sync.api.Repo(context).cachedCircles() }.getOrNull()
            ?.circles?.minByOrNull { it.myRank }
        return Values(
            name = prefs.firstName.ifBlank { "dost" }, steps = row?.steps, goal = prefs.stepGoal,
            screen = row?.socialMinutes, limit = prefs.screenLimitMinutes,
            rank = best?.myRank, circle = best?.name, circleSize = best?.size, stepLengthCm = prefs.stepLengthCm,
            streak = com.bas1minute.sync.ui.TwentyOneStats.streak(steps30, prefs.stepGoal),
        )
    }

    fun matches(audience: String, v: Values): Boolean = when (audience) {
        "below_steps" -> v.steps != null && v.steps < v.goal
        "goal_done" -> v.steps != null && v.steps >= v.goal
        "over_screen" -> v.screen != null && v.screen > v.limit
        "under_screen" -> v.screen != null && v.screen <= v.limit
        "in_circle" -> v.circle != null
        else -> true
    }

    private fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

    fun fill(template: String, v: Values): String? {
        val nf = java.text.NumberFormat.getIntegerInstance(Locale("en", "IN"))
        val map = mapOf(
            "name" to v.name,
            "steps" to v.steps?.let { nf.format(it) },
            "goal" to nf.format(v.goal),
            "steps_left" to v.steps?.let { nf.format((v.goal - it).coerceAtLeast(0)) },
            "km" to v.steps?.let { Distance.format(Distance.km(it, v.stepLengthCm)) },
            "screen" to v.screen?.let { hm(it) },
            "limit" to hm(v.limit),
            // Screen time projected over a year, in full days (shown only when there's something to say)
            "year_days" to v.screen?.let { (it * 365 / 60 / 24).takeIf { d -> d > 0 }?.toString() },
            // Only shown when there's a streak to celebrate
            "streak" to v.streak.takeIf { it > 0 }?.toString(),
            "rank" to v.rank?.toString(),
            "circle" to v.circle,
            "circle_size" to v.circleSize?.toString(),
        )
        var out = template
        for ((key, value) in map) {
            if (out.contains("{$key}")) {
                if (value == null) return null   // don't show "You’re #— in —"
                out = out.replace("{$key}", value)
            }
        }
        return out
    }
}
