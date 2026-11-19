package com.bas1minute.sync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.LocalDb.Bucket
import com.bas1minute.sync.StepsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * "Your history": every day ever recorded on this phone, for any period — a week, a month, a year, all time, or
 * any custom range — straight from the local database. No internet, no account, nothing sent.
 *
 * Bars are daily totals for short ranges; for longer ones each bar is the DAILY AVERAGE of a week, month or year,
 * so a 31-day month and a 28-day month compare fairly. Tap a bar for its exact number.
 */

private enum class Metric(val label: String, val column: String) {
    Steps("Steps", "steps"), Distance("Distance", "steps"), Social("Social media", "social_minutes"),
    Screen("Screen time", "screen_minutes"), Sleep("Sleep", "sleep_minutes"),
}

private enum class Period(val label: String) { Week("Week"), Month("Month"), Year("Year"), All("All time"), Custom("Custom") }

private data class HistoryData(
    val bars: List<Pair<LocalDate, Double>>,   // bar start, value (total for DAY, daily average otherwise)
    val bucket: Bucket,
    val total: Long, val recordedDays: Int, val goalDays: Int,
    val best: Pair<LocalDate, Long>?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(app: AppController, actions: PhoneActions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { LocalDb.get(ctx) }
    val today = remember { LocalDate.now() }
    val numbers = remember { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
    val stepCm = app.prefs.stepLengthCm

    var metric by remember { mutableStateOf(Metric.Steps) }
    var period by remember { mutableStateOf(Period.Month) }
    var anchor by remember { mutableStateOf(today) }                       // any day inside the shown period
    var custom by remember { mutableStateOf(today.minusDays(29) to today) }
    var pickRange by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var first by remember { mutableStateOf<LocalDate?>(null) }
    var footer by remember { mutableStateOf("") }
    var historyGranted by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf<HistoryData?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            first = db.firstStepsDay()
            val days = db.stepsDayCount()
            val mb = db.sizeBytes() / 1_048_576.0
            footer = (first?.let { "Saved on this phone since ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · " } ?: "") +
                "$days day${if (days == 1) "" else "s"} · ${"%.1f".format(mb)} MB"
        }
        historyGranted = StepsReader(ctx).historyGranted() || StepsReader(ctx).healthConnectStatus() != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
    }

    // The range for the chosen period
    val (from, to) = when (period) {
        Period.Week -> anchor.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        Period.Month -> anchor.withDayOfMonth(1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
        Period.Year -> anchor.withDayOfYear(1).let { it to it.withDayOfYear(it.lengthOfYear()) }
        Period.All -> (first ?: today.minusDays(29)) to today
        Period.Custom -> custom
    }
    val span = ChronoUnit.DAYS.between(from, to) + 1
    val bucket = when (period) {
        Period.Week, Period.Month -> Bucket.DAY
        Period.Year -> Bucket.MONTH
        else -> when { span <= 62 -> Bucket.DAY; span <= 370 -> Bucket.WEEK; span <= 3 * 366 -> Bucket.MONTH; else -> Bucket.YEAR }
    }
    val goal: Long = when (metric) {
        Metric.Steps, Metric.Distance -> app.home.stepGoal.toLong()
        Metric.Social -> app.home.screenLimit.toLong()
        else -> Long.MAX_VALUE
    }

    LaunchedEffect(metric, from, to, bucket) {
        selected = null
        data = withContext(Dispatchers.IO) {
            val pts = db.history(metric.column, from, to, bucket, goal)
            HistoryData(
                bars = pts.map { p -> p.start to if (bucket == Bucket.DAY) p.total.toDouble() else p.total.toDouble() / p.days.coerceAtLeast(1) },
                bucket = bucket,
                total = pts.sumOf { it.total }, recordedDays = pts.sumOf { it.days }, goalDays = pts.sumOf { it.goalDays },
                best = db.bestDay(metric.column, from, to),
            )
        }
    }

    fun fmt(v: Double): String = when (metric) {
        Metric.Steps -> numbers.format(v.toLong())
        Metric.Distance -> com.bas1minute.sync.Distance.format(v * stepCm / 100_000.0)
        else -> hm(v.toInt())
    }
    fun label(d: LocalDate, b: Bucket): String = when (b) {
        Bucket.DAY -> d.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))
        Bucket.WEEK -> "Week of " + d.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        Bucket.MONTH -> d.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        Bucket.YEAR -> d.year.toString()
    }
    val rangeText = when (period) {
        Period.Week -> "${from.format(DateTimeFormatter.ofPattern("d MMM"))} – ${to.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        Period.Month -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        Period.Year -> from.year.toString()
        else -> "${from.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} – ${to.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
    }
    val canStep = period in setOf(Period.Week, Period.Month, Period.Year)
    fun shift(n: Long) {
        anchor = when (period) { Period.Week -> anchor.plusWeeks(n); Period.Month -> anchor.plusMonths(n); else -> anchor.plusYears(n) }
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.fillMaxWidth().background(Ink).statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = androidx.compose.ui.graphics.Color.White) }
            Text(t("Your history"), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // What to show
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric.entries.forEach { m ->
                    val on = m == metric
                    Text(t(m.label), style = MaterialTheme.typography.labelLarge, color = if (on) Ink else TextMain,
                        modifier = Modifier.tactileClickable(RoundedCornerShape(50)) { metric = m }
                            .background(if (on) Haldi else CardBg).padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            Segmented(Period.entries.map { t(it.label) }, Period.entries.indexOf(period)) { i ->
                period = Period.entries[i]
                if (period == Period.Custom) pickRange = true
                anchor = today
            }
            // Which stretch of time
            Row(verticalAlignment = Alignment.CenterVertically) {
                val firstDay = first
                if (canStep) IconButton(onClick = { shift(-1) }, enabled = firstDay == null || from > firstDay) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Earlier")
                }
                Text(rangeText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = if (canStep) 0.dp else 4.dp))
                if (canStep) IconButton(onClick = { shift(1) }, enabled = to < today) { Icon(Icons.Filled.ChevronRight, contentDescription = "Later") }
                if (period == Period.Custom) TextButton(onClick = { pickRange = true }) { Text(t("Change"), color = Blue) }
            }

            val d = data
            AppCard {
                if (d == null) Text(t("Loading…"), color = Muted)
                else if (d.recordedDays == 0) Text("Nothing recorded for this period yet.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                else {
                    Text(if (d.bucket == Bucket.DAY) t("Each day") else "${t("Daily average per")} ${d.bucket.name.lowercase()}",
                        style = MaterialTheme.typography.labelMedium, color = Muted)
                    HistoryBars(d.bars.map { it.second }, goal = if (goal == Long.MAX_VALUE) null else goal.toDouble(), selected) { selected = it }
                    val sel = selected?.let { d.bars.getOrNull(it) }
                    Text(sel?.let { "${label(it.first, d.bucket)} · ${fmt(it.second)}${if (d.bucket != Bucket.DAY) " a day" else ""}" }
                        ?: t("Tap a bar to see its number"), style = MaterialTheme.typography.bodyMedium, color = if (sel == null) Muted else TextMain)
                }
            }
            if (d != null && d.recordedDays > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HistoryStat(t("Total"), fmt(d.total.toDouble()), Modifier.weight(1f))
                    HistoryStat(t("Daily average"), fmt(d.total.toDouble() / d.recordedDays), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HistoryStat(t("Best day"), d.best?.let { fmt(it.second.toDouble()) } ?: "—", Modifier.weight(1f),
                        d.best?.first?.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                    if (goal != Long.MAX_VALUE && metric != Metric.Social) HistoryStat(t("Goal days"), "${d.goalDays}/${d.recordedDays}", Modifier.weight(1f))
                    else HistoryStat(t("Days recorded"), "${d.recordedDays}", Modifier.weight(1f))
                }
            }

            // Personal records and badges (worked out on the phone from every saved day)
            BadgesSection(app)

            // Where it lives, and how to bring in older steps
            AppCard {
                Text(footer, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text("Kept forever on this phone, in a compact form. Works offline; nothing here is sent unless you turn on Online features.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                if (!historyGranted) {
                    GhostButton(t("Import older steps from Health Connect")) { actions.onAskHealthHistory() }
                    Text("Brings in steps saved before you installed Bas 1 Minute, a month at a time in the background.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }

    if (pickRange) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = custom.first.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = custom.second.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null, onClick = {
                    val s = state.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    val e = state.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: s
                    if (s != null && e != null) custom = minOf(s, e) to minOf(maxOf(s, e), today)
                    period = Period.Custom
                    pickRange = false
                }) { Text(t("Show")) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(t("Cancel")) } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(520.dp))
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String, modifier: Modifier, sub: String? = null) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(CardBg).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextMain)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

/** Simple bar chart: haldi bars, a dashed goal line, the tapped bar in ink. */
@Composable
private fun HistoryBars(values: List<Double>, goal: Double?, selected: Int?, onSelect: (Int) -> Unit) {
    val max = maxOf(values.maxOrNull() ?: 0.0, goal ?: 0.0).coerceAtLeast(1.0)
    val lineColor = Muted
    Canvas(
        Modifier.fillMaxWidth().height(180.dp)
            .semantics { contentDescription = "Bar chart with ${values.size} bars" }
            .pointerInput(values) {
                detectTapGestures { pos ->
                    if (values.isEmpty()) return@detectTapGestures
                    val i = (pos.x / (size.width / values.size.toFloat())).toInt().coerceIn(0, values.size - 1)
                    onSelect(i)
                }
            },
    ) {
        val n = values.size
        if (n == 0) return@Canvas
        val slot = size.width / n
        val barW = (slot * 0.7f).coerceAtMost(28.dp.toPx()).coerceAtLeast(1.5f)
        values.forEachIndexed { i, v ->
            val h = (v / max * size.height).toFloat()
            drawRoundRect(
                color = if (i == selected) Ink else if (goal != null && v >= goal) Haldi else Haldi.copy(alpha = 0.55f),
                topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 3f, barW / 3f),
            )
        }
        goal?.let {
            val y = (size.height - it / max * size.height).toFloat()
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
    }
}


@Composable
private fun BadgesSection(app: AppController) {
    val ctx = LocalContext.current
    val numbers = remember { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
    var data by remember { mutableStateOf<Pair<com.bas1minute.sync.achieve.Records, List<com.bas1minute.sync.achieve.Badge>>?>(null) }
    var open by remember { mutableStateOf<com.bas1minute.sync.achieve.Badge?>(null) }
    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) {
            val days = com.bas1minute.sync.achieve.AchievementStore.stepDays(ctx)
            val goal = app.prefs.stepGoal
            com.bas1minute.sync.achieve.Achievements.records(days, goal, LocalDate.now()) to
                com.bas1minute.sync.achieve.Achievements.badges(days, goal)
        }
    }
    val d = data ?: return
    val (r, badges) = d
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    AppCard(title = t("Personal records")) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HistoryStat(t("Best day"), r.bestDay?.let { numbers.format(it.steps) } ?: "—", Modifier.weight(1f), r.bestDay?.date?.format(fmt))
            HistoryStat(t("Best week"), if (r.bestWeekSteps > 0) numbers.format(r.bestWeekSteps) else "—", Modifier.weight(1f),
                r.bestWeekStart?.let { "Week of " + it.format(fmt) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HistoryStat(t("Current streak"), "${r.currentStreak} ${if (r.currentStreak == 1) "day" else "days"}", Modifier.weight(1f), "Longest: ${r.longestStreak}")
            HistoryStat(t("All-time steps"), numbers.format(r.lifetimeSteps), Modifier.weight(1f), "${r.goalDays} goal days")
        }
    }
    val earned = badges.count { it.earnedOn != null }
    AppCard(title = "${t("Badges")} · $earned/${badges.size}") {
        badges.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { b ->
                    val on = b.earnedOn != null
                    Column(
                        Modifier.weight(1f).tactileClickable(RoundedCornerShape(16.dp), onClickLabel = b.title) { open = b }
                            .background(if (on) androidx.compose.ui.graphics.Color(0xFFFFF3C8) else Paper)
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(b.emoji, style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.alpha(if (on) 1f else 0.3f))
                        Text(b.title, style = MaterialTheme.typography.labelSmall, color = if (on) Ink else Muted, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (!on) androidx.compose.material3.LinearProgressIndicator(
                            progress = { b.progress }, color = Haldi, trackColor = CardLine,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 6.dp, end = 6.dp).height(3.dp),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    open?.let { b ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = null },
            icon = { Text(b.emoji, style = MaterialTheme.typography.displayMedium) },
            title = { Text(b.title) },
            text = {
                Text(b.earnedOn?.let { "${b.detail}. Earned on ${it.format(fmt)}." }
                    ?: "${b.detail}. You’re ${(b.progress * 100).toInt()}% of the way there.")
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(t("Close"), color = Ink) } },
        )
    }
}
