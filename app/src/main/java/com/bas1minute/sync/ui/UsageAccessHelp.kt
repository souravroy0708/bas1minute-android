package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.PhoneHelp

/** Ways to get help that work on any phone: search Google for this exact model, or copy a ready question. */
data class HelpActions(
    val onSearch: (String) -> Unit,
    val onCopy: (String) -> Unit,
)

/** A help card for one topic, tailored to this phone, with buttons for the settings screens it needs. */
@Composable
fun PhoneHelpCard(
    help: PhoneHelp.Help,
    intro: String,
    helpActions: HelpActions,
    background: Color = Color(0xFFFFF3C8),
    buttons: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val device = PhoneHelp.device()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(help.title, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Text("Your phone: ${device.label}", style = MaterialTheme.typography.labelMedium, color = Muted)
        help.steps.forEachIndexed { i, step -> HelpStep(i + 1, step) }
        if (buttons.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEach { (label, action) -> SmallPill(label, action) }
            }
        }
        Text("Menus can differ after phone updates. If you can’t find it:", style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallPill("Search Google for my phone") { helpActions.onSearch(help.searchQuery) }
            SmallPill("Copy question") { helpActions.onCopy(help.aiQuestion) }
        }
        Text("“Copy question” describes your phone and the problem; paste it into Google, Gemini or ChatGPT.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

/** Shown when Android blocks Usage access because the app was installed outside the Play Store. */
@Composable
fun UsageAccessHelp(onOpenUsageAccess: () -> Unit, onOpenAppInfo: () -> Unit, helpActions: HelpActions) {
    PhoneHelpCard(
        help = PhoneHelp.usageAccess(),
        intro = "Seeing “App was denied access”? Android protects usage access for apps installed outside the Play Store. " +
            "It’s safe to allow for Bas 1 Minute: only daily totals are used, never what you do in apps.",
        helpActions = helpActions,
        buttons = listOf("Open usage access" to onOpenUsageAccess, "Open app info" to onOpenAppInfo),
    )
}

@Composable
private fun HelpStep(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
            Text(n.toString(), style = MaterialTheme.typography.labelMedium, color = Haldi)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 2.dp))
    }
}
