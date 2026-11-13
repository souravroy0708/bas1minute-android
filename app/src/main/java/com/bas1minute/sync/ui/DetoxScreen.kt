package com.bas1minute.sync.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bas1minute.sync.detox.DetoxShareCard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Tab
import com.bas1minute.sync.detox.AppRules
import com.bas1minute.sync.detox.BreakReason
import com.bas1minute.sync.detox.DaySummary
import com.bas1minute.sync.detox.DetoxController
import com.bas1minute.sync.detox.DetoxLogic
import com.bas1minute.sync.detox.DetoxSession
import com.bas1minute.sync.detox.DetoxStore
import com.bas1minute.sync.detox.HourCell
import com.bas1minute.sync.detox.HourState
import com.bas1minute.sync.detox.Outcome
import com.bas1minute.sync.detox.StreakLogic
import com.bas1minute.sync.detox.WeekSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- state shared by the screen and the card

/** Live detox state for a composable: sessions, permissions and a 1-second clock. */
private class DetoxUiState(val sessions: List<DetoxSession>, val now: Long, val usageOk: Boolean, val overlayOk: Boolean) {
    val active: DetoxSession? get() = sessions.lastOrNull { it.outcome == Outcome.ACTIVE }
}

@Composable
private fun rememberDetoxState(): DetoxUiState {
    val ctx = LocalContext.current
    val store = remember { DetoxStore.get(ctx) }
    var sessions by remember { mutableStateOf(store.sessions()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var usageOk by remember { mutableStateOf(DetoxController.hasUsageAccess(ctx)) }
    var overlayOk by remember { mutableStateOf(DetoxController.canDrawOverlays(ctx)) }
    DisposableEffect(store) {
        val l = { sessions = store.sessions() }
        store.addListener(l)
        onDispose { store.removeListener(l) }
    }
    // Every second: clock. Every 2 s: permissions (the user may come back from Settings).
    // Every 5 s: close a finished session (checks real usage, off the main thread).
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            now = System.currentTimeMillis()
            if (n % 2 == 0) {
                usageOk = DetoxController.hasUsageAccess(ctx)
                overlayOk = DetoxController.canDrawOverlays(ctx)
            }
            if (n % 5 == 0) withContext(Dispatchers.IO) { DetoxController.reconcile(ctx) }
            n++
            delay(1_000)
        }
    }
    return DetoxUiState(sessions, now, usageOk, overlayOk)
}

private val CleanGreen = Color(0xFF2E9E5B)

// ---------------------------------------------------------------- Insights entry

// ---------------------------------------------------------------- Detox tab

/**
 * The Detox tab: Bas 1 Minute's second pillar next to fitness. "Focus" (start a detox, clean hours, promises) is
 * the default; "Insights" (screen time, forecast, phone check) sits beside it, one tap away.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun DetoxHub(app: AppController, actions: PhoneActions, view: String = app.detoxView) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.fillMaxWidth().background(Ink).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf(t("Focus"), t("Insights")), if (view == "insights") 1 else 0) { app.detoxView = if (it == 1) "insights" else "detox" }
        }
        // The status bar is already padded above, so the screens below don't pad it again
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
            if (view == "insights") InsightsTab(app) else DetoxScreen(app, actions, onClose = null)
        }
    }
}

private fun openDetox(app: AppController) { app.tab = Tab.Detox; app.phoneCheckOpen = false; app.detoxView = "detox" }

/** The doorway on Today. Shows the countdown while a detox is on, otherwise invites one. */
@Composable
fun DetoxCard(app: AppController) {
    val st = rememberDetoxState()
    val a = st.active
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (a != null) Ink else CardBg)
            .clickable { openDetox(app) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(if (a != null) Haldi else Color(0xFFE9ECFF)), contentAlignment = Alignment.Center) {
            Text("⏳", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Digital detox"), style = MaterialTheme.typography.titleMedium, color = if (a != null) Color.White else TextMain)
            Text(
                if (a != null) "${t("On")} · ${DetoxLogic.formatDuration(a.remainingMs(st.now))} ${t("left")}"
                else "Get an hour back: only calls and messages until the timer ends.",
                style = MaterialTheme.typography.bodySmall, color = if (a != null) MutedOnDark else Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallPill(if (a != null) t("Open") else t("Start")) { openDetox(app) }
    }
}

// ---------------------------------------------------------------- full screen

/**
 * Digital detox: promise a time with only calls and messages (everything else, including WhatsApp and
 * browsers, is paused), see today's clean hours (no social media or browser), the week, and an honest
 * record of kept and broken ("guilty") promises. Everything stays on this phone.
 */
@Composable
fun DetoxScreen(app: AppController, actions: PhoneActions, onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    val store = remember { DetoxStore.get(ctx) }
    val st = rememberDetoxState()
    var cells by remember { mutableStateOf<List<HourCell>>(emptyList()) }
    var week by remember { mutableStateOf<List<Pair<Long, Int>>>(emptyList()) }

    if (onClose != null) BackHandler { onClose() }

    // Clean hours: recomputed once a minute from Android's usage events.
    LaunchedEffect(st.now / 60_000, st.usageOk) {
        if (!st.usageOk) return@LaunchedEffect
        val t0 = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
            DetoxController.hourCells(ctx, DetoxController.dayStart(t0), t0) to DetoxController.cleanHoursByDay(ctx, t0)
        }
        cells = result.first; week = result.second
    }

    val today = DetoxController.dayStart(st.now)
    val day = DetoxLogic.daySummary(today, cells, st.sessions, st.now)
    val weekSum = DetoxLogic.weekSummary(st.sessions, today, st.now)
    val active = st.active

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        // ---------- Hero ----------
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Ink, Ink2))).statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onClose != null) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Ink3).clickable(onClickLabel = "Back", onClick = onClose),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(t("Digital detox"), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(if (active != null) t("Only calls and messages until the timer ends") else "Your focus time. Get hours back from your phone.",
                        color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (active != null) {
                Text(t("Time left"), color = MutedOnDark, style = MaterialTheme.typography.labelMedium)
                Text(
                    DetoxLogic.formatDuration(active.remainingMs(st.now)),
                    color = Haldi, style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.semantics { contentDescription = "Time left ${DetoxLogic.formatDuration(active.remainingMs(st.now))}" },
                )
                val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(active.plannedEndMs))
                Text("${t("Your promise")}: ${DetoxLogic.formatMinutes(active.plannedMinutes)} · ${t("ends at")} $endsAt" +
                    if (active.blockedAttempts > 0) " · ${t("paused")} ${active.blockedAttempts}×" else "",
                    color = Color.White, style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (st.usageOk) "${day.cleanHours}" else "—", color = Color.White, style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(t("clean hours today"), color = MutedOnDark, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(t("A clean hour is a full hour without social media or a browser."), color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
            }
        }

        val streak = remember(st.sessions, st.now) { StreakLogic.currentStreak(st.sessions, st.now) }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (active != null) ActiveCard(active, st.now, streak, store) { DetoxController.endEarly(ctx) }
            else SetupCard(store, st.usageOk, st.overlayOk, actions, onStart = { m, why ->
                if (DetoxController.start(ctx, m, why) == null) app.message = "Allow both permissions to start a detox."
            })
            BadgesCard(st.sessions, st.now)
            TodayCard(st.usageOk, cells, day)
            WeekCard(week, weekSum)
            HistoryCard(st.sessions.filter { it.outcome != Outcome.ACTIVE }.takeLast(10).reversed(), st.now)
            Text(
                "Detox and clean hours are worked out and kept on this phone only. Which apps you opened is never sent.",
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActiveCard(s: DetoxSession, now: Long, streak: Int, store: DetoxStore, onEndEarly: () -> Unit) {
    var showFlow by remember { mutableStateOf(false) }
    AppCard(title = t("Detox is on")) {
        Text("WhatsApp, browsers, social media and every other app are paused. Calls, messages, camera and alarms still work.",
            style = MaterialTheme.typography.bodyMedium, color = Muted)
        // Visible and reachable at all times — the person's own choice, not hidden behind a maze.
        GhostButton(t("End early")) { showFlow = true }
    }
    if (showFlow) EndEarlyFlow(
        s, now, streak, store.accountabilityName, store.accountabilityContact,
        onConfirm = { showFlow = false; onEndEarly() },
        onDismiss = { showFlow = false },
    )
}

@Composable
private fun SetupCard(store: DetoxStore, usageOk: Boolean, overlayOk: Boolean, actions: PhoneActions, onStart: (Int, String) -> Unit) {
    val ctx = LocalContext.current
    var minutes by remember { mutableIntStateOf(store.lastMinutes) }
    var why by remember { mutableStateOf("") }
    var extras by remember { mutableStateOf(store.extraAllowed) }
    var picking by remember { mutableStateOf(false) }
    val rules = remember(extras) { AppRules(ctx, extras) }

    AppCard(title = t("Start a detox")) {
        if (!usageOk || !overlayOk) {
            Text("Two permissions let Bas 1 Minute pause other apps. Both stay on this phone.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            StatusRow(t("Usage access"), "To see which app is open", usageOk,
                if (usageOk) null else t("Allow")) { actions.onOpenUsageAccess() }
            StatusRow(t("Display over other apps"), "To show the pause screen", overlayOk,
                if (overlayOk) null else t("Allow")) {
                runCatching { ctx.startActivity(DetoxController.overlayIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        Text(t("How long?"), style = MaterialTheme.typography.labelLarge)
        val choices = DetoxLogic.DURATION_CHOICES_MIN
        Segmented(choices.map { DetoxLogic.formatMinutes(it) }, choices.indexOf(minutes)) { minutes = choices[it] }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (minutes in choices) t("Or a custom time") else "${t("Custom")}: ${DetoxLogic.formatMinutes(minutes)}",
                style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.weight(1f))
            SmallPill("−30") { minutes = DetoxLogic.clampMinutes(minutes - 30) }
            Spacer(Modifier.width(6.dp))
            SmallPill("+30") { minutes = DetoxLogic.clampMinutes(minutes + 30) }
        }
        Text(t("Always allowed"), style = MaterialTheme.typography.labelLarge)
        Text((listOf("Calls", "Messages", "Contacts", "Camera", "Clock and alarms") + extras.map { rules.label(it) }).joinToString(", "),
            style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { picking = true }) {
            Text(if (extras.isEmpty()) t("Allow up to 3 more apps (maps, UPI…)") else t("Change allowed apps"), color = Blue)
        }
        Text(t("Why now?"), style = MaterialTheme.typography.labelLarge)
        Text("Write it down. If you try to end early, we'll ask you to type it back — your own reason, not ours.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        OutlinedTextField(why, { why = it.take(140) }, placeholder = { Text("e.g. Finish my assignment before midnight") },
            modifier = Modifier.fillMaxWidth())
        if (store.recentWhys.isNotEmpty() && why.isBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                store.recentWhys.take(3).forEach { prev ->
                    Text(prev, style = MaterialTheme.typography.labelMedium, color = TextMain,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(CardBg).clickable { why = prev }
                            .padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        BigButton("${t("I promise")}: ${DetoxLogic.formatMinutes(minutes)}", enabled = usageOk && overlayOk) { onStart(minutes, why) }
    }
    if (picking) AllowedAppsDialog(rules, extras, onDone = { store.extraAllowed = it; extras = store.extraAllowed; picking = false }, onDismiss = { picking = false })
}

@Composable
private fun AllowedAppsDialog(rules: AppRules, current: Set<String>, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { rules.pickableApps() } }
    var chosen by remember { mutableStateOf(current) }
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.second.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t("Also allow")} (${chosen.size}/${DetoxLogic.MAX_EXTRA_ALLOWED})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(t("Cancel")) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (apps.isNotEmpty() && shown.isEmpty()) {
                    Text("No app matches “$query”.", style = MaterialTheme.typography.bodyMedium, color = Muted,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.first }) { (pkg, label) ->
                        val on = pkg in chosen
                        val canChange = on || chosen.size < DetoxLogic.MAX_EXTRA_ALLOWED
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(enabled = canChange) { chosen = if (on) chosen - pkg else chosen + pkg },
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, enabled = canChange)
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen) }) { Text(t("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

@Composable
private fun TodayCard(usageOk: Boolean, cells: List<HourCell>, day: DaySummary) {
    AppCard(title = t("Today")) {
        if (!usageOk) {
            Text("Allow Usage access to count your clean hours.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            return@AppCard
        }
        Text("${day.cleanHours} ${t("clean hours")} · ${day.countedHours - day.cleanHours} ${t("with social media or browser")}",
            style = MaterialTheme.typography.titleMedium)
        Text("${t("Detox kept today")}: ${DetoxLogic.formatMinutes(day.detoxMinutesKept)}" +
            if (day.brokenSessions > 0) " · ${t("Guilty")}: ${day.brokenSessions}" else "",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        cells.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { HourBox(it, Modifier.weight(1f)) }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(CleanGreen, t("Clean")); Legend(Coral, t("Used")); Legend(PaperLine, t("Still to come"))
        }
    }
}

@Composable
private fun HourBox(c: HourCell, modifier: Modifier) {
    val bg = when (c.state) { HourState.CLEAN -> CleanGreen; HourState.USED -> Coral; HourState.IN_PROGRESS -> Color.Transparent; HourState.FUTURE -> PaperLine }
    val fg = when (c.state) { HourState.CLEAN -> Color.White; HourState.USED -> Ink; else -> Muted }
    val desc = "${DetoxLogic.hourLabel(c.hour)}: " + when (c.state) {
        HourState.CLEAN -> "clean"; HourState.USED -> "used"; HourState.IN_PROGRESS -> "in progress"; HourState.FUTURE -> "not yet"
    }
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (c.state == HourState.IN_PROGRESS) Modifier.border(2.dp, CleanGreen, RoundedCornerShape(10.dp)) else Modifier)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Text(DetoxLogic.hourLabel(c.hour).replace(" ", "").lowercase(), color = fg, fontSize = 11.sp, maxLines = 1) }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun WeekCard(days: List<Pair<Long, Int>>, w: WeekSummary) {
    val fmt = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    AppCard(title = t("This week")) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            WeekStat("${w.kept}", t("promises kept"), CleanGreen, Modifier.weight(1f))
            WeekStat("${w.broken}", t("guilty"), Coral, Modifier.weight(1f))
            WeekStat("${w.keptDaysStreak}", t("day streak"), Haldi, Modifier.weight(1f))
        }
        if (w.minutesKept > 0) Text("${DetoxLogic.formatMinutes(w.minutesKept)} ${t("of detox kept this week")}",
            style = MaterialTheme.typography.bodyMedium, color = Muted)
        if (days.isNotEmpty()) {
            Text(t("Clean hours per day"), style = MaterialTheme.typography.labelLarge)
            WeekBars(days.map { (start, h) -> fmt.format(Date(start)) to h }, goal = 6, barColor = CleanGreen, overColor = null) { "$it" }
        }
    }
}

@Composable
private fun WeekStat(value: String, label: String, dot: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Paper).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HistoryCard(list: List<DetoxSession>, now: Long) {
    if (list.isEmpty()) return
    val ctx = LocalContext.current
    val rules = remember { AppRules(ctx, emptySet()) }
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH) }
    AppCard(title = t("Your promises")) {
        list.forEach { s ->
            val kept = s.outcome == Outcome.KEPT
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (kept) Color(0xFFE3F7EE) else Color(0xFFFFE6DE)), contentAlignment = Alignment.Center) {
                    Text(if (kept) "✓" else "✗", color = if (kept) MintDark else Danger, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (kept) "${t("Kept")} · ${DetoxLogic.formatMinutes(s.plannedMinutes)}"
                        else "${t("Guilty")} · ${DetoxLogic.formatMinutes(s.keptMinutes(now))} of ${DetoxLogic.formatMinutes(s.plannedMinutes)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val why = when (s.reason) {
                        BreakReason.ENDED_EARLY -> t("Ended early")
                        BreakReason.USED_BLOCKED_APP -> "${rules.label(s.brokenBy ?: "")} ${t("used during detox")}"
                        BreakReason.PERMISSION_REVOKED -> t("Permission turned off")
                        null -> if (s.blockedAttempts > 0) "${t("Resisted")} ${s.blockedAttempts}×" else t("No slips")
                    }
                    Text("${fmt.format(Date(s.startMs))} · $why", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Badges

@Composable
private fun BadgesCard(sessions: List<DetoxSession>, now: Long) {
    val ctx = LocalContext.current
    val streak = remember(sessions, now) { StreakLogic.currentStreak(sessions, now) }
    val best = remember(sessions) { StreakLogic.bestStreak(sessions) }
    val total = remember(sessions) { StreakLogic.totalFocusDays(sessions) }
    val earned = remember(streak, best) { StreakLogic.badgesFor(maxOf(streak, best)) }
    val next = remember(streak) { StreakLogic.nextBadge(streak) }
    var share by remember { mutableStateOf(false) }

    AppCard(title = t("Focus streak")) {
        Text("A focus day is a 3-hour (or longer) detox you keep, fully between 10 AM and 10 PM.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeekStat("$streak", t("day streak"), Haldi, Modifier.weight(1f))
            WeekStat("$best", "best streak", Violet, Modifier.weight(1f))
            WeekStat("$total", "focus days", CleanGreen, Modifier.weight(1f))
        }
        if (earned.isNotEmpty()) {
            Text(t("Badges earned"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                earned.take(3).forEach { b ->
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Ink).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏅", style = MaterialTheme.typography.titleLarge)
                        Text(StreakLogic.badgeLabel(b.days), style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
        }
        next?.let { (badge, remaining) ->
            Text("$remaining more day${if (remaining == 1) "" else "s"} to ${StreakLogic.badgeLabel(badge.days)}.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (streak >= DetoxLogic.BADGE_DAYS_FIRST_SHAREABLE) BigButton(t("Share my streak"), dark = false) { share = true }
    }
    if (share) DetoxShareSheet(streak, best, total, onDismiss = { share = false })
}

@Composable
private fun DetoxShareSheet(streak: Int, best: Int, total: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val card = remember(streak) { DetoxShareCard.render(ctx, streak, best, total) }
    val caption = "$streak day${if (streak == 1) "" else "s"} of focus, 3+ hours a day, on Bas 1 Minute 🧠 #Bas1Minute"
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Paper).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t("Share your streak"), style = MaterialTheme.typography.titleLarge)
            Image(card.asImageBitmap(), contentDescription = "Preview of the picture", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).clip(RoundedCornerShape(18.dp)))
            BigButton(t("Share"), dark = false) {
                runCatching {
                    val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
                    val file = java.io.File(dir, "bas1minute-streak.png")
                    file.outputStream().use { card.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_TEXT, caption).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share your streak"))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t("Close"), color = Muted) }
        }
    }
}
