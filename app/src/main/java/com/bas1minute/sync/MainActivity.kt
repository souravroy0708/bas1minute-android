package com.bas1minute.sync

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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

    /** Android photo picker: no storage permission needed; the photo is copied into the app’s private storage. */
    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ProfilePhoto.save(applicationContext, uri) }
                if (ok) { app.reloadAvatar(); app.message = "Profile photo updated." } else app.message = "Couldn’t use that photo. Try another."
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) app.message = "Notifications are off. You can allow them in Android Settings."
            Push.refreshToken(applicationContext)
        }

    /** Ask once (Android 13+), then register this phone for motivation pushes. */
    private fun askNotifications() {
        app.prefs.notifAsked = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else Push.refreshToken(applicationContext)
    }

    // ---------- GPS walk / run ----------

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            app.refreshLocationPermission()
            when {
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true -> checkGpsThenCountdown()
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ->
                    app.message = "Choose “Precise” location so your route is accurate (Android Settings → Apps → Bas 1 Minute → Location)."
                else -> app.message = "Location is needed to map your walk. It stays on this phone."
            }
        }

    /** Android's "Turn on location?" dialog. */
    private val gpsSwitch =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == RESULT_OK) app.trackCountdown = true
            else app.message = "Turn on location (GPS) to record your route."
        }

    private var pendingGpx: String? = null
    private val gpxSaver =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
            val text = pendingGpx; pendingGpx = null
            if (uri == null || text == null) return@registerForActivityResult
            app.message = try {
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                "GPS file saved."
            } catch (e: Exception) { "Couldn’t save the file. Try another folder." }
        }

    private var pendingImage: android.graphics.Bitmap? = null
    private val imageSaver =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            val bmp = pendingImage; pendingImage = null
            if (uri == null || bmp == null) return@registerForActivityResult
            app.message = try {
                contentResolver.openOutputStream(uri)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                "Picture saved."
            } catch (e: Exception) { "Couldn’t save the picture." }
        }

    /** Start pressed: precise location → GPS switched on → (notifications, once) → 3-2-1. */
    private fun readyToTrack() {
        if (!com.bas1minute.sync.track.TrackingService.hasLocationPermission(this)) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        // Shows distance and the Pause / Finish buttons while the screen is off
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !app.prefs.notifAsked) askNotifications()
        checkGpsThenCountdown()
    }

    private fun checkGpsThenCountdown() {
        val request = com.google.android.gms.location.LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        val settings = com.google.android.gms.location.LocationSettingsRequest.Builder().addLocationRequest(request).setAlwaysShow(true).build()
        com.google.android.gms.location.LocationServices.getSettingsClient(this).checkLocationSettings(settings)
            .addOnSuccessListener { app.trackCountdown = true }
            .addOnFailureListener { e ->
                if (e is com.google.android.gms.common.api.ResolvableApiException) {
                    try { gpsSwitch.launch(androidx.activity.result.IntentSenderRequest.Builder(e.resolution).build()) }
                    catch (x: Exception) { app.message = "Turn on location in quick settings, then tap Start." }
                } else {
                    // Can't check (no Play services settings): try anyway; the GPS chip shows if it works
                    app.trackCountdown = true
                }
            }
    }

    /** Pictures go to Gallery → Pictures/Bas 1 Minute (no permission needed on Android 10+). */
    private fun saveImage(bitmap: android.graphics.Bitmap, fileName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { pendingImage = bitmap; imageSaver.launch(fileName); return }
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Bas 1 Minute")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            values.clear(); values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            app.message = "Saved to Gallery → Pictures → Bas 1 Minute."
        } catch (e: Exception) {
            app.message = "Couldn’t save the picture. Try Share instead."
        }
    }

    private fun saveGpx(fileName: String, gpx: String) {
        pendingGpx = gpx
        try { gpxSaver.launch(fileName) } catch (e: Exception) { app.message = "No app to save files on this phone." }
    }

    private val activityPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            lifecycleScope.launch {
                if (granted) StepsReader(this@MainActivity).subscribeRecording()
                app.refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        app = AppController(applicationContext, lifecycleScope)
        handleLink(intent)
        if (app.prefs.autoSync) SyncWorker.schedule(this)
        // Battery level every ~15 min for Phone check (phone only, no network)
        com.bas1minute.sync.phone.BatteryWorker.schedule(this)

        val help = HelpActions(
            onSearch = { query -> searchGoogle(query) },
            onCopy = { text -> copyText(text) },
        )
        val phone = PhoneActions(
            onPickPhoto = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onShareImage = { bmp, caption -> shareImage(bmp, caption) },
            onAskNotifications = { askNotifications() },
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
            track = com.bas1minute.sync.ui.TrackActions(
                onReadyToStart = { readyToTrack() },
                onSaveImage = { bmp, name -> saveImage(bmp, name) },
                onSaveGpx = { name, gpx -> saveGpx(name, gpx) },
                onOpenAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) },
            ),
        )

        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            com.bas1minute.sync.ui.Lang.current = app.language
            Bas1MinuteTheme(dark = when (app.theme) { "dark" -> true; "light" -> false; else -> systemDark }) {
                when {
                    !app.setupDone -> SetupScreen(app)
                    !app.unlocked -> LockScreen(app)
                    false -> PermissionsScreen(
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
        // Tapped the walk/run notification
        if (intent?.getBooleanExtra(com.bas1minute.sync.track.TrackingService.EXTRA_OPEN_TRACK, false) == true) {
            intent?.removeExtra(com.bas1minute.sync.track.TrackingService.EXTRA_OPEN_TRACK)
            app.tab = Tab.Today
            app.trackOpen = true
        }
        // Tapped the Phone check evening alert
        if (intent?.getBooleanExtra(com.bas1minute.sync.phone.BatteryWorker.EXTRA_OPEN, false) == true) {
            intent?.removeExtra(com.bas1minute.sync.phone.BatteryWorker.EXTRA_OPEN)
            app.tab = Tab.Insights
            app.phoneCheckOpen = true
        }
        val uri = intent?.data ?: return
        // Website sign-in QR: https://<site>/link/CODE/ or bas1minute://link/CODE → approve screen with the code
        val webCode = when (uri.scheme) {
            "bas1minute" -> uri.pathSegments.firstOrNull()?.takeIf { uri.host == "link" }
            "https", "http" -> uri.pathSegments.takeIf { it.size >= 2 && it[0] == "link" }?.get(1)
            else -> null
        }
        if (webCode != null) {
            intent.data = null
            if (app.setupDone) app.openWebSignIn(webCode) else app.message = "Finish setting up the app, then scan the code again."
            return
        }
        if (uri.scheme == "bas1minute" && uri.host != "join") return
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
        // After setup: ask for notifications once, and keep this phone's push token current.
        if (app.setupDone && app.permissionsDone && app.prefs.pushEnabled) {
            if (!app.prefs.notifAsked) askNotifications() else Push.refreshToken(applicationContext)
        }
        app.refreshLocationPermission()
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

    /** Save the card to the app's private cache and hand it to WhatsApp / Instagram / Facebook via the share sheet. */
    private fun shareImage(bitmap: android.graphics.Bitmap, caption: String) {
        try {
            val dir = java.io.File(cacheDir, "share").apply { mkdirs() }
            val file = java.io.File(dir, "bas1minute.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, caption)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Share your achievement"))
        } catch (e: Exception) {
            app.message = "Couldn’t share the image. Try again."
        }
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
