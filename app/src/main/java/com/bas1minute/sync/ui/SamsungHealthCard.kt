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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SamsungBlue = Color(0xFF1428A0)

/**
 * Shown when Samsung Health is installed but its data isn't reaching Health Connect yet.
 * [hcGranted] false: the app itself still needs Health Connect permission first.
 */
@Composable
fun SamsungHealthCard(
    hcGranted: Boolean,
    onAllowHealthConnect: () -> Unit,
    onOpenSamsungHealth: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(SamsungBlue), contentAlignment = Alignment.Center) {
                Text("S", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Connect Samsung Health", style = MaterialTheme.typography.titleLarge)
                Text("Use your Galaxy phone and watch steps, sleep, weight and water", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        if (!hcGranted) {
            Step(1, "Allow Bas 1 Minute to read Health Connect.")
            Step(2, "Then switch on sharing in Samsung Health (next step appears here).")
            BigButton("Allow Health Connect", onClick = onAllowHealthConnect)
        } else {
            Text("Samsung Health doesn’t share with Health Connect until you switch it on:", style = MaterialTheme.typography.bodyMedium)
            Step(1, "Open Samsung Health and tap ⋮ (top right), then Settings.")
            Step(2, "Tap Health Connect (on some phones: Connected services, then Health Connect).")
            Step(3, "Allow Samsung Health to share Steps, Sleep, Weight and Water.")
            Step(4, "Come back here. Your steps appear within a few minutes.")
            BigButton("Open Samsung Health", onClick = onOpenSamsungHealth)
        }
        if (onDismiss != null) {
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Not now", color = Muted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Paper), contentAlignment = Alignment.Center) {
            Text(n.toString(), style = MaterialTheme.typography.labelMedium, color = Ink)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}
