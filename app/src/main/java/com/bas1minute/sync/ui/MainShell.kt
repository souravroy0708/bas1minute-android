package com.bas1minute.sync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
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
    TabItem(Tab.Log, "Log", Icons.Filled.Add),
    TabItem(Tab.Circles, "Circles", Icons.Filled.Face),
    TabItem(Tab.Calm, "Calm", Icons.Filled.Favorite),
    TabItem(Tab.Profile, "Profile", Icons.Filled.Person),
)

/** The five tabs, same as the website's app: Today, Log, Circles, Calm, Profile. */
@Composable
fun MainShell(app: AppController, actions: PhoneActions, onShare: (String) -> Unit, onCall: (String) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(app.message) {
        app.message?.let {
            snackbar.showSnackbar(it)
            app.message = null
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
                            if (item.tab == Tab.Circles && app.tab == Tab.Circles) app.closeCircle()
                            app.tab = item.tab
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (app.tab) {
                Tab.Today -> TodayTab(app, onCall)
                Tab.Log -> LogTab(app)
                Tab.Circles -> CirclesTab(app, onShare, actions.onOpenUrl)
                Tab.Calm -> CalmTab(app, onCall)
                Tab.Profile -> ProfileTab(app, actions)
            }
        }
    }
}

