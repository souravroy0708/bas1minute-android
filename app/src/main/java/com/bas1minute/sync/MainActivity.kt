package com.bas1minute.sync

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.bas1minute.sync.ui.Bas1MinuteTheme
import com.bas1minute.sync.ui.HomeActions
import com.bas1minute.sync.ui.HomeScreen
import com.bas1minute.sync.ui.HomeState
import com.bas1minute.sync.ui.OnboardingActions
import com.bas1minute.sync.ui.OnboardingScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private var snapshot by mutableStateOf(Snapshot())
    private var home by mutableStateOf(HomeState())
    private var onboarded by mutableStateOf(false)

    private val healthConnectPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { reload() }

    private val activityPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            lifecycleScope.launch {
                if (granted) StepsReader(this@MainActivity).subscribeRecording()
                reload()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        // People who set up the first version skip onboarding.
        if (prefs.isConfigured && !prefs.onboardingDone) prefs.onboardingDone = true
        onboarded = prefs.onboardingDone
        if (prefs.autoSync) SyncWorker.schedule(this)
        refreshHomeState()

        setContent {
            Bas1MinuteTheme {
                if (!onboarded) {
                    OnboardingScreen(
                        snapshot = snapshot,
                        savedKey = prefs.syncKey,
                        actions = OnboardingActions(
                            onSaveKey = { prefs.syncKey = it; refreshHomeState() },
                            onOpenWebsiteConnect = { openUrl("${prefs.serverUrl}/app/connect/") },
                            onAskHealthConnect = { healthConnectPermissions.launch(StepsReader.HC_PERMISSIONS) },
                            onInstallHealthConnect = { openHealthConnectInStore() },
                            onAskActivity = { askActivityPermission() },
                            onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                            onFinish = {
                                prefs.onboardingDone = true
                                onboarded = true
                                syncNow()
                            },
                        ),
                    )
                } else {
                    HomeScreen(
                        snapshot = snapshot,
                        state = home,
                        actions = HomeActions(
                            onSync = { syncNow() },
                            onToggleAuto = { on ->
                                prefs.autoSync = on
                                if (on) SyncWorker.schedule(this) else SyncWorker.cancel(this)
                                refreshHomeState()
                            },
                            onAskHealthConnect = { healthConnectPermissions.launch(StepsReader.HC_PERMISSIONS) },
                            onInstallHealthConnect = { openHealthConnectInStore() },
                            onAskActivity = { askActivityPermission() },
                            onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                            onOpenDashboard = { openUrl("${prefs.serverUrl}/app/") },
                            onOpenPrivacy = { startActivity(Intent(this, PrivacyActivity::class.java)) },
                            onChangeKey = { prefs.syncKey = it; refreshHomeState(); syncNow() },
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            snapshot = loadSnapshot(this@MainActivity)
            refreshHomeState()
        }
    }

    private fun syncNow() {
        if (home.syncing) return
        home = home.copy(syncing = true)
        lifecycleScope.launch {
            Sync.run(this@MainActivity)
            snapshot = loadSnapshot(this@MainActivity)
            refreshHomeState()
        }
    }

    private fun refreshHomeState() {
        home = HomeState(
            stepGoal = prefs.stepGoal,
            screenLimit = prefs.screenLimitMinutes,
            syncing = false,
            lastSyncAt = prefs.lastSyncAt,
            lastSyncMessage = prefs.lastSyncMessage,
            lastSyncOk = prefs.lastSyncOk,
            autoSync = prefs.autoSync,
            syncKeyPrefix = prefs.syncKey.take(12).takeIf { prefs.isConfigured } ?: "",
        )
    }

    private fun askActivityPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            lifecycleScope.launch { StepsReader(this@MainActivity).subscribeRecording(); reload() }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // No browser installed; nothing else to do.
        }
    }

    private fun openHealthConnectInStore() {
        val market = Uri.parse("market://details?id=${StepsReader.HC_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, market).setPackage("com.android.vending"))
        } catch (e: Exception) {
            openUrl("https://play.google.com/store/apps/details?id=${StepsReader.HC_PACKAGE}")
        }
    }
}
