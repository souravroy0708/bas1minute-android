package com.bas1minute.sync.nudge

import com.bas1minute.sync.detox.Interval

/*
 * Catches a long, unbroken stretch on one distracting app (YouTube, Instagram, Facebook, any browser — see
 * AppRules.isDistracting) and taps the person on the shoulder every 15 minutes it continues — not to shame
 * them off their phone, but for the moment someone looks up and realises "oh, that was 40 minutes" to arrive
 * sooner, with something concrete to redirect to. Plain Kotlin, no Android imports, so it's unit tested.
 *
 * Product rules
 *  - A glance away under GAP_TOLERANCE_MS (a notification, a quick app switch and back) doesn't reset the
 *    clock — real sessions have micro-interruptions; treating every one as "a new session" would make this
 *    useless on real phones.
 *  - Three nudges per continuous session, then silence for the rest of it. By 45 minutes the person knows;
 *    repeating the same message past that stops being a nudge and starts being noise.
 *  - Each nudge says something slightly different, ending with the most direct one (a straight offer to
 *    start a detox) rather than three copies of the same line.
 */
object SessionGuard {
    const val SESSION_THRESHOLD_MS = 15 * 60_000L
    const val GAP_TOLERANCE_MS = 90_000L
    const val MAX_NUDGES_PER_SESSION = 3

    /** Same-app intervals close enough together to be one real session, not three separate ones. */
    fun mergeContinuous(intervals: List<Interval>, pkg: String, tolerance: Long = GAP_TOLERANCE_MS): List<Interval> {
        val same = intervals.filter { it.pkg == pkg }.sortedBy { it.start }
        val out = ArrayList<Interval>()
        for (iv in same) {
            val last = out.lastOrNull()
            if (last != null && iv.start - last.end <= tolerance) {
                out[out.lastIndex] = Interval(pkg, last.start, maxOf(last.end, iv.end))
            } else {
                out += iv
            }
        }
        return out
    }

    /**
     * How long, right now, the person has been continuously on [pkg] — 0 if they're not on it right now, or
     * their last moment on it is already further back than [tolerance] (a real break, not a glance away).
     * [intervals] should cover a window well before the session could plausibly have started.
     */
    fun currentSessionMs(intervals: List<Interval>, pkg: String, now: Long, tolerance: Long = GAP_TOLERANCE_MS): Long {
        val merged = mergeContinuous(intervals, pkg, tolerance)
        val last = merged.lastOrNull() ?: return 0L
        if (now - last.end > tolerance) return 0L
        return now - last.start
    }

    /** A stable key for "this particular continuous session" — changes the moment the session actually ends. */
    fun sessionKey(pkg: String, sessionStartMs: Long): String = "$pkg:$sessionStartMs"

    /** How many 15-minute nudges a session this long has earned, capped at [MAX_NUDGES_PER_SESSION]. */
    fun dueNudgeCount(sessionMs: Long): Int =
        (sessionMs / SESSION_THRESHOLD_MS).toInt().coerceAtMost(MAX_NUDGES_PER_SESSION)

    /** The message for the Nth nudge (1-indexed) in a session on [appLabel]. */
    fun message(appLabel: String, nudgeNumber: Int, name: String): Nudge = when (nudgeNumber) {
        1 -> Nudge(
            "15 minutes on $appLabel",
            "Not a big deal — just so you notice. What were you trying to do before you opened it?",
            NudgeTarget.Today,
        )
        2 -> Nudge(
            "30 minutes now, ${name.ifBlank { "dost" }}",
            "Want to put a hard stop on it? A 1-hour detox pauses $appLabel and every other distracting app.",
            NudgeTarget.Detox,
        )
        else -> Nudge(
            "45 minutes on $appLabel",
            "Last check-in for this one. A short walk or your goals list might feel better right now than more scrolling.",
            NudgeTarget.Move,
        )
    }
}
