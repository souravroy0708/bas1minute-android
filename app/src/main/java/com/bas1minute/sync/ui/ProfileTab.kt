package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Edit
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
    val onPickPhoto: () -> Unit,
    val onShareImage: (android.graphics.Bitmap, String) -> Unit,
    val onAskNotifications: () -> Unit,
    val help: HelpActions,
    val onOpenBatterySettings: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onOpenSamsungHealth: () -> Unit,
    val onAskHealthConnect: () -> Unit,
    val onInstallHealthConnect: () -> Unit,
    val onAskActivity: () -> Unit,
    val onOpenUsageAccess: () -> Unit,
    val onShareWhatsApp: (String) -> Unit,
    val track: TrackActions = TrackActions(),
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
    var lockAfterPin by remember { mutableStateOf(false) }
    if (changingPin) {
        androidx.activity.compose.BackHandler { changingPin = false }
        NameAndPin(title = if (lockAfterPin) "Choose a PIN" else "Change PIN", onBack = { changingPin = false; lockAfterPin = false }, askName = false) { _, pin ->
            app.resetPin(pin)
            if (lockAfterPin) { prefs.appLock = true; lockAfterPin = false; app.home = app.homeState() }
            changingPin = false
        }
        return
    }
    val snap = app.phone

    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: photo, name, bio (all stay on this phone)
        var bio by remember { mutableStateOf(prefs.bio) }
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(28.dp)).background(Ink).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(app.avatar, prefs.firstName, 96.dp, Modifier.clickable { actions.onPickPhoto() })
                Box(Modifier.size(30.dp).clip(CircleShape).background(Haldi).clickable { actions.onPickPhoto() },
                    contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = t("Change photo"), tint = Ink, modifier = Modifier.size(16.dp))
                }
            }
            Text(prefs.name.ifBlank { "Your profile" }, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            if (bio.isNotBlank()) Text(bio, style = MaterialTheme.typography.bodyMedium, color = MutedOnDark,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill(if (app.avatar == null) "Add photo" else "Change photo") { actions.onPickPhoto() }
                if (app.avatar != null) Text(t("Remove"), style = MaterialTheme.typography.labelMedium, color = MutedOnDark,
                    modifier = Modifier.clickable { app.removeAvatar() }.padding(horizontal = 10.dp, vertical = 9.dp))
            }
            Text("🔒 Photo and bio stay on this phone", style = MaterialTheme.typography.bodySmall, color = MutedOnDark)
        }
        AppCard(title = t("Bio")) {
            AppField("A line about you", bio, { v -> bio = v.take(120); prefs.bio = bio; app.home = app.homeState() }, null,
                placeholder = "Walking 7K a day with my family 🚶", hint = "${bio.length}/120 · shown on your home screen")
        }

        SectionHeader(t("Appearance"))
        AppCard {
            Text(t("Language"), style = MaterialTheme.typography.titleMedium)
            ChoiceRow(listOf("en" to "English", "hi" to "हिन्दी"), app.language) { v -> prefs.language = v; app.language = v }
            Text(t("Theme"), style = MaterialTheme.typography.titleMedium)
            ChoiceRow(listOf("system" to t("System"), "light" to t("Light"), "dark" to t("Dark")), app.theme) { v -> prefs.theme = v; app.theme = v }
        }

        SectionHeader(t("Goals"))
        // Goals
        AppCard(title = "Your details and goals") {
            AppField("Name", name, { name = it.take(100) }, null)
            AppField("City", city, { city = it.take(80) }, null, placeholder = "Hyderabad")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(t("Steps"), stepGoal, { stepGoal = it.filter(Char::isDigit).take(5) }, null, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
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

        SectionHeader(t("Privacy and security"))
        // Privacy: sharing is off by default
        AppCard(title = t("Your data")) {
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

        // The website: approve a browser with a code from this phone (no password)
        AppCard(title = t("Website")) {
            LinkRow(t("Sign in on the website"), "See your steps, screen time and circles on a computer. Approve with a code; manage signed-in browsers") {
                app.openWebSignIn()
            }
        }

        // App lock with the PIN set at install
        AppCard(title = t("App lock")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ask for my PIN when the app opens", style = MaterialTheme.typography.titleMedium)
                    Text("A simple lock for shared phones. Forgot it? Set a new one from the lock screen.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(checked = prefs.appLock, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on ->
                        if (on && prefs.pinHash.isBlank()) { lockAfterPin = true; changingPin = true }   // choose a PIN first
                        else { prefs.appLock = on; app.home = app.homeState() }
                    })
            }
            if (prefs.pinHash.isNotBlank()) TextButton(onClick = { changingPin = true }) { Text("Change PIN", color = TextMain, style = MaterialTheme.typography.labelLarge) }
        }

        SectionHeader(t("Notifications"))
        // Motivation pushes from the Bas 1 Minute team
        AppCard(title = t("Motivation notifications")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nudges and health facts", style = MaterialTheme.typography.titleMedium)
                    Text("Personal messages built on this phone from your own steps and screen time. Your numbers aren’t sent for this.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(checked = prefs.pushEnabled, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on ->
                        prefs.pushEnabled = on
                        if (on) actions.onAskNotifications() else com.bas1minute.sync.Push.upload(app.ctxForPush)
                        app.home = app.homeState()
                    })
            }
            if (prefs.pushEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Playful taunts 😏", style = MaterialTheme.typography.titleMedium)
                        Text("“Tumse na ho payega?” style teasing. Switch off to get only motivation, facts and cheers.",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Switch(checked = prefs.allowTaunts, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                        onCheckedChange = { on -> prefs.allowTaunts = on; app.home = app.homeState() })
                }
            }
        }

        SectionHeader(t("This phone"))
        // Phone connections
        AppCard(title = t("This phone")) {
            when (snap.hcStatus) {
                HealthConnectClient.SDK_AVAILABLE -> StatusRow("Health Connect", if (snap.hcGranted) "Reading your steps" else "Not allowed yet",
                    ok = snap.hcGranted, action = if (snap.hcGranted) null else "Allow", onAction = actions.onAskHealthConnect)
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StatusRow("Health Connect", "Needs installing or updating",
                    ok = false, action = t("Get it"), onAction = actions.onInstallHealthConnect)
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

        SectionHeader(t("About"))
        // About (native pages, no web links)
        AppCard(title = "About Bas 1 Minute") {
            LinkRow("Our idea", "Why 7,000 steps and four one-minute bursts") { app.aboutPage = "idea" }
            LinkRow("How we’re different", "What other apps do, and the gap we fill") { app.aboutPage = "different" }
            LinkRow("Free for everyone", "Why the core stays free") { app.aboutPage = "free" }
            LinkRow("Privacy", "What the app reads and sends, and your choices") { app.aboutPage = "privacy" }
            LinkRow("Phone-specific help", "Settings for your ${com.bas1minute.sync.PhoneHelp.device().brand}: usage access, battery") { app.aboutPage = "help" }
        }

        SectionHeader(t("Danger zone"))
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
            dismissButton = { TextButton(onClick = { confirmStopSharing = false; app.setSharing(false) }) { Text("Just stop", color = TextMain) } },
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

/** Small uppercase label that groups Profile settings, like Android Settings. */
@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp))
}

@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(Modifier.heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else TextMain,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Paper)
                        .padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}
