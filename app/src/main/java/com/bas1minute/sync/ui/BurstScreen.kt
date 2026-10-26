package com.bas1minute.sync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import kotlinx.coroutines.delay

private data class BurstType(val id: String, val label: String, val cue: String)

private val BURSTS = listOf(
    BurstType("squats", "Squats", "Steady squats. Sit back, stand tall, keep going."),
    BurstType("stairs", "Stairs", "Up the stairs, fast. Turn and go again."),
    BurstType("march", "Fast march", "March on the spot, knees high, arms pumping."),
)

/** The website's "Try a 1-minute burst", in the app. Logs the burst when the minute is done. */
@Composable
fun BurstScreen(app: AppController, onClose: () -> Unit) {
    var type by remember { mutableStateOf(BURSTS[0]) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = System.currentTimeMillis() - (elapsed * 1000).toLong()
        while (running) {
            elapsed = ((System.currentTimeMillis() - start) / 1000f).coerceAtMost(60f)
            if (elapsed >= 60f) {
                running = false
                done = true
                app.addBurst()
            }
            delay(100)
        }
    }

    val left = (60 - elapsed).toInt().coerceAtLeast(0)
    val cue = when {
        done -> "Done. That’s one burst, logged."
        !running && elapsed == 0f -> type.cue
        !running -> "Paused. Tap resume when you’re ready."
        left > 45 -> type.cue
        left > 30 -> "Good pace. Keep it going."
        left > 10 -> "Halfway done. Breathing harder is the point."
        else -> "Last few seconds. Finish strong!"
    }

    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) {
            Text("← Today", color = Muted, style = MaterialTheme.typography.labelLarge)
        }
        Text("One-minute burst", style = MaterialTheme.typography.headlineLarge)
        Text("Pick one and move until the ring closes. Getting a little out of breath is the point.",
            style = MaterialTheme.typography.bodyLarge, color = Muted, textAlign = TextAlign.Center)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BURSTS.forEach { b ->
                val on = b == type
                Text(b.label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Ink,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Color.White)
                        .clickable(enabled = !running) { type = b }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }

        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 16.dp.toPx()
                val arc = Size(size.width - w, size.height - w)
                drawArc(Color.White, -90f, 360f, false, Offset(w / 2, w / 2), arc, style = Stroke(w))
                if (elapsed > 0f) drawArc(if (done) Mint else Haldi, -90f, 360f * (elapsed / 60f), false, Offset(w / 2, w / 2), arc,
                    style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$left", style = MaterialTheme.typography.displayLarge)
                Text("seconds", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        Text(cue, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        when {
            done -> {
                val bursts = (app.today?.today?.bursts ?: 1)
                Text("Today: $bursts of ${app.prefs.burstGoal} bursts.", style = MaterialTheme.typography.bodyMedium, color = MintDark)
                BigButton("Another burst", modifier = Modifier.fillMaxWidth()) { done = false; elapsed = 0f; running = true }
                GhostButton("Back to today", onClick = onClose)
            }
            running -> GhostButton("Pause") { running = false }
            else -> BigButton(if (elapsed > 0f) "Resume" else "Start my minute", modifier = Modifier.fillMaxWidth()) { running = true }
        }
        if (!running && elapsed > 0f && !done) TextButton(onClick = { elapsed = 0f }) { Text("Reset", color = Muted) }
        Text("Heart condition, pregnancy or joint pain? Choose the fast march, go gently, and check with your doctor first.",
            style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center)
    }
}
