package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.bas1minute.sync.AppController

/** Just-in-time setup on Home: only the steps still missing, with one-tap fixes. Disappears when done. */
@Composable
fun FinishSetupCard(app: AppController, actions: PhoneActions) {
    val snap = app.phone
    val todo = listOf(!snap.stepsReady, !snap.usageAccess).count { it }
    AppCard(title = t("Finish setting up"), trailing = {
        Text("$todo step${if (todo == 1) "" else "s"} left", style = MaterialTheme.typography.labelMedium, color = Ink,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Haldi).padding(horizontal = 10.dp, vertical = 5.dp))
    }) {
        Text("Two quick permissions and Bas 1 Minute counts everything for you. Everything stays on this phone.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        when {
            snap.stepsReady -> StatusRow(t("Step counting"), t("On"), ok = true)
            snap.hcStatus == HealthConnectClient.SDK_AVAILABLE ->
                StatusRow(t("Step counting"), "From Health Connect, Google Fit or your watch", ok = false, action = t("Allow"), onAction = actions.onAskHealthConnect)
            snap.recordingAvailable ->
                StatusRow(t("Step counting"), "Use this phone’s step sensor", ok = false, action = t("Allow"), onAction = actions.onAskActivity)
            else -> StatusRow(t("Step counting"), "Install Health Connect to count steps", ok = false, action = t("Get it"), onAction = actions.onInstallHealthConnect)
        }
        StatusRow(t("Screen time"), if (snap.usageAccess) "On" else "Find Bas 1 Minute, switch on “Permit usage access”",
            ok = snap.usageAccess, action = if (snap.usageAccess) null else "Open", onAction = actions.onOpenUsageAccess)
        if (!snap.usageAccess && snap.restrictedSettings) {
            UsageAccessHelp(onOpenUsageAccess = actions.onOpenUsageAccess, onOpenAppInfo = actions.onOpenAppInfo, helpActions = actions.help)
        }
    }
}

/** The four things you can do right now, each one tap. */
@Composable
fun QuickActions(
    bursts: Int, burstGoal: Int, calm: Int, water: Int,
    onBurst: () -> Unit, onCalm: () -> Unit, onWater: () -> Unit, onLog: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Action(Icons.Filled.Bolt, t("Burst"), "$bursts/$burstGoal", Haldi, Modifier.weight(1f), onBurst)
        Action(Icons.Filled.SelfImprovement, t("Calm"), "$calm min", Color(0xFFFFE7DF), Modifier.weight(1f), onCalm)
        Action(Icons.Filled.WaterDrop, t("Water"), if (water >= 1000) "%.1f L".format(water / 1000.0) else "$water ml", Color(0xFFDDF0FD), Modifier.weight(1f), onWater)
        Action(Icons.Filled.EditNote, t("Log"), t("a day"), Color(0xFFE9ECFF), Modifier.weight(1f), onLog)
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 96.dp).clip(RoundedCornerShape(20.dp)).background(CardBg)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextMain)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center)
    }
}

/** Legend under the ring so the two rings explain themselves. */
@Composable
fun RingLegend(onExplain: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "What do the rings mean?", onClick = onExplain)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(Violet); Spacer(Modifier.width(6.dp)); Text(t("Steps"), color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(14.dp))
        Dot(Haldi); Spacer(Modifier.width(6.dp)); Text(t("Screen time"), color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Icon(Icons.Outlined.Info, contentDescription = null, tint = MutedOnDark, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Dot(c: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(c))

@Composable
fun RingExplainer(stepGoal: String, limit: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Your two rings"), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Outer ring: steps. It fills as you walk toward your goal of $stepGoal.", style = MaterialTheme.typography.bodyMedium)
                Text("Inner ring: screen time in social, messaging and browser apps, against your limit of $limit. " +
                    "It turns coral when you go over. Here, a fuller ring isn’t a good thing.", style = MaterialTheme.typography.bodyMedium)
                Text("Change both in Profile → Your details and goals.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Got it")) } },
    )
}
