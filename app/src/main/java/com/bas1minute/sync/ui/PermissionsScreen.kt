package com.bas1minute.sync.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.bas1minute.sync.Snapshot

data class OnboardingActions(
    val help: HelpActions,
    val onOpenAppInfo: () -> Unit,
    val onOpenSamsungHealth: () -> Unit,
    val onAskHealthConnect: () -> Unit,
    val onInstallHealthConnect: () -> Unit,
    val onAskActivity: () -> Unit,
    val onOpenUsageAccess: () -> Unit,
    val onAskNotifications: () -> Unit = {},
    val onFinish: (OnboardingChoices) -> Unit,
)

/** What's on after setup. Everything starts ON; the last page shows each with its own switch to opt out. */
data class OnboardingChoices(
    val nudges: Boolean = true,
    val teamMessages: Boolean = true,
    val online: Boolean = true,
    val ranking: Boolean = true,
)

/**
 * Right after the tour: steps, screen time, notifications, then "what's on". Each permission page can be skipped,
 * but the main button always asks first, and Home keeps a big "Start counting my steps" button (plus up to three
 * reminders) until steps are counting — many people missed the old just-in-time card.
 */
@Composable
fun PermissionsScreen(snapshot: Snapshot, actions: OnboardingActions) {
    var page by rememberSaveable { mutableIntStateOf(1) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page > 1) TextButton(onClick = { page-- }) { Text("← Back", color = Muted, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.weight(1f))
            Text("Step $page of 4", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..4).forEach { i ->
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(if (i <= page) Ink else PaperLine))
            }
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            label = "permissions",
            modifier = Modifier.weight(1f),
        ) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (p) {
                    1 -> StepsPage(snapshot, actions) { page = 2 }
                    2 -> ScreenPage(snapshot, actions) { page = 3 }
                    3 -> NotificationsPage(actions) { page = 4 }
                    else -> DefaultsPage(actions)
                }
            }
        }
    }
}

@Composable
private fun StepsPage(snapshot: Snapshot, actions: OnboardingActions, onNext: () -> Unit) {
    Badge("👟", Color(0xFFE9ECFF))
    Text("Count your steps", style = MaterialTheme.typography.headlineLarge)
    Text(
        "We read steps from Health Connect, where Google Fit, Samsung Health and most watches save them. On the same screen you can also allow sleep, water and weight (optional). If nothing is there, your phone counts steps itself.",
        style = MaterialTheme.typography.bodyLarge,
        color = Muted,
    )
    AppCard {
        when (snapshot.hcStatus) {
            HealthConnectClient.SDK_AVAILABLE -> StatusRow(
                "Health Connect", if (snapshot.hcGranted) "Connected" else "Steps from your health apps",
                ok = snapshot.hcGranted, action = if (snapshot.hcGranted) null else "Allow", onAction = actions.onAskHealthConnect,
            )
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StatusRow(
                "Health Connect", "Needs installing or updating", ok = false, action = t("Get it"), onAction = actions.onInstallHealthConnect,
            )
            else -> StatusRow("Health Connect", "Not available on this phone", ok = false)
        }
        if (snapshot.recordingAvailable) {
            StatusRow(
                "This phone’s step sensor", if (snapshot.activityGranted) "Counting in the background" else "A backup that works on its own",
                ok = snapshot.activityGranted, action = if (snapshot.activityGranted) null else "Allow", onAction = actions.onAskActivity,
            )
        }
    }
    if (snapshot.samsungNeedsSetup) {
        SamsungHealthCard(
            hcGranted = snapshot.hcGranted,
            onAllowHealthConnect = actions.onAskHealthConnect,
            onOpenSamsungHealth = actions.onOpenSamsungHealth,
        )
    }
    if (snapshot.stepsReady) BigButton("Continue", onClick = onNext)
    else {
        // The main button ASKS (most people tap the big button and never read the rows above)
        BigButton("Count my steps") {
            when {
                snapshot.hcStatus == HealthConnectClient.SDK_AVAILABLE -> actions.onAskHealthConnect()
                snapshot.recordingAvailable -> actions.onAskActivity()
                else -> actions.onInstallHealthConnect()
            }
        }
        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Skip — I’ll do it later", color = Muted, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ScreenPage(snapshot: Snapshot, actions: OnboardingActions, onNext: () -> Unit) {
    Badge("📱", Color(0xFFFFE7DF))
    Text("See your screen time", style = MaterialTheme.typography.headlineLarge)
    Text(
        "Your daily screen time, time on social media apps, and when you last and first used your phone around sleep. Worked out on your phone; only totals and those two times are sent, never which apps you used.",
        style = MaterialTheme.typography.bodyLarge,
        color = Muted,
    )
    AppCard {
        StatusRow(
            "Usage access", if (snapshot.usageAccess) "Turned on" else "Find Bas 1 Minute and switch on “Permit usage access”",
            ok = snapshot.usageAccess, action = if (snapshot.usageAccess) null else "Open", onAction = actions.onOpenUsageAccess,
        )
    }
    if (!snapshot.usageAccess && snapshot.restrictedSettings) {
        UsageAccessHelp(onOpenUsageAccess = actions.onOpenUsageAccess, onOpenAppInfo = actions.onOpenAppInfo, helpActions = actions.help)
    }
    BigButton(if (snapshot.usageAccess) "Continue" else "Continue without screen time", onClick = onNext)
}

@Composable
private fun HowToLine(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
            Text(n.toString(), color = Haldi, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}


@Composable
private fun NotificationsPage(actions: OnboardingActions, onNext: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var allowed by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { allowed = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled(); kotlinx.coroutines.delay(1_000) }
    }
    Badge("🔔", Color(0xFFFFF3C8))
    Text("Stay on track", style = MaterialTheme.typography.headlineLarge)
    Text("A nudge when you’ve been sitting too long, your detox countdown, cheers from your circle, and a quick " +
        "morning and evening check-in. Made on your phone from your own numbers.",
        style = MaterialTheme.typography.bodyLarge, color = Muted)
    AppCard {
        StatusRow("Notifications", if (allowed) "Allowed" else "Needed for nudges and reminders",
            ok = allowed, action = if (allowed) null else "Allow", onAction = actions.onAskNotifications)
    }
    if (allowed) BigButton("Continue", onClick = onNext)
    else {
        BigButton("Allow notifications") { actions.onAskNotifications() }
        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Skip", color = Muted, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Everything ON, each with its own switch — opting out is one tap, and nothing is hidden. */
@Composable
private fun DefaultsPage(actions: OnboardingActions) {
    var nudges by remember { mutableStateOf(true) }
    var team by remember { mutableStateOf(true) }
    var online by remember { mutableStateOf(true) }
    var ranking by remember { mutableStateOf(true) }
    Badge("✅", Color(0xFFE3F7EE))
    Text("You’re all set", style = MaterialTheme.typography.headlineLarge)
    Text("Here’s what’s on. Switch off anything you don’t want — you can change all of it later in Profile.",
        style = MaterialTheme.typography.bodyLarge, color = Muted)
    AppCard {
        DefaultRow("Nudges and reminders", "Sitting-too-long nudges, morning and evening check-ins, daily screen-time summary, app time limits", nudges) { nudges = it }
        DefaultRow("Messages from the Bas 1 Minute team", "Challenges and the occasional motivation", team) { team = it }
        DefaultRow("Backup and Circles", "Backs up your daily step and screen-time totals, and lets you walk with family and friends", online) {
            online = it; if (!it) ranking = false
        }
        DefaultRow("Bas 1 Minute India ranking", "Your name and daily steps on the all-India board and monthly challenges", ranking, enabled = online) { ranking = it }
    }
    Text("Never shared: app names, location, diary, goals or detox details.", style = MaterialTheme.typography.bodySmall, color = Muted)
    BigButton("Start") { actions.onFinish(OnboardingChoices(nudges, team, online, ranking)) }
}

@Composable
private fun DefaultRow(title: String, subtitle: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) TextMain else Muted)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        androidx.compose.material3.Switch(checked = on && enabled, enabled = enabled, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi))
    }
}
