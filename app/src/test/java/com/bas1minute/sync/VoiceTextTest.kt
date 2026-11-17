package com.bas1minute.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTextTest {
    @Test fun emptyBox_takesSpokenText_capitalised() =
        assertEquals("Went for a walk with papa", VoiceText.append("", "went for a walk with papa"))

    @Test fun addsAfterTypedText_neverReplaces() =
        assertEquals("Morning was good and evening too", VoiceText.append("Morning was good", "and evening too"))

    @Test fun newSentence_isCapitalised() =
        assertEquals("Long day. Still finished the report", VoiceText.append("Long day. ", "still finished the report"))

    @Test fun hindiFullStop_startsSentence() =
        assertEquals("आज अच्छा था। कल फिर", VoiceText.append("आज अच्छा था।", "कल फिर"))

    @Test fun blankSpeech_changesNothing() =
        assertEquals("Keep this", VoiceText.append("Keep this", "   "))
}
