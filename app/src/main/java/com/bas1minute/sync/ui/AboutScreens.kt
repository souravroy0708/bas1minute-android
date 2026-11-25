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
            "who" -> WhoWeAreContent()
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
            Text("•", style = MaterialTheme.typography.titleMedium, color = TextMain)
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).padding(16.dp)) {
        Text(number, style = MaterialTheme.typography.displayMedium, color = Blue)
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Text(source, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun IdeaContent() {
    Title("Busy people don’t need an hour.",
        "Half of Indian adults don’t move enough, and most say time is the reason. So we stopped asking for time — and started building the rest of the day around it too: the focus to protect it, the goals to aim it at, and the people to keep you honest.")
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
        Text("A handful of nudges a day, never during a detox — turn any of them off in Profile.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    AppCard(title = "Then we built the other half: protecting your time") {
        Points(listOf(
            "Digital detox" to "Promise a time away from social media and browsers. We remind you if you slip, and keep an honest record.",
            "Schedules" to "Set it once — every night, every weekday — and detox starts and ends itself from then on.",
            "App Timers" to "A small daily budget per app. Spend it and we tell you — or let Android lock it for you, with no extra permission.",
            "A tap on the shoulder" to "Lost track of time on one app? A gentle nudge at 15, 30 and 45 minutes in a single sitting, never more than that.",
        ))
    }
    AppCard(title = "And something to aim all that time at") {
        Points(listOf(
            "Daily goals" to "Steps, sleep, screen time, or a custom habit of your own — a streak worth keeping.",
            "Life goals" to "Short, mid and long-term, with your own why and a plan for when it gets hard.",
            "Diary" to "Milestones and how each day actually felt — the story behind the numbers.",
        ))
    }
    AppCard(title = "Built to last past week three") {
        Points(listOf(
            "Start tiny" to "One burst a day in week one; the target grows with you.",
            "Family circles" to "Accountability from people who matter — plus an all-India ranking and seasonal competitions, if you want more.",
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
    Title("Built for a day with no free hour.",
        "Other apps each do one piece well — a step counter, a focus-mode blocker, a journal. We put all of it in one place for busy Indian families.")
    AppCard(title = "What’s already out there") {
        Points(listOf(
            "Step-to-unlock apps" to "Lock social apps until you walk. We add bursts, daily moments and family circles.",
            "Exercise-to-unlock apps" to "Push-ups before scrolling. We build a whole day of small moments, on Android first.",
            "Government fitness app" to "Free tests and a tracker. We add the daily nudges that turn a test into a habit.",
            "Large fitness platforms" to "Classes, coaching and diet tracking. We’re for people who say they have no time.",
            "Focus and blocker apps" to "Freedom, Forest, Opal and similar apps block well. We add the same blocking — but it’s the same app that’s already tracking your steps, your goals and your family circle, so focus time isn’t a separate subscription and a separate account to manage.",
        ))
    }
    AppCard(title = "The combination is the point") {
        Points(listOf(
            "One-minute bursts" to "", "Built on moments you already have" to "", "Private family circles" to "",
            "Social media time check" to "See where the hours go, and what they could become.",
            "Focus tools with almost no permissions" to "Digital detox, Schedules and App Timers need only Usage access — nothing over other apps, nothing running all the time.",
            "Daily goals, life goals and a diary" to "What you’re building toward, and the story of getting there.",
            "India-first, Hindi and English" to "", "Free core, runs on your phone’s own sensors" to "",
            "One app, one account" to "No separate subscription or sign-in for the focus tools.",
        ))
    }
    AppCard(title = "What we leave out, on purpose") {
        Points(listOf(
            "No diet or calorie tracking" to "Weight is optional, private and judgement-free.",
            "No body-shaming" to "No before-and-after photos, no guilt trips.",
            "No public leaderboards" to "Only the people you invite see your progress.",
            "No promised outcomes" to "We say “linked to”, never “will add years”.",
            "No guilt-trip blocking" to "A pause screen and a clear choice when something’s paused — never a shaming message.",
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
            "Sleep, water and weight (optional)" to "", "Family and friends circles, ranking and competitions" to "",
            "Digital detox, Schedules and App Timers" to "",
            "Daily goals, life goals and your diary" to "",
            "Hindi and English" to "", "Gentle mode and safety features" to "",
        ))
    }
    AppCard(title = "Why we can keep it free") {
        Points(listOf(
            "Your phone does the work" to "Counting happens on sensors your phone already has.",
            "The focus tools run here too" to "Detox and App Timers work everything out on your own phone — no server cost that grows with every member.",
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
            "The focus tools stay free too." to "Detox, Schedules and App Timers are not held back for a future paid tier.",
        ))
    }
}

/** Also shown by PrivacyActivity (Health Connect’s “privacy policy” link). */
@Composable
fun PrivacyContent() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Title("Your data stays with you", "No phone number, email or password — just a first name. No ads, no analytics, and we never sell data.")
    AppCard(title = "Kept only on this phone") {
        Points(listOf(
            "Your whole history" to "Steps, distance, screen time, sleep, water, weight, mood, bursts, calm, badges and records — saved in this phone’s own storage, for as long as you keep the app.",
            "Which apps you use" to "Screen time per app is worked out on the phone and never leaves it.",
            "Goals, life goals and diary" to "Stay on this phone, and are shielded from screenshots and Recent apps. Sync to the website only if you turn that on in Profile.",
            "Walks and runs" to "Location is used only while you record, with a notification showing. Routes stay on this phone.",
            "Digital detox" to "Uses Usage access to see which app is open. Strict mode (optional) also uses “Display over other apps” to show the countdown screen, only during a strict detox.",
            "Voice typing" to "Optional. The microphone is on only while the Listening window is open; your phone’s speech service turns it into text. Audio is never saved or sent to us.",
            "No backups we didn’t ask for" to "Android’s Google Drive backup and phone-to-phone copy are switched off for this app.",
        ))
    }
    AppCard(title = "What’s shared, and how to stop") {
        Points(listOf(
            "On from the start" to "Your first name, daily step total and daily social-media minutes — for Circles, the all-India ranking and monthly challenges, and as a backup. The setup screen says so, with switches.",
            "Never shared" to "App names, location, sleep, water, weight, diary, goals or detox details (unless you turn on goals/diary sync).",
            "Stop any time" to "Profile › Your data, or switch Online features off — then nothing leaves this phone. You can also delete what was shared.",
            "Your own export" to "“Export my data” makes a file on your phone for any dates. It goes only where you send it, such as Claude or ChatGPT.",
        ))
    }
    GhostButton("Read the full privacy policy") { com.bas1minute.sync.Links.open(ctx, com.bas1minute.sync.Links.PRIVACY) }
}

/** The two makers, how we work, and how to reach us — same as the website's "Who we are". */
@Composable
fun WhoWeAreContent() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Title("Two people in Hyderabad, building one app",
        "Bas 1 Minute isn’t a company with a marketing team. It’s the two of us, building the app we wanted for our own families.")
    Maker("SR", Haldi, "Sourav Roy", "tosourav6@gmail.com", null) { com.bas1minute.sync.Links.email(ctx, "tosourav6@gmail.com") }
    Maker("DS", Violet, "Dyutik Chaudhary Suryadevara", "dyutikchaudhary@gmail.com", "tech.dyutik.com" to "https://tech.dyutik.com") {
        com.bas1minute.sync.Links.email(ctx, "dyutikchaudhary@gmail.com")
    }
    AppCard(title = "How we work") {
        Points(listOf(
            "We build every part" to "The Android app, the server behind circles and competitions, and the website are designed, written and tested by the two of us.",
            "We answer the email" to "Write to either of us. A person reads it, not a bot, and account deletion requests are done within 7 days.",
            "We keep it small on purpose" to "No ads and no paid installs. Keeping costs low is how the app stays free for everyone.",
            "We keep your data on your phone" to "Your history lives on your phone. Only your name, daily steps and social-media minutes are shared, for circles — and you can switch that off.",
        ))
    }
    BigButton("Visit our website") { com.bas1minute.sync.Links.open(ctx, com.bas1minute.sync.Links.WEBSITE) }
    GhostButton("Tell a friend about Bas 1 Minute") { com.bas1minute.sync.Links.shareApp(ctx) }
}

@Composable
private fun Maker(initials: String, tone: Color, name: String, email: String, site: Pair<String, String>?, onEmail: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).padding(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.padding(end = 14.dp).clip(RoundedCornerShape(50)).background(tone)
            .padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(initials, style = MaterialTheme.typography.titleMedium, color = Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = TextMain)
            TextButton(onClick = onEmail, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(email, color = Blue, style = MaterialTheme.typography.bodyMedium)
            }
            site?.let { (label, url) ->
                TextButton(onClick = { com.bas1minute.sync.Links.open(ctx, url) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(label, color = Blue, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
