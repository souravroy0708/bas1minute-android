package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.BuildConfig

/** The story pages and privacy notice, built into the app (no web pages). */
@Composable
fun AboutScreen(page: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Muted, style = MaterialTheme.typography.labelLarge) }
        when (page) {
            "idea" -> IdeaContent()
            "different" -> DifferentContent()
            "free" -> FreeContent()
            else -> PrivacyContent()
        }
        Spacer(Modifier.padding(8.dp))
    }
}

@Composable
private fun Title(title: String, lede: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Text(lede, style = MaterialTheme.typography.bodyLarge, color = Muted)
}

@Composable
private fun Points(items: List<Pair<String, String>>) {
    items.forEach { (head, body) ->
        Row {
            Text("•", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(head, style = MaterialTheme.typography.titleMedium)
                if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
    }
}

@Composable
private fun Stat(number: String, text: String, source: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
        Text(number, style = MaterialTheme.typography.displayMedium, color = Blue)
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Text(source, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun IdeaContent() {
    Title("Busy people don’t need an hour.",
        "Half of Indian adults don’t move enough, and most say time is the reason. So we stopped asking for time.")
    Stat("49.4%", "of Indian adults didn’t meet WHO activity levels in 2022, up from 22.3% in 2000.", "Lancet Global Health")
    Stat("56.4%", "of Indian adults surveyed named lack of time as their top reason for not exercising.", "Survey of Indian adults, PMC")
    Stat("136M", "Indians have prediabetes, a window where moving more can still change the outcome.", "ICMR-INDIAB")
    AppCard(title = "One daily target: 7K + 4") {
        Points(listOf(
            "7,000 steps a day" to "Spread through the day. Linked to a 47% lower risk of death from any cause compared with 2,000 steps (Lancet Public Health, 2025).",
            "4 one-minute bursts" to "Fast stairs, a brisk walk, 20 squats. Three to four a day are linked to up to 49% fewer heart-disease deaths (Nature Medicine).",
        ))
        Text("These are associations, not guarantees.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    AppCard(title = "Moments, not workout slots") {
        Points(listOf(
            "Morning gate" to "A burst before your social apps.",
            "Sitting alert" to "One minute on the stairs after an hour still.",
            "Walk & Talk" to "Steps on phone calls count.",
            "Chai burst" to "Twenty squats while the water boils.",
        ))
        Text("Never more than three nudges a day.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    AppCard(title = "Built to last past week three") {
        Points(listOf(
            "Start tiny" to "One burst a day in week one; the target grows with you.",
            "Family circles" to "Accountability from people who matter.",
            "Points against your own goals" to "So every age competes fairly.",
            "A minute of calm" to "Mind ka workout counts too.",
        ))
    }
    AppCard(title = "Designed for women from day one") {
        Points(listOf(
            "Home-first bursts" to "Every moment works without stepping out.",
            "Your own timing" to "Any time before midnight.",
            "Women-only circles" to "With people you trust.",
        ))
    }
}

@Composable
fun DifferentContent() {
    Title("Built for a day with no free hour.", "Other apps each do one piece well. We put the pieces together for busy Indian families.")
    AppCard(title = "What’s already out there") {
        Points(listOf(
            "Step-to-unlock apps" to "Lock social apps until you walk. We add bursts, daily moments and family circles.",
            "Exercise-to-unlock apps" to "Push-ups before scrolling. We build a whole day of small moments, on Android first.",
            "Government fitness app" to "Free tests and a tracker. We add the daily nudges that turn a test into a habit.",
            "Large fitness platforms" to "Classes, coaching and diet tracking. We’re for people who say they have no time.",
        ))
    }
    AppCard(title = "The combination is the point") {
        Points(listOf(
            "One-minute bursts" to "", "Built on moments you already have" to "", "Private family circles" to "",
            "Social media time check" to "See where the hours go, and what they could become.",
            "India-first, Hindi and English" to "", "Free core, runs on your phone’s own sensors" to "",
        ))
    }
    AppCard(title = "What we leave out, on purpose") {
        Points(listOf(
            "No diet or calorie tracking" to "Weight is optional, private and judgement-free.",
            "No body-shaming" to "No before-and-after photos, no guilt trips.",
            "No public leaderboards" to "Only the people you invite see your progress.",
            "No promised outcomes" to "We say “linked to”, never “will add years”.",
        ))
    }
}

@Composable
fun FreeContent() {
    Title("Free for as many people as possible.",
        "A price tag would stop the people who need this most, so the core app is free.")
    AppCard(title = "What’s free") {
        Points(listOf(
            "The 7K + 4 ring, bursts and calm" to "", "Steps, screen time and social media time check" to "",
            "Sleep, water and weight (optional)" to "", "Family and friends circles" to "",
            "Hindi and English" to "", "Gentle mode and safety features" to "",
        ))
    }
    AppCard(title = "Why we can keep it free") {
        Points(listOf(
            "Your phone does the work" to "Counting happens on sensors your phone already has.",
            "No AI bill" to "Every extra user costs very little to run.",
            "No paid installs" to "We grow through family invites and word of mouth.",
        ))
    }
    AppCard(title = "Who pays instead") {
        Points(listOf(
            "Workplaces" to "Wellness programmes for their staff.",
            "Brands" to "Sponsored seasonal challenges like IPL and Navratri.",
            "Housing societies and clinics" to "Group programmes, once the evidence is in.",
            "Families who want more" to "An optional family plan. The core never needs it.",
        ))
    }
    AppCard(title = "Our promises") {
        Points(listOf(
            "The core stays free." to "", "We don’t sell your data." to "Sponsors and employers only ever see totals.",
            "Your camera stays yours." to "", "Safety is never a paid extra." to "",
        ))
    }
}

/** Also shown by PrivacyActivity (Health Connect’s “privacy policy” link). */
@Composable
fun PrivacyContent() {
    Title("Your data stays with you", "No phone number, email or password. Just your name. We don’t sell your data.")
    AppCard(title = "Stored only on this phone") {
        Points(listOf(
            "Everything you see in the app" to "Steps, distance, screen time, sleep, water, weight, mood, bursts, calm and your 21 days are saved in this phone’s own storage.",
            "App names" to "Screen time is worked out on the phone. Which apps you used never leaves it.",
            "Your PIN" to "Saved on the phone in scrambled form. It’s a simple lock, not account security.",
        ))
    }
    AppCard(title = "Only if you choose to share") {
        Points(listOf(
            "Off by default" to "Profile → Your data → Share my steps and screen time.",
            "Exactly two numbers" to "Your daily steps and daily screen time. Nothing else, ever.",
            "Why share" to "Your circles can rank you, and you get a backup if you change phones.",
            "Stop anytime" to "Turn it off, and delete what you shared, in one tap.",
        ))
    }
    AppCard(title = "Circles") {
        Points(listOf(
            "Your name only" to "Circles need a name so friends know who you are. No other details are stored.",
            "Private members" to "If you don’t share, you can still join and see members who do. You show as Private.",
            "Cheers" to "Shabash and Chalo messages are stored so the other person can see them.",
        ))
    }
    AppCard(title = "Your choices") {
        Points(listOf(
            "Delete everything" to "Profile → Delete everything removes your data from this phone and your circle account from the server.",
            "Turn access off" to "In Android Settings or Health Connect, any time.",
        ))
    }
    AppCard(title = "Contact") {
        Text("Questions about your data: ${BuildConfig.SUPPORT_EMAIL}", style = MaterialTheme.typography.bodyMedium)
        Text("Bas 1 Minute supports everyday activity and isn’t medical advice.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
