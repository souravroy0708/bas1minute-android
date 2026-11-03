package com.bas1minute.sync.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Hindi + English. t("English") returns the Hindi text when Hindi is chosen, otherwise the English.
 * Anything not translated yet falls back to English, so nothing is ever blank. Reading Lang.current
 * inside a composable makes it update instantly when the language changes.
 */
object Lang {
    var current by mutableStateOf("en")

    private val HI = mapOf(
        // Navigation
        "Today" to "आज", "Insights" to "जानकारी", "Circles" to "सर्कल", "Spin" to "स्पिन", "Profile" to "प्रोफ़ाइल",
        // Home
        "Namaste" to "नमस्ते", "Steps" to "कदम", "Screen time" to "स्क्रीन टाइम", "Steps left" to "बाकी कदम",
        "Burst" to "बर्स्ट", "Calm" to "शांति", "Water" to "पानी", "Log" to "लिखें", "a day" to "दिन का",
        "Finish setting up" to "सेटअप पूरा करें", "Step counting" to "कदमों की गिनती", "Allow" to "अनुमति दें",
        "Open" to "खोलें", "Get it" to "पाएँ", "On" to "चालू",
        "Two quick permissions and Bas 1 Minute counts everything for you. Everything stays on this phone." to
            "दो छोटी अनुमतियाँ, फिर बस 1 मिनट सब अपने आप गिनेगा। सारा डेटा इसी फ़ोन में रहता है।",
        "Your two rings" to "आपके दो रिंग", "Got it" to "समझ गए",
        "How are you feeling?" to "आप कैसा महसूस कर रहे हैं?", "Only you see this" to "सिर्फ़ आप देख सकते हैं",
        "Spin the health wheel" to "हेल्थ व्हील घुमाएँ", "Today’s thought" to "आज का विचार",
        // 21 days
        "Your 21 days" to "आपके 21 दिन", "Distance" to "दूरी", "Goal days" to "लक्ष्य वाले दिन",
        "Show chart and details" to "चार्ट और विवरण देखें", "Hide details" to "विवरण छिपाएँ", "Share" to "शेयर करें",
        "Start a streak today" to "आज से स्ट्रीक शुरू करें",
        "Your first day fills in tonight. Just walk as usual; the phone counts for you." to
            "आपका पहला दिन आज रात तक भर जाएगा। बस हमेशा की तरह चलिए, फ़ोन अपने आप गिनेगा।",
        // Celebration
        "Goal done!" to "लक्ष्य पूरा!", "Share it" to "शेयर करें", "Close" to "बंद करें",
        // Setup and lock
        "Get started" to "शुरू करें", "What should we call you?" to "हम आपको क्या बुलाएँ?", "Your name" to "आपका नाम",
        "Start" to "शुरू करें", "Your data, your phone." to "आपका डेटा, आपका फ़ोन।",
        "Enter your PIN" to "अपना PIN डालें", "Forgot PIN? Set a new one" to "PIN भूल गए? नया बनाएँ",
        // Profile
        "Goals" to "लक्ष्य", "Privacy and security" to "निजता और सुरक्षा", "Notifications" to "सूचनाएँ",
        "This phone" to "यह फ़ोन", "About" to "जानकारी", "Danger zone" to "सावधान",
        "Appearance" to "दिखावट", "Language" to "भाषा", "Theme" to "थीम",
        "System" to "सिस्टम", "Light" to "लाइट", "Dark" to "डार्क",
        "Add photo" to "फ़ोटो जोड़ें", "Change photo" to "फ़ोटो बदलें", "Remove" to "हटाएँ", "Bio" to "परिचय",
        "Your data" to "आपका डेटा", "App lock" to "ऐप लॉक", "Motivation notifications" to "प्रेरणा सूचनाएँ",
        // GPS walk / run
        "Walk or run" to "पैदल या दौड़", "Walk or run with GPS" to "GPS से पैदल या दौड़", "Walk" to "पैदल", "Run" to "दौड़",
        "Ride" to "साइकिल", "Pause" to "रोकें", "Resume" to "फिर शुरू", "Finish" to "पूरा करें", "Your activities" to "आपकी गतिविधियाँ",
        "Auto-pause" to "अपने आप रुकें", "Voice every km" to "हर किमी पर आवाज़", "Show my name" to "मेरा नाम दिखाएँ",
        // Move tab
        "Move" to "चलें",
        // Website sign-in
        "Website" to "वेबसाइट", "Sign in on the website" to "वेबसाइट पर साइन इन करें",
        // Phone check
        "Phone check" to "फ़ोन जाँच",
        // Common
        "Save" to "सेव करें", "Cancel" to "रद्द करें", "Back up now" to "अभी बैकअप लें",
    )

    fun t(en: String): String = if (current == "hi") HI[en] ?: en else en
}

/** Short alias used in screens: Text(t("Today")). */
fun t(en: String): String = Lang.t(en)
