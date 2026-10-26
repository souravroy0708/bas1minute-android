package com.bas1minute.sync.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.api.BoardRow
import com.bas1minute.sync.api.CircleDetail
import com.bas1minute.sync.api.CircleSummary
import com.bas1minute.sync.api.Res
import java.text.NumberFormat
import java.util.Locale

/** Invite message: joins happen inside the app with the code, never on a web page. */
fun inviteText(from: String, circle: String, code: String): String =
    (if (from.isNotBlank()) "$from invited you" else "You’re invited") +
        " to “$circle” on Bas 1 Minute. Roz 7,000 steps aur 4 one-minute bursts, saath mein.\n\n" +
        "Open the Bas 1 Minute app → Circles → Join with code: $code"

fun kindColor(kind: String): Color = when (kind) {
    "family" -> Haldi
    "friends", "college" -> Violet
    "office" -> Mint
    else -> Coral
}

@Composable
fun CirclesTab(app: AppController, onShare: (String) -> Unit, onShareWhatsApp: (String) -> Unit) {
    LaunchedEffect(Unit) { app.refreshCircles() }
    if (app.openCircleId != null) {
        BackHandler { app.closeCircle() }
        CircleScreen(app, onShare, onShareWhatsApp)
    } else {
        CircleList(app)
    }
}

@Composable
private fun CircleList(app: AppController) {
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    val data = app.circles

    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your circles", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
        Text("Move together with family, college friends or your office team.", style = MaterialTheme.typography.bodyLarge, color = Muted)
        if (app.offline) Text("Offline: showing your last saved circles.", style = MaterialTheme.typography.bodySmall, color = Muted)

        if (data == null || data.circles.isEmpty()) {
            AppCard {
                Badge("👨‍👩‍👧", Color(0xFFFFF3C8), 72)
                Text("Fitness is easier together", style = MaterialTheme.typography.titleLarge)
                Text("Start a circle for your family, then share the invite on WhatsApp. You can be in up to 10 circles.",
                    style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        } else {
            data.circles.forEach { c -> CircleCard(c) { app.openCircle(c.id) } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Start a circle", modifier = Modifier.weight(1f)) { creating = true }
            GhostButton("Join with code", modifier = Modifier.weight(1f)) { joining = true }
        }
        AppCard(title = "How points work") {
            Text("Each day you can earn up to 3 points: your step goal, your burst goal, and at least a minute of calm. Goals are personal, so Papa’s 4,000 steps can beat your 7,000 if that’s his goal. Weeks reset every Monday.",
                style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf("family") }
        val kinds = data?.kinds?.takeIf { it.isNotEmpty() }
            ?: listOf("family" to "Family", "friends" to "Friends", "college" to "College friends", "office" to "Office", "other" to "Other")
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Start a circle", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField("Circle name", name, { name = it.take(60) }, null, placeholder = "Sharma family")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kinds.forEach { (value, label) ->
                            val on = kind == value
                            Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Ink,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Paper)
                                    .clickable { kind = value }.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    creating = false
                    app.launch {
                        val res = app.repo.createCircle(name, kind)
                        app.handle(res, "“$name” is ready. Invite people to start.") {
                            if (res is Res.Ok) app.openCircle(res.value.getJSONObject("circle").getInt("id"))
                        }
                    }
                }) { Text("Create", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel", color = Muted) } },
        )
    }

    if (joining) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { joining = false },
            title = { Text("Join a circle", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the code from your invite. It’s at the end of the invite link too.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                    androidx.compose.material3.OutlinedTextField(
                        value = code, onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12) },
                        singleLine = true, placeholder = { Text("K7M2QX9PTA") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = code.length >= 6, onClick = {
                    joining = false
                    app.launch {
                        val res = app.repo.joinCircle(code)
                        app.handle(res, "You joined. Say hello with a Shabash!") {
                            if (res is Res.Ok) app.openCircle(res.value.getJSONObject("circle").getInt("id"))
                        }
                    }
                }) { Text("Join", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { joining = false }) { Text("Cancel", color = Muted) } },
        )
    }
}

@Composable
private fun CircleCard(c: CircleSummary, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).clickable(onClick = onClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.kindLabel, style = MaterialTheme.typography.labelMedium, color = Ink,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(kindColor(c.kind)).padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.weight(1f))
            Text("${c.size} member${if (c.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text(c.name, style = MaterialTheme.typography.headlineMedium)
        Text("You’re #${c.myRank} with ${c.myPoints} point${if (c.myPoints == 1) "" else "s"} this week", style = MaterialTheme.typography.bodyMedium)
        TeamBar(c.teamPct, onDark = false)
        Text("Team goal: ${c.teamPoints} of ${c.teamTarget} points", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun TeamBar(pct: Int, onDark: Boolean) {
    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(if (onDark) Ink3 else Paper)) {
        Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).height(12.dp).clip(RoundedCornerShape(50)).background(Haldi))
    }
}

@Composable
private fun CircleScreen(app: AppController, onShare: (String) -> Unit, onShareWhatsApp: (String) -> Unit) {
    val d = app.circle
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { app.closeCircle() }) { Text("← Circles", color = Muted, style = MaterialTheme.typography.labelLarge) }
        if (d == null) {
            Text(if (app.offline) "You’re offline and this circle isn’t saved yet." else "Loading…", color = Muted)
            return@Column
        }
        // Hero
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(d.kindLabel, style = MaterialTheme.typography.labelMedium, color = Ink,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(kindColor(d.kind)).padding(horizontal = 10.dp, vertical = 4.dp))
            Text(d.name, style = MaterialTheme.typography.displayMedium, color = Color.White)
            Text(
                "${d.board.size} member${if (d.board.size == 1) "" else "s"}. " +
                    if (d.daysLeft > 0) "Week ends in ${d.daysLeft} day${if (d.daysLeft == 1) "" else "s"}." else "Last day of the week.",
                style = MaterialTheme.typography.bodyMedium, color = MutedOnDark,
            )
            Spacer(Modifier.height(6.dp))
            Row { Text("Team goal", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${d.teamPoints} / ${d.teamTarget}", color = Color.White, style = MaterialTheme.typography.titleMedium) }
            TeamBar(d.teamPct, onDark = true)
            Text(if (d.teamPct >= 100) "Team goal reached. Bahut badhiya, sab log!" else "Every point anyone earns fills this bar.",
                style = MaterialTheme.typography.bodySmall, color = MutedOnDark)
        }

        // Leaderboard
        AppCard(title = "This week") {
            Text("Up to ${d.pointsPerDay} points a day, against each person’s own goals.", style = MaterialTheme.typography.bodySmall, color = Muted)
            d.board.forEach { row -> BoardLine(row, onCheer = { kind -> app.cheer(row.userId, kind) }) }
        }

        if (d.feed.isNotEmpty()) {
            AppCard(title = "Today in ${d.name}") {
                d.feed.forEach { f ->
                    Row { Text("${f.from} ${if (f.kind == "cheer") "cheered" else "nudged"} ${f.to}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(f.at, style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
            }
        }

        // Invite
        AppCard(title = "Invite people") {
            if (d.full) {
                Text("This circle is full (20 members).", color = Muted, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Anyone with this code can join. Share it only with people you want in the circle.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Text(d.inviteCode, style = MaterialTheme.typography.displayMedium, color = Ink)
                val invite = inviteText(app.prefs.firstName, d.name, d.inviteCode)
                BigButton("Invite on WhatsApp", dark = false) { onShareWhatsApp(invite) }
                GhostButton("Share invite") { onShare(invite) }
            }
        }

        // What I share
        AppCard(title = "What you share here") {
            Text("Steps, bursts and calm are shared with your circles. Your mood is never shared.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Also show my screen time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = d.shareScreen, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on -> app.launch { app.handle(app.repo.shareScreen(d.id, on), "Sharing updated.") { app.openCircle(d.id) } } })
            }
        }

        if (d.isOwner) OwnerPanel(app, d)

        LeaveButton(app, d)
    }
}

@Composable
private fun BoardLine(row: BoardRow, onCheer: (String) -> Unit) {
    val numbers = remember { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (row.isMe) Color(0xFFF7F8FF) else Color.White).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${row.rank}", style = MaterialTheme.typography.titleLarge, color = if (row.rank == 1) Color(0xFFB36B00) else Muted, modifier = Modifier.width(28.dp))
            Box(Modifier.size(42.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                Text(row.name.take(1).uppercase(), color = Haldi, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name + (if (row.isMe) " (you)" else "") + (if (row.isOwner) "  ·  Owner" else ""), style = MaterialTheme.typography.titleMedium)
                val extras = buildList {
                    add("${numbers.format(row.stepsToday)} steps")
                    add("${row.burstsToday} burst${if (row.burstsToday == 1) "" else "s"}")
                    if (row.calmToday) add("calm done")
                    row.screenToday?.let { add("${hm(it)} screen") }
                }
                Text("Today: " + extras.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Muted)
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(50)).background(Paper)) {
                    Box(Modifier.fillMaxWidth(row.todayPct.coerceIn(0, 100) / 100f).height(6.dp).clip(RoundedCornerShape(50)).background(Blue))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${row.points}", style = MaterialTheme.typography.headlineMedium)
                Text("pts", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        if (!row.isMe) {
            Row(Modifier.padding(start = 80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CheerChip(if (row.cheered) "Cheered" else "Shabash!", done = row.cheered) { onCheer("cheer") }
                if (row.pointsToday < 3) CheerChip(if (row.nudged) "Nudged" else "Chalo!", done = row.nudged) { onCheer("nudge") }
            }
        }
    }
}

@Composable
private fun CheerChip(text: String, done: Boolean, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = if (done) Muted else Ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (done) Paper else Haldi)
            .clickable(enabled = !done, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
private fun OwnerPanel(app: AppController, d: CircleDetail) {
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<BoardRow?>(null) }
    AppCard(title = "Manage circle") {
        StatusRow("Name and type", "${d.name} · ${d.kindLabel}", ok = true, action = "Edit") { renaming = true }
        StatusRow("Invite link", "Make a new one if the old link spread too far", ok = true, action = "New link") {
            app.launch { app.handle(app.repo.manageCircle(d.id, "invite"), "New invite link made. The old one no longer works.") { app.openCircle(d.id) } }
        }
        d.board.filter { !it.isMe }.forEach { m -> StatusRow(m.name, "Member", ok = true, action = "Remove") { removing = m } }
        TextButton(onClick = { confirmDelete = true }) { Text("Delete circle for everyone", color = Danger, style = MaterialTheme.typography.labelLarge) }
    }
    if (renaming) {
        var name by remember { mutableStateOf(d.name) }
        var kind by remember { mutableStateOf(d.kind) }
        val kinds = app.circles?.kinds?.takeIf { it.isNotEmpty() }
            ?: listOf("family" to "Family", "friends" to "Friends", "college" to "College friends", "office" to "Office", "other" to "Other")
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Edit circle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField("Name", name, { name = it.take(60) }, null)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kinds.forEach { (value, label) ->
                            val on = kind == value
                            Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Ink,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Paper)
                                    .clickable { kind = value }.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                renaming = false
                app.launch { app.handle(app.repo.manageCircle(d.id, "rename", org.json.JSONObject().put("name", name).put("kind", kind)), "Circle updated.") { app.openCircle(d.id) } }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = Muted) } },
        )
    }
    removing?.let { m ->
        Confirm("Remove ${m.name}?", "They can rejoin only with a new invite.", "Remove", onDismiss = { removing = null }) {
            removing = null
            app.launch { app.handle(app.repo.manageCircle(d.id, "remove", org.json.JSONObject().put("user_id", m.userId)), "${m.name} removed.") { app.openCircle(d.id) } }
        }
    }
    if (confirmDelete) {
        Confirm("Delete ${d.name}?", "This deletes the circle for every member. It can’t be undone.", "Delete", onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            app.launch { app.handle(app.repo.manageCircle(d.id, "delete"), "Circle deleted.") { app.closeCircle() } }
        }
    }
}

@Composable
private fun LeaveButton(app: AppController, d: CircleDetail) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Leave this circle", color = Muted) }
    if (confirm) {
        Confirm("Leave ${d.name}?", if (d.isOwner) "Another member becomes the owner." else "You can rejoin with an invite.", "Leave", onDismiss = { confirm = false }) {
            confirm = false
            app.launch { app.handle(app.repo.leaveCircle(d.id), "You left ${d.name}.") { app.closeCircle() } }
        }
    }
}

@Composable
fun Confirm(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = Danger, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
    )
}
