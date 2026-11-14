package com.bas1minute.sync.detox

/**
 * Pure rules for Digital Detox. No Android imports, so everything here is unit-tested on the JVM.
 *
 * Two ideas:
 *  1. A detox session is a promise ("no apps except calls and messages for 2 hours"). It is KEPT if the
 *     timer runs out and no blocked app was really used; BROKEN if the user ends it early, revokes a
 *     permission the block needs, or used a blocked app for longer than the block screen's grace period.
 *  2. A clean hour is a finished clock hour in which no social-media app or browser was used at all,
 *     whether or not a detox session was running. "7 clean hours today" is the daily score.
 */

/** One foreground stretch of an app, from Android usage events. */
data class Interval(val pkg: String, val start: Long, val end: Long) {
    val durationMs: Long get() = end - start
    fun overlapWith(from: Long, to: Long): Long = (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0L)
}

/** A raw usage event: [resumed] = app came to the foreground, otherwise it left. */
data class FgEvent(val pkg: String, val timeMs: Long, val resumed: Boolean)

enum class Outcome { ACTIVE, KEPT, BROKEN }

enum class BreakReason { ENDED_EARLY, USED_BLOCKED_APP, PERMISSION_REVOKED }

data class DetoxSession(
    val id: Long,
    val startMs: Long,
    val plannedEndMs: Long,
    val endMs: Long? = null,
    val outcome: Outcome = Outcome.ACTIVE,
    val reason: BreakReason? = null,
    /** Times a blocked app was opened and the block screen stopped it. */
    val blockedAttempts: Int = 0,
    /** Package that broke the promise, when reason is USED_BLOCKED_APP. */
    val brokenBy: String? = null,
    /** The person's own reason for starting, typed before the timer began. Shown back to them if they try to end early. */
    val why: String = "",
) {
    val plannedMinutes: Int get() = ((plannedEndMs - startMs) / DetoxLogic.MINUTE).toInt()

    /** Minutes the promise actually held: the whole plan if kept, up to the break if broken. */
    fun keptMinutes(nowMs: Long): Int {
        val until = when (outcome) {
            Outcome.KEPT -> plannedEndMs
            Outcome.BROKEN -> endMs ?: nowMs
            Outcome.ACTIVE -> minOf(nowMs, plannedEndMs)
        }
        return ((until - startMs).coerceAtLeast(0L) / DetoxLogic.MINUTE).toInt()
    }

    fun remainingMs(nowMs: Long): Long = (plannedEndMs - nowMs).coerceAtLeast(0L)
}

enum class HourState { CLEAN, USED, IN_PROGRESS, FUTURE }

data class HourCell(val hour: Int, val state: HourState, val usedBy: String? = null)

data class DaySummary(
    val dayStartMs: Long,
    val cleanHours: Int,
    val countedHours: Int,
    val keptSessions: Int,
    val brokenSessions: Int,
    val detoxMinutesKept: Int,
)

data class WeekSummary(val kept: Int, val broken: Int, val minutesKept: Int, val keptDaysStreak: Int)

object DetoxLogic {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    /** A "focus day" needs at least this much kept, unbroken detox inside the window below. */
    const val FOCUS_MINUTES = 180
    const val WINDOW_START_HOUR = 10
    const val WINDOW_END_HOUR = 22
    /** Streak lengths that earn a badge, shortest first. */
    val BADGE_DAYS = listOf(3, 7, 21, 50, 100)
    /** Sharing is offered from the first badge onward, not before — nothing to brag about at day 1 or 2. */
    const val BADGE_DAYS_FIRST_SHAREABLE = 3

    /**
     * A blocked app is on screen for a moment before the block screen covers it. Anything shorter than this
     * is "caught", not "used", both for keeping a promise and for clean hours.
     */
    const val GRACE_MS = 5_000L

    val DURATION_CHOICES_MIN = listOf(5, 30, 60, 120, 180, 240)
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 12 * 60
    /** How many individual extra apps a member can add to their own block list, beyond the two categories. */
    const val MAX_BLOCKED_EXTRA = 30

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /**
     * The actual block/allow decision, as a pure function so it's fully unit tested without Android: a
     * member's own block-list, not a fixed wall. Fails OPEN — anything not explicitly chosen (by the member,
     * or by one of the two default categories they can turn off) stays usable. Only [hardAllowed] (dialer,
     * default messaging app, launcher, keyboard, Settings and other system essentials AppRules resolves from
     * the real phone) can never be blocked, so nobody can lock themselves out of calling for help.
     *
     * @param blockCategories which of "social" / "browsers" are turned on (both default on)
     * @param blockedApps individual packages the member added beyond the two categories
     * @param allowedOverride individual packages carved OUT of an on category (e.g. exempt one social app)
     * @param social the Social media / short video reference list (AppRules.SOCIAL)
     * @param browsers every installed browser (AppRules' resolved browser set)
     */
    fun isPackageBlocked(
        pkg: String,
        blockCategories: Set<String>,
        blockedApps: Set<String>,
        allowedOverride: Set<String>,
        hardAllowed: Set<String>,
        social: Set<String>,
        browsers: Set<String>,
    ): Boolean {
        if (pkg in hardAllowed) return false
        if (pkg in allowedOverride) return false
        if (pkg in blockedApps) return true
        if ("browsers" in blockCategories && pkg in browsers) return true
        if ("social" in blockCategories && pkg in social) return true
        return false
    }

    /**
     * Turns usage events into foreground intervals between [from] and [to].
     * An app already open at [from] (it left without a matching arrival) starts at [from];
     * an app still open at [to] ends at [to]. Opening another app ends the previous one, because only one
     * app is in the foreground at a time.
     */
    fun buildIntervals(events: List<FgEvent>, from: Long, to: Long): List<Interval> {
        val out = ArrayList<Interval>()
        var currentPkg: String? = null
        var currentStart = from
        var sawAny = false
        for (e in events.sortedBy { it.timeMs }) {
            if (e.timeMs < from || e.timeMs > to) continue
            if (e.resumed) {
                currentPkg?.let { if (e.timeMs > currentStart) out += Interval(it, currentStart, e.timeMs) }
                currentPkg = e.pkg
                currentStart = e.timeMs
            } else {
                if (currentPkg == e.pkg) {
                    if (e.timeMs > currentStart) out += Interval(e.pkg, currentStart, e.timeMs)
                    currentPkg = null
                } else if (!sawAny && currentPkg == null && e.timeMs > from) {
                    // It was already open when the window began.
                    out += Interval(e.pkg, from, e.timeMs)
                }
            }
            sawAny = true
        }
        currentPkg?.let { if (to > currentStart) out += Interval(it, currentStart, to) }
        return out
    }

    /**
     * Clock hours from [firstHour] (inclusive) to [lastHour] (exclusive) of the day starting at [dayStartMs]
     * (local midnight). Only distracting-app intervals should be passed in.
     */
    fun hourCells(
        dayStartMs: Long,
        firstHour: Int,
        lastHour: Int,
        nowMs: Long,
        distracting: List<Interval>,
    ): List<HourCell> = (firstHour until lastHour).map { h ->
        val from = dayStartMs + h * HOUR
        val to = from + HOUR
        val used = distracting.filter { it.overlapWith(from, to) >= GRACE_MS }
            .maxByOrNull { it.overlapWith(from, to) }
        when {
            nowMs < from -> HourCell(h, HourState.FUTURE)
            used != null -> HourCell(h, HourState.USED, used.pkg)
            nowMs < to -> HourCell(h, HourState.IN_PROGRESS)
            else -> HourCell(h, HourState.CLEAN)
        }
    }

    fun cleanHours(cells: List<HourCell>): Int = cells.count { it.state == HourState.CLEAN }

    /** First blocked-app interval inside the session that lasted long enough to count as real use. */
    fun firstViolation(session: DetoxSession, until: Long, blocked: List<Interval>): Interval? =
        blocked.filter { it.overlapWith(session.startMs, until) >= GRACE_MS }.minByOrNull { it.start }

    /** Final outcome when a session's time is up, checked against what Android actually recorded. */
    fun finish(session: DetoxSession, nowMs: Long, blocked: List<Interval>): DetoxSession {
        val end = minOf(nowMs, session.plannedEndMs)
        val v = firstViolation(session, end, blocked)
        return if (v != null) {
            session.copy(
                outcome = Outcome.BROKEN, reason = BreakReason.USED_BLOCKED_APP, brokenBy = v.pkg,
                endMs = maxOf(v.start, session.startMs),
            )
        } else {
            session.copy(outcome = Outcome.KEPT, endMs = session.plannedEndMs)
        }
    }

    fun breakNow(session: DetoxSession, nowMs: Long, reason: BreakReason, by: String? = null): DetoxSession =
        session.copy(outcome = Outcome.BROKEN, reason = reason, brokenBy = by, endMs = nowMs)

    fun daySummary(
        dayStartMs: Long, cells: List<HourCell>, sessions: List<DetoxSession>, nowMs: Long,
    ): DaySummary {
        val today = sessions.filter { it.startMs >= dayStartMs && it.startMs < dayStartMs + DAY }
        return DaySummary(
            dayStartMs = dayStartMs,
            cleanHours = cleanHours(cells),
            countedHours = cells.count { it.state == HourState.CLEAN || it.state == HourState.USED },
            keptSessions = today.count { it.outcome == Outcome.KEPT },
            brokenSessions = today.count { it.outcome == Outcome.BROKEN },
            detoxMinutesKept = today.filter { it.outcome != Outcome.BROKEN }.sumOf { it.keptMinutes(nowMs) },
        )
    }

    /**
     * Last 7 days ending today. The streak counts back from today over days that had at least one kept
     * session and no broken one; a day with no session ends the streak (today is skipped if empty so far).
     */
    fun weekSummary(sessions: List<DetoxSession>, todayStartMs: Long, nowMs: Long): WeekSummary {
        val weekStart = todayStartMs - 6 * DAY
        val week = sessions.filter { it.startMs >= weekStart && it.outcome != Outcome.ACTIVE }
        var streak = 0
        var day = todayStartMs
        while (true) {
            val s = sessions.filter { it.startMs >= day && it.startMs < day + DAY && it.outcome != Outcome.ACTIVE }
            val good = s.isNotEmpty() && s.none { it.outcome == Outcome.BROKEN }
            if (good) streak++ else if (!(day == todayStartMs && s.isEmpty())) break
            day -= DAY
            if (streak > 365) break
        }
        return WeekSummary(
            kept = week.count { it.outcome == Outcome.KEPT },
            broken = week.count { it.outcome == Outcome.BROKEN },
            minutesKept = week.filter { it.outcome == Outcome.KEPT }.sumOf { it.keptMinutes(nowMs) },
            keptDaysStreak = streak,
        )
    }

    fun formatDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatMinutes(min: Int): String = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    fun hourLabel(h: Int): String = when {
        h == 0 || h == 24 -> "12 AM"
        h < 12 -> "$h AM"
        h == 12 -> "12 PM"
        else -> "${h - 12} PM"
    }
}
