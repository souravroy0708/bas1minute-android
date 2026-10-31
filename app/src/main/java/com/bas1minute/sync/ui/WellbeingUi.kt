package com.bas1minute.sync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.WheelMath
import com.bas1minute.sync.Wellbeing
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// ---------------- Quote of the day ----------------

@Composable
fun QuoteCard(onShare: (String) -> Unit) {
    val quote = remember { Wellbeing.quoteOfDay() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Today’s thought", style = MaterialTheme.typography.labelMedium, color = Haldi)
        Row {
            Text("“", style = MaterialTheme.typography.displayLarge, color = Haldi, modifier = Modifier.offset(y = (-8).dp))
            Spacer(Modifier.width(6.dp))
            Text(quote, style = MaterialTheme.typography.titleLarge, color = Color.White, fontStyle = FontStyle.Normal)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New thought every day", style = MaterialTheme.typography.bodySmall, color = MutedOnDark, modifier = Modifier.weight(1f))
            Text("Share", style = MaterialTheme.typography.labelMedium, color = Ink,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Haldi)
                    .clickable { onShare("“$quote”\n\nToday’s thought from Bas 1 Minute 🌱") }
                    .padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

// ---------------- The wheel drawing ----------------

/** Eight coloured segments with an emoji and label each. [rotation] turns the whole wheel clockwise. */
@Composable
fun HealthWheel(size: Dp, rotation: Float, showLabels: Boolean = true) {
    Canvas(Modifier.size(size).graphicsLayer { rotationZ = rotation }) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        Wellbeing.CATEGORIES.forEachIndexed { i, cat ->
            // Segment i is centred i×45° clockwise from the top (Compose arcs start at 3 o’clock, so −90°).
            val start = i * WheelMath.SWEEP - 90f - WheelMath.SWEEP / 2f
            drawArc(Color(cat.color), start, WheelMath.SWEEP, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
            drawArc(Color.White, start, WheelMath.SWEEP, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(2.dp.toPx()))
            if (showLabels) {
                val mid = Math.toRadians((i * WheelMath.SWEEP - 90f).toDouble())
                val emojiPaint = android.graphics.Paint().apply { textSize = r * 0.2f; textAlign = android.graphics.Paint.Align.CENTER }
                val labelPaint = android.graphics.Paint().apply {
                    textSize = r * 0.09f; textAlign = android.graphics.Paint.Align.CENTER
                    color = android.graphics.Color.parseColor("#0F1235"); isFakeBoldText = true; isAntiAlias = true
                }
                val ex = c.x + (r * 0.66f * cos(mid)).toFloat()
                val ey = c.y + (r * 0.66f * sin(mid)).toFloat()
                drawContext.canvas.nativeCanvas.drawText(cat.emoji, ex, ey, emojiPaint)
                drawContext.canvas.nativeCanvas.drawText(cat.label, ex, ey + r * 0.13f, labelPaint)
            }
        }
        drawCircle(Color.White, r * 0.16f, c)
        drawCircle(Ink, r * 0.1f, c)
    }
}

/** The fixed pointer above the wheel. */
@Composable
private fun Pointer() {
    Canvas(Modifier.size(width = 28.dp, height = 22.dp)) {
        val p = Path().apply {
            moveTo(0f, 0f); lineTo(this@Canvas.size.width, 0f); lineTo(this@Canvas.size.width / 2f, this@Canvas.size.height); close()
        }
        drawPath(p, Ink)
    }
}

// ---------------- Teaser card on Today ----------------

@Composable
fun WheelTeaser(app: AppController) {
    val version = app.wheelVersion   // re-read after spins
    val left = remember(version) { app.spinsLeft() }
    val found = remember(version) { app.prefs.discoveredTips.size }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).clickable { app.wheelOpen = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HealthWheel(size = 72.dp, rotation = 15f, showLabels = false)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Spin the health wheel", style = MaterialTheme.typography.titleLarge)
            Text(if (left > 0) "$left spin${if (left == 1) "" else "s"} left today · discover a health tip"
                else "Come back tomorrow for 3 new spins", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("$found of ${Wellbeing.TIPS.size} tips discovered", style = MaterialTheme.typography.labelMedium, color = Blue)
        }
        SmallPill(if (left > 0) "Spin" else "Tips") { app.wheelOpen = true }
    }
}

// ---------------- Full wheel screen ----------------

private val SpinEasing = CubicBezierEasing(0.12f, 0.8f, 0.22f, 1f)

@Composable
fun WheelScreen(app: AppController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Wellbeing.Tip?>(null) }
    var showFound by remember { mutableStateOf(false) }
    val version = app.wheelVersion
    val left = remember(version, spinning) { app.spinsLeft() }
    val found = remember(version) { app.prefs.discoveredTips }

    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) {
            Text("← Today", color = Muted, style = MaterialTheme.typography.labelLarge)
        }
        Text("Spin the health wheel", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text("Spin to discover a small habit for a healthier day.", style = MaterialTheme.typography.bodyLarge, color = Muted, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("${left} spin${if (left == 1) "" else "s"} left today")
            Chip("${found.size} / ${Wellbeing.TIPS.size} tips found")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Pointer()
            Box(contentAlignment = Alignment.Center) {
                HealthWheel(size = 300.dp, rotation = rotation.value)
            }
        }

        BigButton(
            when { spinning -> "Spinning…"; left > 0 -> "Spin"; else -> "No spins left today" },
            enabled = !spinning && left > 0,
            dark = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val pick = app.takeSpin() ?: return@BigButton
            val (segment, tip) = pick
            result = null
            spinning = true
            scope.launch {
                val jitter = (kotlin.random.Random.nextFloat() - 0.5f) * (WheelMath.SWEEP - 10f)
                val target = WheelMath.targetFor(rotation.value, segment, turns = 5, jitter = jitter)
                rotation.animateTo(target, tween(durationMillis = 3800, easing = SpinEasing))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                app.tipShown(tip)
                result = tip
                spinning = false
            }
        }

        AnimatedVisibility(visible = result != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            result?.let { tip ->
                val cat = Wellbeing.CATEGORIES.first { it.id == tip.category }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(cat.color)), contentAlignment = Alignment.Center) {
                            Text(cat.emoji, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(cat.label, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(tip.text, style = MaterialTheme.typography.bodyLarge)
                    Text(if (left > 0) "Spin again for another tip." else "That’s today’s spins. Come back tomorrow for more.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }

        if (found.isNotEmpty()) {
            TextButton(onClick = { showFound = !showFound }) {
                Text(if (showFound) "Hide my tips" else "See my ${found.size} tips", color = Ink, style = MaterialTheme.typography.labelLarge)
            }
            if (showFound) {
                Wellbeing.TIPS.filter { it.id.toString() in found }.forEach { tip ->
                    val cat = Wellbeing.CATEGORIES.first { it.id == tip.category }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                        Text(cat.emoji, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(tip.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Text("General wellness tips, not medical advice.", style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Chip(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp))
}
