package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.api.CircleDetail
import com.bas1minute.sync.api.FoundCircle
import com.bas1minute.sync.api.GlobalBoard
import com.bas1minute.sync.api.GlobalRow
import com.bas1minute.sync.api.JoinRequest
import com.bas1minute.sync.api.Res
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private val nf: NumberFormat get() = NumberFormat.getIntegerInstance(Locale("en", "IN"))

/** "Saved copy from 10:42" when the screen shows what was last synced. */
@Composable
fun SavedCopyNote(offline: Boolean, savedAt: Long?, onDark: Boolean = false) {
    if (!offline || savedAt == null || savedAt <= 0L) return
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(savedAt))
    val day = if (android.text.format.DateUtils.isToday(savedAt)) "" else java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(savedAt)) + " "
    Text("Offline · saved copy from $day$time", style = MaterialTheme.typography.labelMedium, color = Ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFF3C8)).padding(horizontal = 10.dp, vertical = 4.dp))
}

// ================================================================ Global card on Circles

/** Always first on Circles: everyone is in the global circle. */
@Composable
fun GlobalCard(app: AppController) {
    LaunchedEffect(Unit) { if (app.globalBoard == null) app.loadGlobal() }
    val b = app.globalBoard
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Ink, Ink2)))
            .clickable { app.globalOpen = true }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌏 Global", style = MaterialTheme.typography.labelMedium, color = Ink,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Haldi).padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.width(8.dp))
            SavedCopyNote(app.offline, b?.updatedAt)
            Spacer(Modifier.weight(1f))
            Text(b?.let { "${nf.format(it.members)} members" } ?: "Everyone", style = MaterialTheme.typography.bodySmall, color = MutedOnDark)
        }
        Text(b?.name ?: "Bas 1 Minute India", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            when {
                b == null -> "See the top 100 this week."
                b.me.rank != null -> "You’re #${nf.format(b.me.rank)} of ${nf.format(b.ranked)} this week" + (b.topPercent?.takeIf { b.me.rank!! > 100 }?.let { " · top $it%" } ?: "")
                !b.me.shares -> "You can see the top 100. Share your steps to get a rank."
                else -> "You’re hidden from the ranking. You can still see it."
            },
            style = MaterialTheme.typography.bodyMedium, color = MutedOnDark,
        )
        b?.top?.take(3)?.let { top ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEach { r ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Ink3).padding(10.dp)) {
                        Text(listOf("🥇", "🥈", "🥉").getOrElse(r.rank - 1) { "#${r.rank}" } + " " + r.name, color = Color.White,
                            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.points} pts", color = Haldi, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Text("See top 100 →", style = MaterialTheme.typography.labelLarge, color = Haldi)
    }
}

// ================================================================ Global board screen

@Composable
fun GlobalBoardScreen(app: AppController, onBack: () -> Unit) {
    LaunchedEffect(app.globalPeriod) { app.loadGlobal() }
    val b = app.globalBoard
    var confirmShare by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Ink, Ink2))).statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Circles", color = MutedOnDark, style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.weight(1f)); SavedCopyNote(app.offline, b?.updatedAt)
            }
            Text(b?.name ?: "Bas 1 Minute India", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(b?.let { "${nf.format(it.members)} members · ${nf.format(it.ranked)} ranked" +
                if (app.globalPeriod == "week") " · resets in ${it.daysLeft} day${if (it.daysLeft == 1) "" else "s"}" else "" } ?: "Loading…",
                style = MaterialTheme.typography.bodyMedium, color = MutedOnDark)
            Segmented(listOf("This week", "Today"), if (app.globalPeriod == "today") 1 else 0) { app.globalPeriod = if (it == 1) "today" else "week" }
            b?.let { MyRankBanner(app, it) { confirmShare = true } }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (b == null) {
                Text(if (app.offline) "You’re offline. The ranking needs the internet the first time." else "Loading the ranking…", color = Muted)
            } else {
                AppCard(title = "Top 100") {
                    GlobalPodium(b.top.take(3))
                    b.top.drop(3).forEach { GlobalLine(it) }
                    val me = b.me
                    if (me.rank != null && me.rank > 100) {
                        Text("···", color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        GlobalLine(GlobalRow(me.rank, app.prefs.firstName.ifBlank { "You" }, null, me.points, me.steps, true))
                    }
                }
            }
            AppCard(title = "How the global ranking works") {
                Text("Everyone with the app is in this circle and can see the top 100. You get a rank when you share your steps. " +
                    "One point for every day you reach your own step goal, so a 4,000-step goal counts the same as 10,000. " +
                    "Ties go to more steps. Weeks reset every Monday.",
                    style = MaterialTheme.typography.bodyMedium, color = Muted)
                Text("Fair play: only steps your phone or watch measured count here, not typed ones. Names are shortened so nobody can be identified.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                if (app.prefs.shareActivity) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Show me in the global ranking", style = MaterialTheme.typography.titleMedium)
                            Text("As “${com.bas1minute.sync.api.Community.mask(app.prefs.name.ifBlank { app.prefs.firstName })}” to others", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        Switch(checked = b?.me?.visible ?: app.prefs.globalVisible, onCheckedChange = { app.setGlobalVisible(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmShare) AlertDialog(
        onDismissRequest = { confirmShare = false },
        title = { Text("Share your steps?") },
        text = { Text("Your daily steps and screen time go to the server, so you get a rank here and in your circles. Nothing else leaves your phone. You can stop any time in Profile.") },
        confirmButton = { TextButton(onClick = { confirmShare = false; app.setSharing(true); app.launch { delay(1500); app.loadGlobal() } }) { Text("Share and get my rank") } },
        dismissButton = { TextButton(onClick = { confirmShare = false }) { Text(t("Cancel"), color = Muted) } },
    )
}

@Composable
private fun MyRankBanner(app: AppController, b: GlobalBoard, onShare: () -> Unit) {
    val me = b.me
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink3).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            me.rank != null -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("#${nf.format(me.rank)}", style = MaterialTheme.typography.displayMedium, color = Haldi)
                    Spacer(Modifier.width(8.dp))
                    Text("of ${nf.format(b.ranked)}", style = MaterialTheme.typography.titleMedium, color = MutedOnDark, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${me.points} pts", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("${nf.format(me.steps)} steps", style = MaterialTheme.typography.bodySmall, color = MutedOnDark)
                    }
                }
                val cut = b.top.lastOrNull()?.points
                Text(when {
                    me.rank <= 3 -> "On the podium. Shabash!"
                    me.rank <= 100 -> "You’re in the top 100."
                    cut != null && cut > me.points -> "${cut - me.points} more point${if (cut - me.points == 1) "" else "s"} to reach the top 100" + (b.topPercent?.let { " · you’re in the top $it%" } ?: "")
                    else -> "Keep going to reach the top 100."
                }, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
            !me.shares -> {
                Text("You’re not ranked yet", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Your steps stay on your phone, so there’s nothing to rank. Share them to see where you stand.",
                    style = MaterialTheme.typography.bodySmall, color = MutedOnDark)
                SmallPill("Share my steps", onShare)
            }
            else -> {
                Text("You’re hidden from the ranking", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Others can’t see you here. Your circles still can.", style = MaterialTheme.typography.bodySmall, color = MutedOnDark)
                SmallPill("Show me") { app.setGlobalVisible(true) }
            }
        }
    }
}

@Composable
private fun GlobalPodium(top: List<GlobalRow>) {
    if (top.isEmpty()) return
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        order.forEach { r ->
            val h = when (r.rank) { 1 -> 96.dp; 2 -> 72.dp; else -> 56.dp }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(listOf("🥇", "🥈", "🥉")[r.rank - 1], style = MaterialTheme.typography.headlineMedium)
                Text(r.name + if (r.isMe) " (you)" else "", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                r.city?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1) }
                Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(if (r.rank == 1) Haldi else if (r.rank == 2) Violet else Color(0xFFFFCBA4)), contentAlignment = Alignment.Center) {
                    Text("${r.points}", style = MaterialTheme.typography.headlineMedium, color = Ink)
                }
            }
        }
    }
}

@Composable
private fun GlobalLine(r: GlobalRow) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (r.isMe) Color(0xFFFFF3C8) else Color.Transparent)
        .then(if (r.isMe) Modifier.border(1.dp, Haldi, RoundedCornerShape(14.dp)) else Modifier).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("${nf.format(r.rank)}", style = MaterialTheme.typography.titleMedium, color = if (r.isMe) Ink else Muted, modifier = Modifier.width(48.dp))
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (r.isMe) Haldi else Ink), contentAlignment = Alignment.Center) {
            Text(r.name.take(1).uppercase(), color = if (r.isMe) Ink else Haldi, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name + if (r.isMe) " (you)" else "", style = MaterialTheme.typography.titleMedium, color = if (r.isMe) Ink else TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(r.city, "${nf.format(r.steps)} steps").joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = if (r.isMe) Ink else Muted, maxLines = 1)
        }
        Text("${r.points}", style = MaterialTheme.typography.titleLarge, color = if (r.isMe) Ink else TextMain)
        Text(" pts", style = MaterialTheme.typography.bodySmall, color = if (r.isMe) Ink else Muted)
    }
}

// ================================================================ Find a circle

@Composable
fun FindCirclesScreen(app: AppController, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoundCircle>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<FoundCircle?>(null) }
    var version by remember { mutableStateOf(0) }

    // Search as you type, after a short pause; empty = popular circles
    LaunchedEffect(query, version) {
        if (query.isNotEmpty() && query.trim().length < 2) return@LaunchedEffect
        delay(if (query.isEmpty()) 0 else 400)
        when (val r = app.community.search(query.trim())) {
            is Res.Ok -> { results = r.value; error = null }
            is Res.Fail -> { error = r.message; if (results == null) results = emptyList() }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Circles", color = Muted, style = MaterialTheme.typography.labelLarge) }
        }
        Text("Find a circle", style = MaterialTheme.typography.headlineLarge)
        Text("Search open circles by name, city or type. The owner approves who joins.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(40) }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Try “Hyderabad”, “office” or “runners”") },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium) }
        val list = results
        when {
            list == null -> Text("Loading…", color = Muted)
            list.isEmpty() -> AppCard {
                Text("No open circles match “${query.trim()}”.", style = MaterialTheme.typography.titleMedium)
                Text("Start your own and switch on “Show in search” so others can find it.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            else -> {
                if (query.isBlank()) Text("Popular this week", style = MaterialTheme.typography.titleLarge)
                list.forEach { c -> FoundCard(c, onAsk = { asking = c }, onCancel = {
                    app.launch { app.handle(app.community.cancelRequest(c.id), "Request cancelled.") { version++; app.loadMyRequests() } }
                }) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    asking?.let { c ->
        var msg by remember(c.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("Ask to join ${c.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The owner sees your shortened name and this note, and can approve or decline. If you share steps, they also see your points this week.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    OutlinedTextField(value = msg, onValueChange = { msg = it.take(140) }, placeholder = { Text("Say hello (optional)") },
                        shape = RoundedCornerShape(14.dp), supportingText = { Text("${msg.length}/140") })
                }
            },
            confirmButton = { TextButton(onClick = {
                asking = null
                app.launch { app.handle(app.community.requestJoin(c.id, msg.trim()), "Request sent. We’ll show it here when ${c.owner} answers.") { version++; app.loadMyRequests() } }
            }) { Text("Send request") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(t("Cancel"), color = Muted) } },
        )
    }
}

@Composable
private fun FoundCard(c: FoundCircle, onAsk: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardBg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.kindLabel, style = MaterialTheme.typography.labelMedium, color = Ink,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(kindColor(c.kind)).padding(horizontal = 10.dp, vertical = 4.dp))
            c.city?.let { Text("  📍 $it", style = MaterialTheme.typography.bodySmall, color = Muted) }
            Spacer(Modifier.weight(1f))
            Text("${c.size}/20", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text(c.name, style = MaterialTheme.typography.titleLarge)
        c.about?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Text("Owner ${c.owner} · ${c.weekPoints} team points this week", style = MaterialTheme.typography.bodySmall, color = Muted)
        when {
            c.status == "member" -> Text("✓ You’re a member", style = MaterialTheme.typography.labelLarge, color = MintDark)
            c.status == "pending" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏳ Request sent", style = MaterialTheme.typography.labelLarge, color = Ink, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel request", color = Muted) }
            }
            c.status == "declined" -> Text("The owner didn’t approve this request.", style = MaterialTheme.typography.bodySmall, color = Muted)
            c.full -> Text("Full", style = MaterialTheme.typography.labelLarge, color = Muted)
            else -> SmallPill("Ask to join", onAsk)
        }
    }
}

// ================================================================ My requests (on Circles)

@Composable
fun MyRequestsCard(app: AppController) {
    LaunchedEffect(Unit) { app.loadMyRequests() }
    val list = app.myRequests
    if (list.isEmpty()) return
    AppCard(title = "Your requests") {
        list.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text(when (r.status) {
                        "approved" -> "✓ Approved. You’re in."
                        "declined" -> "Not approved"
                        else -> "⏳ Waiting for the owner · sent ${r.ago}"
                    }, style = MaterialTheme.typography.bodySmall, color = if (r.status == "approved") MintDark else Muted)
                }
                if (r.status == "pending") TextButton(onClick = {
                    app.launch { app.handle(app.community.cancelRequest(r.circleId), "Request cancelled.") { app.loadMyRequests() } }
                }) { Text("Cancel", color = Muted) }
                else if (r.status == "approved") SmallPill("Open") { app.refreshCircles(); app.openCircle(r.circleId) }
            }
        }
    }
}

// ================================================================ Owner: requests + show in search

@Composable
fun JoinRequestsCard(app: AppController, d: CircleDetail) {
    var list by remember(d.id) { mutableStateOf<List<JoinRequest>?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(d.id, reload) {
        val r = app.community.joinRequests(d.id)
        list = if (r is Res.Ok) r.value else emptyList()
    }
    val requests = list ?: return
    if (requests.isEmpty()) return
    AppCard(title = "Join requests") {
        Text("${requests.size} ${if (requests.size == 1) "person wants" else "people want"} to join. Approve only people you’re happy to share steps with.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        requests.forEach { r ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                        Text(r.name.take(1).uppercase(), color = Haldi, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text((if (r.shares && r.weekPoints != null) "${r.weekPoints} pts this week" else "🔒 Private") + " · ${r.ago}",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                r.message?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPill("Approve") {
                        app.launch { app.handle(app.community.decide(d.id, r.id, true), "${r.name} can join now.") { reload++; app.openCircle(d.id) } }
                    }
                    TextButton(onClick = {
                        app.launch { app.handle(app.community.decide(d.id, r.id, false), "Declined.") { reload++ } }
                    }) { Text("Decline", color = Muted) }
                }
            }
        }
    }
}

/** In Manage circle: open the circle to search, with a one-line description. */
@Composable
fun DiscoverableSetting(app: AppController, d: CircleDetail) {
    var on by remember(d.id) { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(d.id) { on = app.community.discoverable(d.id) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Show in search", style = MaterialTheme.typography.titleMedium)
            Text(if (on) "Anyone can find it and ask to join. You approve each person." else "Only people with your invite can join.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked = on, onCheckedChange = { v -> if (v) editing = true else app.launch {
            app.handle(app.community.setDiscoverable(d.id, false, null), "Hidden from search.") { on = false }
        } }, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi))
    }
    if (editing) {
        var about by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Show ${d.name} in search?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("People will see the name, type, member count and this line. Members’ names stay shortened until you approve someone.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    OutlinedTextField(value = about, onValueChange = { about = it.take(100) }, placeholder = { Text("What’s this circle about? (optional)") },
                        shape = RoundedCornerShape(14.dp), supportingText = { Text("${about.length}/100") })
                }
            },
            confirmButton = { TextButton(onClick = {
                editing = false
                app.launch { app.handle(app.community.setDiscoverable(d.id, true, about.trim()), "${d.name} now shows in search.") { on = true } }
            }) { Text("Show in search") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(t("Cancel"), color = Muted) } },
        )
    }
}
