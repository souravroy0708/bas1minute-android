package com.bas1minute.sync.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bas1minute.sync.AppController
import com.bas1minute.sync.track.ActivityRow
import com.bas1minute.sync.track.ActivityType
import com.bas1minute.sync.track.Fmt
import com.bas1minute.sync.track.Gpx
import com.bas1minute.sync.track.LatLng
import com.bas1minute.sync.track.LiveTrack
import com.bas1minute.sync.track.MapHandle
import com.bas1minute.sync.track.RouteCard
import com.bas1minute.sync.track.RouteMap
import com.bas1minute.sync.track.TrackPoint
import com.bas1minute.sync.track.TrackStore
import com.bas1minute.sync.track.TrackSource
import com.bas1minute.sync.track.Stride
import com.bas1minute.sync.track.TrackSummary
import com.bas1minute.sync.track.TrackingService
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Phone-side actions the walk/run screens need (permissions, GPS switch, files). Wired in MainActivity. */
data class TrackActions(
    /** Decides how to record (GPS if chosen and allowed, else steps, else time), then sets app.trackCountdown. */
    val onReadyToStart: () -> Unit = {},
    val onSaveImage: (Bitmap, String) -> Unit = { _, _ -> },
    val onSaveGpx: (String, String) -> Unit = { _, _ -> },
    val onOpenAppSettings: () -> Unit = {},
)

private fun icon(t: ActivityType): ImageVector = when (t) {
    ActivityType.Walk -> Icons.Filled.DirectionsWalk
    ActivityType.Run -> Icons.Filled.DirectionsRun
    ActivityType.Cycle -> Icons.Filled.DirectionsBike
}

private fun paceLabel(t: ActivityType) = if (t == ActivityType.Cycle) "Avg speed" else "Avg pace"
private fun paceValue(t: ActivityType, speedMs: Double) = if (t == ActivityType.Cycle) "${Fmt.kmh(speedMs)} km/h" else "${Fmt.pace(speedMs)} /km"

/** The Move tab: start a walk, run or ride; this week; personal bests; history; a finished activity. */
@Composable
fun MoveTab(app: AppController, actions: PhoneActions) {
    val live by LiveTrack.state.collectAsState()
    val detail = app.trackDetailId
    when {
        live.active -> {
            BackHandler { app.tab = com.bas1minute.sync.Tab.Today; app.message = "Still recording. It’s on the Move tab and in the notification." }
            LiveView(app, live, onClose = { app.tab = com.bas1minute.sync.Tab.Today })
        }
        detail != null -> {
            BackHandler { app.trackDetailId = null }
            ActivityDetail(app, detail, actions, onBack = { app.trackDetailId = null })
        }
        else -> MoveHome(app, actions.track)
    }
    // Recording couldn't start (permission removed, Android refused): say why instead of doing nothing
    val trackError by LiveTrack.error.collectAsState()
    LaunchedEffect(trackError) {
        trackError?.let { app.message = it; LiveTrack.error.value = null }
    }
    if (app.trackCountdown) Countdown(onDone = {
        app.trackCountdown = false
        // GPS, steps or time: the service uses the best one that's actually possible right now
        TrackingService.start(app.ctxForPush, ActivityType.of(app.prefs.trackType), app.prefs.trackAutoPause, app.prefs.trackVoice, app.trackSource)
    }, onCancel = { app.trackCountdown = false })
}

/** Everything Move shows, read once from SQLite. */
private data class MoveData(
    val history: List<ActivityRow>,
    val weekKm: Double, val weekMs: Long, val weekCount: Int, val activeDays: Set<Int>,
    val bests: List<Pair<String, String>>, val totalKm: Double,
    val unfinished: ActivityRow?,
)

private fun loadMove(store: TrackStore): MoveData {
    val all = store.list(500)
    val zone = ZoneId.systemDefault()
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = all.filter { !Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().isBefore(monday) }
    val bests = buildList {
        ActivityType.entries.forEach { t ->
            all.filter { it.type == t }.maxByOrNull { it.distanceM }?.let { add("Longest ${t.label.lowercase()}" to "${Fmt.km(it.distanceM)} km") }
        }
        all.filter { it.type != ActivityType.Cycle && it.distanceM >= 1000 }.maxByOrNull { it.avgSpeedMs }
            ?.let { add("Best pace (1 km+)" to "${Fmt.pace(it.avgSpeedMs)} /km") }
        all.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .maxByOrNull { (_, v) -> v.sumOf { it.distanceM } }?.let { (_, v) -> add("Biggest week" to "${Fmt.km(v.sumOf { it.distanceM })} km") }
    }
    return MoveData(
        history = all.take(60),
        weekKm = week.sumOf { it.distanceM } / 1000, weekMs = week.sumOf { it.movingMs }, weekCount = week.size,
        activeDays = week.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).dayOfWeek.value }.toSet(),
        bests = bests, totalKm = all.sumOf { it.distanceM } / 1000,
        unfinished = store.unfinished(),
    )
}

private fun groupLabel(startedAt: Long): String {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return when {
        !d.isBefore(monday) -> "This week"
        !d.isBefore(monday.minusWeeks(1)) -> "Last week"
        else -> d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    }
}

@Composable
private fun MoveHome(app: AppController, actions: TrackActions) {
    val ctx = LocalContext.current
    val store = remember { TrackStore(ctx) }
    var data by remember { mutableStateOf<MoveData?>(null) }
    var type by remember { mutableStateOf(ActivityType.of(app.prefs.trackType)) }
    var settings by remember { mutableStateOf(false) }
    var goalEdit by remember { mutableStateOf(false) }
    var useGps by remember { mutableStateOf(app.prefs.trackUseGps) }
    LaunchedEffect(app.trackVersion) { data = withContext(Dispatchers.IO) { runCatching { loadMove(store) }.getOrNull() } }
    val goalKm = app.prefs.weeklyKmGoal

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t("Move"), style = MaterialTheme.typography.headlineLarge)
                Text("Walks, runs and rides, with or without GPS", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            RoundIcon(Icons.Filled.Tune, "Tracking settings", light = true) { settings = true }
        }
        val d = data
        d?.unfinished?.let { u -> UnfinishedCard(app, store, u) { app.trackVersion++ } }

        // This week, against a weekly distance goal
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink).clickable { goalEdit = true }.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("This week", color = MutedOnDark, style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(String.format(Locale.US, "%.1f", d?.weekKm ?: 0.0), color = Color.White,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp, lineHeight = 52.sp))
                        Text(" / $goalKm km", color = Haldi, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                val pct = (((d?.weekKm ?: 0.0) / goalKm).coerceIn(0.0, 1.0) * 100).toInt()
                Text(if (pct >= 100) "Goal ✓" else "$pct%", color = Ink, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Haldi).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Ink3)) {
                Box(Modifier.fillMaxWidth((((d?.weekKm ?: 0.0) / goalKm).coerceIn(0.0, 1.0)).toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(Haldi))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeekStat("Activities", "${d?.weekCount ?: 0}", Modifier.weight(1f))
                WeekStat("Time", Fmt.duration(d?.weekMs ?: 0), Modifier.weight(1f))
                WeekStat("All time", "${Fmt.km((d?.totalKm ?: 0.0) * 1000)} km", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                    val on = (i + 1) in (d?.activeDays ?: emptySet())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(if (on) Haldi else Ink3), contentAlignment = Alignment.Center) {
                            if (on) Text("✓", color = Ink, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(l, color = MutedOnDark, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Start: one tap. Location and GPS checks, then 3-2-1.
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityType.entries.forEach { tp ->
                    val on = tp == type
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (on) Ink else Paper)
                            .clickable { type = tp; app.prefs.trackType = tp.id }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icon(tp), contentDescription = null, tint = if (on) Haldi else TextMain)
                        Text(t(tp.label), color = if (on) Color.White else TextMain, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            // GPS is a choice, not a requirement: without it, km, time and pace come from steps
            ToggleRow(
                "Map with GPS",
                when {
                    useGps -> "Your route on a map. Uses location only while recording."
                    type == ActivityType.Cycle -> "No location used. Records your time; add the distance when you finish."
                    else -> "No location used. Km, time and pace from your steps."
                },
                useGps,
            ) { useGps = it; app.prefs.trackUseGps = it }
            BigButton("Start ${type.label.lowercase()}", dark = false) { actions.onReadyToStart() }
            Text(listOf(if (app.prefs.trackAutoPause) "Auto-pause on" else "Auto-pause off",
                if (app.prefs.trackVoice) "voice every km" else "voice off", "works without internet").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.clickable { settings = true })
        }

        if (!d?.bests.isNullOrEmpty()) {
            AppCard(title = "Personal bests") {
                d!!.bests.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (l, v) ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Paper).padding(12.dp)) {
                                Text(l, style = MaterialTheme.typography.bodySmall, color = Muted)
                                Text(v, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        when {
            d == null -> Text("Loading…", color = Muted)
            d.history.isEmpty() -> AppCard {
                Text("🏃", style = MaterialTheme.typography.displayMedium)
                Text("Your first walk is one tap away", style = MaterialTheme.typography.titleLarge)
                Text("Tap Start. Distance, time, pace and kilometre splits are saved here, with your route on a map when GPS is on.",
                    style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            else -> d.history.groupBy { groupLabel(it.startedAt) }.forEach { (label, rows) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${Fmt.km(rows.sumOf { it.distanceM })} km", style = MaterialTheme.typography.labelLarge, color = Muted)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardBg).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    rows.forEach { row -> HistoryRow(row) { app.trackDetailId = row.id } }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (settings) {
        var autoPause by remember { mutableStateOf(app.prefs.trackAutoPause) }
        var voice by remember { mutableStateOf(app.prefs.trackVoice) }
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Tracking settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow("Auto-pause", "Stops the clock at signals and crossings", autoPause) { autoPause = it; app.prefs.trackAutoPause = it }
                    ToggleRow("Voice every km", "Hear distance and time without looking", voice) { voice = it; app.prefs.trackVoice = it }
                }
            },
            confirmButton = { TextButton(onClick = { settings = false; app.trackVersion++ }) { Text("Done") } },
        )
    }
    if (goalEdit) {
        var goal by remember { mutableStateOf(goalKm) }
        AlertDialog(
            onDismissRequest = { goalEdit = false },
            title = { Text("Weekly distance goal") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(5, 10, 15, 20, 30, 50).forEach { g ->
                        Text("$g km", style = MaterialTheme.typography.labelLarge, color = if (g == goal) Color.White else TextMain,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (g == goal) Ink else Paper)
                                .clickable { goal = g }.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { app.prefs.weeklyKmGoal = goal; goalEdit = false; app.trackVersion++ }) { Text(t("Save")) } },
            dismissButton = { TextButton(onClick = { goalEdit = false }) { Text(t("Cancel"), color = Muted) } },
        )
    }
}

@Composable
private fun WeekStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Ink2).padding(10.dp)) {
        Text(label, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun UnfinishedCard(app: AppController, store: TrackStore, u: ActivityRow, changed: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFF3C8)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your ${u.type.label.lowercase()} wasn’t finished", style = MaterialTheme.typography.titleMedium, color = Ink)
        Text("Android closed the app while it was recording. ${Fmt.km(u.distanceM)} km is saved.",
            style = MaterialTheme.typography.bodySmall, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallPill("Resume") { TrackingService.recover(ctx, u.id, app.prefs.trackVoice, u.source) }
            SmallPill("Save") {
                app.launch {
                    withContext(Dispatchers.IO) {
                        val worth = if (u.source.hasMap) store.points(u.id).size >= 2 else u.movingMs >= 30_000
                        if (worth) store.finish(u.id, u.type, null, System.currentTimeMillis()) else store.delete(u.id)
                    }
                    changed(); app.trackDetailId = u.id
                }
            }
            SmallPill("Discard") { app.launch { withContext(Dispatchers.IO) { store.delete(u.id) }; changed() } }
        }
        Text("Tip: Profile → Battery stops Android from closing the app during long walks.", style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

@Composable
private fun HistoryRow(r: ActivityRow, onClick: () -> Unit) {
    val density = LocalDensity.current
    val px = with(density) { 52.dp.roundToPx() }
    val thumb = remember(r.id, r.preview.size) { RouteCard.thumbnail(r.preview, px).asImageBitmap() }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Open ${r.title}", onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (r.source.hasMap) Image(thumb, contentDescription = null, modifier = Modifier.size(52.dp))
        else NoRouteThumb(r, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon(r.type), contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)),
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (r.distanceM > 0) "${Fmt.km(r.distanceM)} km" else Fmt.duration(r.movingMs), style = MaterialTheme.typography.titleMedium)
            Text(if (r.distanceM > 0) "${Fmt.duration(r.movingMs)} · ${paceValue(r.type, r.avgSpeedMs)}" else "time only",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text("  ›", style = MaterialTheme.typography.titleLarge, color = Muted)
    }
}

/** In place of a route thumbnail: the activity icon, and a footprint or a clock for how it was recorded. */
@Composable
private fun NoRouteThumb(r: ActivityRow, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Ink), contentAlignment = Alignment.Center) {
        Icon(if (r.source == TrackSource.Timer) Icons.Filled.Timer else icon(r.type), contentDescription = null, tint = Haldi, modifier = Modifier.size(26.dp))
    }
}

/** How an activity was recorded, in words. */
private fun sourceLabel(s: TrackSource) = when (s) {
    TrackSource.Gps -> "GPS"
    TrackSource.Steps -> "No GPS · km from steps"
    TrackSource.Timer -> "No GPS · time only"
}

// ------------------------------------------------------------------ Live

@Composable
private fun LiveView(app: AppController, live: LiveTrack.State, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var follow by remember { mutableStateOf(true) }
    var confirmFinish by remember { mutableStateOf(false) }
    val s = live.stats
    val paused = live.status == LiveTrack.Status.Paused

    val withMap = live.source.hasMap
    val timeOnly = live.source == TrackSource.Timer

    Box(Modifier.fillMaxSize().background(Paper)) {
        if (withMap) RouteMap(
            segments = live.segments, current = live.current, start = live.start, follow = follow,
            dark = ThemeState.dark, modifier = Modifier.fillMaxSize(), onUserMoved = { follow = false },
        ) else NoMapBackdrop(live, Modifier.fillMaxSize())
        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back, keeps recording", onClose)
            Spacer(Modifier.width(8.dp))
            when {
                paused -> StatusPill("Paused", Haldi, Ink)
                s.autoPaused -> StatusPill("Auto-paused", Haldi, Ink)
                else -> StatusPill("● Recording", Ink, Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (withMap) GpsChip(live.accuracyM) else StatusPill(sourceLabel(live.source), Ink, Color.White)
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (withMap && !follow) {
                Box(Modifier.align(Alignment.End).padding(12.dp)) {
                    RoundIcon(Icons.Filled.MyLocation, "Back to my position") { follow = true }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Ink)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Time only: the clock is the big number. Otherwise distance (from GPS or from steps).
                val bigValue = if (timeOnly) Fmt.duration(s.movingMs) else Fmt.km(s.distanceM)
                val bigUnit = if (timeOnly) "time" else "km"
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = if (timeOnly) "Time ${Fmt.duration(s.movingMs)}" else "${Fmt.km(s.distanceM)} kilometres"
                }) {
                    Text(bigValue, color = Color.White, style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 64.sp))
                    Spacer(Modifier.width(8.dp))
                    Text(bigUnit, color = Haldi, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Icon(icon(live.type), contentDescription = live.type.label, tint = MutedOnDark, modifier = Modifier.size(30.dp).padding(bottom = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        timeOnly -> {
                            LiveStat("Total", Fmt.duration(s.elapsedMs), Modifier.weight(1f))
                            LiveStat("Started", java.time.Instant.ofEpochMilli(live.startedAt).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)), Modifier.weight(1f))
                        }
                        live.source == TrackSource.Steps -> {
                            LiveStat("Time", Fmt.duration(s.movingMs), Modifier.weight(1f))
                            LiveStat("Pace now", Fmt.pace(s.currentSpeedMs), Modifier.weight(1f))
                            LiveStat("Steps", String.format(Locale.US, "%,d", s.steps), Modifier.weight(1f))
                        }
                        else -> {
                            LiveStat("Time", Fmt.duration(s.movingMs), Modifier.weight(1f))
                            if (live.type == ActivityType.Cycle) LiveStat("Speed", "${Fmt.kmh(s.currentSpeedMs)}", Modifier.weight(1f))
                            else LiveStat("Pace now", Fmt.pace(s.currentSpeedMs), Modifier.weight(1f))
                            LiveStat(if (live.type == ActivityType.Cycle) "Avg km/h" else "Avg pace", if (live.type == ActivityType.Cycle) Fmt.kmh(s.avgSpeedMs) else Fmt.pace(s.avgSpeedMs), Modifier.weight(1f))
                        }
                    }
                }
                when {
                    timeOnly -> Text(if (live.type == ActivityType.Cycle) "Rides without GPS record time. Add the distance when you finish."
                        else "Allow Physical activity next time to count km from your steps. You can add the distance when you finish.",
                        color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                    live.source == TrackSource.Steps -> Text(
                        listOfNotNull(s.cadenceSpm.takeIf { it > 0 }?.let { "$it steps/min" }, "avg ${Fmt.pace(s.avgSpeedMs)} /km",
                            "estimated from steps").joinToString(" · "),
                        color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                }
                live.warning?.let { Text(it, color = Haldi, style = MaterialTheme.typography.bodySmall) }
                if (s.splitsMs.isNotEmpty()) {
                    Text("Last km ${Fmt.paceOfSplit(s.splitsMs.last())} · ${s.splitsMs.size} km done · ↑ ${s.elevGainM.toInt()} m",
                        color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()) {
                    if (paused) {
                        BigRound(Icons.Filled.Stop, "Finish", Coral, Modifier.weight(1f)) { confirmFinish = true }
                        BigRound(Icons.Filled.PlayArrow, "Resume", Haldi, Modifier.weight(1f)) { TrackingService.resume(ctx) }
                    } else {
                        // Finish (with Save / Discard) is always one tap away, not hidden behind Pause
                        BigRound(Icons.Filled.Stop, "Finish", Coral, Modifier.weight(1f)) { confirmFinish = true }
                        BigRound(Icons.Filled.Pause, "Pause", Haldi, Modifier.weight(1f)) { TrackingService.pause(ctx) }
                    }
                }
            }
        }
    }

    if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("Finish this ${live.type.label.lowercase()}?") },
        text = {
            Text(when {
                timeOnly && s.movingMs < 30_000 -> "It’s very short (${s.movingMs / 1000} s). Discard, or keep going?"
                timeOnly -> "${Fmt.duration(s.movingMs)} recorded. Save it, then add the distance if you know it."
                !withMap && s.movingMs < 30_000 -> "It’s very short. Discard, or keep going?"
                !withMap -> "${Fmt.km(s.distanceM)} km in ${Fmt.duration(s.movingMs)}. Save it to see your pace, splits and a picture to share."
                s.distanceM < 50 -> "It’s very short (${s.distanceM.toInt()} m). Save it anyway, or discard?"
                else -> "${Fmt.km(s.distanceM)} km in ${Fmt.duration(s.movingMs)}. Save it to see your map, splits and a picture to share."
            })
        },
        confirmButton = { TextButton(onClick = { confirmFinish = false; TrackingService.finish(ctx) }) { Text(t("Save")) } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmFinish = false; TrackingService.discard(ctx); app.message = "Discarded." }) { Text("Discard", color = Danger) }
                TextButton(onClick = { confirmFinish = false }) { Text("Keep going") }
            }
        },
    )
}

/** Without GPS there's no map to watch: a calm screen with the activity, and what's being counted. */
@Composable
private fun NoMapBackdrop(live: LiveTrack.State, modifier: Modifier) {
    val s = live.stats
    Column(
        modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ink2, Ink))).statusBarsPadding().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(132.dp).clip(CircleShape).background(Ink3), contentAlignment = Alignment.Center) {
            Icon(if (live.source == TrackSource.Timer) Icons.Filled.Timer else icon(live.type), contentDescription = null,
                tint = if (s.autoPaused || live.status == LiveTrack.Status.Paused) MutedOnDark else Haldi, modifier = Modifier.size(64.dp))
        }
        if (live.source == TrackSource.Steps) {
            Text(String.format(Locale.US, "%,d", s.steps), color = Color.White, style = MaterialTheme.typography.displayMedium)
            Text("steps", color = MutedOnDark, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(Fmt.duration(s.movingMs), color = Color.White, style = MaterialTheme.typography.displayMedium)
        }
        Text("No map: recording without GPS", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LiveStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Ink2).padding(12.dp)) {
        Text(label, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
    }
}

@Composable
private fun BigRound(icon: ImageVector, label: String, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(50)).background(bg)
            .clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text(t(label), color = Ink, style = MaterialTheme.typography.labelLarge.copy(fontSize = 19.sp))
    }
}

// ------------------------------------------------------------------ Activity detail

/** Loading → ready, missing or error: never a blank screen. */
private sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Failed(val message: String) : DetailState
    data class Ready(val row: ActivityRow, val points: List<TrackPoint>, val best: Double) : DetailState
}

@Composable
private fun ActivityDetail(app: AppController, id: Long, actions: PhoneActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { TrackStore(ctx) }
    var state by remember(id) { mutableStateOf<DetailState>(DetailState.Loading) }
    LaunchedEffect(id, app.trackVersion) {
        state = withContext(Dispatchers.IO) {
            try {
                val row = store.row(id)
                if (row == null) DetailState.Missing else DetailState.Ready(row, store.points(id), store.longest(row.type))
            } catch (e: Exception) {
                DetailState.Failed(e.message ?: "Couldn’t read this activity.")
            }
        }
    }
    when (val s = state) {
        DetailState.Loading -> Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) { Text("Loading…", color = Muted) }
        DetailState.Missing -> DetailMessage("This activity was deleted.", onBack)
        is DetailState.Failed -> DetailMessage("Couldn’t open this activity: ${s.message}", onBack)
        is DetailState.Ready -> DetailContent(app, s, actions, store, onBack)
    }
}

@Composable
private fun DetailMessage(text: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Move", color = Muted, style = MaterialTheme.typography.labelLarge) }
        AppCard { Text(text, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun DetailContent(app: AppController, s: DetailState.Ready, actions: PhoneActions, store: TrackStore, onBack: () -> Unit) {
    val r = s.row
    val points = s.points
    val segments = remember(points) { TrackSummary.segments(points) }
    val loop = remember(points) { TrackSummary.isLoop(points) }
    // GPS splits come from the route; without GPS they were saved while recording
    val splits = remember(points, r.splitsMs) { if (r.source.hasMap) TrackSummary.splits(points) else r.splitsMs }
    // A route needs real movement to draw; a few metres would show an empty, over-zoomed map
    val mappable = remember(points) {
        points.size >= 2 && points.maxOf { it.lat } - points.minOf { it.lat } + points.maxOf { it.lng } - points.minOf { it.lng } > 0.0002
    }
    val handle = remember { MapHandle() }
    var share by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editDistance by remember { mutableStateOf(false) }
    val started = Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault())

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Move", color = Muted, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Filled.Edit, "Rename", light = true) { rename = true }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                Icon(icon(r.type), contentDescription = null, tint = Haldi)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(started.format(DateTimeFormatter.ofPattern("EEEE d MMM yyyy · h:mm a", Locale.ENGLISH)), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        if (!r.source.hasMap) NoRouteCard(r, app.prefs.stepLengthCm) { editDistance = true }
        else Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)).background(CardBg)) {
            if (mappable) {
                RouteMap(segments = segments, start = points.first().let { LatLng(it.lat, it.lng) },
                    finish = points.last().let { LatLng(it.lat, it.lng) }, loop = loop, fitRoute = true,
                    dark = ThemeState.dark, handle = handle, modifier = Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { RoundIcon(Icons.Filled.MyLocation, "Show whole route") { handle.fit(segments.flatten()) } }
            } else {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📍", style = MaterialTheme.typography.displayMedium)
                    Text("Too short to draw a route", style = MaterialTheme.typography.titleMedium)
                    Text("Move at least 50 metres and your path appears here.", style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center)
                }
            }
        }

        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.source.hasMap) Tag(if (loop) "↻ Round trip" else "Start → Finish", Color(0xFFE9ECFF))
                else Tag(if (r.distanceEdited) "Distance entered by you" else sourceLabel(r.source), Color(0xFFE9ECFF))
                if (r.distanceM >= 1000 && r.distanceM >= s.best - 1) Tag("🏅 Longest ${r.type.label.lowercase()}", Haldi)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.km(r.distanceM), style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.width(6.dp))
                Text("km", style = MaterialTheme.typography.headlineMedium, color = Blue, modifier = Modifier.padding(bottom = 6.dp))
            }
            val hasDistance = r.distanceM > 0
            buildList {
                add("Moving time" to Fmt.duration(r.movingMs))
                if (hasDistance) add(paceLabel(r.type) to paceValue(r.type, r.avgSpeedMs))
                if (hasDistance) add("Avg speed" to "${Fmt.kmh(r.avgSpeedMs)} km/h")
                // Max speed and climb need GPS; steps and cadence need the step counter
                if (r.source.hasMap) { add("Max speed" to "${Fmt.kmh(r.maxSpeedMs)} km/h"); add("Elevation gain" to "${r.elevGainM.toInt()} m") }
                r.steps?.takeIf { it > 0 }?.let { add("Steps" to String.format(Locale.US, "%,d", it)) }
                r.avgCadence?.let { add("Cadence" to "$it steps/min") }
                if (hasDistance) add("Energy (est.)" to (r.kcal?.let { "$it kcal" } ?: "–"))
                if (!r.source.hasMap) add("Total time" to Fmt.duration(r.elapsedMs))
            }.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (label, value) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Paper).padding(12.dp)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
                            Text(value, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                if (r.source.hasMap) "Total time ${Fmt.duration(r.elapsedMs)} · ${points.size} GPS points. Energy uses your latest weight (else 65 kg)."
                else "Recorded without GPS. Energy uses your latest weight (else 65 kg).",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }

        if (splits.isNotEmpty()) AppCard(title = "Splits") { Splits(splits, r.type) }

        if (r.source.hasMap) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Share", dark = false, modifier = Modifier.weight(1f)) { share = true }
                GhostButton("GPX file", modifier = Modifier.weight(1f)) {
                    val name = r.title.replace(Regex("[^A-Za-z0-9 ]"), "").trim().replace(' ', '-').ifBlank { "activity" }
                    actions.track.onSaveGpx("$name-${started.toLocalDate()}.gpx", Gpx.write(r.title, r.type, points))
                }
            }
            Text("GPX opens in Strava, Google Earth, Komoot and most map apps.", style = MaterialTheme.typography.bodySmall, color = Muted)
        } else {
            // No route, so no GPX: the picture shows distance, time and pace
            BigButton("Share", dark = false) { share = true }
        }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Danger)
            Spacer(Modifier.width(6.dp))
            Text("Delete this activity", color = Danger)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (share) RouteShareSheet(app, r, points, loop, actions, onDismiss = { share = false })
    if (editDistance) EditDistanceDialog(app, r, store, onDone = { editDistance = false })
    if (rename) {
        var text by remember { mutableStateOf(r.title) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Name this activity") },
            text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                rename = false
                if (text.isNotBlank()) app.launch { withContext(Dispatchers.IO) { store.rename(r.id, text) }; app.trackVersion++ }
            }) { Text(t("Save")) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(t("Cancel")) } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this activity?") },
        text = { Text("The route and stats are removed from this phone. This can’t be undone.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            app.launch { withContext(Dispatchers.IO) { store.delete(r.id) }; app.trackDetailId = null; app.trackVersion++; app.message = "Activity deleted." }
        }) { Text(t("Remove"), color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel")) } },
    )
}

/** Where the map would be, for an activity recorded without GPS: how the distance was worked out, and a fix. */
@Composable
private fun NoRouteCard(r: ActivityRow, stepCm: Int, onEditDistance: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Ink3), contentAlignment = Alignment.Center) {
                Icon(if (r.source == TrackSource.Timer) Icons.Filled.Timer else icon(r.type), contentDescription = null, tint = Haldi)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("No map for this one", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Recorded without GPS, so no location was used.", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            when {
                r.distanceEdited -> "You entered the distance."
                r.source == TrackSource.Steps && (r.steps ?: 0) > 0 ->
                    "Distance is an estimate: ${String.format(Locale.US, "%,d", r.steps)} steps using your $stepCm cm step length" +
                        (if (r.type == ActivityType.Run) ", longer when you run faster." else ".")
                r.type == ActivityType.Cycle -> "Rides without GPS record time only. Know the distance? Add it."
                else -> "Only the time was recorded. Know the distance? Add it."
            },
            color = Color.White, style = MaterialTheme.typography.bodyMedium,
        )
        SmallPill(if (r.distanceM > 0) "Correct the distance" else "Add the distance", onEditDistance)
        Text("Turn on “Map with GPS” on Move to see your route next time.", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Type the real distance (from a treadmill, a known route, a bike computer). For a walk with steps it can also
 * set the step length, so every later walk and the daily km are closer to the truth.
 */
@Composable
private fun EditDistanceDialog(app: AppController, r: ActivityRow, store: TrackStore, onDone: () -> Unit) {
    var text by remember { mutableStateOf(if (r.distanceM > 0) String.format(Locale.US, "%.2f", r.distanceM / 1000) else "") }
    val km = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 300 }
    val calibrated = km?.let { k -> r.steps?.let { Stride.calibratedCm(k * 1000, it) } }
        ?.takeIf { r.type == ActivityType.Walk && r.source == TrackSource.Steps }
    var setStepLength by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (r.distanceM > 0) "Correct the distance" else "Add the distance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it.take(7) }, singleLine = true, label = { Text("Kilometres") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (text.isNotBlank() && km == null) Text("Enter a distance like 3.5", color = Danger, style = MaterialTheme.typography.bodySmall)
                calibrated?.let { cm ->
                    ToggleRow("Use it for my step length", "Sets your step length to $cm cm (now ${app.prefs.stepLengthCm} cm) for future walks and daily km", setStepLength) { setStepLength = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = km != null, onClick = {
                val meters = (km ?: return@TextButton) * 1000
                val cm = calibrated.takeIf { setStepLength }
                app.launch {
                    withContext(Dispatchers.IO) { store.editDistance(r.id, meters) }
                    cm?.let { app.prefs.stepLengthCm = it }
                    app.trackVersion++
                    app.message = if (cm != null) "Distance saved. Step length is now $cm cm." else "Distance saved."
                }
                onDone()
            }) { Text(t("Save")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(t("Cancel")) } },
    )
}

/** One bar per kilometre: longer = faster. Fastest km in yellow. */
@Composable
private fun Splits(splits: List<Long>, type: ActivityType) {
    val fastest = splits.min()
    val slowest = splits.max()
    splits.forEachIndexed { i, ms ->
        val frac = if (slowest == fastest) 1f else (0.45f + 0.55f * (slowest - ms).toFloat() / (slowest - fastest))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.width(26.dp))
            Box(Modifier.weight(1f).height(26.dp)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (ms == fastest) Haldi else Violet))
            }
            Text(if (type == ActivityType.Cycle) "${Fmt.kmh(1000.0 / (ms / 1000.0))} km/h" else "${Fmt.paceOfSplit(ms)} /km",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
        }
    }
}

// ------------------------------------------------------------------ Share

@Composable
private fun RouteShareSheet(app: AppController, r: ActivityRow, points: List<TrackPoint>, loop: Boolean, actions: PhoneActions, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var mapStyle by remember { mutableStateOf(r.source.hasMap) }
    var story by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(true) }
    var hideEnds by remember { mutableStateOf(app.prefs.trackHideEnds) }
    var mapShot by remember { mutableStateOf<Bitmap?>(null) }
    var moved by remember { mutableIntStateOf(0) }
    val handle = remember { MapHandle() }

    val shown = remember(points, hideEnds) { if (hideEnds) TrackSummary.trimEnds(points) else points }
    val segments = remember(shown) { TrackSummary.segments(shown) }
    // The route stays a single line on the card when ends are hidden
    val cardLoop = loop && !hideEnds

    // Re-take the map picture a few times while tiles load, and after the person pans or zooms
    LaunchedEffect(mapStyle, story, hideEnds, moved) {
        if (!mapStyle) return@LaunchedEffect
        repeat(4) { delay(700); handle.snapshot()?.let { mapShot = it } }
    }
    val card = remember(mapStyle, story, showName, hideEnds, mapShot, r) {
        RouteCard.render(ctx, RouteCard.Input(
            row = r, segments = segments, loop = cardLoop, map = if (mapStyle) mapShot else null,
            name = app.prefs.firstName.takeIf { showName }, story = story, hideEnds = hideEnds,
        ))
    }
    val caption = if (r.distanceM > 0) "${Fmt.km(r.distanceM)} km ${r.type.verb} in ${Fmt.duration(r.movingMs)} · ${paceValue(r.type, r.avgSpeedMs)} on Bas 1 Minute 🏃 #Bas1Minute"
        else "${Fmt.duration(r.movingMs)} ${r.type.label.lowercase()} on Bas 1 Minute 🏃 #Bas1Minute"
    val fileName = "bas1minute-${r.type.id}-${Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()}.png"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Paper)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share your ${r.type.label.lowercase()}", style = MaterialTheme.typography.titleLarge)
            if (r.source.hasMap) ChoiceRow(listOf("map" to "Map", "route" to "Route only"), if (mapStyle) "map" else "route") { mapStyle = it == "map" }
            ChoiceRow(listOf("post" to "Post 4:5", "story" to "Story 9:16"), if (story) "story" else "post") { story = it == "story" }
            if (mapStyle) {
                Text("Move or zoom the map to frame your picture", style = MaterialTheme.typography.bodySmall, color = Muted)
                // Same shape as the map on the card, so what you frame is what you share
                Box(Modifier.fillMaxWidth().aspectRatio(if (story) 952f / 900f else 952f / 610f).clip(RoundedCornerShape(18.dp))) {
                    RouteMap(segments = segments, start = segments.firstOrNull()?.firstOrNull(),
                        finish = segments.lastOrNull()?.lastOrNull(), loop = cardLoop, fitRoute = true,
                        handle = handle, modifier = Modifier.fillMaxSize(), onUserMoved = { moved++ })
                }
            }
            Image(card.asImageBitmap(), contentDescription = "Preview of the picture", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).clip(RoundedCornerShape(18.dp)))
            ToggleRow("Show my name", null, showName) { showName = it }
            if (r.source.hasMap) ToggleRow("Hide exact start and finish", "Leaves out the first and last 200 m, so your home isn’t shown", hideEnds) {
                hideEnds = it; app.prefs.trackHideEnds = it
            }
            BigButton("Share", dark = false) { actions.onShareImage(card, caption) }
            GhostButton("Save to phone") { actions.track.onSaveImage(card, fileName) }
            Text(if (r.source.hasMap) "Only this picture is shared. Your saved route stays on this phone." else "Only this picture is shared.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t("Close"), color = Muted) }
        }
    }
}

// ------------------------------------------------------------------ Bits

@Composable
private fun Countdown(onDone: () -> Unit, onCancel: () -> Unit) {
    var n by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (n > 0) { delay(900); n-- }
        onDone()
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.92f)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (n > 0) "$n" else "Go!", color = Haldi, style = MaterialTheme.typography.displayLarge.copy(fontSize = 140.sp, lineHeight = 140.sp))
                Text("Tap to cancel", color = MutedOnDark, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun GpsChip(accuracy: Float?) {
    val (label, color) = when {
        accuracy == null -> "Finding GPS…" to Haldi
        accuracy <= 10f -> "GPS strong · ±${accuracy.toInt()} m" to Mint
        accuracy <= 25f -> "GPS okay · ±${accuracy.toInt()} m" to Haldi
        else -> "GPS weak · go into the open" to Coral
    }
    Row(Modifier.clip(RoundedCornerShape(50)).background(Ink).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun Tag(text: String, bg: Color) {
    Text(text, color = Ink, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) = RoundIcon(icon, label, false, onClick)

@Composable
private fun RoundIcon(icon: ImageVector, label: String, light: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(if (light) Paper else Ink).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = if (light) TextMain else Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToggleRow(title: String, hint: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!on) }) {
        Column(Modifier.weight(1f)) {
            Text(t(title), style = MaterialTheme.typography.titleMedium)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi))
    }
}

@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else TextMain,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else CardBg)
                    .clickable { onSelect(value) }.padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}

// ------------------------------------------------------------------ Today card

/** On Today: start a walk, see one in progress, or this week's total. */
@Composable
fun TrackTodayCard(app: AppController) {
    val ctx = LocalContext.current
    val live by LiveTrack.state.collectAsState()
    var week by remember { mutableStateOf(0 to 0.0) }
    var last by remember { mutableStateOf<ActivityRow?>(null) }
    LaunchedEffect(app.trackVersion, live.active) {
        withContext(Dispatchers.IO) {
            val store = TrackStore(ctx)
            val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            week = store.totalsSince(monday)
            last = store.list(1).firstOrNull()
        }
    }
    if (live.active) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink).clickable { app.trackOpen = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon(live.type), contentDescription = null, tint = Haldi, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (live.status == LiveTrack.Status.Paused) "${live.type.label} paused" else "${live.type.label} in progress",
                    color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("${Fmt.km(live.stats.distanceM)} km · ${Fmt.duration(live.stats.movingMs)}", color = MutedOnDark, style = MaterialTheme.typography.bodyMedium)
            }
            SmallPill(t("Open")) { app.trackOpen = true }
        }
        return
    }
    // Tapping the card opens your latest activity (if this week has one); Start goes to Move
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg)
        .clickable { app.trackOpen = true; if (week.first == 0) last?.let { app.trackDetailId = it.id } }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE3F7EE)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DirectionsRun, contentDescription = null, tint = MintDark)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Walk, run or ride"), style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    week.first > 0 -> "This week: ${week.first} · ${Fmt.km(week.second)} km"
                    last != null -> "Last: ${last!!.title}, ${Fmt.km(last!!.distanceM)} km · tap to open"
                    else -> "Km, time and pace, with or without GPS. Share it when you’re done"
                },
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }
        SmallPill(t("Start")) { app.trackOpen = true }
    }
}
