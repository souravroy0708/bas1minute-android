package com.bas1minute.sync

/**
 * Every main feature, in order of importance, with the one line that explains it. One list drives the home grid
 * (order, long-press help, "New" dots) and the first-week tips notifications, so they can never disagree.
 */
enum class AppFeature(val key: String, val title: String, val what: String, val tipTitle: String, val tipText: String) {
    CIRCLES("circles", "Circles", "Walk together with family and friends: see each other’s steps and send cheers.",
        "Walk with your people 👨‍👩‍👧", "Make a circle with family or friends — see each other’s steps and cheer each other on."),
    GOALS("goals", "Goals", "Small daily goals and bigger life goals, ticked off automatically from your steps and screen time.",
        "Set one small goal today 🎯", "Pick a daily goal — like 7,000 steps or under 2h of social media — and watch it tick itself off."),
    WALK("walk", "Walk · Run", "Record a walk, run or ride: distance, time and pace, with or without GPS.",
        "Going for a walk? 🚶", "Tap Walk · Run before you head out to see your distance, time and pace — then share it."),
    DETOX("detox", "Detox", "Promise yourself time away from social media and browsers; calls and messages still work.",
        "Get an hour back ⏳", "Start a digital detox: pick how long, and Bas 1 Minute keeps you honest."),
    DIARY("diary", "Diary", "A one-minute reflection each evening, and the big moments of your life. Stays on this phone.",
        "One minute to reflect ✍️", "How did today go? Two taps in the Diary — and you can speak instead of typing."),
    BURST("burst", "Burst", "One-minute movement breaks you can do anywhere, a few times a day.",
        "Sitting for long? ⚡", "Try a 1-minute Burst — a quick movement break that wakes up your body."),
    SCREEN("screen", "Screen time", "How long you spent on social media and your phone, and when.",
        "Where did your phone time go? 📱", "Open Screen time to see which hours and apps took the most of your day."),
    HISTORY("history", "History", "Every day you’ve recorded — any week, month, year or all time — plus your records and badges.",
        "See your whole journey 📈", "History shows every day you’ve walked, your best day and week, and the badges you’ve earned."),
    CALM("calm", "Calm", "Guided slow breathing for a minute or more, when you feel stressed.",
        "Feeling stressed? 🧘", "Five slow breaths can change your mood. Try Calm for just one minute."),
    PHONE("phone", "Phone check", "Battery health, storage and apps that drain your phone.",
        "Is your phone healthy? 🔋", "Phone check looks at battery, storage and apps that drain your phone."),
    EXPORT("export", "Export my data", "All your data for any dates — send it to Claude or ChatGPT for a deeper look, or open it as a spreadsheet.",
        "Ask AI about your habits 🤖", "Export your steps, screen time and sleep, and ask Claude or ChatGPT what patterns it sees."),
    WATER("water", "Water", "Tap to add a glass (250 ml) whenever you drink water.", "", ""),
    LOG("log", "Log", "Note your mood, sleep or weight for the day.", "", "");

    companion object {
        fun of(key: String?): AppFeature? = entries.firstOrNull { it.key == key }
        /** Tips go in this order, one a day, skipping anything already used. */
        val TIPS = entries.filter { it.tipTitle.isNotEmpty() }
    }
}
