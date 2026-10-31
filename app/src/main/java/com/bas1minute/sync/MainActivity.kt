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
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.bas1minute.sync.ui.LockScreen
import com.bas1minute.sync.ui.SetupScreen
import com.bas1minute.sync.ui.Bas1MinuteTheme
import com.bas1minute.sync.ui.HelpActions
import com.bas1minute.sync.ui.MainShell
import com.bas1minute.sync.ui.OnboardingActions
import com.bas1minute.sync.ui.PermissionsScreen
import com.bas1minute.sync.ui.PhoneActions
import kotlinx.coroutines.launch

/** Login → phone permissions (once) → the five tabs. Everything shown works offline from saved data. */
class MainActivity : ComponentActivity() {

    private lateinit var app: AppController

    private val healthConnectPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { app.refresh() }

    private val activityPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            lifecycleScope.launch {
                if (granted) StepsReader(this@MainActivity).subscribeRecording()
                app.refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        app = AppController(applicationContext, lifecycleScope)
        handleLink(intent)
        if (app.prefs.autoSync) SyncWorker.schedule(this)

        val help = HelpActions(
            onSearch = { query -> searchGoogle(query) },
            onCopy = { text -> copyText(text) },
        )
        val phone = PhoneActions(
            help = help,
            onOpenBatterySettings = { openBatterySettings() },
            onOpenAppInfo = {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            },
            onOpenSamsungHealth = { if (!SamsungHealth.open(this)) app.message = "Couldn’t open Samsung Health. Open it from your home screen." },
            onAskHealthConnect = { healthConnectPermissions.launch(StepsReader.HC_PERMISSIONS) },
            onInstallHealthConnect = { openHealthConnectInStore() },
            onAskActivity = { askActivityPermission() },
            onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            onShareWhatsApp = { shareWhatsApp(it) },
        )

        setContent {
            Bas1MinuteTheme {
                when {
                    !app.setupDone -> SetupScreen(app)
                    !app.unlocked -> LockScreen(app)
                    !app.permissionsDone -> PermissionsScreen(
                        snapshot = app.phone,
                        actions = OnboardingActions(
                            help = help,
                            onOpenAppInfo = phone.onOpenAppInfo,
                            onOpenSamsungHealth = phone.onOpenSamsungHealth,
                            onAskHealthConnect = phone.onAskHealthConnect,
                            onInstallHealthConnect = phone.onInstallHealthConnect,
                            onAskActivity = phone.onAskActivity,
                            onOpenUsageAccess = phone.onOpenUsageAccess,
                            onFinish = { app.finishPermissions() },
                        ),
                    )
                    else -> MainShell(app, phone, onShare = { share(it) }, onCall = { call(it) })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /** https://<site>/join/CODE/ or bas1minute://join/CODE → join that circle. */
    private fun handleLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val code = when (uri.scheme) {
            "bas1minute" -> uri.pathSegments.firstOrNull()
            "https", "http" -> uri.pathSegments.takeIf { it.size >= 2 && it[0] == "join" }?.get(1)
            else -> null
        } ?: return
        intent.data = null   // don't join twice on rotation
        app.joinFromLink(code)
    }

    override fun onResume() {
        super.onResume()
        app.refresh()
    }

    private fun askActivityPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            lifecycleScope.launch { StepsReader(this@MainActivity).subscribeRecording(); app.refresh() }
        }
    }

    /** Only used for Google Play (installing Health Connect). The app never opens the Bas 1 Minute website. */
    private fun openStore(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { app.message = "Open the Play Store and search for Health Connect." }
    }

    /** Phone-specific help: a Google search for this exact phone (Google app or browser). */
    private fun searchGoogle(query: String) {
        try {
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query))
        } catch (e: Exception) {
            openStore("https://www.google.com/search?q=" + Uri.encode(query))
        }
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Bas 1 Minute help", text))
        app.message = "Question copied. Paste it into Google, Gemini or ChatGPT."
    }

    /** Battery options live in App info on most phones; the optimisation list is the fallback. */
    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e2: Exception) { app.message = "Open Settings → Battery." }
        }
    }

    /** Straight into WhatsApp (or WhatsApp Business); falls back to the share sheet. */
    private fun shareWhatsApp(text: String) {
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                startActivity(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg))
                return
            } catch (e: Exception) { /* not installed, try the next */ }
        }
        share(text)
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Share invite"))
    }

    /** Opens the dialer with the number filled in; the person taps call themselves. */
    private fun call(number: String) {
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) } catch (e: Exception) { app.message = "Call $number from your phone app." }
    }

    private fun openHealthConnectInStore() {
        val market = Uri.parse("market://details?id=${StepsReader.HC_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, market).setPackage("com.android.vending"))
        } catch (e: Exception) {
            openStore("https://play.google.com/store/apps/details?id=${StepsReader.HC_PACKAGE}")
        }
    }
}
