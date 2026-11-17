package com.bas1minute.sync

/** How spoken text joins what's already typed. Plain Kotlin, no Android imports, so it's unit tested. */
object VoiceText {
    /**
     * Adds [heard] after [existing] with one space between, never replacing what was typed. Speech engines return
     * lower-case text, so the first letter is capitalised when it starts the box or a new sentence.
     */
    fun append(existing: String, heard: String): String {
        val spoken = heard.trim()
        if (spoken.isEmpty()) return existing
        val before = existing.trimEnd()
        val startsSentence = before.isEmpty() || before.last() in ".!?\n।"
        val piece = if (startsSentence) spoken.replaceFirstChar { it.uppercaseChar() } else spoken
        return if (before.isEmpty()) piece else "$before $piece"
    }
}
