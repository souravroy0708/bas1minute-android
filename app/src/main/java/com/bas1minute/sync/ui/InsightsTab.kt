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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Forecast
import com.bas1minute.sync.Tab
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

/** Social media time check, phone and sleep habits, water and weight. All from this phone; works offline. */
@Composable
fun InsightsTab(app: AppController) {
    val ins = app.phone.insights
    val n = remember { NumberFormat.getIntegerInstance(Locale("en", "IN")) }
    var pick by remember { mutableIntStateOf(2) } // 10 years by default: the eye-opener

    Column(
        Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()),
    ) {
        // ---------- Hero: social media time ----------
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Ink, Ink2))).statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Time check", color = Haldi, style = MaterialTheme.typography.labelLarge)
            Text("Social media today", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(ins?.socialToday?.let { hm(it) } ?: "—", color = Color.White, style = MaterialTheme.typography.displayLarge)
            Text(
                when {
                    !app.phone.usageAccess -> "Switch on usage access in Profile to measure this automatically."
                    ins?.socialAvg == null -> "Measuring. Check back tomorrow for your weekly average."
                    else -> "Your 7-day average is ${hm(ins.socialAvg)} a day" +
                        (ins.socialShare?.let { ", $it% of your screen time." } ?: ".")
                },
                color = MutedOnDark, style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---------- Forecast ----------
            if (ins != null && ins.forecasts.isNotEmpty() && ins.socialAvg != null && ins.socialAvg > 0) {
                val f: Forecast = ins.forecasts[pick]
                AppCard(title = "If this continues", trailing = { Segmented(listOf("1 yr", "3 yrs", "10 yrs"), pick) { pick = it } }) {
                    Text("At ${hm(ins.socialAvg)} a day on social apps, in ${if (f.years == 1) "1 year" else "${f.years} years"} that’s:",
                        style = MaterialTheme.typography.bodyMedium, color = Muted)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink).padding(20.dp)) {
                        Column {
                            Text("${n.format(f.days)} full days", color = Haldi, style = MaterialTheme.typography.displayMedium)
                            Text("${n.format(f.hours)} hours, or ${n.format(f.wakingDays)} days of your waking life", color = Color.White,
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text("The same time could be:", style = MaterialTheme.typography.titleMedium)
                    Equivalent("🚶", "${n.format(f.walkKm)} km of brisk walking", "at about 5 km an hour")
                    Equivalent("💪", "${n.format(f.hours * 2)} thirty-minute workouts", "")
                    Equivalent("📚", "${n.format(f.hours / 100)} new skills learned", "at about 100 focused hours per skill")
                    Text("Based on your last ${ins.daysMeasured} measured day${if (ins.daysMeasured == 1) "" else "s"}. Messaging apps aren’t counted.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }

                AppCard(title = "Take back just a little") {
                    ins.reclaim.forEach { r ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).padding(14.dp)) {
                            Text("${r.minutesPerDay} minutes a day = ${r.hoursPerYear} hours a year", style = MaterialTheme.typography.titleMedium)
                            Text("That’s ${n.format(r.walkKm)} km walked, or ${r.workouts30} workouts, or ${n.format(r.bursts)} one-minute bursts.",
                                style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                    BigButton("Do a 1-minute burst now", dark = false) { app.tab = Tab.Today; app.addBurst() }
                }
            }

            // ---------- Top social apps (this phone only) ----------
            if (ins != null && ins.topApps.isNotEmpty()) {
                AppCard(title = "Where it goes", trailing = { Text("Last 7 days", color = Muted, style = MaterialTheme.typography.bodySmall) }) {
                    val max = ins.topApps.maxOf { it.second }.coerceAtLeast(1)
                    ins.topApps.forEach { (name, minutes) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row { Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(hm(minutes), style = MaterialTheme.typography.titleMedium) }
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Paper)) {
                                Box(Modifier.fillMaxWidth(minutes.toFloat() / max).height(8.dp).clip(RoundedCornerShape(50)).background(Coral))
                            }
                        }
                    }
                    Text("App names stay on this phone. Only the daily total is synced.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }

            // ---------- Phone & sleep habits ----------
            val night = ins?.lastNight
            AppCard(title = "Phone and sleep") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("Last use at night", night?.lastUse ?: "—", "avg ${ins?.avgLastUse ?: "—"}", Modifier.weight(1f))
                    MiniStat("First use today", night?.firstUse ?: "—", "avg ${ins?.avgFirstUse ?: "—"}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat(
                        "Sleep last night", night?.sleepMinutes?.let { hm(it) } ?: "—",
                        when (night?.sleepSource) { "health_connect" -> "from Health Connect"; "phone_estimate" -> "estimated from phone"; "manual" -> "you logged it"; else -> "not measured" },
                        Modifier.weight(1f),
                    )
                    MiniStat("7-day average", ins?.avgSleep?.let { hm(it) } ?: "—", "sleep", Modifier.weight(1f))
                }
                Text(
                    "The estimate is the longest break from your phone overnight, so it can be longer than actual sleep. A watch or Samsung Health in Health Connect is more accurate. Most adults need 7 or more hours.",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
                if (night?.firstUse != null) {
                    Nudge("Tomorrow, try a 1-minute burst before you open your phone.")
                }
            }

            // ---------- Water ----------
            if (ins != null) {
                AppCard(title = "Water", trailing = { SmallPill("+ 1 glass") { app.addWater() } }) {
                    val days = ins.water
                    WeekBars(
                        days.mapIndexed { i, (d, ml) -> (if (i == days.lastIndex) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) to ml },
                        goal = 2000, barColor = Color(0xFF7FC8F8), overColor = null,
                    ) { if (it >= 1000) "%.1fL".format(it / 1000.0) else "${it}ml" }
                    Text(
                        if (app.phone.extras.water) "Also reads water from apps that save to Health Connect."
                        else "Tap + 1 glass (250 ml). Water apps can fill this in automatically through Health Connect.",
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                }
            }

            // ---------- Weight (opt-in, private) ----------
            if (app.prefs.trackWeight && ins != null) {
                AppCard(title = "Weight", trailing = { Text("Only you see this", color = Muted, style = MaterialTheme.typography.bodySmall) }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(ins.weightLatest?.let { "%.1f".format(it) } ?: "—", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.width(6.dp))
                        Text("kg", style = MaterialTheme.typography.titleMedium, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    ins.weightChange?.let {
                        Text("${if (it > 0) "+" else ""}${"%.1f".format(it)} kg over the last month", style = MaterialTheme.typography.bodyMedium, color = Muted)
                    }
                    Text("Log it in Log a day, or let a smart scale fill it in through Health Connect. Energy and consistency matter more than any single number.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun Equivalent(icon: String, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Paper).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
