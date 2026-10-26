package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.bas1minute.sync.AppController
import com.bas1minute.sync.api.Res
import org.json.JSONObject

data class PhoneActions(
    val onOpenSamsungHealth: () -> Unit,
    val onAskHealthConnect: () -> Unit,
    val onInstallHealthConnect: () -> Unit,
    val onAskActivity: () -> Unit,
    val onOpenUsageAccess: () -> Unit,
    val onShareWhatsApp: (String) -> Unit,
)

@Composable
fun ProfileTab(app: AppController, actions: PhoneActions) {
    val prefs = app.prefs
    val me = app.today?.me
    var name by remember(me) { mutableStateOf(me?.name ?: prefs.name) }
    var city by remember(me) { mutableStateOf(me?.city ?: "") }
    var stepGoal by remember(me) { mutableStateOf((me?.stepGoal ?: prefs.stepGoal).toString()) }
    var burstGoal by remember(me) { mutableStateOf((me?.burstGoal ?: prefs.burstGoal).toString()) }
    var screenGoal by remember(me) { mutableStateOf((me?.screenGoal ?: prefs.screenLimitMinutes).toString()) }
    var confirmLogout by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val snap = app.phone

    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                Text((prefs.firstName.ifBlank { "B" }).take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, color = Haldi)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(prefs.name.ifBlank { "Your profile" }, style = MaterialTheme.typography.headlineMedium)
                Text(prefs.mobile + (me?.memberSince?.let { "  ·  since ${it.take(7)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        // Goals
        AppCard(title = "Your details and goals") {
            AppField("Name", name, { name = it.take(100) }, null)
            AppField("City", city, { city = it.take(80) }, null, placeholder = "Hyderabad")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField("Steps", stepGoal, { stepGoal = it.filter(Char::isDigit).take(5) }, null, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                AppField("Bursts", burstGoal, { burstGoal = it.filter(Char::isDigit).take(1) }, null, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
            }
            AppField("Screen-time limit (minutes)", screenGoal, { screenGoal = it.filter(Char::isDigit).take(3) }, null,
                keyboard = KeyboardType.Number, hint = "180 = 3 hours")
            BigButton(if (app.busy) "Saving…" else "Save", enabled = !app.busy) {
                val body = JSONObject().put("name", name).put("city", city)
                stepGoal.toIntOrNull()?.let { body.put("step_goal", it) }
                burstGoal.toIntOrNull()?.let { body.put("burst_goal", it) }
                screenGoal.toIntOrNull()?.let { body.put("screen_goal_minutes", it) }
                app.updateGoals(body)
            }
        }

        // Two-way sync with the server
        AppCard(title = "Sync with server") {
            val waiting = snap.pendingDays + app.waiting
            Text(
                "Backs up everything to your Bas 1 Minute account and brings your last 90 days to this phone, " +
                    "so nothing is lost if you change phones.",
                style = MaterialTheme.typography.bodyMedium, color = Muted,
            )
            BigButton(if (app.home.syncing) "Syncing…" else "Sync with server", enabled = !app.home.syncing) { app.fullSync() }
            OfflineNote(waiting, snap.savedDays)
            app.fullSyncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (waiting == 0) MintDark else Muted) }
        }

        // Phone connections
        AppCard(title = "This phone") {
            when (snap.hcStatus) {
                HealthConnectClient.SDK_AVAILABLE -> StatusRow("Health Connect", if (snap.hcGranted) "Reading your steps" else "Not allowed yet",
                    ok = snap.hcGranted, action = if (snap.hcGranted) null else "Allow", onAction = actions.onAskHealthConnect)
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StatusRow("Health Connect", "Needs installing or updating",
                    ok = false, action = "Get it", onAction = actions.onInstallHealthConnect)
                else -> {}
            }
            if (snap.samsungInstalled) StatusRow(
                "Samsung Health",
                when (snap.samsungSharing) { true -> "Sharing with Health Connect"; false -> "Installed, but not sharing yet"; else -> "Allow Health Connect first" },
                ok = snap.samsungSharing == true,
                action = if (snap.samsungSharing == true) null else if (snap.hcGranted) "Set up" else "Allow",
                onAction = if (snap.hcGranted) actions.onOpenSamsungHealth else actions.onAskHealthConnect,
            )
            if (snap.recordingAvailable) StatusRow("Phone step sensor", if (snap.activityGranted) "Backup counting is on" else "Off",
                ok = snap.activityGranted, action = if (snap.activityGranted) null else "Allow", onAction = actions.onAskActivity)
            StatusRow("Screen time and social media", if (snap.usageAccess) "Usage access on" else "Usage access off",
                ok = snap.usageAccess, action = if (snap.usageAccess) null else "Open", onAction = actions.onOpenUsageAccess)
            if (snap.hcStatus == HealthConnectClient.SDK_AVAILABLE) {
                val e = snap.extras
                val on = listOfNotNull("sleep".takeIf { e.sleep }, "water".takeIf { e.water }, "weight".takeIf { e.weight })
                StatusRow("Sleep, water and weight", if (on.isEmpty()) "Optional: read from your watch, scale or apps" else "Reading " + on.joinToString(", "),
                    ok = on.size == 3, action = if (on.size == 3) null else "Allow", onAction = actions.onAskHealthConnect)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sync automatically", style = MaterialTheme.typography.titleMedium)
                    Text("About every 3 hours when online.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(checked = prefs.autoSync, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on -> app.setAutoSync(on) })
            }
            Text("${snap.savedDays} days saved on this phone. Everything works offline and syncs later.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }

        if (snap.samsungInstalled && snap.samsungSharing != true) {
            SamsungHealthCard(
                hcGranted = snap.hcGranted,
                onAllowHealthConnect = actions.onAskHealthConnect,
                onOpenSamsungHealth = actions.onOpenSamsungHealth,
            )
        }

        // Weight is opt-in
        AppCard(title = "Weight") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Track my weight", style = MaterialTheme.typography.titleMedium)
                    Text("Optional and private. Never shared with circles.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(checked = prefs.trackWeight, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on -> prefs.trackWeight = on; app.home = app.homeState() })
            }
        }

        // About (native pages, no web links)
        AppCard(title = "About Bas 1 Minute") {
            LinkRow("Our idea", "Why 7,000 steps and four one-minute bursts") { app.aboutPage = "idea" }
            LinkRow("How we’re different", "What other apps do, and the gap we fill") { app.aboutPage = "different" }
            LinkRow("Free for everyone", "Why the core stays free") { app.aboutPage = "free" }
            LinkRow("Privacy", "What the app reads and sends, and your choices") { app.aboutPage = "privacy" }
        }

        GhostButton("Log out") { confirmLogout = true }
        TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete my account and data", color = Danger, style = MaterialTheme.typography.labelLarge)
        }
        Text("Bas 1 Minute supports everyday activity and isn’t medical advice.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }

    if (confirmLogout) {
        Confirm("Log out?", "Your phone keeps counting steps. Log in again any time to sync.", "Log out", onDismiss = { confirmLogout = false }) {
            confirmLogout = false
            app.logout()
        }
    }
    if (deleting) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete your account?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently removes your profile, every day of activity, your circles memberships and cheers. It can’t be undone.",
                        style = MaterialTheme.typography.bodyMedium)
                    AppField("Type DELETE to confirm", typed, { typed = it }, null)
                }
            },
            confirmButton = {
                TextButton(enabled = typed == "DELETE", onClick = {
                    deleting = false
                    app.launch {
                        when (val r = app.repo.deleteAccount()) {
                            is Res.Ok -> { app.message = "Your account and data were deleted."; app.logout() }
                            is Res.Fail -> app.message = r.message
                            is Res.Queued -> {}
                        }
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel", color = Muted) } },
        )
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = Muted)
    }
}
