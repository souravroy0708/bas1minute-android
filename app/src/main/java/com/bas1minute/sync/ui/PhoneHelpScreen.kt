package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.PhoneHelp

/** Profile → Phone-specific help: every per-phone setting in one place, tailored to this phone. */
@Composable
fun PhoneHelpScreen(app: AppController, actions: PhoneActions, onBack: () -> Unit) {
    val d = PhoneHelp.device()
    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Profile", color = Muted, style = MaterialTheme.typography.labelLarge) }
        Text("Help for your phone", style = MaterialTheme.typography.headlineLarge)
        Text("Every brand puts these settings in a slightly different place. Here’s the usual path for your ${d.brand}, " +
            "plus a one-tap search for your exact model.", style = MaterialTheme.typography.bodyLarge, color = Muted)

        PhoneHelpCard(
            help = PhoneHelp.usageAccess(d),
            intro = if (app.phone.usageAccess) "Usage access is already on. Keep this in case you reinstall."
            else "Needed for screen time, social media time and sleep estimates.",
            helpActions = actions.help,
            background = if (app.phone.usageAccess) Color.White else Color(0xFFFFF3C8),
            buttons = listOf("Open usage access" to actions.onOpenUsageAccess, "Open app info" to actions.onOpenAppInfo),
        )

        PhoneHelpCard(
            help = PhoneHelp.battery(d),
            intro = if (PhoneHelp.batteryStrict(d)) "${d.brand} phones save battery by pausing apps in the background. Allow Bas 1 Minute so it can sync on its own."
            else "Most phones like yours sync fine. If syncing only happens when you open the app, do this.",
            helpActions = actions.help,
            background = Color.White,
            buttons = listOf("Open battery settings" to actions.onOpenBatterySettings),
        )
    }
}
