package com.bas1minute.sync.goal

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * Life goals: the things worth the hard work — not a daily habit check, but a destination with a reason.
 * Three honest horizons (the person picks one; the app doesn't pretend to know their life better than they
 * do), a free-text plan, a "why" with a photo of who or what it's for, and a WOOP-style obstacle/plan pair
 * (Gollwitzer & Oettingen's "mental contrasting with implementation intentions" — naming the obstacle and
 * deciding the response to it in advance is one of the better-evidenced ways to follow through on a goal).
 * Plain Kotlin, no Android imports, so it's unit tested.
 */

enum class Horizon(val id: String, val label: String, val minMonths: Int, val maxMonths: Int) {
    Short("short", "Short-term", 1, 3),
    Mid("mid", "Mid-term", 12, 36),
    Long("long", "Long-term", 60, 120);

    val rangeLabel: String get() = when (this) {
        Short -> "1–3 months"; Mid -> "1–3 years"; Long -> "5–10 years"
    }

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Short }
}

data class Milestone(
    val id: Long,
    /** Stable id for syncing this milestone to the website. */
    val uuid: String,
    val goalId: Long,
    val title: String,
    val done: Boolean,
    val doneAt: Long?,
    val order: Int,
)

data class LifeGoal(
    val id: Long,
    /** Stable id for syncing this life goal to the website — never reused, even if the goal is deleted. */
    val uuid: String,
    val horizon: Horizon,
    val title: String,
    /** The plan, in the person's own words: what they'll actually do. */
    val details: String = "",
    /** Why it matters — "for my daughter", "so Mum can see me graduate". Shown with the photo as the anchor. */
    val why: String = "",
    /** WOOP: the obstacle most likely to get in the way. */
    val obstacle: String = "",
    /** WOOP: "If [obstacle], then I will [this]." */
    val plan: String = "",
    /** Path to a photo on this phone — the person, or who this is for. Never required. */
    val photoPath: String? = null,
    val startedAt: Long,
    val targetDate: LocalDate,
    val achievedAt: Long? = null,
    val archivedAt: Long? = null,
) {
    val active: Boolean get() = achievedAt == null && archivedAt == null
    val startedDate: LocalDate get() = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
}

enum class Pace { Ahead, OnTrack, Behind, Unknown }

data class Progress(val daysElapsed: Int, val daysTotal: Int, val milestonesDone: Int, val milestonesTotal: Int) {
    val timeFraction: Float get() = if (daysTotal <= 0) 0f else (daysElapsed.toFloat() / daysTotal).coerceIn(0f, 1f)
    val milestoneFraction: Float get() = if (milestonesTotal <= 0) 0f else (milestonesDone.toFloat() / milestonesTotal).coerceIn(0f, 1f)

    /** Reading progress against time elapsed, only once there are milestones to compare against. */
    val pace: Pace get() = when {
        milestonesTotal == 0 -> Pace.Unknown
        milestoneFraction >= timeFraction + 0.1f -> Pace.Ahead
        milestoneFraction <= timeFraction - 0.2f -> Pace.Behind
        else -> Pace.OnTrack
    }
}

object LifeGoalLogic {
    const val MAX_TITLE = 80
    const val MAX_DETAILS = 1000
    const val MAX_WHY = 400
    const val MAX_OBSTACLE = 200
    const val MAX_PLAN = 200
    const val MAX_MILESTONE_TITLE = 100
    /** Per horizon, so the three categories each stay a short, real list, not an infinite backlog. */
    const val MAX_ACTIVE_PER_HORIZON = 3
    const val MAX_MILESTONES = 20

    fun suggestedTargetDate(horizon: Horizon, from: LocalDate = LocalDate.now()): LocalDate = when (horizon) {
        Horizon.Short -> from.plusMonths(2)
        Horizon.Mid -> from.plusMonths(24)
        Horizon.Long -> from.plusMonths(84)
    }

    /** Keeps a chosen target date honest to the horizon the person picked. */
    fun clampTargetDate(horizon: Horizon, from: LocalDate, target: LocalDate): LocalDate {
        val minD = from.plusMonths(horizon.minMonths.toLong())
        val maxD = from.plusMonths(horizon.maxMonths.toLong())
        return when {
            target.isBefore(minD) -> minD
            target.isAfter(maxD) -> maxD
            else -> target
        }
    }

    fun progress(goal: LifeGoal, milestones: List<Milestone>, today: LocalDate = LocalDate.now()): Progress {
        val total = ChronoUnit.DAYS.between(goal.startedDate, goal.targetDate).toInt().coerceAtLeast(1)
        val elapsed = ChronoUnit.DAYS.between(goal.startedDate, today).toInt().coerceIn(0, total)
        return Progress(elapsed, total, milestones.count { it.done }, milestones.size)
    }

    fun daysLeft(goal: LifeGoal, today: LocalDate = LocalDate.now()): Int = ChronoUnit.DAYS.between(today, goal.targetDate).toInt()

    /** "Day 45 of 90": a concrete sense of where today sits in the whole journey. */
    fun dayLabel(goal: LifeGoal, today: LocalDate = LocalDate.now()): String {
        val total = ChronoUnit.DAYS.between(goal.startedDate, goal.targetDate).toInt().coerceAtLeast(1)
        val day = (ChronoUnit.DAYS.between(goal.startedDate, today).toInt() + 1).coerceIn(1, total)
        return "Day $day of $total"
    }

    fun clampText(s: String, max: Int): String = s.trim().take(max)

    fun nextMilestoneOrder(existing: List<Milestone>): Int = (existing.maxOfOrNull { it.order } ?: -1) + 1
}
