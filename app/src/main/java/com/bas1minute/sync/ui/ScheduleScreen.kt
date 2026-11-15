package com.bas1minute.sync.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.schedule.Schedule
import com.bas1minute.sync.schedule.ScheduleLogic
import com.bas1minute.sync.schedule.ScheduleStore

/**
 * "Every weeknight, automatically" — recurring windows that start and end Digital detox on their own, using
 * whichever apps and categories the member already set up to block in Detox itself. The same thing every
 * well-known paid focus app treats as a flagship feature (Freedom's Scheduled Sessions, Opal's Schedules,
 * Digital Wellbeing's Bedtime mode) — Detox here only ever ran if someone remembered to start it, until now.
 */
@Composable
fun SchedulesCard(ctx: Context) {
    var schedules by remember { mutableStateOf(ScheduleStore.get(ctx).all()) }
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun reload() { schedules = ScheduleStore.get(ctx).all() }

    AppCard(title = t("Schedules")) {
        Text(
            "Automatically start and end a detox at times you choose — blocking whatever you've already set up in Detox above.",
            style = MaterialTheme.typography.bodyMedium, color = Muted,
        )
        schedules.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { editing = s }) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(s.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text("${ScheduleLogic.daysSummary(s.days)} \u00b7 ${ScheduleLogic.timeRangeSummary(s)}",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(checked = s.enabled, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on -> ScheduleStore.get(ctx).update(s.copy(enabled = on)); reload() })
            }
        }
        TextButton(onClick = { adding = true }) { Text(t("+ Add a schedule"), color = Blue) }
    }

    // No periodic job to (re)schedule here — Schedules ride on the shared battery-log tick, which is always
    // running; saving or removing a schedule just changes what that tick finds next time it runs.
    if (adding) EditScheduleDialog(ctx, existing = null, onSaved = { reload() }, onDismiss = { adding = false })
    editing?.let { s ->
        EditScheduleDialog(ctx, existing = s, onSaved = { reload() }, onDismiss = { editing = null },
            onDelete = { ScheduleStore.get(ctx).remove(s.id); reload(); editing = null })
    }
}

private val DAY_ORDER = listOf(1, 2, 3, 4, 5, 6, 7)

@Composable
private fun EditScheduleDialog(ctx: Context, existing: Schedule?, onSaved: () -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var days by remember { mutableStateOf(existing?.days ?: setOf(1, 2, 3, 4, 5)) }
    var startMinute by remember { mutableIntStateOf(existing?.startMinute ?: 22 * 60) }
    var endMinute by remember { mutableIntStateOf(existing?.endMinute ?: 7 * 60) }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) t("New schedule") else t("Edit schedule")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(label, { label = it }, singleLine = true, placeholder = { Text("e.g. Bedtime") }, modifier = Modifier.fillMaxWidth())
                Text(t("Days"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_ORDER.forEach { day ->
                        val on = day in days
                        Column(
                            Modifier.size(34.dp).clip(CircleShape).background(if (on) Haldi else Ink3)
                                .clickable { days = if (on) days - day else days + day },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(ScheduleLogic.dayLabel(day).take(1), modifier = Modifier.padding(top = 7.dp),
                                color = if (on) Ink else MutedOnDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(t("Starts"), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { pickTime(startMinute) { startMinute = it } }) {
                            Text(ScheduleLogic.formatTimeOfDay(startMinute), color = Blue, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column {
                        Text(t("Ends"), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { pickTime(endMinute) { endMinute = it } }) {
                            Text(ScheduleLogic.formatTimeOfDay(endMinute), color = Blue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (endMinute == startMinute) Text("Start and end can't be the same time.", color = Danger, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank() && days.isNotEmpty() && endMinute != startMinute, onClick = {
                val finalLabel = label.trim().take(60)
                if (existing == null) ScheduleStore.get(ctx).add(finalLabel, days, startMinute, endMinute)
                else ScheduleStore.get(ctx).update(existing.copy(label = finalLabel, days = days, startMinute = startMinute, endMinute = endMinute))
                onSaved(); onDismiss()
            }) { Text(t("Save")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(t("Remove"), color = Danger) }
                TextButton(onClick = onDismiss) { Text(t("Cancel")) }
            }
        },
    )
}
