package com.bas1minute.sync.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    /** Checks location permission and GPS, then sets app.trackCountdown = true. */
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

/** Walk / run with GPS: start screen and history, the live map, or a finished activity. */
@Composable
fun TrackScreen(app: AppController, actions: PhoneActions, onClose: () -> Unit) {
    val live by LiveTrack.state.collectAsState()
    val detail = app.trackDetailId
    when {
        live.active -> {
            BackHandler { onClose(); app.message = "Still recording. Open it from Today or the notification." }
            LiveView(app, live, onClose)
        }
        detail != null -> {
            BackHandler { app.trackDetailId = null }
            SummaryView(app, detail, actions, onBack = { app.trackDetailId = null })
        }
        else -> {
            BackHandler { onClose() }
            StartView(app, actions.track, onClose)
        }
    }
    if (app.trackCountdown) Countdown(onDone = {
        app.trackCountdown = false
        TrackingService.start(app.ctxForPush, ActivityType.of(app.prefs.trackType), app.prefs.trackAutoPause, app.prefs.trackVoice)
    }, onCancel = { app.trackCountdown = false })
}

// ------------------------------------------------------------------ Start + history

@SuppressLint("MissingPermission")
@Composable
private fun StartView(app: AppController, actions: TrackActions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { TrackStore(ctx) }
    var type by remember { mutableStateOf(ActivityType.of(app.prefs.trackType)) }
    var autoPause by remember { mutableStateOf(app.prefs.trackAutoPause) }
    var voice by remember { mutableStateOf(app.prefs.trackVoice) }
    var here by remember { mutableStateOf<LatLng?>(null) }
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var history by remember { mutableStateOf<List<ActivityRow>>(emptyList()) }
    var week by remember { mutableStateOf(0 to 0.0) }
    var unfinished by remember { mutableStateOf<ActivityRow?>(null) }

    LaunchedEffect(app.trackVersion) {
        withContext(Dispatchers.IO) {
            history = store.list()
            val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            week = store.totalsSince(monday)
            unfinished = store.unfinished()
        }
    }

    // Warm up GPS while the person gets ready, so the first metres are accurate
    val granted = app.locationGranted
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { here = LatLng(it.latitude, it.longitude); accuracy = if (it.hasAccuracy()) it.accuracy else null }
            }
        }
        try {
            client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build(), cb, Looper.getMainLooper())
        } catch (e: SecurityException) { /* permission removed meanwhile */ }
        onDispose { client.removeLocationUpdates(cb) }
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            if (granted) {
                RouteMap(segments = emptyList(), current = here, follow = true, dark = ThemeState.dark, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Ink2), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Haldi, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Your route appears here", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text("Tap Start and allow location. It stays on this phone.", color = MutedOnDark,
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
            }
            Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClose)
                Spacer(Modifier.weight(1f))
                if (granted) GpsChip(accuracy)
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            unfinished?.let { u -> UnfinishedCard(app, store, u) { app.trackVersion++ } }

            AppCard(title = t("Walk or run")) {
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
                ToggleRow("Auto-pause", "Stops the clock at signals and crossings", autoPause) { autoPause = it; app.prefs.trackAutoPause = it }
                ToggleRow("Voice every km", "Hear your distance and time without looking", voice) { voice = it; app.prefs.trackVoice = it }
                BigButton(t("Start"), dark = false) { actions.onReadyToStart() }
                Text("GPS works without internet. The map needs data to load; your route records either way.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }

            AppCard(title = t("Your activities"), trailing = {
                if (week.first > 0) Text("This week ${Fmt.km(week.second)} km", style = MaterialTheme.typography.labelMedium, color = Muted)
            }) {
                if (history.isEmpty()) {
                    Text("Your walks and runs will be listed here, with the map, splits and a picture to share.",
                        style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                history.forEach { row -> HistoryRow(row) { app.trackDetailId = row.id } }
            }
            Spacer(Modifier.height(8.dp))
        }
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
            SmallPill("Resume") { TrackingService.recover(ctx, u.id, app.prefs.trackVoice) }
            SmallPill("Save") {
                app.launch {
                    withContext(Dispatchers.IO) { if (store.points(u.id).size >= 2) store.finish(u.id, u.type, null, System.currentTimeMillis()) else store.delete(u.id) }
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
    val px = with(density) { 56.dp.roundToPx() }
    val thumb = remember(r.id, r.preview.size) { RouteCard.thumbnail(r.preview, px).asImageBitmap() }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(thumb, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)),
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${Fmt.km(r.distanceM)} km", style = MaterialTheme.typography.titleMedium)
            Text("${Fmt.duration(r.movingMs)} · ${paceValue(r.type, r.avgSpeedMs)}", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

// ------------------------------------------------------------------ Live

@Composable
private fun LiveView(app: AppController, live: LiveTrack.State, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var follow by remember { mutableStateOf(true) }
    var confirmFinish by remember { mutableStateOf(false) }
    val s = live.stats
    val paused = live.status == LiveTrack.Status.Paused

    Box(Modifier.fillMaxSize().background(Paper)) {
        RouteMap(
            segments = live.segments, current = live.current, start = live.start, follow = follow,
            dark = ThemeState.dark, modifier = Modifier.fillMaxSize(), onUserMoved = { follow = false },
        )
        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back, keeps recording", onClose)
            Spacer(Modifier.width(8.dp))
            when {
                paused -> StatusPill("Paused", Haldi, Ink)
                s.autoPaused -> StatusPill("Auto-paused", Haldi, Ink)
                else -> StatusPill("● Recording", Ink, Color.White)
            }
            Spacer(Modifier.weight(1f))
            GpsChip(live.accuracyM)
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (!follow) {
                Box(Modifier.align(Alignment.End).padding(12.dp)) {
                    RoundIcon(Icons.Filled.MyLocation, "Back to my position") { follow = true }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Ink)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${Fmt.km(s.distanceM)} kilometres"
                }) {
                    Text(Fmt.km(s.distanceM), color = Color.White, style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 64.sp))
                    Spacer(Modifier.width(8.dp))
                    Text("km", color = Haldi, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Icon(icon(live.type), contentDescription = live.type.label, tint = MutedOnDark, modifier = Modifier.size(30.dp).padding(bottom = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveStat("Time", Fmt.duration(s.movingMs), Modifier.weight(1f))
                    if (live.type == ActivityType.Cycle) LiveStat("Speed", "${Fmt.kmh(s.currentSpeedMs)}", Modifier.weight(1f))
                    else LiveStat("Pace now", Fmt.pace(s.currentSpeedMs), Modifier.weight(1f))
                    LiveStat(if (live.type == ActivityType.Cycle) "Avg km/h" else "Avg pace", if (live.type == ActivityType.Cycle) Fmt.kmh(s.avgSpeedMs) else Fmt.pace(s.avgSpeedMs), Modifier.weight(1f))
                }
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
            Text(if (s.distanceM < 50) "It’s very short (${s.distanceM.toInt()} m). Save it anyway, or discard?"
                else "${Fmt.km(s.distanceM)} km in ${Fmt.duration(s.movingMs)}. Save it to see your map, splits and a picture to share.")
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

// ------------------------------------------------------------------ Summary

@Composable
private fun SummaryView(app: AppController, id: Long, actions: PhoneActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { TrackStore(ctx) }
    var row by remember { mutableStateOf<ActivityRow?>(null) }
    var points by remember { mutableStateOf<List<TrackPoint>>(emptyList()) }
    var best by remember { mutableStateOf(0.0) }
    var share by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id, app.trackVersion) {
        withContext(Dispatchers.IO) {
            row = store.row(id); points = store.points(id)
            row?.let { best = store.longest(it.type) }
        }
    }
    val r = row ?: run {
        Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) { Text("Loading…", color = Muted) }
        return
    }
    val segments = remember(points) { TrackSummary.segments(points) }
    val loop = remember(points) { TrackSummary.isLoop(points) }
    val splits = remember(points) { TrackSummary.splits(points) }
    val handle = remember { MapHandle() }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            RouteMap(segments = segments, start = points.firstOrNull()?.let { LatLng(it.lat, it.lng) },
                finish = points.lastOrNull()?.let { LatLng(it.lat, it.lng) }, loop = loop, fitRoute = true,
                dark = ThemeState.dark, handle = handle, modifier = Modifier.fillMaxSize())
            Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth()) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                RoundIcon(Icons.Filled.MyLocation, "Show whole route") { handle.fit(segments.flatten()) }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.headlineMedium)
                        Text(Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE d MMM yyyy · h:mm a", Locale.ENGLISH)),
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    RoundIcon(Icons.Filled.Edit, "Rename", light = true) { rename = true }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(if (loop) "↻ Round trip" else "Start → Finish", Color(0xFFE9ECFF))
                    if (r.distanceM >= 1000 && r.distanceM >= best - 1) Tag("🏅 Longest ${r.type.label.lowercase()}", Haldi)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.km(r.distanceM), style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.width(6.dp))
                    Text("km", style = MaterialTheme.typography.headlineMedium, color = Blue, modifier = Modifier.padding(bottom = 6.dp))
                }
                val cells = listOf(
                    "Moving time" to Fmt.duration(r.movingMs),
                    paceLabel(r.type) to paceValue(r.type, r.avgSpeedMs),
                    "Avg speed" to "${Fmt.kmh(r.avgSpeedMs)} km/h",
                    "Max speed" to "${Fmt.kmh(r.maxSpeedMs)} km/h",
                    "Elevation gain" to "${r.elevGainM.toInt()} m",
                    "Energy (est.)" to (r.kcal?.let { "$it kcal" } ?: "–"),
                    "Total time" to Fmt.duration(r.elapsedMs),
                    "GPS points" to "${points.size}",
                )
                cells.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (label, value) ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Paper).padding(12.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
                                Text(value, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
                Text("Energy uses your latest weight if you log it (else 65 kg). Elevation from GPS is approximate.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }

            if (splits.isNotEmpty()) AppCard(title = "Splits") { Splits(splits, r.type) }

            BigButton("Share or save picture", dark = false) { share = true }
            GhostButton("Download GPS file (GPX)") {
                val name = r.title.replace(Regex("[^A-Za-z0-9 ]"), "").trim().replace(' ', '-').ifBlank { "activity" }
                val date = Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                actions.track.onSaveGpx("$name-$date.gpx", Gpx.write(r.title, r.type, points))
            }
            Text("GPX opens in Strava, Google Earth, Komoot and most map apps.", style = MaterialTheme.typography.bodySmall, color = Muted)
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Danger)
                Spacer(Modifier.width(6.dp))
                Text("Delete this activity", color = Danger)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (share) RouteShareSheet(app, r, points, loop, actions, onDismiss = { share = false })
    if (rename) {
        var text by remember { mutableStateOf(r.title) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Name this activity") },
            text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                rename = false
                if (text.isNotBlank()) app.launch { withContext(Dispatchers.IO) { store.rename(id, text) }; app.trackVersion++ }
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
            app.launch { withContext(Dispatchers.IO) { store.delete(id) }; app.trackDetailId = null; app.trackVersion++; app.message = "Activity deleted." }
        }) { Text(t("Remove"), color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel")) } },
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
    var mapStyle by remember { mutableStateOf(true) }
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
    val caption = "${Fmt.km(r.distanceM)} km ${r.type.verb} in ${Fmt.duration(r.movingMs)} · ${paceValue(r.type, r.avgSpeedMs)} on Bas 1 Minute 🏃 #Bas1Minute"
    val fileName = "bas1minute-${r.type.id}-${Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()}.png"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Paper)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share your ${r.type.label.lowercase()}", style = MaterialTheme.typography.titleLarge)
            ChoiceRow(listOf("map" to "Map", "route" to "Route only"), if (mapStyle) "map" else "route") { mapStyle = it == "map" }
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
            ToggleRow("Hide exact start and finish", "Leaves out the first and last 200 m, so your home isn’t shown", hideEnds) {
                hideEnds = it; app.prefs.trackHideEnds = it
            }
            BigButton("Share", dark = false) { actions.onShareImage(card, caption) }
            GhostButton("Save to phone") { actions.track.onSaveImage(card, fileName) }
            Text("Only this picture is shared. Your saved route stays on this phone.", style = MaterialTheme.typography.bodySmall, color = Muted)
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).clickable { app.trackOpen = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE3F7EE)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DirectionsRun, contentDescription = null, tint = MintDark)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Walk or run with GPS"), style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    week.first > 0 -> "This week: ${week.first} · ${Fmt.km(week.second)} km"
                    last != null -> "Last: ${last!!.title}, ${Fmt.km(last!!.distanceM)} km"
                    else -> "See your route on the map, km, pace and share it"
                },
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }
        SmallPill(t("Start")) { app.trackOpen = true }
    }
}
