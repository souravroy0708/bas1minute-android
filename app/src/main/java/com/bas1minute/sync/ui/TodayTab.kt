package com.bas1minute.sync.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Tab
import java.text.DateFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

data class HomeState(
    val stepGoal: Int = 7000,
    val screenLimit: Int = 180,
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastSyncMessage: String = "",
    val lastSyncOk: Boolean = false,
    val autoSync: Boolean = true,
    val syncKeyPrefix: String = "",
)

private val MOODS = listOf(1 to "Very low", 2 to "Low", 3 to "Okay", 4 to "Good", 5 to "Great")

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayTab(app: AppController, onCall: (String) -> Unit, phoneActions: PhoneActions) {
    val numbers = remember { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
    val state = app.home
    val server = app.today
    val phoneToday = app.phone.today
    // Phone steps win (fresher); otherwise what the website has (for example typed on the website)
    val stepsKnown = phoneToday?.steps ?: server?.today?.steps?.toLong()
    val steps = stepsKnown ?: 0L
    // Home "screen time" = social, messaging and browser apps (see ScreenTimeReader.SOCIAL)
    val screen = phoneToday?.row?.socialMinutes ?: server?.today?.social
    val km = if (stepsKnown == null) null else com.bas1minute.sync.Distance.km(steps, app.prefs.stepLengthCm)
    // Bursts, calm and mood live on this phone only
    val bursts = phoneToday?.row?.bursts ?: 0
    val calm = phoneToday?.row?.calmMinutes ?: 0
    val mood = phoneToday?.row?.mood
    val burstGoal = app.prefs.burstGoal
    val stepsLeft = (state.stepGoal - steps).coerceAtLeast(0)
    val screenOver = screen != null && screen > state.screenLimit
    var chart by remember { mutableIntStateOf(0) }
    val name = app.prefs.firstName.ifBlank { "dost" }
    var explainRings by remember { mutableStateOf(false) }
    // Celebrate reaching the goal, once a day
    var celebrate by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(stepsKnown, state.stepGoal) {
        val today = com.bas1minute.sync.LocalDateHolder.today()
        if (stepsKnown != null && stepsKnown >= state.stepGoal && app.prefs.celebratedDay != today) {
            app.prefs.celebratedDay = today
            celebrate = true
        }
    }
    if (celebrate) GoalCelebration(
        numbers.format(steps), com.bas1minute.sync.Distance.format(com.bas1minute.sync.Distance.km(steps, app.prefs.stepLengthCm)),
        onShare = { celebrate = false; app.shareKind = "steps" }, onClose = { celebrate = false },
    )
    if (explainRings) RingExplainer(numbers.format(state.stepGoal), hm(state.screenLimit)) { explainRings = false }

    PullToRefreshBox(isRefreshing = state.syncing, onRefresh = { app.sync() }, modifier = Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ---------- Dark hero ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Ink, Ink2)))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(app.avatar, name, 48.dp, Modifier.clickable { app.tab = Tab.Profile })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${t("Namaste")}, $name", color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            app.prefs.bio.ifBlank { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)) },
                            color = MutedOnDark, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Ink3).clickable { app.shareKind = "steps" },
                        contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.IosShare, contentDescription = "Share my day",
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    SyncChip(state, app.phone.pendingDays + app.waiting, app.offline)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DualRing(
                        stepsFraction = steps.toFloat() / state.stepGoal,
                        screenFraction = (screen ?: 0).toFloat() / state.screenLimit,
                        screenOver = screenOver,
                        modifier = Modifier.size(170.dp)
                            .clickable(onClickLabel = "What do the rings mean?") { explainRings = true }
                            .semantics(mergeDescendants = true) {
                                contentDescription = (if (stepsKnown == null) "Steps not counted yet. " else "${numbers.format(steps)} of ${numbers.format(state.stepGoal)} steps. ") +
                                    (if (screen == null) "" else "Screen time ${hm(screen)} of ${hm(state.screenLimit)}.")
                            },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (stepsKnown == null) "0" else numbers.format(steps), color = Color.White, style = MaterialTheme.typography.displayMedium)
                            Text("of ${numbers.format(state.stepGoal)} steps", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                            if (km != null) {
                                Text(com.bas1minute.sync.Distance.format(km), color = Haldi, style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RingLegend { explainRings = true } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderTile(
                        "Steps left",
                        if (stepsKnown == null) "—" else if (stepsLeft == 0L) "Done!" else numbers.format(stepsLeft),
                        when (app.phone.todaySource) { "health_connect" -> "Health Connect"; "phone_sensor" -> "This phone"; else -> "Allow in Profile" },
                        Violet, Modifier.weight(1f),
                    )
                    HeaderTile(
                        "Screen time", screen?.let { hm(it) } ?: "—",
                        when { screen == null -> "Allow in Profile"; screenOver -> "Over ${hm(state.screenLimit)} · social apps"; else -> "Social apps · limit ${hm(state.screenLimit)}" },
                        if (screenOver) Coral else Haldi, Modifier.weight(1f),
                    )
                }
            }

            // ---------- White sheet ----------
            Column(
                Modifier
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Paper)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (app.offline) OfflineBanner(app.waiting)

                // 1. Finish setup, just in time (only while something is missing)
                if (!app.phone.stepsReady || !app.phone.usageAccess) FinishSetupCard(app, phoneActions)

                // Sunday / Monday: the weekly story, until opened
                if (com.bas1minute.sync.WeekReview.isReviewDay() && app.prefs.reviewSeenWeek != com.bas1minute.sync.WeekReview.weekKey()) {
                    WeekReviewCard(app)
                }

                // 2. The habit at a glance
                TwentyOneDays(app, compact = true)

                // 3. What can I do right now?
                QuickActions(
                    bursts = bursts, burstGoal = burstGoal, calm = calm,
                    water = app.phone.today?.row?.waterMl ?: 0,
                    onBurst = { app.burstOpen = true }, onCalm = { app.calmOpen = true },
                    onWater = { app.addWater() }, onLog = { app.logOpen = true },
                )

                // 4. One smart card: the most relevant thing today
                val hasCheers = !server?.cheers.isNullOrEmpty()
                when {
                    hasCheers -> {
                    server?.cheers?.takeIf { it.isNotEmpty() }?.let { cheers ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Haldi).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("From your circles today", style = MaterialTheme.typography.titleLarge, color = Ink)
                            cheers.forEach { c ->
                                Text(
                                    if (c.kind == "cheer") "${c.from} sent you a Shabash!  ·  ${c.circle}" else "${c.from}: Chalo, ek burst?  ·  ${c.circle}",
                                    style = MaterialTheme.typography.bodyMedium, color = Ink,
                                )
                            }
                        }
                    }
                    }
                    stepsKnown != null && stepsLeft in 1L..1500L -> Nudge("Bas ${numbers.format(stepsLeft)} steps left. A 10-minute walk will do it.")
                    screenOver -> Nudge("Social and browsing time is over your limit. Swap 10 minutes of scrolling for a walk?")
                    else -> QuoteCard(onShare = phoneActions.onShareWhatsApp)
                }

                if (app.phone.samsungNeedsSetup && System.currentTimeMillis() - app.prefs.samsungDismissedAt > WEEK_MS) {
                    SamsungHealthCard(
                        hcGranted = app.phone.hcGranted,
                        onAllowHealthConnect = phoneActions.onAskHealthConnect,
                        onOpenSamsungHealth = phoneActions.onOpenSamsungHealth,
                        onDismiss = { app.prefs.samsungDismissedAt = System.currentTimeMillis(); app.home = app.homeState() },
                    )
                }

                // Below the fold
                WheelTeaser(app)

                // Mood (private)
                AppCard(title = t("How are you feeling?"), trailing = { Text(t("Only you see this"), color = Muted, style = MaterialTheme.typography.bodySmall) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        MOODS.forEach { (value, label) ->
                            val on = mood == value
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) Color.White else TextMain,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (on) Ink else Paper)
                                    .clickable { app.setMood(value) }
                                    .padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    if (mood != null && mood <= 2) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFEEF0FF)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Thanks for checking in. Rough days happen. A slow minute of breathing can help, and talking to someone helps too.",
                                style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallPill("1-minute calm") { app.calmOpen = true }
                                SmallPill("Call Tele-MANAS 14416") { onCall("14416") }
                            }
                            Text("Free Government of India mental health helpline, 24x7, many languages. Also 1800-891-4416.",
                                style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun QuickTile(label: String, value: String, action: String, modifier: Modifier = Modifier, alert: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (alert) Color(0xFFFFE7DF) else CardBg).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(action, style = MaterialTheme.typography.bodySmall, color = if (alert) Color(0xFF8A2A0F) else Blue)
    }
}

@Composable
private fun ActionTile(
    title: String, value: String, hint: String, dots: Pair<Int, Int>?, button: String,
    modifier: Modifier = Modifier, onClick: () -> Unit, secondary: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(CardBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(value, style = MaterialTheme.typography.headlineMedium)
        if (dots != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(dots.first.coerceAtMost(8)) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < dots.second) Haldi else PaperLine))
                }
            }
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
        SmallPill(button, onClick)
        secondary?.let { (label, action) ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = Blue,
                modifier = Modifier.clickable(onClick = action).padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun OfflineBanner(waiting: Int) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF3C8)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.CloudOff, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Offline. Showing what’s saved on your phone." + if (waiting > 0) " $waiting action${if (waiting == 1) "" else "s"} will send later." else "",
            style = MaterialTheme.typography.bodyMedium, color = Ink,
        )
    }
}

@Composable
fun SyncChip(state: HomeState, pending: Int, offline: Boolean) {
    val ok = state.lastSyncOk && state.lastSyncAt > 0 && pending == 0 && !offline
    val label = when {
        state.syncing -> "Syncing…"
        offline -> "Offline"
        pending > 0 -> "$pending to send"
        ok -> "Synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lastSyncAt))
        else -> "Not synced"
    }
    Row(Modifier.clip(RoundedCornerShape(50)).background(Ink3).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) Mint else Haldi))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun OfflineNote(pending: Int, saved: Int) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Paper).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (pending == 0) Mint else Haldi))
        Spacer(Modifier.width(10.dp))
        Text(
            if (pending == 0) "Everything is backed up. $saved day${if (saved == 1) "" else "s"} also saved on this phone."
            else "$pending item${if (pending == 1) "" else "s"} saved on this phone, waiting to sync. Nothing is lost offline.",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
    }
}

@Composable
fun Nudge(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Bolt, contentDescription = null, tint = Haldi, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
fun WeekSummary(left: Pair<String, String>, right: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(left, right).forEach { (label, value) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Paper).padding(12.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(value, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
