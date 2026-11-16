package com.bas1minute.sync.diary

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/*
 * The diary: two kinds of entry, one timeline.
 *  - Milestone: a life event, dated whenever it actually happened (today or decades ago) — first day of
 *    school, a daughter's birth, the first job, a slam-book page, a memory from college.
 *  - Reflection: today only, one per day — how hard the person worked toward their goals, how satisfied
 *    they feel about today, and a short note. This is what closes the loop with Goals: Goals says what you
 *    want, the reflection says how it actually felt to chase it.
 * Plain Kotlin, no Android imports, so it's unit tested.
 */

enum class EntryType { Milestone, Reflection }

enum class Category(val id: String, val label: String, val emoji: String) {
    Family("family", "Family", "👨\u200d👩\u200d👧"),
    Birth("birth", "Birth", "👶"),
    School("school", "School", "🎒"),
    College("college", "College", "🎓"),
    Career("career", "Career", "💼"),
    Relationship("relationship", "Relationship", "💞"),
    Achievement("achievement", "Achievement", "🏆"),
    Travel("travel", "Travel", "✈️"),
    Other("other", "Other", "📔");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Other }
}

data class DiaryEntry(
    val id: Long,
    /** Stable id for syncing this entry to the website — never reused, even if the entry is deleted. */
    val uuid: String,
    val type: EntryType,
    val date: LocalDate,
    val title: String,
    val category: Category = Category.Other,
    val story: String = "",
    /** Reflection only: 1 (barely) to 5 (gave it everything). Null for a Milestone. */
    val effort: Int? = null,
    /** Reflection only: 1 (rough day) to 5 (great day). Null for a Milestone. */
    val satisfaction: Int? = null,
    val photoPath: String? = null,
    val createdAt: Long,
)

data class ReflectionStreak(val current: Int, val best: Int)

object DiaryLogic {
    const val MAX_TITLE = 80
    const val MAX_STORY = 2000
    const val MIN_RATING = 1
    const val MAX_RATING = 5

    fun clampRating(v: Int): Int = v.coerceIn(MIN_RATING, MAX_RATING)
    fun clampText(s: String, max: Int): String = s.trim().take(max)

    /**
     * Milestones whose month and day match [today], excluding this exact year (that's not an anniversary of
     * itself) and excluding anything dated in the future. Newest-first isn't meaningful here; sorted so the
     * most personally resonant (longest ago) comes first, as that's usually the one worth lingering on.
     */
    fun onThisDay(entries: List<DiaryEntry>, today: LocalDate): List<Pair<DiaryEntry, Int>> =
        entries.filter {
            it.type == EntryType.Milestone && it.date.year != today.year && !it.date.isAfter(today) &&
                it.date.monthValue == today.monthValue && it.date.dayOfMonth == today.dayOfMonth
        }.map { it to Period.between(it.date, today).years }
            .sortedByDescending { it.second }

    /** Years (or, for a recent one, months/days) since a milestone, worded naturally: "3 years ago", "2 months ago", "today". */
    fun agoLabel(date: LocalDate, today: LocalDate): String {
        if (date.isAfter(today)) return "upcoming"
        val p = Period.between(date, today)
        return when {
            p.years >= 1 -> "${p.years} year${if (p.years == 1) "" else "s"} ago"
            p.months >= 1 -> "${p.months} month${if (p.months == 1) "" else "s"} ago"
            p.days == 0 -> "today"
            else -> "${p.days} day${if (p.days == 1) "" else "s"} ago"
        }
    }

    /** Current and best run of consecutive days with a reflection, counting back from today. */
    fun reflectionStreak(reflectionDates: Set<LocalDate>, today: LocalDate): ReflectionStreak {
        var current = 0
        var day = today
        var isToday = true
        while (true) {
            val has = day in reflectionDates
            if (has) current++
            else if (!isToday) break
            isToday = false
            day = day.minusDays(1)
            if (ChronoUnit.DAYS.between(day, today) > 1000) break
        }
        if (reflectionDates.isEmpty()) return ReflectionStreak(0, 0)
        val sorted = reflectionDates.sorted()
        var best = 1; var run = 1
        for (i in 1 until sorted.size) {
            run = if (ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]) == 1L) run + 1 else 1
            best = maxOf(best, run)
        }
        return ReflectionStreak(current, maxOf(best, current))
    }

    /** Average effort and satisfaction over the given reflections (null if none have that rating yet). */
    fun averages(reflections: List<DiaryEntry>): Pair<Double?, Double?> {
        val effort = reflections.mapNotNull { it.effort }
        val satisfaction = reflections.mapNotNull { it.satisfaction }
        return (effort.takeIf { it.isNotEmpty() }?.average()) to (satisfaction.takeIf { it.isNotEmpty() }?.average())
    }

    /** One honest line reading the gap between effort and satisfaction — never invented, just the numbers said plainly. */
    fun effortVsSatisfactionNote(effortAvg: Double?, satisfactionAvg: Double?): String? {
        if (effortAvg == null || satisfactionAvg == null) return null
        val diff = effortAvg - satisfactionAvg
        return when {
            diff >= 1.0 -> "You've been putting in more effort than it's felt worth lately. Worth asking why."
            diff <= -1.0 -> "You're feeling good about days where the effort was lighter than you think. Fair — not everything has to be a grind."
            else -> "Effort and how it feels have been tracking closely."
        }
    }
}
