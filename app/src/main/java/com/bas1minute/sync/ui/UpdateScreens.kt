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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bas1minute.sync.AppUpdate
import com.bas1minute.sync.BuildConfig

/** Shown instead of the app when this version is below the minimum. Your data on the phone stays. */
@Composable
fun UpdateRequiredScreen(onUpdate: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
                Text("1", color = Ink, style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp))
            }
            Text("Time to update", color = Color.White, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Text(AppUpdate.message, color = MutedOnDark, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text("This version no longer works with our servers. Updating takes a minute, and everything on your phone stays as it is.",
                color = MutedOnDark, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            BigButton("Update now", dark = false, modifier = Modifier.fillMaxWidth(), onClick = onUpdate)
            Text("Version ${BuildConfig.VERSION_NAME}", color = MutedOnDark, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A gentle banner on Today when a newer version is on Google Play. */
@Composable
fun UpdateAvailableBanner(onUpdate: () -> Unit, onLater: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFE9ECFF)).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Update available", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("New features and fixes on Google Play", style = MaterialTheme.typography.bodySmall, color = Ink)
        }
        TextButton(onClick = onLater) { Text("Later", color = Muted) }
        Spacer(Modifier.width(2.dp))
        SmallPill("Update", onUpdate)
    }
}
