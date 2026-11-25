package com.bas1minute.sync.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bas1minute.sync.AiExport
import com.bas1minute.sync.AppController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Export my data: pick a range (last 30 / 90 days, this year, all time or any dates) and what to include, then
 * send ONE file to Claude, ChatGPT or Gemini for a deeper look — or a zip of spreadsheets. Built on the phone;
 * nothing is sent anywhere unless the member sends it. GPS routes are never included; the diary only if ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataDialog(app: AppController, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val first = remember { AiExport.firstDay(ctx) }
    var range by remember { mutableStateOf(0) }                    // 0: 30 days, 1: 90 days, 2: this year, 3: all, 4: custom
    var custom by remember { mutableStateOf(today.minusDays(29) to today) }
    var picking by remember { mutableStateOf(false) }
    var parts by remember { mutableStateOf(AiExport.Part.entries.filter { it.defaultOn }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val (from, to) = when (range) {
        0 -> today.minusDays(29) to today
        1 -> today.minusDays(89) to today
        2 -> today.withDayOfYear(1) to today
        3 -> first to today
        else -> custom
    }
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    fun opts() = AiExport.Options(minOf(from, to), maxOf(from, to), parts)

    fun run(kind: String) {
        if (busy) return
        busy = true; note = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when (kind) { "csv" -> AiExport.spreadsheets(ctx, opts()); else -> AiExport.forAi(ctx, opts()) }
                }
            }
            busy = false
            result.onFailure { note = "Couldn’t make the file. Try a shorter range." }
            result.onSuccess { r ->
                when (kind) {
                    "copy" -> {
                        val text = r.file.readText()
                        if (text.length > 400_000) note = "That’s too long to paste. Use “Send file” instead — AI apps accept files."
                        else {
                            val clip = ClipData.newPlainText("Bas 1 Minute data", text)
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                clip.description.extras = android.os.PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                            }
                            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                            note = "Copied ${r.days} days. Open Claude or ChatGPT, paste it, and ask a question."
                        }
                    }
                    "csv" -> runCatching { ctx.startActivity(AiExport.shareIntent(ctx, r, "application/zip")) }
                    else -> runCatching { ctx.startActivity(AiExport.shareIntent(ctx, r, "text/markdown")) }
                }
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Paper)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(t("Export my data"), style = MaterialTheme.typography.headlineMedium, color = TextMain)
            Text("Everything Bas 1 Minute saved on this phone — steps, screen time by app, sleep, walks, detox and goals. " +
                "Send it to Claude, ChatGPT or Gemini and ask about your habits, or open it in a spreadsheet.",
                style = MaterialTheme.typography.bodyMedium, color = Muted)

            Text(t("Dates"), style = MaterialTheme.typography.titleMedium, color = TextMain)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Last 30 days", "Last 90 days", "This year", "All time", "Choose dates").forEachIndexed { i, label ->
                    val on = range == i
                    Text(t(label), style = MaterialTheme.typography.labelLarge, color = if (on) Ink else TextMain,
                        modifier = Modifier.tactileClickable(RoundedCornerShape(50)) { range = i; if (i == 4) picking = true }
                            .background(if (on) Haldi else CardBg).padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            Text("${minOf(from, to).format(fmt)} – ${maxOf(from, to).format(fmt)}", style = MaterialTheme.typography.bodyMedium, color = Muted)

            Text(t("Include"), style = MaterialTheme.typography.titleMedium, color = TextMain)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).border(1.dp, CardLine, RoundedCornerShape(20.dp))
                .padding(vertical = 4.dp)) {
                AiExport.Part.entries.forEach { p ->
                    Row(Modifier.fillMaxWidth().tactileClickable { parts = if (p in parts) parts - p else parts + p }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = p in parts, onCheckedChange = { parts = if (it) parts + p else parts - p },
                            colors = CheckboxDefaults.colors(checkedColor = Ink, checkmarkColor = Haldi))
                        Text(t(p.title), style = MaterialTheme.typography.bodyLarge, color = TextMain)
                    }
                }
            }
            if (AiExport.Part.DIARY in parts) Text("Your diary is private. Only include it if you’re happy for the AI service to read it.",
                style = MaterialTheme.typography.bodySmall, color = Danger)

            BigButton(if (busy) t("Preparing…") else t("Send to an AI app"), enabled = !busy && parts.isNotEmpty()) { run("ai") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(t("Copy for AI"), modifier = Modifier.weight(1f)) { run("copy") }
                GhostButton(t("Spreadsheet (CSV)"), modifier = Modifier.weight(1f)) { run("csv") }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMain) }
            Text("Made on your phone. Routes’ GPS points are never included. Once you send it, that app’s own privacy policy applies.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            GhostButton(t("Done"), onClick = onClose)
        }
    }

    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = custom.first.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = custom.second.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null, onClick = {
                    val s = state.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    val e = state.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: s
                    if (s != null && e != null) custom = minOf(s, e) to minOf(maxOf(s, e), today)
                    picking = false
                }) { Text(t("Use these dates")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(t("Cancel")) } },
        ) { DateRangePicker(state = state, modifier = Modifier.height(520.dp)) }
    }
}
