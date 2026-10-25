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
    val onSaveKey: (String) -> Unit,
    val onOpenWebsiteConnect: () -> Unit,
    val onAskHealthConnect: () -> Unit,
    val onInstallHealthConnect: () -> Unit,
    val onAskActivity: () -> Unit,
    val onOpenUsageAccess: () -> Unit,
    val onFinish: () -> Unit,
)

@Composable
fun OnboardingScreen(snapshot: Snapshot, savedKey: String, actions: OnboardingActions) {
    var page by rememberSaveable { mutableIntStateOf(0) }

    if (page == 0) {
        WelcomePage(onStart = { page = 1 })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top bar: back + progress
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { page -= 1 }) { Text("← Back", color = Muted, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.weight(1f))
            Text("Step ${page} of 3", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= page) Ink else PaperLine)
                )
            }
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            label = "onboarding",
            modifier = Modifier.weight(1f),
        ) { p ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (p) {
                    1 -> KeyPage(savedKey, actions) { page = 2 }
                    2 -> StepsPage(snapshot, actions) { page = 3 }
                    else -> ScreenPage(snapshot, actions)
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
                Text("1", style = MaterialTheme.typography.headlineMedium, color = Ink)
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Bas 1 Minute", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("बस 1 मिनट", fontFamily = Devanagari, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.weight(1f))
        DualRing(stepsFraction = 0.75f, screenFraction = 0.55f, screenOver = false, modifier = Modifier.size(170.dp)) {
            Text("7K+4", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        }
        Spacer(Modifier.height(32.dp))
        Text("Fit hone ke liye ghante nahi, minute chahiye.", style = MaterialTheme.typography.displayLarge, color = Color.White)
        Spacer(Modifier.height(14.dp))
        Text(
            "This app counts your daily steps and screen time, and sends the totals to your Bas 1 Minute dashboard. Setup takes 2 minutes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedOnDark,
        )
        Spacer(Modifier.weight(1f))
        BigButton("Get started", dark = false, onClick = onStart)
    }
}

@Composable
private fun KeyPage(savedKey: String, actions: OnboardingActions, onNext: () -> Unit) {
    var key by remember { mutableStateOf(savedKey) }
    val valid = key.trim().startsWith("b1m_") && key.trim().length > 20

    Badge("🔑", Color(0xFFFFF3C8))
    Text("Link your account", style = MaterialTheme.typography.headlineLarge)
    Text("Your sync key connects this phone to your Bas 1 Minute account. It only lets the phone add daily totals.", style = MaterialTheme.typography.bodyLarge, color = Muted)

    AppCard {
        HowToLine(1, "Open the website and log in")
        HowToLine(2, "Go to Profile, then Connect your phone")
        HowToLine(3, "Tap Create sync key, then copy it")
        GhostButton("Open the website") { actions.onOpenWebsiteConnect() }
    }

    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("Paste your sync key") },
        placeholder = { Text("b1m_…") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        isError = key.isNotBlank() && !valid,
        supportingText = { if (key.isNotBlank() && !valid) Text("A sync key starts with b1m_ and is about 50 characters long.") },
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    BigButton("Continue", enabled = valid) {
        actions.onSaveKey(key)
        onNext()
    }
}

@Composable
private fun StepsPage(snapshot: Snapshot, actions: OnboardingActions, onNext: () -> Unit) {
    Badge("👟", Color(0xFFE9ECFF))
    Text("Count your steps", style = MaterialTheme.typography.headlineLarge)
    Text(
        "We read steps from Health Connect, where Google Fit, Samsung Health and most watches save them. If nothing is there, your phone can count them itself.",
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
    BigButton(if (snapshot.stepsReady) "Continue" else "Continue without steps", onClick = onNext)
}

@Composable
private fun ScreenPage(snapshot: Snapshot, actions: OnboardingActions) {
    Badge("📱", Color(0xFFFFE7DF))
    Text("See your screen time", style = MaterialTheme.typography.headlineLarge)
    Text(
        "The same daily total Digital Wellbeing shows you. We add it up on your phone and send only the total, never which apps you used.",
        style = MaterialTheme.typography.bodyLarge,
        color = Muted,
    )
    AppCard {
        StatusRow(
            "Usage access", if (snapshot.usageAccess) "Turned on" else "Find Bas 1 Minute and switch on “Permit usage access”",
            ok = snapshot.usageAccess, action = if (snapshot.usageAccess) null else "Open", onAction = actions.onOpenUsageAccess,
        )
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
