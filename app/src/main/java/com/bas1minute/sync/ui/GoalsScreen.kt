package com.bas1minute.sync.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Tab
import com.bas1minute.sync.goal.Goal
import com.bas1minute.sync.goal.GoalKind
import com.bas1minute.sync.goal.GoalLogic
import com.bas1minute.sync.goal.GoalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The Goals tab: "Goals" (the fourth pillar — daily and life goals) by default, "Diary" one tap away. Mirrors
 * the Detox tab's Focus/Insights pattern. Lives where Circles used to; Circles moved to Profile.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun GoalsHub(app: AppController, actions: PhoneActions, view: String = app.goalsHubView) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.fillMaxWidth().background(Ink).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf(t("Goals"), t("Diary")), if (view == "diary") 1 else 0) { app.goalsHubView = if (it == 1) "diary" else "goals" }
        }
        // The status bar is already padded above, so the screens below don't pad it again
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
            if (view == "diary") DiaryScreen(app, actions, onClose = null) else GoalsScreen(app, actions, onClose = null)
        }
    }
}

/**
 * Goals: the fourth pillar, tying steps, sleep, screen time and detox to what the person actually wants out
 * of using Bas 1 Minute, plus simple yes/no goals (study, read, no phone after 10) for anything the phone
 * can't measure on its own. Everything here is worked out from data already on the phone; nothing is sent.
 */
@Composable
fun GoalsScreen(app: AppController, actions: PhoneActions, onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    val store = remember { GoalStore(ctx) }
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var checkins by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }   // today's custom check-ins
    var reload by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    var section by remember { mutableStateOf("life") }   // "life" (the three horizons) or "daily" (habit check)

    LaunchedEffect(reload) {
        val loaded = withContext(Dispatchers.IO) { store.active() }
        goals = loaded
        checkins = withContext(Dispatchers.IO) {
            loaded.filter { it.kind == GoalKind.Custom }.associate { it.id to (store.checkin(it.id, today)?.done ?: false) }
        }
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Ink, Ink2))).statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    Text(t("Your goals"), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("From today's habits to the life you're working toward", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                }
            }
            Segmented(listOf(t("Life goals"), t("Daily")), if (section == "daily") 1 else 0) { section = if (it == 1) "daily" else "life" }
        }

        if (section == "life") {
            LifeGoalsBoard(app, actions)
            return@Column
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (goals.isEmpty()) {
                AppCard {
                    Text("🎯", style = MaterialTheme.typography.displayMedium)
                    Text(t("Set your first goal"), style = MaterialTheme.typography.titleLarge)
                    Text("Pick something real — a step goal, more sleep, less screen time, a focus habit, or your own: study, read, no phone after 10.",
                        style = MaterialTheme.typography.bodyMedium, color = Muted)
                    BigButton(t("Add a goal"), dark = false) { showAdd = true }
                }
            } else {
                goals.forEach { goal ->
                    GoalCard(
                        goal, store, today,
                        checkedToday = checkins[goal.id] ?: false,
                        onToggleCheckin = { done ->
                            app.launch {
                                withContext(Dispatchers.IO) { store.setCheckin(goal.id, today, done) }
                                checkins = checkins + (goal.id to done)
                            }
                        },
                        onArchive = { app.launch { withContext(Dispatchers.IO) { store.archive(goal.id) }; reload++ } },
                    )
                }
                if (goals.size < GoalLogic.MAX_ACTIVE_GOALS) {
                    GhostButton(t("Add another goal")) { showAdd = true }
                } else {
                    Text("Up to ${GoalLogic.MAX_ACTIVE_GOALS} goals at once keeps this a short daily check, not another chore.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            Text("Goals are worked out from what's already on this phone (steps, sleep, screen time, detox) plus what you check off yourself. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    if (showAdd && section == "daily") AddGoalDialog(
        app.prefs.stepGoal, app.prefs.screenLimitMinutes,
        onAdd = { kind, title, target ->
            app.launch { withContext(Dispatchers.IO) { store.create(kind, title, target) }; showAdd = false; reload++ }
        },
        onDismiss = { showAdd = false },
    )
}

@Composable
private fun GoalCard(
    goal: Goal, store: GoalStore, today: LocalDate, checkedToday: Boolean,
    onToggleCheckin: (Boolean) -> Unit, onArchive: () -> Unit,
) {
    var week by remember(goal.id) { mutableStateOf<com.bas1minute.sync.goal.GoalWeek?>(null) }
    var metToday by remember(goal.id) { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }

    LaunchedEffect(goal.id, checkedToday) {
        val (w, today2met) = withContext(Dispatchers.IO) {
            fun statusFor(d: LocalDate) = GoalLogic.dayStatus(goal, d, store.signal(d), if (goal.kind == GoalKind.Custom) store.checkin(goal.id, d) else null)
            GoalLogic.week(goal, today, ::statusFor) to statusFor(today).met
        }
        week = w; metToday = today2met
    }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (metToday) Color(0xFFE3F7EE) else Color(0xFFE9ECFF)),
                contentAlignment = Alignment.Center) {
                Text(kindEmoji(goal.kind), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    week?.let { "${it.daysMet}/${it.daysTracked.coerceAtLeast(1)} this week" + (if (it.streak > 0) " · ${it.streak}-day streak" else "") } ?: "…",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
            }
            if (goal.kind == GoalKind.Custom) {
                Checkbox(checked = checkedToday, onCheckedChange = onToggleCheckin)
            } else if (metToday) {
                Icon(Icons.Filled.Check, contentDescription = "Met today", tint = MintDark)
            }
        }
        TextButton(onClick = { confirmArchive = true }) { Text(t("Remove this goal"), color = Muted, style = MaterialTheme.typography.bodySmall) }
    }

    if (confirmArchive) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        title = { Text("Remove “${goal.title}”?") },
        text = { Text("Your history for it is kept; it just stops showing here.") },
        confirmButton = { TextButton(onClick = { confirmArchive = false; onArchive() }) { Text(t("Remove"), color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text(t("Cancel")) } },
    )
}

private fun kindEmoji(kind: GoalKind) = when (kind) {
    GoalKind.Steps -> "👣"; GoalKind.Sleep -> "😴"; GoalKind.ScreenTime -> "📵"; GoalKind.Detox -> "⏳"; GoalKind.Custom -> "✅"
}

@Composable
private fun AddGoalDialog(stepGoal: Int, screenLimitMinutes: Int, onAdd: (GoalKind, String, Int) -> Unit, onDismiss: () -> Unit) {
    val presets = remember { GoalLogic.presets(stepGoal, screenLimitMinutes) }
    var customTitle by remember { mutableStateOf("") }
    var pickingCustom by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add a goal")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!pickingCustom) {
                    presets.filter { it.kind != GoalKind.Custom }.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(p.kind, p.title, GoalLogic.clampTarget(p.kind, p.defaultTarget)) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(kindEmoji(p.kind), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Column { Text(p.title, style = MaterialTheme.typography.titleMedium); Text(presetTargetLabel(p.kind, p.defaultTarget), style = MaterialTheme.typography.bodySmall, color = Muted) }
                        }
                    }
                    Text(t("Or your own, for anything the phone can't measure:"), style = MaterialTheme.typography.labelLarge)
                    presets.filter { it.kind == GoalKind.Custom }.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(GoalKind.Custom, p.title, 0) }.padding(vertical = 8.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(p.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { pickingCustom = true }.padding(vertical = 8.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Blue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t("Write my own goal"), color = Blue, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    OutlinedTextField(customTitle, { customTitle = it.take(GoalLogic.MAX_TITLE_LEN) }, singleLine = true,
                        placeholder = { Text("e.g. 30 minutes of guitar practice") }, modifier = Modifier.fillMaxWidth())
                    Text("You'll check this off yourself each day it's done.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        },
        confirmButton = {
            if (pickingCustom) TextButton(enabled = customTitle.isNotBlank(), onClick = { onAdd(GoalKind.Custom, customTitle, 0) }) { Text(t("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

private fun presetTargetLabel(kind: GoalKind, target: Int): String = when (kind) {
    GoalKind.Steps -> "$target steps a day"
    GoalKind.Sleep -> "${target / 60}h ${target % 60}m a night"
    GoalKind.ScreenTime -> "Under ${if (target >= 60) "${target / 60}h ${target % 60}m" else "${target}m"} of social media a day"
    GoalKind.Detox -> "${target}-minute kept detox"
    GoalKind.Custom -> ""
}

// ---------------------------------------------------------------- Today card

/** The doorway on Today: today's goals at a glance, or an invite to set the first one. */
@Composable
fun GoalsCard(app: AppController) {
    val ctx = LocalContext.current
    val store = remember { GoalStore(ctx) }
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var metCount by remember { mutableStateOf(0) }
    LaunchedEffect(app.tab) {
        val loaded = withContext(Dispatchers.IO) { store.active() }
        goals = loaded
        val today = LocalDate.now()
        metCount = withContext(Dispatchers.IO) {
            loaded.count { GoalLogic.dayStatus(it, today, store.signal(today), if (it.kind == GoalKind.Custom) store.checkin(it.id, today) else null).met }
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).clickable { app.tab = Tab.Goals; app.goalsHubView = "goals" }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFFF3C8)), contentAlignment = Alignment.Center) {
            Text("🎯", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Your goals"), style = MaterialTheme.typography.titleMedium)
            Text(
                if (goals.isEmpty()) "Set your first goal: steps, sleep, screen time, focus, or your own"
                else "$metCount of ${goals.size} met today",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallPill(if (goals.isEmpty()) t("Start") else t("Open")) { app.tab = Tab.Goals; app.goalsHubView = "goals" }
    }
}
