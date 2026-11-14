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
        "Walk or run" to "पैदल या दौड़", "Walk or run with GPS" to "GPS से पैदल या दौड़", "Walk, run or ride" to "पैदल, दौड़ या साइकिल", "Map with GPS" to "GPS से मैप", "Walk" to "पैदल", "Run" to "दौड़",
        "Ride" to "साइकिल", "Pause" to "रोकें", "Resume" to "फिर शुरू", "Finish" to "पूरा करें", "Your activities" to "आपकी गतिविधियाँ",
        "Auto-pause" to "अपने आप रुकें", "Voice every km" to "हर किमी पर आवाज़", "Show my name" to "मेरा नाम दिखाएँ",
        // Move tab
        "Move" to "चलें",
        // Website sign-in
        "Website" to "वेबसाइट", "Sign in on the website" to "वेबसाइट पर साइन इन करें",
        // Phone check
        "Phone check" to "फ़ोन जाँच",
        // Digital detox
        "Digital detox" to "डिजिटल डिटॉक्स", "Detox" to "डिटॉक्स", "Daily nudges" to "रोज़ के संदेश", "Focus" to "फ़ोकस", "left" to "बाकी", "Time left" to "बाकी समय",
        "Only calls and messages until the timer ends" to "टाइमर ख़त्म होने तक सिर्फ़ कॉल और मैसेज",
        "Only calls and messages, for as long as you promise" to "जितनी देर का वादा, उतनी देर सिर्फ़ कॉल और मैसेज",
        "Your promise" to "आपका वादा", "ends at" to "ख़त्म", "paused" to "रोका",
        "clean hours today" to "आज के साफ़ घंटे", "clean hours" to "साफ़ घंटे",
        "A clean hour is a full hour without social media or a browser." to "साफ़ घंटा यानी पूरा एक घंटा बिना सोशल मीडिया या ब्राउज़र के।",
        "Detox is on" to "डिटॉक्स चालू है", "End early" to "पहले ख़त्म करें", "Break your promise?" to "वादा तोड़ें?",
        "End anyway" to "फिर भी ख़त्म करें", "Keep my promise" to "वादा निभाऊँगा", "Start a detox" to "डिटॉक्स शुरू करें",
        "Usage access" to "उपयोग की अनुमति", "Display over other apps" to "दूसरे ऐप्स के ऊपर दिखाएँ",
        "How long?" to "कितनी देर?", "Or a custom time" to "या अपना समय", "Custom" to "अपना समय",
        "Always allowed" to "हमेशा खुले", "Allow up to 3 more apps (maps, UPI…)" to "3 और ऐप्स खुले रखें (मैप, UPI…)",
        "Change allowed apps" to "खुले ऐप्स बदलें", "I promise" to "मेरा वादा", "Also allow" to "ये भी खुले रखें",
        "with social media or browser" to "सोशल मीडिया या ब्राउज़र के साथ", "Detox kept today" to "आज निभाया डिटॉक्स",
        "Guilty" to "वादा टूटा", "guilty" to "वादे टूटे", "Clean" to "साफ़", "Used" to "इस्तेमाल", "Still to come" to "अभी बाकी",
        "This week" to "इस हफ़्ते", "promises kept" to "वादे निभाए", "day streak" to "दिन लगातार",
        "of detox kept this week" to "डिटॉक्स इस हफ़्ते", "Clean hours per day" to "हर दिन के साफ़ घंटे",
        "Your promises" to "आपके वादे", "Kept" to "निभाया", "Ended early" to "पहले ख़त्म किया",
        "used during detox" to "डिटॉक्स में खोला", "Permission turned off" to "अनुमति बंद की",
        "Resisted" to "रोका", "No slips" to "कोई चूक नहीं",
        // Goals
        "Your goals" to "आपके लक्ष्य", "Set your first goal" to "अपना पहला लक्ष्य बनाएँ",
        "Add a goal" to "लक्ष्य जोड़ें", "Add another goal" to "एक और लक्ष्य जोड़ें",
        "Remove this goal" to "यह लक्ष्य हटाएँ", "Or your own, for anything the phone can't measure:" to "या अपना खुद का, जो फ़ोन नहीं माप सकता:",
        "Write my own goal" to "अपना लक्ष्य लिखें",
        // Life goals
        "Life goals" to "जीवन के लक्ष्य", "Daily" to "रोज़ाना", "Short-term" to "अल्पकालिक", "Mid-term" to "मध्यकालिक", "Long-term" to "दीर्घकालिक",
        "Add" to "जोड़ें", "Mark achieved" to "पूरा हुआ चिह्नित करें", "Pause this goal" to "यह लक्ष्य रोकें", "Keep going" to "जारी रखें",
        "Add a photo — who or what this is for" to "एक फ़ोटो जोड़ें — यह किसके लिए है",
        "The plan" to "योजना", "If it gets hard" to "अगर मुश्किल हो तो", "Milestones" to "पड़ाव",
        "Why does this matter to you?" to "यह आपके लिए क्यों मायने रखता है?", "What's the plan?" to "योजना क्या है?",
        // Diary
        "Diary" to "डायरी", "Today's reflection" to "आज का सोच-विचार",
        "How hard did you work toward your goals today?" to "आज आपने अपने लक्ष्यों के लिए कितनी मेहनत की?",
        "How satisfied are you with today?" to "आज से आप कितने संतुष्ट हैं?",
        "Update today's reflection" to "आज का सोच-विचार अपडेट करें", "Save today's reflection" to "आज का सोच-विचार सेव करें",
        "Add a life milestone" to "जीवन का पड़ाव जोड़ें", "What kind of moment is this?" to "यह किस तरह का पल है?",
        "When did it happen?" to "यह कब हुआ?", "Tell the story" to "कहानी बताएँ", "Reflect" to "सोच-विचार करें",
        "Preview the pause screen" to "पॉज़ स्क्रीन पहले देखें", "Open battery settings" to "बैटरी सेटिंग खोलें",
        "What should it block?" to "यह क्या रोके?", "Social media & short video" to "सोशल मीडिया और शॉर्ट वीडियो",
        "Web browsers" to "वेब ब्राउज़र", "Review" to "देखें", "Block another app" to "एक और ऐप रोकें",
        "+ Block another app" to "+ एक और ऐप रोकें",
        // Common
        "Save" to "सेव करें", "Cancel" to "रद्द करें", "Back up now" to "अभी बैकअप लें",
    )

    fun t(en: String): String = if (current == "hi") HI[en] ?: en else en
}

/** Short alias used in screens: Text(t("Today")). */
fun t(en: String): String = Lang.t(en)
