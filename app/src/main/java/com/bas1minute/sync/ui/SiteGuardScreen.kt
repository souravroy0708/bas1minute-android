package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.siteguard.SiteGuardAccessibilityService
import com.bas1minute.sync.siteguard.SiteGuardLogic
import java.time.LocalDate

/**
 * Site Guard: a small daily time budget per distracting website (facebook.com, instagram.com, youtube.com —
 * the member's own list), enforced inside any browser via Accessibility access to the address bar only.
 * Deliberately a two-step turn-on: this app can never grant Accessibility access to itself, so the member
 * sees exactly what it's for and why, before system Settings ever opens.
 */
@Composable
fun SiteGuardCard(app: AppController) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(SiteGuardAccessibilityService.isGrantedByUser(ctx)) }
    var showDisclosure by remember { mutableStateOf(false) }
    var showDomains by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(app.prefs.siteGuardEnabled) }
    var budget by remember { mutableIntStateOf(app.prefs.siteGuardBudgetMinutes) }
    val today = remember { LocalDate.now() }

    // Re-check whenever this screen is shown — the member grants/revokes this from system Settings, outside the app.
    LaunchedEffect(Unit) { granted = SiteGuardAccessibilityService.isGrantedByUser(ctx) }

    AppCard(title = t("Block distracting websites")) {
        Text(
            "A small daily time limit per website — not the whole browser. facebook.com, instagram.com, youtube.com " +
                "and the rest of your list stop loading once today's minutes are spent; everything else — work, email, " +
                "banking, search — is never touched.",
            style = MaterialTheme.typography.bodyMedium, color = Muted,
        )
        if (!granted) {
            Text("Needs Accessibility access to read the website address — never page content, never any other app.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            BigButton(t("Turn on"), dark = false) { showDisclosure = true }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active", style = MaterialTheme.typography.titleMedium, color = MintDark)
                    Text("Pause this without changing your system Accessibility setting.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Switch(checked = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi),
                    onCheckedChange = { on -> enabled = on; app.prefs.siteGuardEnabled = on })
            }
            if (enabled) {
                Text(t("Daily limit per site"), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextButton(onClick = { budget = SiteGuardLogic.clampBudget(budget - 5); app.prefs.siteGuardBudgetMinutes = budget }) { Text("−5", color = Blue) }
                    Text("$budget min", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { budget = SiteGuardLogic.clampBudget(budget + 5); app.prefs.siteGuardBudgetMinutes = budget }) { Text("+5", color = Blue) }
                }
                val domains = app.prefs.siteGuardDomains.sorted()
                Text(t("Websites on your list"), style = MaterialTheme.typography.labelLarge)
                domains.forEach { domain ->
                    val used = app.prefs.siteTimeMs("$today:$domain")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(domain, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(SiteGuardLogic.remainingLabel(used, budget), style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                TextButton(onClick = { showDomains = true }) { Text(t("Edit the list"), color = Blue) }
            }
        }
    }

    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false },
        title = { Text("Before you turn this on") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bas 1 Minute will ask Android for Accessibility access, used only to read the website address shown in your browser's address bar.")
                Text("• Never the page content, never anything you type or read.")
                Text("• Never any app other than the browsers listed in the app — WhatsApp, banking apps and everything else are invisible to it.")
                Text("• Worked out and kept on this phone only; nothing about which sites you visit is ever sent anywhere.")
                Text("You'll land on Android's own Accessibility settings next, where you turn it on for Bas 1 Minute yourself — the app can never do that for itself.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showDisclosure = false
                app.prefs.siteGuardEnabled = true; enabled = true
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }) { Text(t("Continue"), color = Blue, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text(t("Cancel")) } },
    )

    if (showDomains) EditDomainsDialog(app, onDismiss = { showDomains = false })
}

@Composable
private fun EditDomainsDialog(app: AppController, onDismiss: () -> Unit) {
    var domains by remember { mutableStateOf(app.prefs.siteGuardDomains) }
    var entry by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t("Edit the list")} (${domains.size}/${SiteGuardLogic.MAX_DOMAINS})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                domains.sorted().forEach { domain ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(domain, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { domains = domains - domain }) { Text(t("Remove"), color = Danger) }
                    }
                }
                if (domains.size < SiteGuardLogic.MAX_DOMAINS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(entry, { entry = it }, singleLine = true, placeholder = { Text("e.g. reddit.com") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        SmallPill(t("Add")) {
                            SiteGuardLogic.sanitizeEnteredDomain(entry)?.let { domains = domains + it; entry = "" }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { app.prefs.siteGuardDomains = domains; onDismiss() }) { Text(t("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}
