package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.bas1minute.sync.Snapshot
import java.text.DateFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

data class HomeState(
    val name: String = "",
    val stepGoal: Int = 7000,
    val screenLimit: Int = 180,
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastSyncMessage: String = "",
    val lastSyncOk: Boolean = false,
    val autoSync: Boolean = true,
    val syncKeyPrefix: String = "",
)

data class HomeActions(
    val onSync: () -> Unit,
    val onToggleAuto: (Boolean) -> Unit,
    val onAskHealthConnect: () -> Unit,
    val onInstallHealthConnect: () -> Unit,
    val onAskActivity: () -> Unit,
    val onOpenUsageAccess: () -> Unit,
    val onOpenDashboard: () -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onChangeKey: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(snapshot: Snapshot, state: HomeState, actions: HomeActions) {
    val numbers = remember { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
    val today = snapshot.today
    val steps = today?.steps ?: 0L
    val screen = today?.screenMinutes
    val stepsLeft = (state.stepGoal - steps).coerceAtLeast(0)
    val screenOver = screen != null && screen > state.screenLimit
    var chart by remember { mutableIntStateOf(0) }
    var editingKey by remember { mutableStateOf(false) }

    PullToRefreshBox(isRefreshing = state.syncing, onRefresh = actions.onSync, modifier = Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // ---------- Dark hero (cult.fit style) ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Ink, Ink2)))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                            color = MutedOnDark, style = MaterialTheme.typography.bodySmall,
                        )
                        Text("Aaj ka target", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                    SyncChip(state)
                }
                Spacer(Modifier.height(18.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DualRing(
                        stepsFraction = steps.toFloat() / state.stepGoal,
                        screenFraction = (screen ?: 0).toFloat() / state.screenLimit,
                        screenOver = screenOver,
                        modifier = Modifier.size(230.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (today?.steps == null) "—" else numbers.format(steps), color = Color.White, style = MaterialTheme.typography.displayMedium)
                            Text("of ${numbers.format(state.stepGoal)} steps", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderTile(
                        label = "Steps left",
                        value = if (today?.steps == null) "—" else if (stepsLeft == 0L) "Done!" else numbers.format(stepsLeft),
                        hint = when (snapshot.todaySource) {
                            "health_connect" -> "From Health Connect"
                            "phone_sensor" -> "From this phone"
                            else -> "Allow steps below"
                        },
                        dot = Violet,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderTile(
                        label = "Screen time",
                        value = screen?.let { hm(it) } ?: "—",
                        hint = when {
                            screen == null -> "Allow below"
                            screenOver -> "Over your ${hm(state.screenLimit)} limit"
                            else -> "Limit ${hm(state.screenLimit)}"
                        },
                        dot = if (screenOver) Coral else Haldi,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ---------- White sheet (HealthifyMe style) ----------
            Column(
                Modifier
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Paper)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (stepsLeft in 1L..1500L && today?.steps != null) {
                    Nudge("Bas ${numbers.format(stepsLeft)} steps left. A 10-minute walk will do it.")
                } else if (screenOver) {
                    Nudge("Screen time is over your limit. Swap 10 minutes of scrolling for a walk?")
                }

                // This week
                val week = snapshot.week
                AppCard(title = "This week", trailing = { Segmented(listOf("Steps", "Screen"), chart) { chart = it } }) {
                    if (week.isEmpty()) {
                        Text("Loading…", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val labels = week.mapIndexed { i, d ->
                            if (i == week.lastIndex) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        }
                        if (chart == 0) {
                            WeekBars(
                                days = labels.zip(week.map { it.steps?.toInt() }),
                                goal = state.stepGoal, barColor = Violet, overColor = null,
                                format = { if (it >= 1000) "${it / 1000}.${(it % 1000) / 100}k" else it.toString() },
                            )
                            val logged = week.mapNotNull { it.steps }
                            val avg = if (logged.isEmpty()) null else logged.sum() / logged.size
                            val hit = logged.count { it >= state.stepGoal }
                            WeekSummary(
                                left = "Average" to (avg?.let { numbers.format(it) } ?: "—"),
                                right = "Goal days" to "$hit of 7",
                            )
                        } else {
                            WeekBars(
                                days = labels.zip(week.map { it.screenMinutes }),
                                goal = state.screenLimit, barColor = Color(0xFFB9C0FF), overColor = Coral,
                                format = { hm(it) },
                            )
                            val logged = week.mapNotNull { it.screenMinutes }
                            val avg = if (logged.isEmpty()) null else logged.sum() / logged.size
                            val under = logged.count { it <= state.screenLimit }
                            WeekSummary(
                                left = "Average" to (avg?.let { hm(it) } ?: "—"),
                                right = "Under limit" to "$under of 7",
                            )
                        }
                    }
                }

                // Sync
                AppCard(title = "Sync") {
                    BigButton(if (state.syncing) "Syncing…" else "Sync now", enabled = !state.syncing, onClick = actions.onSync)
                    if (state.lastSyncMessage.isNotBlank()) {
                        val time = if (state.lastSyncAt > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lastSyncAt)) else ""
                        Text(
                            "${state.lastSyncMessage} $time".trim(),
                            color = if (state.lastSyncOk) MintDark else Danger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider(color = Paper)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sync automatically", style = MaterialTheme.typography.titleMedium)
                            Text("About every 3 hours when online. Pull down to sync anytime.", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        Switch(
                            checked = state.autoSync,
                            onCheckedChange = actions.onToggleAuto,
                            colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                        )
                    }
                }

                // Connections
                AppCard(title = "Connections") {
                    StatusRow(
                        "Account", if (state.syncKeyPrefix.isBlank()) "No sync key yet" else "Key ${state.syncKeyPrefix}…",
                        ok = state.syncKeyPrefix.isNotBlank(), action = "Change", onAction = { editingKey = true },
                    )
                    when (snapshot.hcStatus) {
                        HealthConnectClient.SDK_AVAILABLE -> StatusRow(
                            "Health Connect", if (snapshot.hcGranted) "Reading your steps" else "Not allowed yet",
                            ok = snapshot.hcGranted, action = if (snapshot.hcGranted) null else "Allow", onAction = actions.onAskHealthConnect,
                        )
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StatusRow(
                            "Health Connect", "Needs installing or updating", ok = false, action = "Get it", onAction = actions.onInstallHealthConnect,
                        )
                        else -> {}
                    }
                    if (snapshot.recordingAvailable) {
                        StatusRow(
                            "Phone step sensor", if (snapshot.activityGranted) "Backup counting is on" else "Off",
                            ok = snapshot.activityGranted, action = if (snapshot.activityGranted) null else "Allow", onAction = actions.onAskActivity,
                        )
                    }
                    StatusRow(
                        "Screen time", if (snapshot.usageAccess) "Usage access on" else "Usage access off",
                        ok = snapshot.usageAccess, action = if (snapshot.usageAccess) null else "Open", onAction = actions.onOpenUsageAccess,
                    )
                }

                GhostButton("Open my dashboard", onClick = actions.onOpenDashboard)
                TextButton(onClick = actions.onOpenPrivacy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("What this app reads and sends", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (editingKey) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingKey = false },
            title = { Text("Change sync key", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create a new key on the website (Profile, then Connect your phone) and paste it here.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                    OutlinedTextField(
                        value = key, onValueChange = { key = it }, singleLine = true,
                        placeholder = { Text("b1m_…") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = key.trim().startsWith("b1m_"), onClick = { actions.onChangeKey(key); editingKey = false }) {
                    Text("Save", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = { TextButton(onClick = { editingKey = false }) { Text("Cancel", color = Muted) } },
        )
    }
}

@Composable
private fun SyncChip(state: HomeState) {
    val ok = state.lastSyncOk && state.lastSyncAt > 0
    val label = when {
        state.syncing -> "Syncing…"
        ok -> "Synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lastSyncAt))
        state.lastSyncAt > 0 -> "Sync failed"
        else -> "Not synced"
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Ink3)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) Mint else Haldi))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Nudge(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Haldi)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚡", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun WeekSummary(left: Pair<String, String>, right: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(left, right).forEach { (label, value) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Paper)
                    .padding(12.dp)
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(value, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
