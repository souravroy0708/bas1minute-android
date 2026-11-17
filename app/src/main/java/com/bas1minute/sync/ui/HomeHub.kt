package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Tab
import com.bas1minute.sync.detox.DetoxLogic
import com.bas1minute.sync.detox.DetoxStore
import com.bas1minute.sync.diary.DiaryStore
import com.bas1minute.sync.goal.GoalKind
import com.bas1minute.sync.goal.GoalLogic
import com.bas1minute.sync.goal.GoalStore
import com.bas1minute.sync.track.LiveTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * The home screen's "everything in one tap" grid.
 *
 * Before: only Burst/Calm/Water/Log sat near the top; Goals, Diary, Walk and Detox were cards further down, and
 * Circles, the India ranking, Screen-time insights and Phone check weren't on Today at all (Circles was two
 * taps deep inside Profile). Now every main feature is a tile on the first screen, each showing ONE live
 * number so the grid is useful even without tapping, and each tap opens the real feature screen.
 *
 * Rows go from "do it now" to "people" to "focus": the order someone's day usually runs in.
 */

/** Live one-liners for the tiles that need a disk read (goals, diary, detox). Reloaded whenever Today shows. */
private data class HubStatus(val goalsMet: Int = 0, val goalsTotal: Int = 0, val diaryDone: Boolean = false, val detoxLeftMs: Long? = null)

@Composable
private fun rememberHubStatus(app: AppController): HubStatus {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(HubStatus()) }
    LaunchedEffect(app.tab, app.home) {
        status = withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            val goals = runCatching {
                val store = GoalStore(ctx)
                val active = store.active()
                val met = active.count {
                    GoalLogic.dayStatus(it, today, store.signal(today), if (it.kind == GoalKind.Custom) store.checkin(it.id, today) else null).met
                }
                met to active.size
            }.getOrDefault(0 to 0)
            val diary = runCatching { DiaryStore(ctx).reflectionOn(today) != null }.getOrDefault(false)
            val detox = runCatching { DetoxStore.get(ctx).active()?.remainingMs(System.currentTimeMillis()) }.getOrNull()
            HubStatus(goals.first, goals.second, diary, detox)
        }
    }
    return status
}

private class Feature(
    val icon: ImageVector, val label: String, val status: String, val tint: Color,
    val badge: Int = 0, val highlight: Boolean = false, val onClick: () -> Unit,
)

@Composable
fun FeatureGrid(app: AppController, bursts: Int, burstGoal: Int, calm: Int, water: Int, socialMinutes: Int?) {
    val s = rememberHubStatus(app)
    val live by LiveTrack.state.collectAsState()
    val cheers = app.today?.cheers?.size ?: 0
    val circles = app.circles?.circles?.size ?: app.today?.circleCount ?: 0

    val features = listOf(
        // Row 1 — do it now (the four that were already here)
        Feature(Icons.Filled.Bolt, t("Burst"), "$bursts/$burstGoal", Haldi) { app.burstOpen = true },
        Feature(Icons.Filled.SelfImprovement, t("Calm"), "$calm min", Color(0xFFFFE7DF)) { app.calmOpen = true },
        Feature(Icons.Filled.WaterDrop, t("Water"), if (water >= 1000) "%.1f L".format(water / 1000.0) else "$water ml · +1", Color(0xFFDDF0FD)) { app.addWater() },
        Feature(Icons.Filled.EditNote, t("Log"), t("a day"), Color(0xFFE9ECFF)) { app.logOpen = true },
        // Row 2 — building something
        Feature(Icons.Filled.Flag, t("Goals"), if (s.goalsTotal == 0) t("Set one") else "${s.goalsMet}/${s.goalsTotal} ${t("today")}", Color(0xFFFFF3C8)) {
            app.tab = Tab.Goals; app.goalsHubView = "goals"
        },
        Feature(Icons.Filled.AutoStories, t("Diary"), if (s.diaryDone) t("Done today") else t("Reflect"), Color(0xFFE9D9FF), highlight = !s.diaryDone) {
            app.tab = Tab.Goals; app.goalsHubView = "diary"
        },
        Feature(Icons.Filled.DirectionsRun, t("Walk · Run"), if (live.active) t("On now") else t("Start"), Color(0xFFDFF5EA), highlight = live.active) {
            app.trackOpen = true
        },
        Feature(Icons.Filled.Groups, t("Circles"), when { cheers > 0 -> "$cheers ${t("new")}"; circles > 0 -> "$circles ${t("circles")}"; else -> t("Join one") },
            Color(0xFFFFE3EC), badge = cheers) { openCircles(app, ranking = false) },
        // Row 3 — focus and the wider picture
        Feature(Icons.Filled.HourglassBottom, t("Detox"), s.detoxLeftMs?.let { "${DetoxLogic.formatDuration(it)} ${t("left")}" } ?: t("Start"),
            Color(0xFFE9ECFF), highlight = s.detoxLeftMs != null) {
            app.tab = Tab.Detox; app.phoneCheckOpen = false; app.detoxView = "detox"
        },
        Feature(Icons.Filled.Insights, t("Screen time"), socialMinutes?.let { "${hm(it)} ${t("social")}" } ?: t("See it"), Color(0xFFFFF3C8)) {
            app.tab = Tab.Detox; app.phoneCheckOpen = false; app.detoxView = "insights"
        },
        Feature(Icons.Filled.PhoneAndroid, t("Phone check"), t("Battery, apps"), Color(0xFFDDF0FD)) {
            app.tab = Tab.Detox; app.phoneCheckOpen = true
        },
        Feature(Icons.Filled.Leaderboard, t("India rank"), t("This week"), Color(0xFFDFF5EA)) { openCircles(app, ranking = true) },
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        features.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { FeatureTile(it, Modifier.weight(1f)) }
            }
        }
    }
}

/** Circles lives under Profile; opened from Today, Back returns to Today (see MainShell). */
private fun openCircles(app: AppController, ranking: Boolean) {
    app.openCircleId = null; app.openCompetitionId = null; app.findOpen = false
    app.globalOpen = ranking
    app.aboutPage = null
    app.circlesReturnTab = Tab.Today
    app.tab = Tab.Profile
    app.circlesOpen = true
}

@Composable
private fun FeatureTile(f: Feature, modifier: Modifier) {
    Column(
        modifier.heightIn(min = 92.dp)
            .tactileClickable(RoundedCornerShape(20.dp), onClickLabel = f.label, onClick = f.onClick)
            .background(CardBg)
            .border(if (f.highlight) 2.dp else 1.dp, if (f.highlight) Haldi else CardLine, RoundedCornerShape(20.dp))
            .semantics(mergeDescendants = true) { contentDescription = "${f.label}, ${f.status}" }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box {
            Box(Modifier.size(40.dp).clip(CircleShape).background(f.tint), contentAlignment = Alignment.Center) {
                Icon(f.icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            }
            if (f.badge > 0) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(18.dp).clip(CircleShape).background(Coral),
                    contentAlignment = Alignment.Center) {
                    Text(if (f.badge > 9) "9+" else "${f.badge}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(f.label, style = MaterialTheme.typography.labelLarge, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
        Text(f.status, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
    }
}

/** Right-hand stat beside the smaller ring on the dark header. */
@Composable
fun HeroStat(label: String, value: String, hint: String, dot: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().tactileClickable(RoundedCornerShape(16.dp), onClickLabel = label, onClick = onClick)
        .padding(vertical = 6.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(label, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 1.dp))
        Text(hint, color = MutedOnDark, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
