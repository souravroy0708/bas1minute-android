package com.bas1minute.sync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.detox.AppRules
import com.bas1minute.sync.detox.AppTimerLogic
import com.bas1minute.sync.detox.UsageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "App Timers": a daily time budget per app — Instagram, TikTok, Facebook, the member's own short list —
 * resetting at midnight. Once spent, a reminder with a one-tap "Close it" (see AppTimerChecker). For a lock
 * that can't be skipped, the card sends the member to Android's own Digital Wellbeing app timers.
 */
@Composable
fun AppTimerCard(app: AppController) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(app.prefs.appTimerEnabled) }
    var budget by remember { mutableIntStateOf(app.prefs.appTimerBudgetMinutes) }
    var showPicker by remember { mutableStateOf(false) }
    val rules = remember { AppRules(ctx, emptySet(), emptySet()) }
    var usedToday by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    LaunchedEffect(enabled, app.prefs.appTimerApps) {
        if (!enabled) return@LaunchedEffect
        usedToday = withContext(Dispatchers.IO) {
            val reader = UsageReader(ctx)
            val now = System.currentTimeMillis()
            app.prefs.appTimerApps.associateWith { reader.totalTodayMs(it, now) }
        }
    }

    AppCard(title = t("App Timers")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("A daily time limit, per app", style = MaterialTheme.typography.titleMedium)
                Text("Once an app's minutes for today are spent, we remind you to close it (checked about every 15 minutes).",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Switch(checked = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                onCheckedChange = { on ->
                    // No periodic job to start/stop here any more — this rides on the shared battery-log
                    // tick, which is always running; flipping the preference is the only thing needed.
                    enabled = on; app.prefs.appTimerEnabled = on
                })
        }
        if (enabled) {
            Text(t("Daily limit per app"), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TextButton(onClick = { budget = AppTimerLogic.clampBudget(budget - 5); app.prefs.appTimerBudgetMinutes = budget }) { Text("\u22125", color = Blue) }
                Text("$budget min", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { budget = AppTimerLogic.clampBudget(budget + 5); app.prefs.appTimerBudgetMinutes = budget }) { Text("+5", color = Blue) }
            }
            Text(t("Apps on your list"), style = MaterialTheme.typography.labelLarge)
            app.prefs.appTimerApps.sorted().forEach { pkg ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(rules.label(pkg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(AppTimerLogic.remainingLabel(usedToday[pkg] ?: 0L, budget), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            TextButton(onClick = { showPicker = true }) { Text(t("Edit the list"), color = Blue) }
            Text("Want the app to lock itself when time's up? Set the same limit in Android's Digital Wellbeing — " +
                "no extra permission for Bas 1 Minute.", style = MaterialTheme.typography.bodySmall, color = Muted)
            TextButton(onClick = {
                if (!com.bas1minute.sync.DigitalWellbeing.open(ctx)) {
                    android.widget.Toast.makeText(ctx, "In Settings, search for \u201cDigital Wellbeing\u201d.", android.widget.Toast.LENGTH_LONG).show()
                }
            }) { Text(t("Set a hard limit"), color = Blue) }
        }
    }

    if (showPicker) EditAppTimerAppsDialog(app, rules, onDismiss = { showPicker = false })
}

@Composable
private fun EditAppTimerAppsDialog(app: AppController, rules: AppRules, onDismiss: () -> Unit) {
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { rules.pickableToBlock() } }
    var chosen by remember { mutableStateOf(app.prefs.appTimerApps) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t("Edit the list")} (${chosen.size}/${AppTimerLogic.MAX_APPS})") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(apps, key = { it.first }) { (pkg, label) ->
                    val on = pkg in chosen
                    val canChange = on || chosen.size < AppTimerLogic.MAX_APPS
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Checkbox(checked = on, enabled = canChange, onCheckedChange = { chosen = if (on) chosen - pkg else chosen + pkg })
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { app.prefs.appTimerApps = chosen; onDismiss() }) { Text(t("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}
