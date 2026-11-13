package com.bas1minute.sync.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.diary.Category
import com.bas1minute.sync.diary.DiaryEntry
import com.bas1minute.sync.diary.DiaryLogic
import com.bas1minute.sync.diary.DiaryPhoto
import com.bas1minute.sync.diary.DiaryStore
import com.bas1minute.sync.diary.EntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The diary: a slam book of your own life (first school, a daughter's birth, the first job, a college
 * memory — whatever belongs in it) plus a one-a-day reflection on effort and satisfaction toward your goals.
 * Everything here stays on this phone.
 */
@Composable
fun DiaryScreen(app: AppController, actions: PhoneActions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { DiaryStore(ctx) }
    val today = remember { LocalDate.now() }
    var section by remember { mutableStateOf("today") }   // "today" (reflection) or "timeline" (milestones)
    var timeline by remember { mutableStateOf<List<DiaryEntry>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var showAddMilestone by remember { mutableStateOf(false) }
    val photoSaved by DiaryPhoto.saved.collectAsState()

    LaunchedEffect(reload, photoSaved) {
        timeline = withContext(Dispatchers.IO) { store.timeline() }
    }
    val onThisDay = remember(timeline, today) { DiaryLogic.onThisDay(timeline, today) }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Ink, Ink2))).statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Ink3).clickable(onClickLabel = "Back", onClick = onClose),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Diary"), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("Your life's milestones, and how today actually felt", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                }
            }
            Segmented(listOf(t("Today"), t("Timeline")), if (section == "timeline") 1 else 0) { section = if (it == 1) "timeline" else "today" }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onThisDay.isNotEmpty()) OnThisDayCard(onThisDay, today)

            if (section == "today") {
                TodayReflectionCard(app, store, today) { reload++ }
                GhostButton(t("Add a life milestone")) { showAddMilestone = true }
            } else {
                TimelineSection(app, store, timeline, today) { reload++ }
                GhostButton(t("Add a life milestone")) { showAddMilestone = true }
            }
            Text("Your diary stays on this phone only — never sent anywhere, even if you share steps or screen time elsewhere in the app.",
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    if (showAddMilestone) AddMilestoneDialog(
        onAdd = { date, title, category, story ->
            app.launch {
                val id = withContext(Dispatchers.IO) { store.addMilestone(date, title, category, story) }
                showAddMilestone = false; reload++
                actions.onPickDiaryPhoto(id)
            }
        },
        onDismiss = { showAddMilestone = false },
    )
}

@Composable
private fun OnThisDayCard(entries: List<Pair<DiaryEntry, Int>>, today: LocalDate) {
    val (entry, years) = entries.first()
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📅", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("On this day", style = MaterialTheme.typography.labelLarge, color = Violet)
                Text("${entry.category.emoji} ${entry.title} — $years ${if (years == 1) "year" else "years"} ago today",
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        if (entry.story.isNotBlank()) Text(entry.story, style = MaterialTheme.typography.bodyMedium, color = Muted)
        if (entries.size > 1) Text("+${entries.size - 1} more from this day", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun TodayReflectionCard(app: AppController, store: DiaryStore, today: LocalDate, onSaved: () -> Unit) {
    var existing by remember { mutableStateOf<DiaryEntry?>(null) }
    var effort by remember { mutableIntStateOf(3) }
    var satisfaction by remember { mutableIntStateOf(3) }
    var note by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var streak by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val e = withContext(Dispatchers.IO) { store.reflectionOn(today) }
        existing = e
        e?.let { effort = it.effort ?: 3; satisfaction = it.satisfaction ?: 3; note = it.story; saved = true }
        streak = withContext(Dispatchers.IO) { DiaryLogic.reflectionStreak(store.reflectionDates(), today).current }
    }

    AppCard(title = t("Today's reflection")) {
        if (streak > 1) Text("$streak days in a row you've checked in. Keep it going.", style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(t("How hard did you work toward your goals today?"), style = MaterialTheme.typography.labelLarge)
        RatingRow(effort, listOf("Barely", "A little", "Some", "A lot", "Gave it everything")) { effort = it }
        Text(t("How satisfied are you with today?"), style = MaterialTheme.typography.labelLarge)
        RatingRow(satisfaction, listOf("Rough day", "Not great", "Okay", "Good", "Great day")) { satisfaction = it }
        OutlinedTextField(note, { note = it.take(DiaryLogic.MAX_STORY) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            placeholder = { Text("Anything you want to remember about today") })
        BigButton(if (saved) t("Update today's reflection") else t("Save today's reflection"), dark = false) {
            app.launch {
                withContext(Dispatchers.IO) { store.setTodayReflection(effort, satisfaction, note, today) }
                saved = true; onSaved()
            }
        }
    }
}

@Composable
private fun RatingRow(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        (1..5).forEach { n ->
            val on = n == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Ink else Paper)
                    .clickable { onSelect(n) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$n", color = if (on) Haldi else TextMain, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Text(labels[selected - 1], style = MaterialTheme.typography.bodySmall, color = Muted)
}

@Composable
private fun TimelineSection(app: AppController, store: DiaryStore, entries: List<DiaryEntry>, today: LocalDate, onChanged: () -> Unit) {
    val milestones = remember(entries) { entries.filter { it.type == EntryType.Milestone } }
    if (milestones.isEmpty()) {
        AppCard {
            Text("📔", style = MaterialTheme.typography.displayMedium)
            Text("Your story starts here", style = MaterialTheme.typography.titleLarge)
            Text("First school, a slam-book page, a daughter's birth, your first job, a college memory — add whatever belongs in your life's story.",
                style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        return
    }
    milestones.groupBy { it.date.year }.toSortedMap(compareByDescending { it }).forEach { (year, rows) ->
        Text("$year", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        rows.forEach { entry -> MilestoneRow(app, store, entry, today, onChanged) }
    }
}

@Composable
private fun MilestoneRow(app: AppController, store: DiaryStore, entry: DiaryEntry, today: LocalDate, onChanged: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val photo = remember(entry.photoPath) { entry.photoPath?.let { android.graphics.BitmapFactory.decodeFile(it) } }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).clickable { open = !open }.padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE9ECFF)), contentAlignment = Alignment.Center) {
                Text(entry.category.emoji, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = if (open) 3 else 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))} · ${DiaryLogic.agoLabel(entry.date, today)}",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            if (open) {
                if (entry.story.isNotBlank()) Text(entry.story, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { confirmDelete = true }) { Text(t("Remove"), color = Danger, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove “${entry.title}”?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            app.launch { withContext(Dispatchers.IO) { store.delete(entry.id) }; onChanged() }
        }) { Text(t("Remove"), color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel")) } },
    )
}

// ---------------------------------------------------------------- Add milestone

@Composable
private fun AddMilestoneDialog(
    onAdd: (date: LocalDate, title: String, category: Category, story: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableIntStateOf(1) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.Other) }
    var year by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var month by remember { mutableStateOf((LocalDate.now().monthValue).toString()) }
    var day by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }
    var story by remember { mutableStateOf("") }

    fun parsedDate(): LocalDate? = runCatching {
        LocalDate.of(year.toInt(), month.toInt().coerceIn(1, 12), 1).withDayOfMonth(day.toInt().coerceIn(1, 28))
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add a life milestone")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step == 1) {
                    OutlinedTextField(title, { title = it.take(DiaryLogic.MAX_TITLE) }, singleLine = true,
                        placeholder = { Text("e.g. My daughter was born") }, modifier = Modifier.fillMaxWidth())
                    Text(t("What kind of moment is this?"), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Category.entries.take(5).forEach { c -> CategoryChip(c, c == category) { category = c } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Category.entries.drop(5).forEach { c -> CategoryChip(c, c == category) { category = c } }
                    }
                    Text(t("When did it happen?"), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(day, { day = it.filter { ch -> ch.isDigit() }.take(2) }, singleLine = true, label = { Text("Day") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(month, { month = it.filter { ch -> ch.isDigit() }.take(2) }, singleLine = true, label = { Text("Month") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(year, { year = it.filter { ch -> ch.isDigit() }.take(4) }, singleLine = true, label = { Text("Year") }, modifier = Modifier.weight(1f))
                    }
                    Text("Not sure of the exact day? An approximate date is fine.", style = MaterialTheme.typography.bodySmall, color = Muted)
                } else {
                    Text(t("Tell the story"), style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(story, { story = it.take(DiaryLogic.MAX_STORY) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        placeholder = { Text("What happened, how it felt, who was there") })
                    Text("You can add a photo right after saving.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        },
        confirmButton = {
            if (step == 1) TextButton(enabled = title.isNotBlank() && parsedDate() != null, onClick = { step = 2 }) { Text("Next") }
            else TextButton(onClick = { parsedDate()?.let { onAdd(it, title, category, story) } }) { Text(t("Save")) }
        },
        dismissButton = {
            TextButton(onClick = { if (step > 1) step-- else onDismiss() }) { Text(if (step > 1) "Back" else t("Cancel")) }
        },
    )
}

@Composable
private fun CategoryChip(c: Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Ink else Paper).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(c.emoji, style = MaterialTheme.typography.titleMedium)
        Text(c.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else Muted)
    }
}

// ---------------------------------------------------------------- Today card

/** The doorway on Today: whether today's reflection is done, and a hint of "on this day" if there is one. */
@Composable
fun DiaryCard(app: AppController) {
    val ctx = LocalContext.current
    val store = remember { DiaryStore(ctx) }
    var doneToday by remember { mutableStateOf(false) }
    var onThisDayTitle by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }

    LaunchedEffect(app.diaryOpen) {
        doneToday = withContext(Dispatchers.IO) { store.reflectionOn(today) != null }
        onThisDayTitle = withContext(Dispatchers.IO) { DiaryLogic.onThisDay(store.milestones(), today).firstOrNull()?.first?.title }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).clickable { app.diaryOpen = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE9D9FF)), contentAlignment = Alignment.Center) {
            Text("📔", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Diary"), style = MaterialTheme.typography.titleMedium)
            Text(
                onThisDayTitle?.let { "On this day: $it" }
                    ?: if (doneToday) "Today's reflection is done" else "How hard did you work today? Take a minute to reflect",
                style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallPill(if (doneToday) t("Open") else t("Reflect")) { app.diaryOpen = true }
    }
}
