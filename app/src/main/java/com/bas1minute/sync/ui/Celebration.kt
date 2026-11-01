package com.bas1minute.sync.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.random.Random

private data class Piece(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val w: Float)

/** Shown once a day when the step goal is reached: confetti, a buzz, and a chance to share. */
@Composable
fun GoalCelebration(steps: String, km: String, onShare: () -> Unit, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val t = remember { Animatable(0f) }
    val pieces = remember {
        val colors = listOf(Haldi, Violet, Mint, Coral, Color.White)
        List(90) { Piece(Random.nextFloat(), Random.nextFloat() * 0.35f, 0.6f + Random.nextFloat() * 0.8f,
            (Random.nextFloat() - 0.5f) * 0.25f, Random.nextFloat() * 720f, colors.random(), 6f + Random.nextFloat() * 8f) }
    }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        t.animateTo(1f, tween(2800, easing = LinearEasing))
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                pieces.forEach { p ->
                    val k = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f) * p.speed
                    if (k <= 0f) return@forEach
                    val x = (p.x + p.drift * k) * size.width
                    val y = -20f + k * size.height * 1.1f
                    rotate(p.spin * k, Offset(x, y)) {
                        drawRect(p.color, Offset(x, y), Size(p.w, p.w * 1.8f), alpha = (1.4f - k).coerceIn(0f, 1f))
                    }
                }
            }
            Column(
                Modifier.padding(28.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(t("Goal done!"), style = MaterialTheme.typography.displayMedium, color = Haldi)
                Text("$steps steps · $km", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Shabash! Every day like this builds the habit.", style = MaterialTheme.typography.bodyMedium,
                    color = MutedOnDark, textAlign = TextAlign.Center)
                BigButton(t("Share it"), dark = false) { onShare() }
                TextButton(onClick = onClose) { Text(t("Close"), color = MutedOnDark) }
            }
        }
    }
}

/** Soft pulsing placeholder while phone data loads. */
@Composable
fun Skeleton(modifier: Modifier, dark: Boolean = false) {
    val a by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    Box(modifier.alpha(a).clip(RoundedCornerShape(12.dp)).background(if (dark) Ink3 else PaperLine))
}
