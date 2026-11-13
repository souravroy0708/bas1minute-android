package com.bas1minute.sync.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class TourPage(val emoji: String, val tint: Color, val title: String, val body: String)

private val PAGES = listOf(
    TourPage("1", Haldi, "Welcome to Bas 1 Minute",
        "One app, four things worth doing every day: walk more, focus better, stay close to people who keep you honest, and work toward goals that actually matter. This short tour shows you where each one lives — it only takes a minute."),
    TourPage("👣", Violet, "Today: your rings",
        "Steps and screen time, at a glance. A 1-minute burst when you're short on time, 1-minute calm breathing when you need to settle down, and Log a day for anything the phone missed — sleep, water, weight, mood."),
    TourPage("🏃", Mint, "Move: walk, run or ride",
        "Start with one tap. GPS draws your route on a map — or switch it off and still get distance, time and pace from your steps, no location used at all. Either way: splits, pace, and a picture to share when you're done."),
    TourPage("🌏", Coral, "Circles: never alone in this",
        "You're in Bas 1 Minute India, the all-India step ranking, from day one — no joining required, and you can step out any time. Start your own private circle for family or friends, cheer each other on, and join monthly challenges."),
    TourPage("⏳", Haldi, "Detox: real focus time",
        "Promise yourself an hour or two of only calls and messages — everything else pauses. Build a streak of focused days, earn badges, and see exactly how many clean hours — no social media, no browser — you get each day."),
    TourPage("🎯", Blue, "Goals: the reason for all of it",
        "Daily goals turn your steps, sleep, screen time and detox into a streak worth keeping. Life goals go further: short, mid and long-term goals with your own why, a photo of who it's for, and a plan for when it gets hard."),
    TourPage("🔒", MintDark, "Your data stays on your phone",
        "No phone number, email or password — just a name. Sleep, water, mood, your routes and your goals never leave this phone unless you choose to share steps and screen time for the ranking. You're always in control, and you can change your mind later in Profile."),
)

/**
 * The mandatory first-run tour: one page per major part of the app, shown once right after setup. There's no
 * skip — the point is that a member starting out with Bas 1 Minute India, Detox and Goals already in the app
 * shouldn't have to stumble onto them by accident. Back only moves within the tour; the last page is the one
 * way out.
 */
@Composable
fun TourScreen(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val isLast = page == PAGES.lastIndex

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PAGES.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(if (i <= page) Haldi else Ink3),
                )
            }
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "tour",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { i ->
            val p = PAGES[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Badge(p.emoji, p.tint, size = 104)
                Spacer(Modifier.height(28.dp))
                Text(p.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(p.body, color = MutedOnDark, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
            BigButton(if (isLast) "Let's go" else "Next", dark = false, modifier = Modifier.fillMaxWidth()) {
                if (isLast) onFinish() else page++
            }
            if (page > 0) {
                TextButton(onClick = { page-- }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back", color = MutedOnDark)
                }
            }
        }
    }
}
