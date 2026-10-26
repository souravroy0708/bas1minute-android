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
    val onFinish: () -> Unit,
)

/** After login: two short permission steps (steps, screen time). Both can be skipped. */
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
            if (page == 2) TextButton(onClick = { page = 1 }) { Text("← Back", color = Muted, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.weight(1f))
            Text("Step $page of 2", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..2).forEach { i ->
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
                if (p == 1) StepsPage(snapshot, actions) { page = 2 } else ScreenPage(snapshot, actions)
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
                "Health Connect", "Needs installing or updating", ok = false, action = "Get it", onAction = actions.onInstallHealthConnect,
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
    BigButton(if (snapshot.stepsReady) "Continue" else "Continue without steps", onClick = onNext)
}

@Composable
private fun ScreenPage(snapshot: Snapshot, actions: OnboardingActions) {
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
    BigButton(if (snapshot.usageAccess) "Finish setup" else "Finish without screen time", onClick = actions.onFinish)
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
