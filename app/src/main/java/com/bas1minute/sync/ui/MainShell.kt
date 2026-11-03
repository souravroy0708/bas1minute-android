package com.bas1minute.sync.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Tab

private data class TabItem(val tab: Tab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(Tab.Today, "Today", Icons.Filled.Home),
    TabItem(Tab.Insights, "Insights", Icons.Filled.Insights),
    TabItem(Tab.Circles, "Circles", Icons.Filled.Groups),
    TabItem(Tab.Spin, "Spin", Icons.Filled.Casino),
    TabItem(Tab.Profile, "Profile", Icons.Filled.Person),
)

/** Five tabs: Today (with Log a day), Insights, Circles, Calm, Profile. */
@Composable
fun MainShell(app: AppController, actions: PhoneActions, onShare: (String) -> Unit, onCall: (String) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(app.message) {
        app.message?.let {
            snackbar.showSnackbar(it)
            app.message = null
        }
    }
    // A walk/run was just saved (from the screen or the notification): show its summary
    LaunchedEffect(Unit) {
        com.bas1minute.sync.track.LiveTrack.justFinished.collect { id ->
            if (id != null) {
                com.bas1minute.sync.track.LiveTrack.justFinished.value = null
                app.trackVersion++
                app.tab = Tab.Today
                app.trackDetailId = id
                app.trackOpen = true
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
                            if (item.tab == Tab.Circles && app.tab == Tab.Circles) { app.closeCircle(); app.globalOpen = false; app.findOpen = false }
                            if (item.tab == Tab.Today) { app.logOpen = false; app.burstOpen = false; app.calmOpen = false; app.trackOpen = false }
                            if (item.tab == Tab.Profile) app.aboutPage = null
                            if (item.tab == Tab.Insights) app.phoneCheckOpen = false
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
        val screen = "${app.tab}:${app.burstOpen}:${app.calmOpen}:${app.logOpen}:${app.openCircleId}:${app.aboutPage}:${app.trackOpen}:${app.phoneCheckOpen}"
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
                    part[6] == "true" -> TrackScreen(app, actions, onClose = { app.trackOpen = false; app.trackDetailId = null })
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
                Tab.Insights -> if (part[7] == "true") PhoneCheckScreen(app, actions, onClose = { app.phoneCheckOpen = false }) else InsightsTab(app)
                Tab.Circles -> CirclesTab(app, onShare, actions.onShareWhatsApp)
                Tab.Spin -> WheelScreen(app, onClose = null)
                Tab.Profile -> app.aboutPage?.let { page ->
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

