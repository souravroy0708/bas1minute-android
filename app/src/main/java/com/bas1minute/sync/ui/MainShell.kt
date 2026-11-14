package com.bas1minute.sync.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Tab

private data class TabItem(val tab: Tab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(Tab.Today, "Today", Icons.Filled.Home),
    TabItem(Tab.Move, "Move", Icons.Filled.DirectionsRun),
    TabItem(Tab.Goals, "Goals", Icons.Filled.EmojiEvents),
    TabItem(Tab.Detox, "Detox", Icons.Filled.HourglassBottom),
    TabItem(Tab.Profile, "Profile", Icons.Filled.Person),
)

/** Five tabs: Today, Move (walks and runs), Circles, Detox (digital detox + screen-time insights), Profile. */
@Composable
fun MainShell(app: AppController, actions: PhoneActions, onShare: (String) -> Unit, onCall: (String) -> Unit) {
    // A required update replaces the whole app: see UpdateGate (MainActivity), which also covers setup and the lock
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(app.message) {
        app.message?.let {
            snackbar.showSnackbar(it)
            app.message = null
        }
    }
    // The app closed unexpectedly last time: offer to email the report (nothing is sent automatically)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var crash by remember { androidx.compose.runtime.mutableStateOf(com.bas1minute.sync.CrashLog.pending(ctx)) }
    crash?.let { report ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { com.bas1minute.sync.CrashLog.clear(ctx); crash = null },
            title = { Text("Bas 1 Minute closed unexpectedly") },
            text = { Text("Sorry about that. Sending the report helps us fix it quickly. You’ll see exactly what’s sent in your email app.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                com.bas1minute.sync.CrashLog.email(ctx, report); com.bas1minute.sync.CrashLog.clear(ctx); crash = null
            }) { Text("Send report") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { com.bas1minute.sync.CrashLog.clear(ctx); crash = null }) { Text("Not now") } },
        )
    }
    // Members from before everyone was in the global circle, who weren't sharing: invite once, one tap to join
    var invite by remember { androidx.compose.runtime.mutableStateOf(crash == null && !app.prefs.shareActivity && !app.prefs.globalInviteShown) }
    if (invite) androidx.compose.material3.AlertDialog(
        onDismissRequest = { app.prefs.globalInviteShown = true; invite = false },
        title = { Text("Everyone’s in Bas 1 Minute India now 🇮🇳") },
        text = { Text("All members are now in the all-India step ranking and its monthly challenges, no joining needed. " +
            "To be ranked, your daily step and screen-time totals are shared (never app names or location). You can leave any time in Circles.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            app.prefs.globalInviteShown = true; invite = false; app.setSharing(true); app.setGlobalVisible(true)
        }) { Text("Count me in") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { app.prefs.globalInviteShown = true; invite = false }) { Text("Not now") } },
    )
    // Reinstalled on the same phone: say so, so nobody thinks their circles were lost
    LaunchedEffect(Unit) {
        com.bas1minute.sync.api.Welcome.back.collect { name ->
            if (name != null) {
                com.bas1minute.sync.api.Welcome.back.value = null
                app.message = "Welcome back, $name. Your circles, ranking and shared history are back."
                app.refreshCircles()
            }
        }
    }
    // A walk/run was just saved (from the screen or the notification): show its summary
    LaunchedEffect(Unit) {
        com.bas1minute.sync.track.LiveTrack.justFinished.collect { id ->
            if (id != null) {
                com.bas1minute.sync.track.LiveTrack.justFinished.value = null
                app.trackVersion++
                app.tab = Tab.Move
                app.trackDetailId = id
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Ink) {
                TABS.forEach { item ->
                    NavigationBarItem(
                        selected = app.tab == item.tab,
                        onClick = {
                            if (item.tab == Tab.Today) { app.logOpen = false; app.burstOpen = false; app.calmOpen = false; app.spinOpen = false }
                            if (item.tab == Tab.Move && app.tab == Tab.Move) app.trackDetailId = null
                            if (item.tab == Tab.Profile) { app.aboutPage = null; app.circlesOpen = false }
                            if (item.tab == Tab.Detox) app.phoneCheckOpen = false
                            app.tab = item.tab
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(t(item.label), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink, selectedTextColor = Haldi, indicatorColor = Haldi,
                            unselectedIconColor = MutedOnDark, unselectedTextColor = MutedOnDark,
                        ),
                    )
                }
            }
        },
        containerColor = Paper,
        // The tabs draw their own status-bar space; the bar handles the bottom.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        ShareSheet(app, actions.onShareImage)
        WeekReviewStory(app)
        // Fade-through between tabs and their full screens (burst, calm, log, circle)
        val screen = "${app.tab}:${app.burstOpen}:${app.calmOpen}:${app.logOpen}:${app.openCircleId}:${app.aboutPage}:${app.spinOpen}:${app.phoneCheckOpen}:${app.detoxView}:${app.goalsHubView}:${app.circlesOpen}"
        androidx.compose.animation.AnimatedContent(
            targetState = screen,
            transitionSpec = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
            },
            label = "screens",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { key ->
        // Each side of the fade draws from its own snapshot, so old and new screens really cross-fade
        val part = key.split(":")
        val shownTab = com.bas1minute.sync.Tab.valueOf(part[0])
        Box(Modifier.fillMaxSize()) {
            when (shownTab) {
                Tab.Today -> when {
                    part[6] == "true" -> {
                        androidx.activity.compose.BackHandler { app.spinOpen = false }
                        WheelScreen(app, onClose = { app.spinOpen = false })
                    }
                    part[2] == "true" -> {
                        androidx.activity.compose.BackHandler { app.calmOpen = false }
                        CalmTab(app, onCall, onClose = { app.calmOpen = false })
                    }
                    part[1] == "true" -> {
                        androidx.activity.compose.BackHandler { app.burstOpen = false }
                        BurstScreen(app, onClose = { app.burstOpen = false })
                    }
                    part[3] == "true" -> {
                        androidx.activity.compose.BackHandler { app.logOpen = false }
                        LogTab(app, onClose = { app.logOpen = false })
                    }
                    else -> TodayTab(app, onCall, actions)
                }
                Tab.Detox -> if (part[7] == "true") PhoneCheckScreen(app, actions, onClose = { app.phoneCheckOpen = false })
                    else DetoxHub(app, actions, part[8])
                Tab.Goals -> GoalsHub(app, actions, part[9])
                Tab.Move -> MoveTab(app, actions)
                Tab.Profile -> if (part[10] == "true") {
                    // Circles (Bas 1 Minute India, private circles, competitions) lives here now, one tap from Profile
                    val atCircleRoot = app.openCompetitionId == null && !app.globalOpen && !app.findOpen && app.openCircleId == null
                    if (atCircleRoot) androidx.activity.compose.BackHandler { app.circlesOpen = false }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().background(Ink).padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.IconButton(onClick = { app.circlesOpen = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Text(t("Circles"), color = Color.White, style = MaterialTheme.typography.titleLarge)
                        }
                        Box(Modifier.weight(1f)) { CirclesTab(app, onShare, actions.onShareWhatsApp) }
                    }
                } else app.aboutPage?.let { page ->
                    androidx.activity.compose.BackHandler { app.aboutPage = null }
                    if (page == "help") PhoneHelpScreen(app, actions, onBack = { app.aboutPage = null })
                    else if (page == "web") WebSignInScreen(app, onBack = { app.aboutPage = null })
                    else AboutScreen(page, onBack = { app.aboutPage = null })
                } ?: ProfileTab(app, actions)
            }
        }
        }
    }
}

