package com.bas1minute.sync.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class TourPage(val emoji: String, val tint: Color, val title: String, val body: String)

private val PAGES = listOf(
    TourPage("1", Haldi, "Welcome to Bas 1 Minute",
        "One app, four things worth doing every day: walk more, focus better, stay close to people who keep you honest, and work toward goals that actually matter. This short tour shows you where each one lives — it only takes a minute."),
    TourPage("👣", Violet, "Today: your rings",
        "Steps and screen time, at a glance. A 1-minute burst when you're short on time, 1-minute calm breathing when you need to settle down, and Log a day for anything the phone missed — sleep, water, weight, mood."),
    TourPage("🏃", Mint, "Move: walk, run or ride",
        "Start with one tap. GPS draws your route on a map — or switch it off and still get distance, time and pace from your steps, no location used at all. Either way: splits, pace, and a picture to share when you're done."),
    TourPage("⏳", Haldi, "Detox: real focus time",
        "Promise yourself an hour or two, free of social media and browsers by default — you choose exactly what's paused, so calls, messages, WhatsApp, music, UPI and maps keep working. Build a streak, earn badges, see your clean hours."),
    TourPage("🎯", Blue, "Goals & Diary: the reason for all of it",
        "Daily goals turn your steps, sleep, screen time and detox into a streak worth keeping. Life goals go further — short, mid and long-term, with your own why and a plan for when it gets hard. Diary holds the story: your milestones, and how each day actually felt."),
    TourPage("🌏", Coral, "Circles: never alone in this",
        "You're in Bas 1 Minute India, the all-India step ranking, from day one — no joining required, and you can step out any time. Find it from Profile: start your own private circle for family or friends, and join monthly challenges."),
    TourPage("🔒", MintDark, "Your data stays on your phone",
        "No phone number, email or password — just a name. Sleep, routes, goals and diary stay on this phone unless you turn on sharing or website sync yourself — both off by default. You're always in control, and you can change your mind later in Profile."),
)

/**
 * The mandatory first-run tour: one page per major part of the app, shown once right after setup. There's no
 * skip — the point is that a member starting out with Bas 1 Minute India, Detox and Goals already in the app
 * shouldn't have to stumble onto them by accident. Swipe or tap Next through every page; the last page is the
 * one way out. Each page gets a small animated hero (a sweeping ring, a spring-bounce badge, a gentle float)
 * rather than a static icon, and the last page ends with a small celebration — the kind of polish a member
 * only sees once, but which sets the tone for the rest of the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TourScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val isLast = pagerState.currentPage == PAGES.lastIndex

    // A light tick every time the page actually settles on a new index — swiped or tapped, same feel.
    LaunchedEffect(pagerState.currentPage) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    androidx.activity.compose.BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        TourProgress(pagerState, PAGES.size)

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            val p = PAGES[page]
            // Classic pager carousel feel: the page being swiped away shrinks and fades slightly.
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp)
                    .graphicsLayer {
                        val fraction = (1f - min(1f, kotlin.math.abs(pageOffset))).coerceIn(0f, 1f)
                        scaleX = 0.88f + 0.12f * fraction
                        scaleY = 0.88f + 0.12f * fraction
                        alpha = 0.35f + 0.65f * fraction
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TourHero(p.emoji, p.tint, visible = page == pagerState.currentPage, celebrate = page == PAGES.lastIndex && page == pagerState.currentPage)
                Spacer(Modifier.height(28.dp))
                Text(p.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(p.body, color = MutedOnDark, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
            BigButton(if (isLast) "Let's go" else "Next", dark = false, modifier = Modifier.fillMaxWidth()) {
                if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
            if (pagerState.currentPage > 0) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Back", color = MutedOnDark) }
            }
        }
    }
}

/** Top progress bar: each segment fills smoothly, and the active segment tracks the swipe itself — like a story. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TourProgress(pagerState: PagerState, count: Int) {
    Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val raw = (pagerState.currentPage + pagerState.currentPageOffsetFraction) - i
            val fraction = raw.coerceIn(0f, 1f)
            val animated by animateFloatAsState(fraction, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "progress$i")
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(Ink3)) {
                Box(Modifier.fillMaxWidth(animated.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(Haldi))
            }
        }
    }
}

/**
 * The hero: a soft pulsing glow, a ring that sweeps in when the page arrives, a spring-bounce badge with the
 * page's glyph, and a slow continuous float so nothing on screen ever looks frozen. On the final page, a
 * small outward burst of sparks marks the end of the tour.
 *
 * bob and glowPulse use InfiniteTransition.animateFloat which lives in androidx.compose.animation
 * (not animation-core). To avoid adding that dependency, they are driven by looping Animatable coroutines
 * instead — identical visual result, zero extra Gradle dependency.
 */
@Composable
private fun TourHero(glyph: String, tint: Color, visible: Boolean, celebrate: Boolean) {
    val sweep = remember(glyph) { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) { sweep.snapTo(0f); sweep.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    }
    val entrance by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "entrance",
    )

    // Continuous float — loops forever between -1f and 1f (replaces InfiniteTransition.animateFloat)
    val bob = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        while (true) {
            bob.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
            bob.animateTo(-1f, tween(1800, easing = FastOutSlowInEasing))
        }
    }

    // Glow pulse — loops forever between 0.85f and 1.15f (replaces InfiniteTransition.animateFloat)
    val glowPulse = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        while (true) {
            glowPulse.animateTo(1.15f, tween(1400, easing = FastOutSlowInEasing))
            glowPulse.animateTo(0.85f, tween(1400, easing = FastOutSlowInEasing))
        }
    }

    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Soft outer glow, breathing slowly. `center` here is DrawScope's own property — the centre
            // of this drawing area — not a property of Size itself, which has no such member.
            drawCircle(color = tint.copy(alpha = 0.18f), radius = this.size.minDimension / 2f * glowPulse.value)
            // Progress ring sweeping in on arrival
            val stroke = 5.dp.toPx()
            drawArc(
                color = tint, startAngle = -90f, sweepAngle = 360f * sweep.value, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            if (celebrate) drawCelebration(tint)
        }
        Box(
            Modifier.size(84.dp).graphicsLayer {
                scaleX = entrance; scaleY = entrance
                translationY = bob.value * 3.dp.toPx()
            }.clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** A dozen small sparks flung outward from the centre once, fading as they go — the tour's one-time "you made it." */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCelebration(tint: Color) {
    val center = this.center   // DrawScope's own centre-point property
    val particles = 10
    repeat(particles) { i ->
        val angle = (360f / particles) * i
        val seedRadius = this.size.minDimension / 2f * (0.75f + 0.25f * ((i % 3) / 2f))
        val rad = Math.toRadians(angle.toDouble())
        val x = center.x + (seedRadius * cos(rad)).toFloat()
        val y = center.y + (seedRadius * sin(rad)).toFloat()
        drawCircle(color = if (i % 2 == 0) tint else Color.White, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y), alpha = 0.85f)
    }
}
