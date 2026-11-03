package com.bas1minute.sync.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bas1minute.sync.AppController
import com.bas1minute.sync.phone.AppUse
import com.bas1minute.sync.phone.BatteryLog
import com.bas1minute.sync.phone.BatteryNow
import com.bas1minute.sync.phone.BatterySample
import com.bas1minute.sync.phone.Finding
import com.bas1minute.sync.phone.Fix
import com.bas1minute.sync.phone.Perm
import com.bas1minute.sync.phone.PhoneCheck
import com.bas1minute.sync.phone.PhoneCheckReader
import com.bas1minute.sync.phone.Report
import com.bas1minute.sync.phone.Severity
import com.bas1minute.sync.phone.Special
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun sevColor(s: Severity) = when (s) { Severity.High -> Coral; Severity.Medium -> Haldi; Severity.Info -> Violet }

/** Opens Android's own screen for an app: Battery, Mobile data, Permissions and Uninstall are all there. */
private fun openApp(ctx: Context, pkg: String) {
    try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))) }
    catch (e: Exception) { openSettings(ctx, Settings.ACTION_SETTINGS) }
}

private fun openSettings(ctx: Context, action: String) {
    try { ctx.startActivity(Intent(action)) } catch (e: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (x: Exception) { }
    }
}

private fun fix(ctx: Context, f: Finding) = when (f.fix) {
    Fix.AppSettings -> f.pkg?.let { openApp(ctx, it) }
    Fix.BatteryOptimisation -> openSettings(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    Fix.None -> Unit
}

/** Which apps cost you battery, data and privacy in the last 24 hours. Everything is worked out on this phone. */
@Composable
fun PhoneCheckScreen(app: AppController, actions: PhoneActions, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(PhoneCheckReader.cached) }
    var samples by remember { mutableStateOf<List<BatterySample>>(emptyList()) }
    var now by remember { mutableStateOf<BatteryNow?>(null) }
    var historyHours by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var openPkg by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf(app.prefs.phoneAlerts) }
    val access = app.phone.usageAccess

    BackHandler { if (openPkg != null) openPkg = null else onClose() }

    LaunchedEffect(access, refresh) {
        loading = true
        withContext(Dispatchers.IO) {
            val r = PhoneCheckReader(ctx)
            now = BatteryLog.now(ctx)
            if (r.hasAccess()) report = r.report() else BatteryLog.record(ctx)
            val from = System.currentTimeMillis() - PhoneCheck.DAY_MS
            samples = BatteryLog.samples(ctx, from)
            historyHours = BatteryLog.firstSampleAt(ctx)?.let { (System.currentTimeMillis() - it) / 3_600_000.0 } ?: 0.0
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        // ---------- Hero ----------
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Ink, Ink2))).statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClose)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Phone check", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(if (loading) "Checking your phone…" else "Last 24 hours" + (report?.let { " · " + time(it.generatedAt) } ?: ""),
                        color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                }
                HeroIcon(Icons.Filled.Refresh, "Check again") { refresh++ }
            }
            val r = report
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(r?.score, Modifier.size(112.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r?.let { PhoneCheck.scoreLabel(it.score) } ?: (if (access) "Checking…" else "Needs usage access"),
                        color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    val high = r?.findings?.count { it.severity == Severity.High } ?: 0
                    val med = r?.findings?.count { it.severity == Severity.Medium } ?: 0
                    Text(
                        when {
                            r == null -> "See which apps drain your battery and data, and which can use your camera, mic and location."
                            high + med == 0 -> "Nothing unusual found."
                            else -> listOfNotNull(high.takeIf { it > 0 }?.let { "$it to fix" }, med.takeIf { it > 0 }?.let { "$it to review" }).joinToString(" · ")
                        }, color = MutedOnDark, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            now?.let { BatteryNowRow(it, r?.battery?.hoursLeft) }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!access) {
                AppCard(title = "Allow usage access") {
                    Text("Phone check reads app use, background activity and data use with Android’s Usage access — the same permission screen time uses. Nothing leaves this phone.",
                        style = MaterialTheme.typography.bodyMedium, color = Muted)
                    BigButton("Open usage access", dark = false) { actions.onOpenUsageAccess() }
                    if (app.phone.restrictedSettings) UsageAccessHelp(onOpenUsageAccess = actions.onOpenUsageAccess, onOpenAppInfo = actions.onOpenAppInfo, helpActions = actions.help)
                }
                if (samples.size >= 2) AppCard(title = "Battery today") { BatteryChart(samples, Modifier.fillMaxWidth().height(150.dp)) }
                return@Column
            }
            val r = report ?: run {
                Text("Checking apps…", color = Muted, modifier = Modifier.padding(24.dp)); return@Column
            }

            // ---------- Findings ----------
            AppCard(title = if (r.findings.isEmpty()) "All clear" else "What to look at") {
                if (r.findings.isEmpty()) {
                    Text("No app is draining battery or data unusually, and no risky access was found. Check again any time.",
                        style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                val shown = if (showAll) r.findings else r.findings.take(5)
                shown.forEach { f -> FindingRow(f, onOpen = { f.pkg?.let { openPkg = it } }, onFix = { fix(ctx, f) }) }
                if (r.findings.size > 5) TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show fewer" else "Show all ${r.findings.size}", color = Blue)
                }
            }

            Segmented(listOf("Battery", "Data", "Background", "Permissions"), tab) { tab = it }
            when (tab) {
                0 -> BatteryTab(r, samples, historyHours) { openPkg = it }
                1 -> DataTab(r) { openPkg = it }
                2 -> BackgroundTab(r) { openPkg = it }
                else -> PermissionsTab(r) { openPkg = it }
            }

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Evening alert", style = MaterialTheme.typography.titleMedium)
                        Text("At most once a day, only for something serious (an app eating data or battery, risky access).",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Switch(checked = alerts, onCheckedChange = { alerts = it; app.prefs.phoneAlerts = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi))
                }
                Text("Worked out on this phone and never sent. Android shares exact battery per app only with system apps, " +
                    "so battery per app here is an estimate from screen time, background activity and data, matched to your real battery drop.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    openPkg?.let { pkg -> report?.apps?.firstOrNull { it.pkg == pkg }?.let { a -> AppDetail(a, report!!, onClose = { openPkg = null }) } }
}

// ---------------------------------------------------------------- hero bits

@Composable
private fun HeroIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Ink3).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ScoreRing(score: Int?, modifier: Modifier) {
    val color = when { score == null -> MutedOnDark; score >= 85 -> Mint; score >= 60 -> Haldi; else -> Coral }
    Box(modifier.semantics { contentDescription = score?.let { "Phone health $it out of 100" } ?: "Not checked yet" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.1f
            val tl = Offset(w / 2, w / 2); val sz = Size(size.width - w, size.height - w)
            drawArc(Ink3, -90f, 360f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            if (score != null && score > 0) drawArc(color, -90f, 360f * score / 100f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "–", color = Color.White, style = MaterialTheme.typography.displayMedium)
            Text("of 100", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BatteryNowRow(n: BatteryNow, hoursLeft: Double?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        HeroChip("🔋 ${n.level ?: "–"}%" + if (n.charging) " · charging" + (n.plug?.let { " ($it)" } ?: "") else "")
        if (!n.charging && hoursLeft != null) HeroChip("≈ ${hoursLeft.toInt()} h left at today’s pace")
        n.tempC?.let { HeroChip("🌡 ${it.toInt()}°C") }
        n.health?.let { HeroChip("Condition: $it") }
        n.cycles?.let { HeroChip("$it charge cycles") }
        n.currentMa?.let { HeroChip("Using ${it} mA now") }
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Ink3).padding(horizontal = 12.dp, vertical = 8.dp))
}

// ---------------------------------------------------------------- findings

@Composable
private fun FindingRow(f: Finding, onOpen: () -> Unit, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).clickable(enabled = f.pkg != null, onClick = onOpen).padding(12.dp)) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(sevColor(f.severity)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(f.title, style = MaterialTheme.typography.titleMedium)
            Text(f.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
            if (f.fix != Fix.None && f.fixLabel.isNotBlank()) SmallPill(f.fixLabel, onFix)
        }
    }
}

// ---------------------------------------------------------------- battery

@Composable
private fun BatteryTab(r: Report, samples: List<BatterySample>, historyHours: Double, onApp: (String) -> Unit) {
    val b = r.battery
    AppCard(title = "Battery level", trailing = { Text("24 h", color = Muted, style = MaterialTheme.typography.bodySmall) }) {
        if (samples.size >= 2) BatteryChart(samples, Modifier.fillMaxWidth().height(150.dp))
        else Text("Measuring. The chart fills in over the next few hours.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Used", if (b.hoursCovered >= 1) "${b.dischargedPct.toInt()}%" else "–", Modifier.weight(1f))
            Tile("Screen on", b.onRate?.let { "${PhoneCheck.pct(it)}/h" } ?: "–", Modifier.weight(1f))
            Tile("Screen off", b.offRate?.let { "${PhoneCheck.pct(it)}/h" } ?: "–", Modifier.weight(1f))
        }
        Text("Screen time ${PhoneCheck.hm((b.screenOnHours * 3_600_000).toLong())} · charged ${b.charges} time${if (b.charges == 1) "" else "s"}" +
            (b.maxTempC?.let { " · hottest ${it.toInt()}°C" } ?: "") + ". Healthy screen-off drain is under 1% an hour.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    AppCard(title = "Battery by app", trailing = { Text("estimate", color = Muted, style = MaterialTheme.typography.bodySmall) }) {
        if (historyHours < 6) Text("Battery history is still building (${historyHours.toInt()} h of 24). Until then this uses a typical phone’s numbers.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        val rows = r.appBattery.take(15)
        val max = rows.maxOfOrNull { it.pct }?.coerceAtLeast(0.1) ?: 1.0
        rows.forEach { a ->
            AppBarRow(
                pkg = a.pkg, label = a.label, value = PhoneCheck.pct(a.pct),
                sub = listOfNotNull(
                    a.screenMs.takeIf { it >= 60_000 }?.let { "${PhoneCheck.hm(it)} on screen" },
                    a.backgroundMs.takeIf { it >= 60_000 }?.let { "${PhoneCheck.hm(it)} background" },
                    a.dataBytes.takeIf { it >= 1024 * 1024 }?.let { PhoneCheck.bytes(it) },
                ).joinToString(" · ").ifBlank { if (a.pkg == null) "Android, network and sensors while you sleep" else "" },
                parts = listOf((a.pct / max).toFloat() to Violet), onClick = a.pkg?.let { p -> { onApp(p) } },
            )
        }
    }
}

@Composable
private fun BatteryChart(samples: List<BatterySample>, modifier: Modifier) {
    val line = Ink; val charge = Mint; val grid = PaperLine
    Column {
        Canvas(modifier) {
            val t0 = samples.first().t; val t1 = samples.last().t.coerceAtLeast(t0 + 1)
            fun x(t: Long) = (t - t0).toFloat() / (t1 - t0) * size.width
            fun y(l: Int) = size.height - l / 100f * size.height
            for (g in listOf(0, 50, 100)) drawLine(grid, Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 2f)
            val fill = Path().apply {
                moveTo(x(samples.first().t), size.height)
                samples.forEach { lineTo(x(it.t), y(it.level)) }
                lineTo(x(samples.last().t), size.height); close()
            }
            drawPath(fill, Violet.copy(alpha = 0.18f))
            for (i in 1 until samples.size) {
                val a = samples[i - 1]; val b = samples[i]
                drawLine(if (b.charging) charge else line, Offset(x(a.t), y(a.level)), Offset(x(b.t), y(b.level)), strokeWidth = 5f, cap = StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(time(samples.first().t), style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
            Text("green = charging", style = MaterialTheme.typography.bodySmall, color = MintDark, modifier = Modifier.weight(1f))
            Text("now", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

// ---------------------------------------------------------------- data

@Composable
private fun DataTab(r: Report, onApp: (String) -> Unit) {
    val ctx = LocalContext.current
    var mobile by remember { mutableStateOf(r.mobileKnown) }
    val apps = r.apps.filter { (if (mobile) it.mobile else it.wifi) >= 512 * 1024 }.sortedByDescending { if (mobile) it.mobile else it.wifi }
    val total = r.apps.sumOf { if (mobile) it.mobile else it.wifi }
    val bg = r.apps.sumOf { if (mobile) it.mobileBg else it.wifiBg }
    AppCard(title = "Data used", trailing = { Segmented(listOf("Mobile", "Wi-Fi"), if (mobile) 0 else 1) { mobile = it == 0 } }) {
        if (mobile && !r.mobileKnown) {
            Text("Mobile data per app needs Android 10 or newer.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Total", PhoneCheck.bytes(total), Modifier.weight(1f))
                Tile("In background", PhoneCheck.bytes(bg), Modifier.weight(1f))
            }
            Legend()
            val max = apps.maxOfOrNull { if (mobile) it.mobile else it.wifi }?.coerceAtLeast(1L) ?: 1L
            apps.take(20).forEach { a ->
                val fg = if (mobile) a.mobileFg else a.wifiFg; val b = if (mobile) a.mobileBg else a.wifiBg
                AppBarRow(a.pkg, a.label, PhoneCheck.bytes(fg + b),
                    sub = if (b > 0) "${PhoneCheck.bytes(b)} in background" else "while open",
                    parts = listOf(fg.toFloat() / max to Blue, b.toFloat() / max to Coral),
                    onClick = if (a.system) null else ({ onApp(a.pkg) }))
            }
            if (apps.isEmpty()) Text("No app used more than 0.5 MB.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Text("Android updates these numbers every hour or two.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        GhostButton("Open Data saver settings") { openSettings(ctx, "android.settings.DATA_SAVER_SETTINGS") }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(Blue); Text("While open", style = MaterialTheme.typography.bodySmall, color = Muted)
        Dot(Coral); Text("In background", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable private fun Dot(c: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(c))

// ---------------------------------------------------------------- background

@Composable
private fun BackgroundTab(r: Report, onApp: (String) -> Unit) {
    val ctx = LocalContext.current
    val running = r.apps.filter { it.backgroundMs >= 60_000 }.sortedByDescending { it.backgroundMs }
    AppCard(title = "Ran in the background") {
        if (!r.backgroundKnown) Text("Background times need Android 10 or newer.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        else if (running.isEmpty()) Text("No app ran a background task today.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        val max = running.maxOfOrNull { it.backgroundMs }?.coerceAtLeast(1L) ?: 1L
        running.take(20).forEach { a ->
            AppBarRow(a.pkg, a.label, PhoneCheck.hm(a.backgroundMs),
                sub = (if (a.foregroundMs < 2 * 60_000) "not opened today" else "${PhoneCheck.hm(a.foregroundMs)} on screen") +
                    if (a.unrestricted) " · no battery limits" else "",
                parts = listOf(a.backgroundMs.toFloat() / max to if (a.foregroundMs < 2 * 60_000) Coral else Violet), onClick = { onApp(a.pkg) })
        }
        Text("Counts tasks that show a notification while running (music, navigation, uploads, VPNs). Fine for those; suspicious for apps you never open.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    val unres = r.apps.filter { it.unrestricted && !it.system }.sortedBy { it.label.lowercase() }
    AppCard(title = "No battery limits", trailing = { Text("${unres.size}", color = Muted, style = MaterialTheme.typography.titleMedium) }) {
        Text("Android won’t pause these when the phone sleeps. Keep it for apps that must run all the time (alarms, health, messaging).",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        unres.forEach { a -> AppLine(a, if (a.backgroundMs > 0) "${PhoneCheck.hm(a.backgroundMs)} in background today" else "quiet today") { onApp(a.pkg) } }
        GhostButton("Open battery optimisation list") { openSettings(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
    }
}

// ---------------------------------------------------------------- permissions

@Composable
private fun PermissionsTab(r: Report, onApp: (String) -> Unit) {
    val ctx = LocalContext.current
    var showSystem by remember { mutableStateOf(false) }
    val pool = r.apps.filter { showSystem || !it.system }
    val order = listOf(Perm.Location, Perm.BackgroundLocation, Perm.Camera, Perm.Microphone, Perm.Contacts, Perm.Sms,
        Perm.CallLogs, Perm.Photos, Perm.Calendar, Perm.Phone, Perm.Activity, Perm.Nearby)
    var pick by remember { mutableStateOf(Perm.Microphone) }

    val special = Special.entries.associateWith { s -> pool.filter { s in it.special } }.filterValues { it.isNotEmpty() }
    AppCard(title = "Powerful access") {
        if (special.isEmpty()) Text("No app has accessibility, notification-reading or device-admin access.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        special.forEach { (s, apps) ->
            Text(s.label, style = MaterialTheme.typography.titleMedium)
            Text("Can ${s.risk.removePrefix("can ")}.", style = MaterialTheme.typography.bodySmall, color = Muted)
            apps.forEach { a -> AppLine(a, if (a.sideloaded) "⚠ installed outside an app store" else "from an app store") { onApp(a.pkg) } }
        }
    }

    AppCard(title = "Who can use…") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            order.forEach { p ->
                val n = pool.count { p in it.perms }
                val on = p == pick
                Text("${p.label} $n", style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else TextMain,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Paper).clickable { pick = p }
                        .padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        val list = pool.filter { pick in it.perms }.sortedBy { it.lastUsedMs ?: 0L }
        if (list.isEmpty()) Text("No app can ${pick.can}.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        list.forEach { a -> AppLine(a, lastUsed(a, r.generatedAt)) { onApp(a.pkg) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Include system apps", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = showSystem, onCheckedChange = { showSystem = it }, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi))
        }
        GhostButton("Open Android privacy settings") { openSettings(ctx, "android.settings.PRIVACY_SETTINGS") }
        Text("Shows apps with a home-screen icon. Oldest-used first: those are the easiest to clean up.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }

    val side = pool.filter { it.sideloaded }.sortedBy { it.label.lowercase() }
    if (side.isNotEmpty()) AppCard(title = "Installed outside an app store") {
        Text("From an APK file, a browser or a chat. Usually fine if you know where it came from; fake loan and KYC apps spread this way.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        side.forEach { a -> AppLine(a, a.perms.filter { it.sensitive }.joinToString { it.label }.ifBlank { "no sensitive permissions" }) { onApp(a.pkg) } }
    }
}

private fun lastUsed(a: AppUse, now: Long): String {
    val t = a.lastUsedMs ?: return "not opened recently"
    val days = ((now - t) / PhoneCheck.DAY_MS).toInt()
    return when { days <= 0 -> "used today"; days == 1 -> "used yesterday"; days >= 60 -> "not opened in 60+ days"; else -> "last used $days days ago" }
}

// ---------------------------------------------------------------- app detail

@Composable
private fun AppDetail(a: AppUse, r: Report, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val icon = rememberIcon(a.pkg, 128)
    val battery = r.appBattery.firstOrNull { it.pkg == a.pkg }
    val mine = r.findings.filter { it.pkg == a.pkg }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Paper)
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(52.dp))
                else Box(Modifier.size(52.dp).clip(CircleShape).background(Ink3))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(if (a.system) "Came with the phone" else null, if (a.sideloaded) "Installed outside an app store" else null,
                        if (a.unrestricted) "No battery limits" else null).joinToString(" · ").ifBlank { a.pkg },
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            mine.forEach { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(sevColor(f.severity).copy(alpha = 0.18f)).padding(12.dp)) {
                    Text(f.title + ". " + f.detail, style = MaterialTheme.typography.bodySmall, color = TextMain)
                }
            }
            val cells = listOf(
                "On screen today" to PhoneCheck.hm(a.foregroundMs),
                "Background" to (if (r.backgroundKnown) PhoneCheck.hm(a.backgroundMs) else "–"),
                "Battery (est.)" to (battery?.let { PhoneCheck.pct(it.pct) } ?: "under 0.1%"),
                "Mobile data" to (if (r.mobileKnown) PhoneCheck.bytes(a.mobile) + (if (a.mobileBg > 0) " (${PhoneCheck.bytes(a.mobileBg)} bg)" else "") else "–"),
                "Wi-Fi" to PhoneCheck.bytes(a.wifi),
                "Last opened" to lastUsed(a, r.generatedAt).replaceFirstChar { it.uppercase() },
            )
            cells.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pair.forEach { (l, v) -> Tile(l, v, Modifier.weight(1f)) } }
            }
            a.installedMs?.let { Text("Installed " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)),
                style = MaterialTheme.typography.bodySmall, color = Muted) }
            if (a.special.isNotEmpty() || a.perms.isNotEmpty()) {
                Text("It can", style = MaterialTheme.typography.titleMedium)
                a.special.forEach { s -> Text("⚠ ${s.risk.removePrefix("can ")} (${s.label})", style = MaterialTheme.typography.bodyMedium, color = Danger) }
                a.perms.sortedBy { it.ordinal }.forEach { p ->
                    Text("• ${p.can.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyMedium, color = if (p.sensitive) TextMain else Muted)
                }
            }
            BigButton("Open app settings", dark = false) { openApp(ctx, a.pkg) }
            Text("Battery, Mobile data, Permissions and Uninstall are all on that screen. Android lets only you change them.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t("Close"), color = Muted) }
        }
    }
}

// ---------------------------------------------------------------- rows

@Composable
private fun AppBarRow(pkg: String?, label: String, value: String, sub: String, parts: List<Pair<Float, Color>>, onClick: (() -> Unit)?) {
    val icon = pkg?.let { rememberIcon(it, 72) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        else Box(Modifier.size(36.dp).clip(CircleShape).background(PaperLine))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Paper)) {
                parts.forEach { (f, c) -> if (f > 0f) Box(Modifier.fillMaxHeight().weight(f.coerceIn(0.001f, 1f)).background(c)) }
                val rest = 1f - parts.sumOf { it.first.toDouble() }.toFloat()
                if (rest > 0.001f) Spacer(Modifier.weight(rest))
            }
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppLine(a: AppUse, sub: String, onClick: () -> Unit) {
    val icon = rememberIcon(a.pkg, 72)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        else Box(Modifier.size(36.dp).clip(CircleShape).background(PaperLine))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Muted)
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Paper).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** App icon from Android, drawn once and kept for the screen. */
@Composable
private fun rememberIcon(pkg: String, px: Int): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(pkg, px) {
        try {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            d.setBounds(0, 0, px, px); d.draw(c)
            bmp.asImageBitmap()
        } catch (e: Exception) { null }
    }
}

private fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

// ---------------------------------------------------------------- Insights entry

/** The doorway on Insights. Shows the last score if there is one. */
@Composable
fun PhoneCheckCard(app: AppController) {
    val r = PhoneCheckReader.cached
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).clickable { app.phoneCheckOpen = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(if (r == null) Color(0xFFE9ECFF) else sevColorFor(r.score)), contentAlignment = Alignment.Center) {
            Text(r?.score?.toString() ?: "🔋", style = MaterialTheme.typography.titleMedium, color = Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Phone check"), style = MaterialTheme.typography.titleMedium)
            Text(r?.let { rep ->
                rep.findings.firstOrNull { it.severity != Severity.Info }?.title ?: "Nothing unusual in the last 24 hours"
            } ?: "Which apps drain battery and data, run in the background, or use your camera, mic and location",
                style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        SmallPill(t("Open")) { app.phoneCheckOpen = true }
    }
}

private fun sevColorFor(score: Int) = when { score >= 85 -> Mint; score >= 60 -> Haldi; else -> Coral }
