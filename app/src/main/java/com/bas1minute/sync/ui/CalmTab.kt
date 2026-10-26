package com.bas1minute.sync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Tab
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos

/** Bas 1 minute shaanti: breathe in 4 seconds, out 6, six times. Works offline (counted later). */
@Composable
fun CalmTab(app: AppController, onCall: (String) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (running) {
            elapsed = ((System.currentTimeMillis() - start) / 1000f).coerceAtMost(60f)
            if (elapsed >= 60f) {
                running = false
                done = true
                app.calmDone()
            }
            delay(40)
        }
    }

    val inCycle = elapsed % 10f
    val breathingIn = inCycle < 4f
    // Smooth grow over 4s, shrink over 6s
    val t = if (breathingIn) inCycle / 4f else 1f - (inCycle - 4f) / 6f
    val eased = (1 - cos(t * PI).toFloat()) / 2f
    val scale = if (running) 0.55f + 0.45f * eased else 0.6f

    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Bas 1 minute shaanti", style = MaterialTheme.typography.labelMedium, color = Blue,
            modifier = Modifier.padding(top = 12.dp).clip(CircleShape).background(Color(0xFFE9ECFF)).padding(horizontal = 14.dp, vertical = 6.dp))
        Text("One minute of slow breathing", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text("Breathe in for 4, out for 6. Six slow breaths and you’re done.", style = MaterialTheme.typography.bodyLarge, color = Muted, textAlign = TextAlign.Center)

        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 10.dp.toPx()
                val inset = w / 2
                val arc = Size(size.width - w, size.height - w)
                drawArc(Color(0xFFE2E5F3), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                if (elapsed > 0f) drawArc(Blue, -90f, 360f * (elapsed / 60f), false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Box(
                Modifier.size(190.dp).scale(scale).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFB9C0FF), Color(0xFF5865E8))))
            )
        }

        Text(
            when { done -> "Done. Notice how you feel."; !running -> "Press start when you’re ready"; breathingIn -> "Breathe in…"; else -> "Breathe out, slowly…" },
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
        )
        if (running) Text("${(60 - elapsed).toInt()} seconds", color = Muted, style = MaterialTheme.typography.bodyMedium)

        if (!running) {
            BigButton(if (done) "One more minute" else "Start", modifier = Modifier.fillMaxWidth()) {
                done = false; elapsed = 0f; running = true
            }
        } else {
            TextButton(onClick = { running = false; elapsed = 0f }) { Text("Stop", color = Muted) }
        }
        if (done) {
            Text("Today so far: ${app.today?.today?.mindful ?: 1} min of calm. It counts toward your ring points.",
                style = MaterialTheme.typography.bodyMedium, color = MintDark, textAlign = TextAlign.Center)
            GhostButton("Back to today") { app.tab = Tab.Today }
        }
        Text("If you feel dizzy, breathe normally. For support any time, call Tele-MANAS on 14416, free and 24x7.",
            style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center)
        TextButton(onClick = { onCall("14416") }) { Text("Call 14416", color = Ink, style = MaterialTheme.typography.labelLarge) }
    }
}
