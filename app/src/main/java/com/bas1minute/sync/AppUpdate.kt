package com.bas1minute.sync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.bas1minute.sync.api.Api
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/**
 * Everyone on the latest version.
 *
 * Policy (ALWAYS_LATEST): every release published on Google Play is required. As soon as Play can deliver a newer
 * version to this phone, the app is replaced by "Time to update" and Play's full-screen update starts by itself.
 *
 * Signals, combined so one can never undo another:
 *  - Google Play in-app updates: the source of truth for Play installs. Play only reports an update once the
 *    release (including a staged rollout) has reached this phone, so nobody is blocked by an update they can't get.
 *  - The server (Admin → App versions): `min_version_code` is a kill switch that blocks everyone below it, Play or
 *    not (the API also answers 426). `latest_version_code` forces phones that didn't install from Play (test APKs).
 *
 * Not forced mid-activity: while a walk is recording or a detox is on, the update waits until it ends
 * (installing restarts the app). Offline, the app can't know, so it keeps working.
 */
object AppUpdate {
    enum class Status { Current, Available, Required }

    /** Every new Play release must be installed to keep using the app. Set false to go back to "Update available". */
    const val ALWAYS_LATEST = true

    val status = MutableStateFlow(Status.Current)
    private var fromServer = Status.Current
    private var fromPlay = Status.Current
    /** Installed from Google Play: Play decides when an update is really available. */
    var playInstall = false
        private set

    @Suppress("DEPRECATION")
    fun init(context: android.content.Context) {
        playInstall = try {
            val pm = context.packageManager
            val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            else pm.getInstallerPackageName(context.packageName)
            installer == "com.android.vending"
        } catch (e: Exception) { false }
    }

    private fun publish() { status.value = maxOf(fromServer, fromPlay) }

    fun applyPlay(s: Status) { fromPlay = s; publish() }
    var message: String = "A new version of Bas 1 Minute is ready with fixes and improvements."
        private set
    var storeUrl: String = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        private set

    private val mine = BuildConfig.VERSION_CODE

    /** From GET info/: {"app": {"latest_version_code", "min_version_code", "message", "store_url"}}. */
    fun applyServer(app: JSONObject?) {
        app ?: return
        app.optString("message").takeIf { it.isNotBlank() }?.let { message = it }
        app.optString("store_url").takeIf { it.startsWith("https://") }?.let { storeUrl = it }
        val min = app.optInt("min_version_code", 0)
        val latest = app.optInt("latest_version_code", 0)
        fromServer = when {
            mine < min -> Status.Required
            // Play installs wait for Play to confirm (the server may be updated before the rollout reaches them)
            mine < latest && ALWAYS_LATEST && !playInstall -> Status.Required
            mine < latest -> Status.Available
            else -> Status.Current
        }
        publish()
    }

    /** Any API call answered 426: this version is no longer allowed. */
    fun requireFromServer(json: JSONObject?) {
        json?.optString("store_url")?.takeIf { it.startsWith("https://") }?.let { storeUrl = it }
        fromServer = Status.Required
        publish()
    }

    suspend fun checkServer(api: Api) {
        val r = api.post("info/")
        if (r.ok) applyServer(r.json?.optJSONObject("app"))
    }
}

/**
 * Runs Google Play's update flow for one activity. Create it as an activity property: the result launcher must be
 * registered before the activity starts. The Play manager itself is created on first use, because the activity has
 * no context yet while its properties are being set up (creating it there crashed the app on launch).
 */
class PlayUpdater(private val activity: ComponentActivity) {
    private val manager by lazy { AppUpdateManagerFactory.create(activity.applicationContext) }
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Backed out of Play's screen: the "Time to update" screen stays, and its button opens Play again
        if (result.resultCode != Activity.RESULT_OK && AppUpdate.status.value == AppUpdate.Status.Required) Unit
    }

    /** Started Play's update screen by itself once in this run; after that, the "Update now" button does it. */
    private var autoStarted = false

    /**
     * Asks Play on every resume. A newer release that Play can deliver to this phone makes the update required
     * (ALWAYS_LATEST) and opens Play's update screen straight away, unless [busy] (a walk or a detox is running).
     */
    fun checkPlay(busy: () -> Boolean = { false }) = guard {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            AppUpdate.applyPlay(when {
                !available -> AppUpdate.Status.Current
                // Every release is required; without the policy, Play priority 4-5 (set at upload) still is
                AppUpdate.ALWAYS_LATEST || info.updatePriority() >= 4 -> AppUpdate.Status.Required
                else -> AppUpdate.Status.Available
            })
            if (available && AppUpdate.status.value == AppUpdate.Status.Required && !autoStarted && !busy() &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                autoStarted = true
                start(info)
            }
        }
    }

    /** On resume: an update started earlier must finish (Play shows its screen again). */
    fun resumeIfNeeded() = guard {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) start(info)
        }
    }

    /** "Update now": Play's in-place update, or the Play listing when that isn't possible. */
    fun update() = guard(orElse = { openStore() }) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) start(info)
                else openStore()
            }
            .addOnFailureListener { openStore() }
    }

    /** Play services missing, old or busy must never crash the app: updates are a nice-to-have at runtime. */
    private inline fun guard(orElse: () -> Unit = {}, block: () -> Unit) {
        try { block() } catch (e: Exception) { orElse() }
    }

    private fun start(info: AppUpdateInfo) {
        try {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        } catch (e: Exception) {
            openStore()
        }
    }

    private fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}")).setPackage("com.android.vending")
        try { activity.startActivity(market) } catch (e: Exception) {
            try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdate.storeUrl))) } catch (x: Exception) { }
        }
    }
}
