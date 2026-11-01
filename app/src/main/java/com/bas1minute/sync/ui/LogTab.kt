package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Type numbers by hand for any of the last 7 days. Phone-synced numbers are never replaced. */
@Composable
fun LogTab(app: AppController, onClose: () -> Unit) {
    val today = LocalDate.now()
    val days = (0..6).map { today.minusDays(it.toLong()) }
    var day by remember { mutableStateOf(today) }
    // Everything here is saved on this phone only
    val local = app.phone.last21.firstOrNull { it.date == day }?.row
    var steps by remember(day) { mutableStateOf(local?.steps?.toString() ?: "") }
    var sleepH by remember(day) { mutableStateOf(local?.sleepMinutes?.let { (it / 60).toString() } ?: "") }
    var sleepM by remember(day) { mutableStateOf(local?.sleepMinutes?.let { (it % 60).toString() } ?: "") }
    var water by remember(day) { mutableStateOf(local?.waterMl?.toString() ?: "") }
    var weight by remember(day) { mutableStateOf(local?.weightKg?.let { "%.1f".format(it) } ?: "") }
    val sleepFromPhone = local?.sleepSource == "health_connect" || local?.sleepSource == "phone_estimate"
    val waterFromPhone = local?.waterSource == "health_connect"
    val weightFromPhone = local?.weightSource == "health_connect"
    var error by remember { mutableStateOf<String?>(null) }
    val phoneSynced = local?.stepsSource == "health_connect" || local?.stepsSource == "phone_sensor"

    LaunchedEffect(Unit) { error = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.TextButton(onClick = onClose) { Text("← Today", color = Muted, style = MaterialTheme.typography.labelLarge) }
        Text("Log a day", style = MaterialTheme.typography.headlineLarge)
        Text("Screen time is measured by the phone, so it can’t be typed. Add what the phone can’t see.", style = MaterialTheme.typography.bodyLarge, color = Muted)

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEachIndexed { i, d ->
                val on = d == day
                val label = when (i) { 0 -> "Today"; 1 -> "Yesterday"; else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " ${d.dayOfMonth}" }
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Ink,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Color.White)
                        .clickable { day = d }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }

        val dayKey = day.toString()
        val burstUsed = dayKey in app.prefs.manualBurstDays
        val calmUsed = dayKey in app.prefs.manualCalmDays
        AppCard(title = "Bursts and calm") {
            Text("This day: ${local?.bursts ?: 0} burst${if ((local?.bursts ?: 0) == 1) "" else "s"} · ${local?.calmMinutes ?: 0} calm min. " +
                "The timers count every minute; here you can add one of each per day that you did without the app.",
                style = MaterialTheme.typography.bodyMedium, color = Muted)
            OnceRow("+1 burst done without the timer", used = burstUsed) {
                app.launch { app.handle(app.repo.addManualBurst(day), "Burst added.") { app.refresh() } }
            }
            OnceRow("+1 calm minute done without the app", used = calmUsed) {
                app.launch { app.handle(app.repo.addManualCalm(day), "Calm minute added.") { app.refresh() } }
            }
        }

        AppCard(title = "Steps") {
            if (phoneSynced) {
                Text("Your phone counted ${local?.steps ?: 0} steps for this day. That number is kept.", style = MaterialTheme.typography.bodyMedium, color = MintDark)
            } else {
                Text("Only if your phone can’t count them. Copy from Google Fit, Samsung Health or your watch.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                AppField("Steps walked", steps, { steps = it.filter(Char::isDigit).take(6) }, null, placeholder = "5240", keyboard = KeyboardType.Number)
            }
        }

        AppCard(title = "Sleep") {
            if (sleepFromPhone) {
                Text("Measured automatically (${if (local?.sleepSource == "health_connect") "Health Connect" else "estimated from your phone"}): ${local?.sleepMinutes?.let { hm(it) }}.",
                    style = MaterialTheme.typography.bodyMedium, color = MintDark)
            } else {
                Text("Hours slept the night before this day.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField("Hours", sleepH, { sleepH = it.filter(Char::isDigit).take(2) }, null, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                    AppField("Minutes", sleepM, { sleepM = it.filter(Char::isDigit).take(2) }, null, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                }
            }
        }

        AppCard(title = "Water") {
            if (waterFromPhone) {
                Text("From Health Connect: ${local?.waterMl} ml.", style = MaterialTheme.typography.bodyMedium, color = MintDark)
            } else {
                Text("Total for the day. One glass is about 250 ml.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                AppField("Water (ml)", water, { water = it.filter(Char::isDigit).take(5) }, null, placeholder = "1500", keyboard = KeyboardType.Number)
            }
        }

        if (app.prefs.trackWeight) {
            AppCard(title = "Weight", trailing = { Text("Only you see this", color = Muted, style = MaterialTheme.typography.bodySmall) }) {
                if (weightFromPhone) {
                    Text("From your scale via Health Connect: ${"%.1f".format(local?.weightKg ?: 0.0)} kg.", style = MaterialTheme.typography.bodyMedium, color = MintDark)
                } else {
                    AppField("Weight (kg)", weight, { weight = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, null,
                        placeholder = "68.5", keyboard = KeyboardType.Decimal)
                }
            }
        }

        error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium) }
        BigButton("Save") {
            val sleepTotal = if (sleepFromPhone || (sleepH.isBlank() && sleepM.isBlank())) null else (sleepH.toIntOrNull() ?: 0) * 60 + (sleepM.toIntOrNull() ?: 0)
            if (sleepTotal != null && sleepTotal > 18 * 60) { error = "Sleep can’t be more than 18 hours."; return@BigButton }
            val waterMl = if (waterFromPhone) null else water.toIntOrNull()
            if (waterMl != null && waterMl > 10_000) { error = "Water looks too high. Enter millilitres, for example 1500."; return@BigButton }
            val kg = if (!app.prefs.trackWeight || weightFromPhone) null else weight.toDoubleOrNull()
            if (kg != null && kg !in 20.0..300.0) { error = "Enter weight in kg, between 20 and 300."; return@BigButton }
            error = null
            app.launch {
                app.handle(
                    app.repo.logDay(day, if (phoneSynced) null else steps.toIntOrNull(), null, null, null,
                        waterMl = waterMl, weightKg = kg, sleepMinutes = sleepTotal),
                    "Saved. Nice work.",
                ) { app.refresh(); onClose() }
            }
        }
    }
}

@Composable
private fun RoundButton(symbol: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Paper).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(symbol, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun OnceRow(label: String, used: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (used) Paper else Color(0xFFFFF3C8))
        .clickable(enabled = !used, onClick = onAdd).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (used) Muted else Ink, modifier = Modifier.weight(1f))
        Text(if (used) "Added ✓" else "Add", style = MaterialTheme.typography.labelLarge, color = if (used) MintDark else Ink)
    }
}
