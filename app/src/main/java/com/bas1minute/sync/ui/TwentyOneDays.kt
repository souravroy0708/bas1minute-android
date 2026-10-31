package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Distance
import java.text.NumberFormat
import java.util.Locale

/** Pure numbers for the 21-day card (kept separate so they're easy to check). */
object TwentyOneStats {
    /** Days in a row reaching the goal, counting back from today (or from yesterday if today isn't done yet). */
    fun streak(steps: List<Long?>, goal: Int): Int {
        var i = steps.lastIndex
        if (i >= 0 && (steps[i] ?: 0L) < goal) i--          // today still in progress
        var n = 0
        while (i >= 0 && (steps[i] ?: 0L) >= goal) { n++; i-- }
        return n
    }
}

/**
 * Home screen: the last 21 days from this phone's database. Habit grid, streak, totals with distance,
 * and a 21-day chart for steps or screen time (social, messaging and browser apps).
 */
@Composable
fun TwentyOneDays(app: AppController) {
    val days = app.phone.last21
    if (days.size != 21) return
    val n = remember { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
    val goal = app.home.stepGoal
    val limit = app.home.screenLimit
    val stepCm = app.prefs.stepLengthCm
    var chart by remember { mutableIntStateOf(0) }

    val steps = days.map { it.row?.steps }
    val screen = days.map { it.row?.socialMinutes }
    val measured = steps.filterNotNull()
    val total = measured.sum()
    val goalDays = measured.count { it >= goal }
    val streak = TwentyOneStats.streak(steps, goal)
    val screenMeasured = screen.filterNotNull()

    AppCard(title = "Your 21 days", trailing = {
        Text(if (streak > 0) "🔥 $streak-day streak" else "Start a streak today", style = MaterialTheme.typography.labelMedium,
            color = Ink, modifier = Modifier.clip(RoundedCornerShape(50)).background(if (streak > 0) Haldi else Paper)
                .padding(horizontal = 10.dp, vertical = 6.dp))
    }) {
        Text("A 21-day run is a great first challenge. Fill the grid by reaching your step goal each day.",
            style = MaterialTheme.typography.bodySmall, color = Muted)

        // Habit grid: 3 weeks × 7 days, oldest top-left, today bottom-right
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            days.chunked(7).forEachIndexed { week, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { i, d ->
                        val s = d.row?.steps
                        val isToday = week == 2 && i == 6
                        val color = when {
                            s == null -> PaperLine
                            s >= goal -> Haldi
                            s > 0 -> Color(0xFFC9CEFF)
                            else -> PaperLine
                        }
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(color)
                                .then(if (isToday) Modifier.border(2.dp, Ink, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                                color = if (s != null && s >= goal) Ink else Muted)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(Haldi, "Goal reached"); Legend(Color(0xFFC9CEFF), "Moved"); Legend(PaperLine, "No data")
        }

        // Totals
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat21("Total steps", if (measured.isEmpty()) "—" else n.format(total), Modifier.weight(1f))
            Stat21("Distance", if (measured.isEmpty()) "—" else Distance.format(Distance.km(total, stepCm)), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat21("Avg a day", if (measured.isEmpty()) "—" else n.format(total / measured.size), Modifier.weight(1f))
            Stat21("Goal days", "$goalDays / 21", Modifier.weight(1f))
        }

        // 21-day chart
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (chart == 0) "Steps each day" else "Screen time each day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Segmented(listOf("Steps", "Screen"), chart) { chart = it }
        }
        if (chart == 0) {
            MiniBars(steps.map { it?.toInt() }, goal, Violet, null)
        } else {
            MiniBars(screen, limit, Color(0xFFB9C0FF), Coral)
            Text(
                if (screenMeasured.isEmpty()) "Switch on usage access in Profile to measure this."
                else "Avg ${hm(screenMeasured.sum() / screenMeasured.size)} a day · under your ${hm(limit)} limit on " +
                    "${screenMeasured.count { it <= limit }} of ${screenMeasured.size} days",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Text("Screen time here counts social, messaging and browser apps (Instagram, YouTube, WhatsApp, Chrome…).",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Row {
            Text(days.first().date.let { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }}" },
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
            Text("Today", style = MaterialTheme.typography.bodySmall, color = Ink)
        }
    }
}

/** 21 thin bars with a goal line. Grey stubs for days with no data; [overColor] marks days above the goal. */
@Composable
private fun MiniBars(values: List<Int?>, goal: Int, color: Color, overColor: Color?) {
    val peak = (maxOf(goal, values.maxOfOrNull { it ?: 0 } ?: 0)).coerceAtLeast(1) * 1.1f
    Box(Modifier.fillMaxWidth().height(110.dp)) {
        val goalFrac = goal / peak
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f - goalFrac + 0.0001f))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(PaperLine))
            Spacer(Modifier.weight(goalFrac + 0.0001f))
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val frac = if (v == null) 0.03f else (v / peak).coerceIn(0.03f, 1f)
                val c = when {
                    v == null -> PaperLine
                    overColor != null && v > goal -> overColor
                    overColor == null && v >= goal -> Ink
                    i == values.lastIndex -> Blue
                    else -> color
                }
                Box(Modifier.weight(1f).fillMaxHeight(frac).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(c))
            }
        }
    }
}

@Composable
private fun Stat21(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Paper).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
