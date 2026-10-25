package com.bas1minute.sync.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Two concentric rings: steps on the outside, screen time on the inside. Animates on change. */
@Composable
fun DualRing(
    stepsFraction: Float,
    screenFraction: Float,
    screenOver: Boolean,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val steps by animateFloatAsState(stepsFraction.coerceIn(0f, 1f), tween(900), label = "steps")
    val screen by animateFloatAsState(screenFraction.coerceIn(0f, 1f), tween(900), label = "screen")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension
            val outerW = d * 0.085f
            val innerW = d * 0.06f
            val gap = d * 0.035f
            fun ring(inset: Float, width: Float, fraction: Float, color: Color) {
                val topLeft = Offset(inset, inset)
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(Ink3, -90f, 360f, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
                if (fraction > 0.001f) {
                    drawArc(color, -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
                }
            }
            ring(outerW / 2, outerW, steps, Violet)
            ring(outerW + gap + innerW / 2, innerW, screen, if (screenOver) Coral else Haldi)
        }
        center()
    }
}

/** Small stat tile on the dark header. */
@Composable
fun HeaderTile(label: String, value: String, hint: String, dot: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Ink2)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(label, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 2.dp))
        Text(hint, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
    }
}

/** White rounded card with an optional title. */
@Composable
fun AppCard(title: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        content()
    }
}

/** Seven vertical bars, one per day. A dashed-free goal marker is shown as a thin line. */
@Composable
fun WeekBars(
    days: List<Pair<String, Int?>>,
    goal: Int,
    barColor: Color,
    overColor: Color?,
    format: (Int) -> String,
) {
    val peak = (maxOf(goal, days.maxOfOrNull { it.second ?: 0 } ?: 0)).coerceAtLeast(1) * 1.12f
    Box(Modifier.fillMaxWidth().height(170.dp)) {
        // goal line
        val goalFraction = goal / peak
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f - goalFraction * 0.78f))
            Box(Modifier.fillMaxWidth().height(2.dp).background(PaperLine))
            Spacer(Modifier.weight(goalFraction * 0.78f + 0.0001f))
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, (label, value) ->
                val isToday = i == days.lastIndex
                val fraction = ((value ?: 0) / peak).coerceIn(0f, 1f)
                val over = overColor != null && value != null && value > goal
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        value?.let(format) ?: "",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = Muted,
                        maxLines = 1,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight(if (value == null) 0.04f else fraction.coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    when {
                                        value == null -> PaperLine
                                        over -> overColor!!
                                        isToday -> Ink
                                        else -> barColor
                                    }
                                )
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = if (isToday) Ink else Muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** One permission or connection line with a status badge and an optional fix-it button. */
@Composable
fun StatusRow(title: String, subtitle: String, ok: Boolean, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (ok) Color(0xFFE3F7EE) else Color(0xFFFFF3C8)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (ok) "✓" else "!", color = if (ok) MintDark else Color(0xFF8A6200), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            SmallPill(action, onAction)
        }
    }
}

@Composable
fun SmallPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Haldi)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Segmented two-option switch (for the week chart). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Paper)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (i == selected) Color.White else Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (i == selected) Ink else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, dark: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = if (dark) ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White)
        else ButtonDefaults.buttonColors(containerColor = Haldi, contentColor = Ink),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun GhostButton(text: String, onDark: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(2.dp, if (onDark) Line else Ink),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (onDark) Color.White else Ink) }
}

/** Round badge with a symbol, used as the illustration on onboarding pages. */
@Composable
fun Badge(symbol: String, background: Color, size: Int = 96) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(symbol, fontSize = (size * 0.45f).sp)
    }
}

fun hm(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
