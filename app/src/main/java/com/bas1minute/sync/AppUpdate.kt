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
 * Keeping everyone on a supported version.
 *
 * Two signals, combined:
 *  - The server (Admin → App versions): `min_version_code` makes older versions MUST update (blocking screen),
 *    `latest_version_code` shows "Update available". The API also answers 426 to versions below the minimum.
 *  - Google Play in-app updates: installs the update in place with Play's own full-screen flow.
 *    Phones that didn't install from Play (or have no Play Store) get the Play listing instead.
 */
object AppUpdate {
    enum class Status { Current, Available, Required }

    val status = MutableStateFlow(Status.Current)
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
        status.value = when {
            mine < min -> Status.Required
            mine < latest -> Status.Available
            else -> Status.Current
        }
    }

    /** Any API call answered 426: this version is no longer allowed. */
    fun requireFromServer(json: JSONObject?) {
        json?.optString("store_url")?.takeIf { it.startsWith("https://") }?.let { storeUrl = it }
        status.value = Status.Required
    }

    suspend fun checkServer(api: Api) {
        val r = api.post("info/")
        if (r.ok) applyServer(r.json?.optJSONObject("app"))
    }
}

/** Runs Google Play's update flow for one activity. Create it as a property (it registers a result launcher). */
class PlayUpdater(private val activity: ComponentActivity) {
    private val manager = AppUpdateManagerFactory.create(activity)
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Cancelled a required update: the blocking screen stays and offers it again
        if (result.resultCode != Activity.RESULT_OK && AppUpdate.status.value == AppUpdate.Status.Required) Unit
    }

    /** Also asks Play directly, so a published update is noticed even before the server minimum is raised. */
    fun checkPlay() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && AppUpdate.status.value == AppUpdate.Status.Current) {
                AppUpdate.status.value = AppUpdate.Status.Available
            }
            // Play marks high-priority releases (priority 4-5 set at upload): treat them as required
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.updatePriority() >= 4) {
                AppUpdate.status.value = AppUpdate.Status.Required
            }
        }
    }

    /** On resume: an update started earlier must finish (Play shows its screen again). */
    fun resumeIfNeeded() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) start(info)
        }
    }

    /** "Update now": Play's in-place update, or the Play listing when that isn't possible. */
    fun update() {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) start(info)
                else openStore()
            }
            .addOnFailureListener { openStore() }
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
