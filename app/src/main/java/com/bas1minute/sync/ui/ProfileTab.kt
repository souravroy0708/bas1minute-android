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
    val help: HelpActions,
    val onOpenBatterySettings: () -> Unit,
    val onOpenAppInfo: () -> Unit,
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
    var confirmStopSharing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }
    if (changingPin) {
        androidx.activity.compose.BackHandler { changingPin = false }
        NameAndPin(title = "Change PIN", onBack = { changingPin = false }, askName = false) { _, pin -> app.resetPin(pin); changingPin = false }
        return
    }
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
                Text(if (prefs.shareActivity) "Sharing steps and screen time" else "Private: everything stays on this phone", style = MaterialTheme.typography.bodySmall, color = Muted)
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
            var stepCm by remember { mutableStateOf(prefs.stepLengthCm.toString()) }
            AppField("Step length (cm)", stepCm, { v ->
                stepCm = v.filter(Char::isDigit).take(3)
                stepCm.toIntOrNull()?.takeIf { it in 40..120 }?.let { prefs.stepLengthCm = it; app.home = app.homeState() }
            }, if (stepCm.toIntOrNull()?.let { it in 40..120 } == false) "Between 40 and 120 cm" else null,
                keyboard = KeyboardType.Number,
                hint = "Used for distance: ${com.bas1minute.sync.Distance.stepsPerKm(prefs.stepLengthCm)} steps ≈ 1 km. " +
                    "To measure: walk 10 normal steps, measure the distance, divide by 10.")
            BigButton(if (app.busy) "Saving…" else "Save", enabled = !app.busy) {
                val body = JSONObject().put("name", name).put("city", city)
                stepGoal.toIntOrNull()?.let { body.put("step_goal", it) }
                burstGoal.toIntOrNull()?.let { body.put("burst_goal", it) }
                screenGoal.toIntOrNull()?.let { body.put("screen_goal_minutes", it) }
                app.updateGoals(body)
            }
        }

        // Privacy: sharing is off by default
        AppCard(title = "Your data") {
            Text("Everything is stored on this phone. No phone number, email or password. We don’t sell your data.",
                style = MaterialTheme.typography.bodyMedium, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Share my steps and screen time", style = MaterialTheme.typography.titleMedium)
                    Text("Lets circles rank you and keeps a backup. Only these two numbers are sent; nothing else ever leaves the phone.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(checked = prefs.shareActivity, enabled = !app.busy,
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on -> if (on) app.setSharing(true) else confirmStopSharing = true })
            }
            if (!prefs.shareActivity) {
                Text("You can still join circles and see members who share. You’ll show as Private.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            } else {
                BigButton(if (app.home.syncing) "Syncing…" else "Back up now", enabled = !app.home.syncing) { app.fullSync() }
                OfflineNote(snap.pendingDays + app.waiting, snap.savedDays)
                app.fullSyncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
            }
        }

        // App lock with the PIN set at install
        AppCard(title = "App lock") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ask for my PIN when the app opens", style = MaterialTheme.typography.titleMedium)
                    Text("A simple lock for shared phones. Forgot it? Set a new one from the lock screen.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(checked = prefs.appLock, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on -> prefs.appLock = on; app.home = app.homeState() })
            }
            TextButton(onClick = { changingPin = true }) { Text("Change PIN", color = Ink, style = MaterialTheme.typography.labelLarge) }
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
            if (!snap.usageAccess && snap.restrictedSettings) {
                UsageAccessHelp(onOpenUsageAccess = actions.onOpenUsageAccess, onOpenAppInfo = actions.onOpenAppInfo, helpActions = actions.help)
            }
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
            LinkRow("Phone-specific help", "Settings for your ${com.bas1minute.sync.PhoneHelp.device().brand}: usage access, battery") { app.aboutPage = "help" }
        }

        TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete everything and start fresh", color = Danger, style = MaterialTheme.typography.labelLarge)
        }
        Text("Bas 1 Minute supports everyday activity and isn’t medical advice.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }

    if (confirmStopSharing) {
        AlertDialog(
            onDismissRequest = { confirmStopSharing = false },
            title = { Text("Stop sharing?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Nothing more will leave this phone, and you’ll show as Private in circles. Also delete what you shared before?",
                style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { confirmStopSharing = false; app.setSharing(false, deleteShared = true) }) {
                Text("Stop and delete", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmStopSharing = false; app.setSharing(false) }) { Text("Just stop", color = Ink) } },
        )
    }
    if (deleting) {
        Confirm("Delete everything?", "Removes all your data from this phone and your circle account from the server. You’ll start again from setup. This can’t be undone.",
            "Delete everything", onDismiss = { deleting = false }) {
            deleting = false
            app.deleteEverything()
        }
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
