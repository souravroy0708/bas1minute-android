package com.bas1minute.sync

import java.time.LocalDate

/**
 * Quote of the day and the health wheel. All content is hardcoded and works offline.
 * Quotes are original Bas 1 Minute lines (no misattributed "famous" quotes).
 * Tips are general wellness habits, worded as "can/helps", never as medical promises.
 */
object Wellbeing {

    val QUOTES: List<String> = listOf(
        "Fit hone ke liye ghante nahi, minute chahiye.",
        "Small steps, every day, beat big plans that never start.",
        "Your body keeps a record of every walk you take. Give it good news today.",
        "One minute of movement is one minute more than zero.",
        "Energy isn’t found. It’s built, one habit at a time.",
        "Thoda chalo, thoda hasso, thoda aaram karo.",
        "The stairs are free. The gym membership isn’t.",
        "Health is the wealth that pays interest every single day.",
        "Don’t wait for Monday. Today is a perfectly good day to start.",
        "A tired mind scrolls. A rested mind chooses.",
        "Drink water before you feel thirsty, and rest before you feel broken.",
        "Progress, not perfection. Pehle kadam ki keemat sabse zyada hai.",
        "Your phone has a charger. You have sleep. Use it.",
        "Move a little after every meal. Your body will thank you quietly.",
        "Consistency is the secret ingredient no one sells.",
        "Be the reason your family takes the stairs today.",
        "Five slow breaths can change the next five hours.",
        "Sunlight in the morning, screens off at night.",
        "Strong isn’t a size. It’s showing up again tomorrow.",
        "Aaj ka kaam aaj, aaj ki walk bhi aaj.",
        "You don’t need more time. You need to use one minute better.",
        "Rest is not laziness. It’s how your body repairs itself.",
        "Every glass of water is a small favour to your future self.",
        "A short walk is the cheapest therapy there is.",
        "Look up from the screen. The world is waiting.",
        "Habits are votes for the person you want to become.",
        "Mann shant, tann swasth.",
        "Celebrate the small wins. They add up to big ones.",
        "Sleep early, wake fresh, win the morning.",
        "Bas 1 minute. That’s all it takes to begin.",
    )

    /** The same quote all day for everyone; changes at midnight; cycles through all 30 before repeating. */
    fun quoteOfDay(date: LocalDate = LocalDate.now()): String =
        QUOTES[Math.floorMod(date.toEpochDay(), QUOTES.size.toLong()).toInt()]

    data class Category(val id: String, val label: String, val emoji: String, val color: Long)
    data class Tip(val id: Int, val category: String, val text: String)

    /** Eight wheel segments, clockwise from the top. Colours follow the app palette. */
    val CATEGORIES: List<Category> = listOf(
        Category("sleep", "Sleep", "😴", 0xFF8F99FF),
        Category("food", "Food", "🥗", 0xFF5ED6A0),
        Category("move", "Move", "🚶", 0xFFFFC21A),
        Category("water", "Water", "💧", 0xFF7FC8F8),
        Category("mind", "Mind", "🧘", 0xFFFF8A65),
        Category("phone", "Phone", "📵", 0xFFB9C0FF),
        Category("morning", "Morning", "🌅", 0xFFFFD866),
        Category("habit", "Habit", "⭐", 0xFF9BE7C4),
    )

    val TIPS: List<Tip> = listOf(
        // Sleep
        Tip(1, "sleep", "Most adults need 7 or more hours of sleep. Count back from your alarm and set a bedtime."),
        Tip(2, "sleep", "Go to bed and wake up at about the same time every day, weekends too. A steady body clock makes sleep easier."),
        Tip(3, "sleep", "Keep your bedroom dark, cool and quiet. It tells your body it’s time to rest."),
        Tip(4, "sleep", "A short afternoon nap of 20 minutes can refresh you. Long or late naps can make night sleep harder."),
        // Food
        Tip(5, "food", "Finish dinner 2–3 hours before bed (around 7–8 pm if you sleep at 10–11). It gives your body time to digest and can help you sleep better."),
        Tip(6, "food", "Eat slowly and chew well. It’s easier on digestion and you notice when you’re full."),
        Tip(7, "food", "Fill half your plate with vegetables or salad. Colour on the plate usually means more fibre and vitamins."),
        Tip(8, "food", "Go easy on sugar in chai and sweet drinks. Small cuts every day add up over a year."),
        // Move
        Tip(9, "move", "A 10–15 minute walk after meals can help your body manage blood sugar."),
        Tip(10, "move", "Sitting for an hour? Stand up, stretch, or take the stairs for one minute."),
        Tip(11, "move", "Take the stairs for a floor or two. It’s a free one-minute burst."),
        Tip(12, "move", "Walk while you talk on the phone. Calls can quietly add thousands of steps."),
        // Water
        Tip(13, "water", "Start the day with a glass of water. Your body goes hours without any while you sleep."),
        Tip(14, "water", "Keep a water bottle where you can see it. You drink more when it’s in sight."),
        Tip(15, "water", "Feeling tired or getting a headache? Sometimes it’s just thirst. Try a glass of water first."),
        // Mind
        Tip(16, "mind", "Five slow breaths, in for 4 and out for 6, can help you calm down in under a minute."),
        Tip(17, "mind", "Talk to a friend or family member today. Feeling connected is good for both mind and body."),
        Tip(18, "mind", "Write down one thing you’re grateful for. Small habits like this can lift your mood."),
        Tip(19, "mind", "Feeling low for many days? Talk to someone you trust, or call Tele-MANAS on 14416 (free, 24x7)."),
        // Phone
        Tip(20, "phone", "Put your phone away 30–60 minutes before bed. Scrolling keeps your brain alert when it should be winding down."),
        Tip(21, "phone", "Charge your phone outside the bedroom. You’ll scroll less at night and sleep sooner."),
        Tip(22, "phone", "Try the 20-20-20 rule: every 20 minutes, look at something 20 feet away for 20 seconds. It rests your eyes."),
        Tip(23, "phone", "Don’t reach for your phone first thing. Give your morning 10 minutes before the feed takes over."),
        // Morning
        Tip(24, "morning", "Get some daylight in the morning, even 10 minutes on a balcony. It helps set your body clock."),
        Tip(25, "morning", "Wake up a little earlier and you get calm time before the day gets busy."),
        Tip(26, "morning", "Do one burst before breakfast: 20 squats or a quick stair climb. It wakes your body up."),
        // Habit
        Tip(27, "habit", "Attach a new habit to an old one: after brushing your teeth, do 10 squats."),
        Tip(28, "habit", "Missed a day? Don’t quit. Just don’t miss two in a row."),
        Tip(29, "habit", "Make it easy: keep your walking shoes by the door."),
        Tip(30, "habit", "Move with family. Habits stick better when someone is doing them with you."),
    )

    const val SPINS_PER_DAY = 3

    fun tipsIn(category: String) = TIPS.filter { it.category == category }
}

/**
 * Wheel geometry. Segment i is centred at i × 45° clockwise from the top; the pointer is at the top.
 * After the wheel turns clockwise by R degrees, the segment under the pointer is the one whose centre
 * plus R is a multiple of 360°.
 */
object WheelMath {
    const val SEGMENTS = 8
    const val SWEEP = 360f / SEGMENTS

    /** Which segment is under the pointer for a given total rotation. */
    fun segmentAt(rotation: Float): Int {
        val a = ((-rotation % 360f) + 360f) % 360f
        return (Math.round(a / SWEEP) % SEGMENTS)
    }

    /**
     * Where to stop so [segment] ends under the pointer: at least [turns] full turns past [current],
     * plus a small [jitter] inside the segment (keep it within ±(SWEEP / 2 − 4) so it never lands on a line).
     */
    fun targetFor(current: Float, segment: Int, turns: Int = 5, jitter: Float = 0f): Float {
        val now = ((current % 360f) + 360f) % 360f
        val wanted = ((360f - segment * SWEEP) % 360f + 360f) % 360f
        val extra = ((wanted - now) % 360f + 360f) % 360f
        return current + turns * 360f + extra + jitter
    }
}
