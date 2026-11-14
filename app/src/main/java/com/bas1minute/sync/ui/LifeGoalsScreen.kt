package com.bas1minute.sync.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.goal.Horizon
import com.bas1minute.sync.goal.LifeGoal
import com.bas1minute.sync.goal.LifeGoalLogic
import com.bas1minute.sync.goal.LifeGoalPhoto
import com.bas1minute.sync.goal.LifeGoalStore
import com.bas1minute.sync.goal.Milestone
import com.bas1minute.sync.goal.Pace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Life goals: three honest horizons instead of one vague bucket — short-term (1–3 months), mid-term
 * (1–3 years), long-term (5–10 years) — because a goal's shape and pace should match how far out it is.
 * Each one gets a plan in the person's own words, a "why" anchored to a photo of who or what it's for, a
 * named obstacle with a plan for it ("if X happens, I will do Y" — implementation intentions, one of the
 * better-evidenced ways psychology has found to actually follow through), and a milestone checklist that
 * turns the destination into a path. Everything here stays on this phone.
 */
@Composable
fun LifeGoalsBoard(app: AppController, actions: PhoneActions) {
    val ctx = LocalContext.current
    val store = remember { LifeGoalStore(ctx) }
    var byHorizon by remember { mutableStateOf<Map<Horizon, List<LifeGoal>>>(emptyMap()) }
    var reload by remember { mutableIntStateOf(0) }
    var addFor by remember { mutableStateOf<Horizon?>(null) }
    var openGoal by remember { mutableStateOf<Long?>(null) }
    val photoSaved by LifeGoalPhoto.saved.collectAsState()

    LaunchedEffect(reload, photoSaved) {
        byHorizon = withContext(Dispatchers.IO) { Horizon.entries.associateWith { store.active(it) } }
    }

    openGoal?.let { id ->
        androidx.activity.compose.BackHandler { openGoal = null }
        LifeGoalDetailScreen(app, actions, id, onBack = { openGoal = null }, onChanged = { reload++ })
        return
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Horizon.entries.forEach { horizon ->
            val goals = byHorizon[horizon].orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t(horizon.label), style = MaterialTheme.typography.titleLarge)
                        Text(horizon.rangeLabel, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    if (goals.size < LifeGoalLogic.MAX_ACTIVE_PER_HORIZON) {
                        SmallPill(t("Add")) { addFor = horizon }
                    }
                }
                if (goals.isEmpty()) {
                    AppCard {
                        Text(
                            when (horizon) {
                                Horizon.Short -> "Something real in the next 1–3 months."
                                Horizon.Mid -> "Where you want to be in 1–3 years."
                                Horizon.Long -> "The 5–10 year version of your life you're working toward."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = Muted,
                        )
                        GhostButton(t("Set a ${horizon.label.lowercase(Locale.ENGLISH)} goal")) { addFor = horizon }
                    }
                } else {
                    goals.forEach { goal -> LifeGoalCard(ctx, store, goal) { openGoal = goal.id } }
                }
            }
        }
    }

    addFor?.let { horizon ->
        AddLifeGoalDialog(horizon, onAdd = { title, details, why, obstacle, plan, target ->
            app.launch {
                withContext(Dispatchers.IO) { store.create(horizon, title, details, why, obstacle, plan, target) }
                addFor = null; reload++
            }
        }, onDismiss = { addFor = null })
    }
}

@Composable
private fun LifeGoalCard(ctx: android.content.Context, store: LifeGoalStore, goal: LifeGoal, onOpen: () -> Unit) {
    var milestones by remember(goal.id) { mutableStateOf<List<Milestone>>(emptyList()) }
    LaunchedEffect(goal.id) { milestones = withContext(Dispatchers.IO) { store.milestones(goal.id) } }
    val progress = remember(goal.id, milestones) { LifeGoalLogic.progress(goal, milestones) }
    val photo = remember(goal.id, goal.photoPath) { goal.photoPath?.let { android.graphics.BitmapFactory.decodeFile(it) } }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
        } else {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9ECFF)), contentAlignment = Alignment.Center) {
                Text("🎯", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(goal.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(LifeGoalLogic.dayLabel(goal) + if (milestones.isNotEmpty()) " · ${progress.milestonesDone}/${progress.milestonesTotal} steps" else "",
                style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE9ECFF)).padding(top = 6.dp)) {
                Box(Modifier.fillMaxWidth(progress.timeFraction.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(50))
                    .background(paceColor(progress.pace)))
            }
        }
        Text("  ›", style = MaterialTheme.typography.titleLarge, color = Muted)
    }
}

private fun paceColor(pace: Pace): Color = when (pace) {
    Pace.Ahead -> Color(0xFF2E9E5B); Pace.OnTrack -> Color(0xFF3446C8); Pace.Behind -> Color(0xFFFF8A65); Pace.Unknown -> Color(0xFFBCC1E0)
}

// ---------------------------------------------------------------- Detail / "why wall"

@Composable
private fun LifeGoalDetailScreen(app: AppController, actions: PhoneActions, goalId: Long, onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { LifeGoalStore(ctx) }
    var goal by remember { mutableStateOf<LifeGoal?>(null) }
    var milestones by remember { mutableStateOf<List<Milestone>>(emptyList()) }
    var newMilestone by remember { mutableStateOf("") }
    var confirmGiveUp by remember { mutableStateOf(false) }
    var confirmAchieved by remember { mutableStateOf(false) }
    var showAchievedCelebration by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val photoSaved by LifeGoalPhoto.saved.collectAsState()

    LaunchedEffect(reload, photoSaved) {
        goal = withContext(Dispatchers.IO) { store.row(goalId) }
        milestones = withContext(Dispatchers.IO) { store.milestones(goalId) }
    }
    val g = goal ?: return
    val progress = remember(g, milestones) { LifeGoalLogic.progress(g, milestones) }
    val photo = remember(g.photoPath) { g.photoPath?.let { android.graphics.BitmapFactory.decodeFile(it) } }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        // ---------- The why wall: the photo and the reason, first thing seen ----------
        Box(Modifier.fillMaxWidth().aspectRatio(if (photo != null) 1.1f else 2.6f)) {
            if (photo != null) {
                Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0F1235)), startY = 0.3f)))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Ink2))))
            }
            Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x55000000)).clickable(onClickLabel = "Back", onClick = onBack),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x55000000))
                    .clickable(onClickLabel = "Change photo") { actions.onPickGoalPhoto(g.id) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(t(g.horizon.label).uppercase(Locale.ENGLISH), color = Haldi, style = MaterialTheme.typography.labelLarge)
                Text(g.title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                if (g.why.isNotBlank()) Text("“${g.why}”", color = Color(0xFFEEF0FF), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (photo == null) GhostButton(t("Add a photo — who or what this is for")) { actions.onPickGoalPhoto(g.id) }

            // ---------- Progress ----------
            AppCard(title = LifeGoalLogic.dayLabel(g)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (progress.pace) {
                                Pace.Ahead -> "Ahead of schedule"
                                Pace.OnTrack -> "Right on track"
                                Pace.Behind -> "Behind schedule — worth a look"
                                Pace.Unknown -> "Add milestones to see your pace"
                            },
                            style = MaterialTheme.typography.titleMedium, color = paceColor(progress.pace),
                        )
                        val left = LifeGoalLogic.daysLeft(g)
                        Text(if (left >= 0) "$left days to go" else "${-left} days past target", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Text("${g.targetDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE9ECFF))) {
                    Box(Modifier.fillMaxWidth(progress.timeFraction.coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(paceColor(progress.pace)))
                }
            }

            if (g.details.isNotBlank()) AppCard(title = t("The plan")) {
                Text(g.details, style = MaterialTheme.typography.bodyMedium)
            }

            if (g.obstacle.isNotBlank() || g.plan.isNotBlank()) AppCard(title = t("If it gets hard")) {
                if (g.obstacle.isNotBlank()) Text("What's most likely to get in the way: ${g.obstacle}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                if (g.plan.isNotBlank()) Text("If that happens, I will: ${g.plan}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            // ---------- Milestones: the path, not just the destination ----------
            AppCard(title = t("Milestones")) {
                if (milestones.isEmpty()) Text("Break this into a few steps — each one you check off is real progress toward the day above.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                milestones.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                        .clickable { app.launch { withContext(Dispatchers.IO) { store.setMilestoneDone(m.id, !m.done) }; reload++ } }) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(if (m.done) Color(0xFFE3F7EE) else Color(0xFFE9ECFF)),
                            contentAlignment = Alignment.Center) {
                            if (m.done) Icon(Icons.Filled.Check, contentDescription = null, tint = MintDark, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(m.title, style = MaterialTheme.typography.bodyMedium,
                            color = if (m.done) Muted else TextMain, modifier = Modifier.weight(1f))
                    }
                }
                if (milestones.size < LifeGoalLogic.MAX_MILESTONES) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newMilestone, { newMilestone = it.take(LifeGoalLogic.MAX_MILESTONE_TITLE) }, singleLine = true,
                            placeholder = { Text("Add a step") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        SmallPill(t("Add")) {
                            if (newMilestone.isNotBlank()) {
                                val title = newMilestone; newMilestone = ""
                                app.launch { withContext(Dispatchers.IO) { store.addMilestone(g.id, title, LifeGoalLogic.nextMilestoneOrder(milestones)) }; reload++ }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(t("Mark achieved"), dark = false, modifier = Modifier.weight(1f)) { confirmAchieved = true }
                GhostButton(t("Pause this goal"), modifier = Modifier.weight(1f)) { confirmGiveUp = true }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmAchieved) AlertDialog(
        onDismissRequest = { confirmAchieved = false },
        title = { Text("Mark “${g.title}” achieved?") },
        text = { Text("You did it. This moves to your achieved goals; you can always look back on it.") },
        confirmButton = { TextButton(onClick = {
            confirmAchieved = false
            app.launch { withContext(Dispatchers.IO) { store.markAchieved(g.id) }; onChanged(); showAchievedCelebration = true }
        }) { Text("Yes, I did it 🎉", color = MintDark, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmAchieved = false }) { Text(t("Cancel")) } },
    )
    if (showAchievedCelebration) LifeGoalAchievedCelebration(g.title, g.why, onClose = { showAchievedCelebration = false; onBack() })

    // Honest, not punitive: this is a long-term goal, not a craving — one clear reminder of the why, then the
    // person's own call. "Pause" rather than "give up": they can bring it back from Profile any time.
    if (confirmGiveUp) AlertDialog(
        onDismissRequest = { confirmGiveUp = false },
        title = { Text("Before you pause this") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (g.why.isNotBlank()) Text("Your reason: “${g.why}”", fontWeight = FontWeight.Medium)
                Text("${LifeGoalLogic.dayLabel(g)} · ${progress.milestonesDone}/${progress.milestonesTotal} milestones done.")
                Text("Pausing keeps everything you've entered — the plan, the why, your milestones — so you can pick it back up whenever you're ready.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = {
            confirmGiveUp = false; app.launch { withContext(Dispatchers.IO) { store.archive(g.id) }; onChanged(); onBack() }
        }) { Text("Pause it", color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmGiveUp = false }) { Text(t("Keep going"), color = Blue, fontWeight = FontWeight.Bold) } },
    )
}

// ---------------------------------------------------------------- Add flow

@Composable
private fun AddLifeGoalDialog(
    horizon: Horizon,
    onAdd: (title: String, details: String, why: String, obstacle: String, plan: String, target: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableIntStateOf(1) }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var obstacle by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("") }
    val target = remember { LifeGoalLogic.suggestedTargetDate(horizon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t(horizon.label)} goal (${horizon.rangeLabel})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    1 -> {
                        Text("What do you want?", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(title, { title = it.take(LifeGoalLogic.MAX_TITLE) }, singleLine = true,
                            placeholder = { Text("e.g. Run a half marathon") }, modifier = Modifier.fillMaxWidth())
                        Text(t("Why does this matter to you?"), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(why, { why = it.take(LifeGoalLogic.MAX_WHY) }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. So I can run alongside my daughter's first 10K") })
                        Text("You can add a photo of who or what this is for once it's created.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    2 -> {
                        Text(t("What's the plan?"), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(details, { details = it.take(LifeGoalLogic.MAX_DETAILS) }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            placeholder = { Text("What you'll actually do, roughly how") })
                    }
                    else -> {
                        Text("What's most likely to get in the way?", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(obstacle, { obstacle = it.take(LifeGoalLogic.MAX_OBSTACLE) }, singleLine = true,
                            placeholder = { Text("e.g. Getting too tired after work") }, modifier = Modifier.fillMaxWidth())
                        Text("If that happens, what will you do?", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(plan, { plan = it.take(LifeGoalLogic.MAX_PLAN) }, singleLine = true,
                            placeholder = { Text("e.g. Do a 10-minute version instead of skipping it") }, modifier = Modifier.fillMaxWidth())
                        Text("Naming this now makes it easier to follow through later.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                1 -> TextButton(enabled = title.isNotBlank(), onClick = { step = 2 }) { Text("Next") }
                2 -> TextButton(onClick = { step = 3 }) { Text("Next") }
                else -> TextButton(onClick = { onAdd(title, details, why, obstacle, plan, target) }) { Text(t("Save")) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step > 1) step-- else onDismiss() }) { Text(if (step > 1) "Back" else t("Cancel")) }
        },
    )
}
