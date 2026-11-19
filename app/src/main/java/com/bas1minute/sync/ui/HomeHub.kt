package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import com.bas1minute.sync.AppFeature
import androidx.compose.ui.composed
import androidx.compose.foundation.combinedClickable
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
private data class HubStatus(val goalsMet: Int = 0, val goalsTotal: Int = 0, val diaryDone: Boolean = false, val detoxLeftMs: Long? = null, val badges: Int = 0)

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
            val badges = runCatching { com.bas1minute.sync.achieve.AchievementStore.badges(ctx).count { it.earnedOn != null } }.getOrDefault(0)
            HubStatus(goals.first, goals.second, diary, detox, badges)
        }
    }
    return status
}

private class Tile(
    val f: AppFeature, val icon: ImageVector, val status: String, val tint: Color,
    val badge: Int = 0, val highlight: Boolean = false,
)

/**
 * Home grid, by importance:
 *  1. Circles and Goals — two big cards with a line saying what they're for (people stay for these two);
 *  2. eight everyday features, four to a row;
 *  3. Water and Log — the quickest, least important — as two slim buttons at the end.
 * Each tile shows one live number. A small "New" dot marks features not tried yet (first two weeks), and a
 * long-press on any tile explains it — so nobody has to guess where things are.
 */
@Composable
fun FeatureGrid(app: AppController, bursts: Int, burstGoal: Int, calm: Int, water: Int, socialMinutes: Int?) {
    val ctx = LocalContext.current
    val s = rememberHubStatus(app)
    val live by LiveTrack.state.collectAsState()
    val cheers = app.today?.cheers?.size ?: 0
    val circles = app.circles?.circles?.size ?: app.today?.circleCount ?: 0
    val firstWeeks = remember {
        val installed = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime }.getOrDefault(0L)
        System.currentTimeMillis() - installed < 14L * 24 * 3600_000L
    }
    // Anything already set up counts as "used", even if it wasn't opened from here
    LaunchedEffect(circles, s.goalsTotal, s.diaryDone) {
        if (circles > 0) app.prefs.markFeatureUsed(AppFeature.CIRCLES.key)
        if (s.goalsTotal > 0) app.prefs.markFeatureUsed(AppFeature.GOALS.key)
        if (s.diaryDone) app.prefs.markFeatureUsed(AppFeature.DIARY.key)
        app.usedFeatures = app.prefs.usedFeatures
    }
    var explain by remember { mutableStateOf<Tile?>(null) }
    fun isNew(f: AppFeature) = firstWeeks && f.key !in app.usedFeatures

    val circlesTile = Tile(AppFeature.CIRCLES, Icons.Filled.Groups,
        when { !app.cloudOn -> t("Turn on to join"); cheers > 0 -> "$cheers ${t("new cheers")}"; circles > 0 -> "$circles ${t("circles")}"; else -> t("Start one with family") },
        Color(0xFFFFE3EC), badge = cheers)
    val goalsTile = Tile(AppFeature.GOALS, Icons.Filled.Flag,
        if (s.goalsTotal == 0) t("Set your first goal") else "${s.goalsMet} of ${s.goalsTotal} ${t("met today")}", Color(0xFFFFF3C8))
    val everyday = listOf(
        Tile(AppFeature.WALK, Icons.Filled.DirectionsRun, if (live.active) t("On now") else t("Start"), Color(0xFFDFF5EA), highlight = live.active),
        Tile(AppFeature.DETOX, Icons.Filled.HourglassBottom, s.detoxLeftMs?.let { "${DetoxLogic.formatDuration(it)} ${t("left")}" } ?: t("Start"),
            Color(0xFFE9ECFF), highlight = s.detoxLeftMs != null),
        Tile(AppFeature.DIARY, Icons.Filled.AutoStories, if (s.diaryDone) t("Done today") else t("Reflect"), Color(0xFFE9D9FF)),
        Tile(AppFeature.BURST, Icons.Filled.Bolt, "$bursts/$burstGoal", Haldi),
        Tile(AppFeature.SCREEN, Icons.Filled.Insights, socialMinutes?.let { "${hm(it)} ${t("social")}" } ?: t("See it"), Color(0xFFFFF3C8)),
        Tile(AppFeature.HISTORY, Icons.Filled.Leaderboard, if (s.badges > 0) "${s.badges} ${t("badges")}" else t("All time"), Color(0xFFDFF5EA)),
        Tile(AppFeature.CALM, Icons.Filled.SelfImprovement, "$calm min", Color(0xFFFFE7DF)),
        Tile(AppFeature.PHONE, Icons.Filled.PhoneAndroid, t("Battery, apps"), Color(0xFFDDF0FD)),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(circlesTile, goalsTile).forEach { tile ->
                PrimaryCard(tile, isNew(tile.f), Modifier.weight(1f), onLong = { explain = tile }) { app.openFeature(tile.f) }
            }
        }
        everyday.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> FeatureTile(tile, isNew(tile.f), Modifier.weight(1f), onLong = { explain = tile }) { app.openFeature(tile.f) } }
            }
        }
        // Quickest, least important: one tap each
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickButton(Icons.Filled.WaterDrop, "${t("Water")} · " + (if (water >= 1000) "%.1f L".format(water / 1000.0) else "$water ml"), "+1",
                Modifier.weight(1f)) { app.openFeature(AppFeature.WATER) }
            QuickButton(Icons.Filled.EditNote, t("Log your day"), null, Modifier.weight(1f)) { app.openFeature(AppFeature.LOG) }
        }
        if (firstWeeks) Text(t("New here? Press and hold any tile to see what it does."),
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(horizontal = 4.dp))
    }

    explain?.let { tile ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { explain = null },
            icon = {
                Box(Modifier.size(48.dp).clip(CircleShape).background(tile.tint), contentAlignment = Alignment.Center) {
                    Icon(tile.icon, contentDescription = null, tint = Ink)
                }
            },
            title = { Text(t(tile.f.title)) },
            text = { Text(tile.f.what, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { explain = null; app.openFeature(tile.f) }) { Text(t("Open"), color = Ink) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { explain = null }) { Text(t("Close"), color = Muted) } },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.tileClick(label: String, onLong: () -> Unit, onClick: () -> Unit): Modifier = composed {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    this.clip(RoundedCornerShape(22.dp)).combinedClickable(
        role = Role.Button, onClickLabel = label, onLongClickLabel = t("What is this?"),
        onLongClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLong() },
        onClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onClick() },
    )
}

@Composable
private fun NewDot(modifier: Modifier) {
    Text(t("New"), color = Ink, style = MaterialTheme.typography.labelSmall,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Haldi).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** Big card for the two most important features: icon, name, live status and what it's for. */
@Composable
private fun PrimaryCard(tile: Tile, isNew: Boolean, modifier: Modifier, onLong: () -> Unit, onClick: () -> Unit) {
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 148.dp)
                .tileClick(tile.f.title, onLong, onClick)
                .background(CardBg)
                .border(1.dp, CardLine, RoundedCornerShape(22.dp))
                .semantics(mergeDescendants = true) { contentDescription = "${tile.f.title}, ${tile.status}" }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                Box(Modifier.size(44.dp).clip(CircleShape).background(tile.tint), contentAlignment = Alignment.Center) {
                    Icon(tile.icon, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
                }
                if (tile.badge > 0) CountBadge(tile.badge, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp))
            }
            Text(t(tile.f.title), style = MaterialTheme.typography.titleLarge, color = TextMain, maxLines = 1)
            Text(tile.status, style = MaterialTheme.typography.labelLarge, color = Blue, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tile.f.what, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (isNew) NewDot(Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

@Composable
private fun FeatureTile(tile: Tile, isNew: Boolean, modifier: Modifier, onLong: () -> Unit, onClick: () -> Unit) {
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 96.dp)
                .tileClick(tile.f.title, onLong, onClick)
                .background(CardBg)
                .border(if (tile.highlight) 2.dp else 1.dp, if (tile.highlight) Haldi else CardLine, RoundedCornerShape(22.dp))
                .semantics(mergeDescendants = true) { contentDescription = "${tile.f.title}, ${tile.status}" }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tile.tint), contentAlignment = Alignment.Center) {
                Icon(tile.icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            }
            // Smaller label so "Screen time" and "Phone check" fit on one line
            Text(t(tile.f.title), style = MaterialTheme.typography.labelMedium, color = TextMain, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(tile.status, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center)
        }
        if (isNew) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(9.dp).clip(CircleShape).background(Haldi))
    }
}

@Composable
private fun CountBadge(n: Int, modifier: Modifier) {
    Box(modifier.size(18.dp).clip(CircleShape).background(Coral), contentAlignment = Alignment.Center) {
        Text(if (n > 9) "9+" else "$n", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** Slim one-tap button for Water and Log. */
@Composable
private fun QuickButton(icon: ImageVector, label: String, trailing: String?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 52.dp).tactileClickable(RoundedCornerShape(50), onClickLabel = label, onClick = onClick)
            .background(CardBg).border(1.dp, CardLine, RoundedCornerShape(50)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = Ink,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Haldi).padding(horizontal = 8.dp, vertical = 2.dp))
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


/** Shown instead of a feature that needs the internet while Online features are off. */
@Composable
fun OnlineNeeded(app: AppController, what: String, why: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(what, style = MaterialTheme.typography.headlineMedium, color = TextMain)
        Text(why, style = MaterialTheme.typography.bodyLarge, color = Muted)
        Text("This part needs Online features, which are off: right now nothing leaves your phone, and everything else " +
            "works without internet. Turning them on makes a name-only account and backs up your daily totals — never " +
            "app names, location, diary or goals. You can turn them off again any time.",
            style = MaterialTheme.typography.bodyMedium, color = Muted)
        BigButton(t("Turn on in Profile")) {
            app.circlesOpen = false; app.circlesReturnTab = null
            app.tab = Tab.Profile
        }
    }
}
