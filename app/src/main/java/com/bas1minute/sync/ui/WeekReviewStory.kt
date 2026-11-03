package com.bas1minute.sync.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bas1minute.sync.AppController
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.WeekReview
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

/** Home card on Sunday and Monday until opened. */
@Composable
fun WeekReviewCard(app: AppController) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF3446C8), Color(0xFF8F99FF))))
            .clickable(onClickLabel = "Open week in review") { app.openReview() }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Your week in review is ready", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Steps, best day, streak and screen time. 30 seconds.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE9ECFF))
        }
        SmallPill("Open") { app.openReview() }
    }
}

/** Swipeable story: 5 pages, then share. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekReviewStory(app: AppController) {
    if (!app.reviewOpen) return
    val context = LocalContext.current
    val r = remember { WeekReview.build(LocalDb.get(context), app.prefs) }
    val n = remember { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
    val pager = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val close = { app.reviewOpen = false }

    fun change(now: Long, before: Long): String = when {
        before <= 0 -> "Your first full week. Great start."
        now >= before -> "${n.format(now - before)} more than the week before ↑"
        else -> "${n.format(before - now)} fewer than the week before. This week can be better."
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Color(0xFF232963))))) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> Page("This week you walked", n.format(r.steps), "steps · ${r.km}", change(r.steps, r.prevSteps))
                        1 -> Page("Your best day", r.bestDay?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.ENGLISH) ?: "—",
                            if (r.bestSteps > 0) "${n.format(r.bestSteps)} steps" else "No steps yet", "Do it again this week.")
                        2 -> Page("Goal reached on", "${r.goalDays} of 7 days",
                            if (r.streak > 0) "🔥 ${r.streak}-day streak going" else "Start a streak tomorrow",
                            "${r.bursts} bursts and ${r.calm} calm minutes too.")
                        3 -> Page("Screen time", r.screenAvg?.let { hm(it) } ?: "—", "a day on social, messaging and browser apps",
                            when {
                                r.screenAvg == null -> "Allow screen time in Profile to see this."
                                r.prevScreenAvg == null -> "We’ll compare with next week."
                                r.screenAvg <= r.prevScreenAvg -> "${hm(r.prevScreenAvg - r.screenAvg)} less a day than the week before. Shabash!"
                                else -> "${hm(r.screenAvg - r.prevScreenAvg)} more a day than the week before."
                            })
                        else -> {
                            Page("That’s your week", "Bas 1 Minute", "small steps, every day", "Share your week with family and friends.")
                            Spacer(Modifier.height(20.dp))
                            BigButton("Share my week", dark = false) { close(); app.shareKind = "streak" }
                        }
                    }
                }
            }
            // Progress bars like a story + tap to advance
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(5) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(if (i <= pager.currentPage) Haldi else Ink3))
                }
            }
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = close) { Text("Close", color = MutedOnDark) }
                if (pager.currentPage < 4) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text("Next →", color = Haldi)
                }
            }
        }
    }
}

@Composable
private fun Page(top: String, big: String, sub: String, note: String) {
    Text(top, style = MaterialTheme.typography.titleLarge, color = MutedOnDark, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(big, style = MaterialTheme.typography.displayLarge, color = Haldi, textAlign = TextAlign.Center)
    Text(sub, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Text(note, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE9ECFF), textAlign = TextAlign.Center)
}
