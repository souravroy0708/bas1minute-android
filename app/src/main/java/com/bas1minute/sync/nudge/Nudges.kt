package com.bas1minute.sync.nudge

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

/*
 * Three nudges a day, written on the phone from the person's own numbers. Plain Kotlin, unit tested.
 *
 * Product rules
 *  - One job per message: walk now, put the phone down now, or celebrate. Never a nag without a next step.
 *  - The next step is always small and concrete: "a 15-minute walk", "a 1-hour detox". Tapping opens exactly that.
 *  - Screen time beats steps when it's clearly over the limit, because that's the habit Bas 1 Minute is for.
 *  - Silence when it would be noise: during a detox, and when there's nothing true to say.
 */

enum class NudgeSlot(val hour: Int, val minute: Int, val id: String) {
    Morning(8, 30, "morning"), Afternoon(13, 30, "afternoon"), Evening(19, 30, "evening");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Morning }
}

/** Where tapping the notification goes. */
enum class NudgeTarget(val open: String) { Today("today"), Move("move"), Detox("detox") }

data class NudgeState(
    val slot: NudgeSlot,
    val name: String,
    val steps: Long?,
    val goal: Int,
    /** Minutes on social apps (and browsers) today / yesterday; null without Usage access. */
    val socialToday: Int?,
    val socialYesterday: Int?,
    val socialLimit: Int,
    val detoxActive: Boolean,
    val detoxKeptToday: Boolean,
    val streak: Int,
    val stepLengthCm: Int,
    /** Picks between equally good wordings so the same message doesn't repeat every day. */
    val dayOfYear: Int,
)

data class Nudge(val title: String, val body: String, val target: NudgeTarget)

object Nudges {
    private val nf: NumberFormat = NumberFormat.getIntegerInstance(Locale("en", "IN"))
    /** A brisk walk is about 100 steps a minute. */
    private const val STEPS_PER_MINUTE = 100

    fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

    private fun walkMinutes(steps: Long) = (ceil(steps / STEPS_PER_MINUTE.toDouble()).toInt()).coerceAtLeast(5).let { ((it + 4) / 5) * 5 }

    private fun km(steps: Long, cm: Int) = String.format(Locale.US, "%.1f km", steps * cm / 100_000.0)

    private fun <T> pick(day: Int, vararg options: T): T = options[Math.floorMod(day, options.size)]

    /** The message for this slot, or null when nothing should be sent. */
    fun pick(s: NudgeState): Nudge? {
        if (s.detoxActive) return null                       // never interrupt a detox
        val left = s.steps?.let { (s.goal - it).coerceAtLeast(0) }
        val overLimit = s.socialToday?.let { it > s.socialLimit } == true
        return when (s.slot) {
            NudgeSlot.Morning -> morning(s)
            NudgeSlot.Afternoon -> afternoon(s, left, overLimit)
            NudgeSlot.Evening -> evening(s, left, overLimit)
        }
    }

    private fun morning(s: NudgeState): Nudge {
        val y = s.socialYesterday
        if (y != null && y > s.socialLimit + 30) return Nudge(
            "Win back an hour today",
            "Yesterday: ${hm(y)} on social apps. Start the day with a 1-hour digital detox: only calls and messages.",
            NudgeTarget.Detox,
        )
        if (s.streak >= 2) return Nudge(
            "Day ${s.streak + 1}, ${s.name}! 🔥",
            "Keep your streak alive: ${nf.format(s.goal)} steps today. A 1-minute burst is a good start.",
            NudgeTarget.Today,
        )
        return pick(s.dayOfYear,
            Nudge("Good morning, ${s.name}", "Today’s goal: ${nf.format(s.goal)} steps. A 10-minute walk after breakfast is 1,000 of them.", NudgeTarget.Move),
            Nudge("Bas 1 minute, ${s.name}", "Before the phone takes your morning: 20 squats or a fast flight of stairs. Then ${nf.format(s.goal)} steps to go.", NudgeTarget.Today),
            Nudge("Focus first, scroll later", "Try a 1-hour digital detox this morning. Calls and messages still come through.", NudgeTarget.Detox),
        )
    }

    private fun afternoon(s: NudgeState, left: Long?, overLimit: Boolean): Nudge {
        val social = s.socialToday
        if (overLimit && social != null) return Nudge(
            "${hm(social)} on social apps already",
            "That’s past your ${hm(s.socialLimit)} limit. Take it back: start a 1-hour detox now.",
            NudgeTarget.Detox,
        )
        if (left == null) return Nudge("Halfway through the day", "Stand up for a 1-minute burst. Your body will thank you this evening.", NudgeTarget.Today)
        if (left == 0L) return Nudge(
            "Goal done before evening. Shabash! 🎉",
            "Make it a focused day too: a 1-hour digital detox this afternoon.",
            NudgeTarget.Detox,
        )
        val steps = s.steps ?: 0
        return if (steps < s.goal * 0.4) Nudge(
            "${nf.format(left)} steps to go",
            "A ${walkMinutes(left / 2)}-minute walk after lunch gets you halfway there.",
            NudgeTarget.Move,
        ) else Nudge(
            "On track: ${nf.format(steps)} of ${nf.format(s.goal)}",
            "${nf.format(left)} to go. A 1-minute burst now keeps the momentum.",
            NudgeTarget.Today,
        )
    }

    private fun evening(s: NudgeState, left: Long?, overLimit: Boolean): Nudge {
        if (left == 0L) return if (s.detoxKeptToday) Nudge(
            "Goal and detox done. What a day, ${s.name}!",
            "You moved and you focused. Same again tomorrow?",
            NudgeTarget.Today,
        ) else Nudge(
            "Goal done today. Shabash, ${s.name}! 🎉",
            "Now give your eyes a break: a 1-hour detox before bed helps you sleep better.",
            NudgeTarget.Detox,
        )
        if (left != null) return Nudge(
            "${nf.format(left)} steps left (${km(left, s.stepLengthCm)})",
            "A ${walkMinutes(left)}-minute walk after dinner and today’s goal is done." +
                if (overLimit) " Leave the phone at home." else "",
            NudgeTarget.Move,
        )
        val social = s.socialToday
        if (overLimit && social != null) return Nudge(
            "${hm(social)} on social apps today",
            "Put the phone down for the last hour of the day: start a detox.",
            NudgeTarget.Detox,
        )
        return Nudge("How did today go, ${s.name}?", "See your steps, screen time and clean hours.", NudgeTarget.Today)
    }
}
