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
    val bursts = server?.today?.bursts ?: 0
    val calm = server?.today?.mindful ?: 0
    val mood = server?.today?.mood
    val burstGoal = app.prefs.burstGoal
    val stepsLeft = (state.stepGoal - steps).coerceAtLeast(0)
    val screenOver = screen != null && screen > state.screenLimit
    var chart by remember { mutableIntStateOf(0) }
    val name = app.prefs.firstName.ifBlank { "dost" }

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
                    Column(Modifier.weight(1f)) {
                        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                            color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                        Text("Namaste, $name", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                    SyncChip(state, app.phone.pendingDays + app.waiting, app.offline)
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DualRing(
                        stepsFraction = steps.toFloat() / state.stepGoal,
                        screenFraction = (screen ?: 0).toFloat() / state.screenLimit,
                        screenOver = screenOver,
                        modifier = Modifier.size(220.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (stepsKnown == null) "—" else numbers.format(steps), color = Color.White, style = MaterialTheme.typography.displayMedium)
                            Text("of ${numbers.format(state.stepGoal)} steps", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                            if (km != null) {
                                Text(com.bas1minute.sync.Distance.format(km), color = Haldi, style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
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

                QuoteCard(onShare = phoneActions.onShareWhatsApp)

                if (app.phone.samsungNeedsSetup && System.currentTimeMillis() - app.prefs.samsungDismissedAt > WEEK_MS) {
                    SamsungHealthCard(
                        hcGranted = app.phone.hcGranted,
                        onAllowHealthConnect = phoneActions.onAskHealthConnect,
                        onOpenSamsungHealth = phoneActions.onOpenSamsungHealth,
                        onDismiss = { app.prefs.samsungDismissedAt = System.currentTimeMillis(); app.home = app.homeState() },
                    )
                }

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

                if (stepsKnown != null && stepsLeft in 1L..1500L) Nudge("Bas ${numbers.format(stepsLeft)} steps left. A 10-minute walk will do it.")
                else if (screenOver) Nudge("Social and browsing time is over your limit. Swap 10 minutes of scrolling for a walk?")

                // Bursts + calm
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(
                        title = "Bursts", value = "$bursts / $burstGoal",
                        hint = if (bursts >= burstGoal) "All done today" else "One minute, out of breath",
                        dots = burstGoal to bursts, button = "Start 1 min", modifier = Modifier.weight(1f),
                        onClick = { app.burstOpen = true },
                        secondary = "+ I already did one" to { app.addBurst() },
                    )
                    ActionTile(
                        title = "Calm", value = "$calm min",
                        hint = if (calm > 0) "Mind ka workout done" else "Slow breathing",
                        dots = null, button = "Start 1 min", modifier = Modifier.weight(1f),
                        onClick = { app.tab = Tab.Calm },
                    )
                }

                TwentyOneDays(app)

                WheelTeaser(app)

                // Water, sleep, social media (quick look; details in Insights)
                val row = app.phone.today?.row
                val ins = app.phone.insights
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile("Water", (row?.waterMl ?: server?.today?.water)?.let { if (it >= 1000) "%.1f L".format(it / 1000.0) else "$it ml" } ?: "0 ml",
                        "+ 1 glass", Modifier.weight(1f)) { app.addWater() }
                    QuickTile("Sleep", row?.sleepMinutes?.let { hm(it) } ?: "—",
                        if (row?.sleepSource == "phone_estimate") "estimate" else "last night", Modifier.weight(1f)) { app.tab = Tab.Insights }
                    QuickTile("1-year view", ins?.forecasts?.firstOrNull()?.let { "${it.days} days" } ?: "—",
                        "on screens at this rate", Modifier.weight(1f), alert = (ins?.socialAvg ?: 0) >= 120) { app.tab = Tab.Insights }
                }
                GhostButton("Log a day") { app.logOpen = true }

                // Mood (private)
                AppCard(title = "How are you feeling?", trailing = { Text("Only you see this", color = Muted, style = MaterialTheme.typography.bodySmall) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        MOODS.forEach { (value, label) ->
                            val on = mood == value
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) Color.White else Ink,
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
                                SmallPill("1-minute calm") { app.tab = Tab.Calm }
                                SmallPill("Call Tele-MANAS 14416") { onCall("14416") }
                            }
                            Text("Free Government of India mental health helpline, 24x7, many languages. Also 1800-891-4416.",
                                style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }

                // Sync status
                AppCard(title = "Sync") {
                    BigButton(if (state.syncing) "Syncing…" else "Sync now", enabled = !state.syncing) { app.sync() }
                    if (state.lastSyncMessage.isNotBlank()) {
                        val time = if (state.lastSyncAt > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lastSyncAt)) else ""
                        Text("${state.lastSyncMessage} $time".trim(), color = if (state.lastSyncOk) MintDark else Danger, style = MaterialTheme.typography.bodySmall)
                    }
                    OfflineNote(app.phone.pendingDays + app.waiting, app.phone.savedDays)
                }
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, value: String, action: String, modifier: Modifier = Modifier, alert: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (alert) Color(0xFFFFE7DF) else Color.White).clickable(onClick = onClick).padding(14.dp),
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
        modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
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
        Text("☁", style = MaterialTheme.typography.titleLarge)
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
        Text("⚡", style = MaterialTheme.typography.titleLarge, color = Haldi)
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
