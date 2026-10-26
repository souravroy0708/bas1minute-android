package com.bas1minute.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bas1minute.sync.api.CircleDetail
import com.bas1minute.sync.api.CirclesData
import com.bas1minute.sync.api.Repo
import com.bas1minute.sync.api.Res
import com.bas1minute.sync.api.TodayData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

enum class Tab { Today, Log, Circles, Calm, Profile }

/** Holds what the screens show. Reads phone data and the website; falls back to saved copies offline. */
class AppController(private val ctx: android.content.Context, private val scope: CoroutineScope) {
    val prefs = Prefs(ctx)
    val repo = Repo(ctx)

    var loggedIn by mutableStateOf(prefs.isConfigured)
    var permissionsDone by mutableStateOf(prefs.onboardingDone)
    var tab by mutableStateOf(Tab.Today)
    var openCircleId by mutableStateOf<Int?>(null)

    var phone by mutableStateOf(Snapshot())
    var today by mutableStateOf<TodayData?>(repo.cachedToday())
    var circles by mutableStateOf<CirclesData?>(repo.cachedCircles())
    var circle by mutableStateOf<CircleDetail?>(null)
    var offline by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var syncing by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var waiting by mutableIntStateOf(0)
    var home by mutableStateOf(homeState())

    fun launch(block: suspend () -> Unit) { scope.launch { block() } }

    fun homeState() = com.bas1minute.sync.ui.HomeState(
        stepGoal = prefs.stepGoal,
        screenLimit = prefs.screenLimitMinutes,
        syncing = syncing,
        lastSyncAt = prefs.lastSyncAt,
        lastSyncMessage = prefs.lastSyncMessage,
        lastSyncOk = prefs.lastSyncOk,
        autoSync = prefs.autoSync,
        syncKeyPrefix = prefs.token.take(12).takeIf { prefs.isConfigured } ?: "",
    )

    private fun refreshCounters() {
        waiting = repo.waitingActions()
        home = homeState()
    }

    /** Phone readings (always available) + website data (cached when offline). */
    fun refresh() = launch {
        phone = loadSnapshot(ctx)
        if (!loggedIn) return@launch
        val t = repo.today()
        t.value?.let { today = it }
        offline = t.offline
        if (t.authLost) return@launch onAuthLost()
        if (tab == Tab.Circles) refreshCircles()
        refreshCounters()
    }

    fun refreshCircles() = launch {
        val c = repo.circles()
        c.value?.let { circles = it }
        offline = c.offline
        openCircleId?.let { id -> loadCircle(id) }
    }

    fun openCircle(id: Int) {
        openCircleId = id
        circle = repo.cachedCircle(id)
        launch { loadCircle(id) }
    }

    private suspend fun loadCircle(id: Int) {
        val d = repo.circle(id)
        d.value?.let { circle = it }
        offline = d.offline
    }

    fun closeCircle() {
        openCircleId = null
        circle = null
        refreshCircles()
    }

    fun sync() {
        if (syncing) return
        syncing = true
        home = homeState()
        launch {
            Sync.run(ctx)
            syncing = false
            refresh()
        }
    }

    /** Shows the right message for any action result, and reloads what changed. */
    fun handle(res: Res<*>, success: String? = null, after: (() -> Unit)? = null) {
        when (res) {
            is Res.Ok<*> -> { success?.let { message = it }; after?.invoke() }
            is Res.Queued -> { message = res.message; after?.invoke() }
            is Res.Fail -> { message = res.message; if (res.authLost) onAuthLost() }
        }
        refreshCounters()
    }

    // ---- account ----

    fun loggedInNow() {
        loggedIn = true
        permissionsDone = prefs.onboardingDone
        refresh()
        sync()
    }

    fun finishPermissions() {
        prefs.onboardingDone = true
        permissionsDone = true
        sync()
    }

    fun logout() = launch {
        repo.logout()
        today = null; circles = null; circle = null; openCircleId = null
        loggedIn = false
        tab = Tab.Today
    }

    private fun onAuthLost() {
        prefs.clearSession()
        loggedIn = false
        message = "Please log in again."
    }

    fun setAutoSync(on: Boolean) {
        prefs.autoSync = on
        if (on) SyncWorker.schedule(ctx) else SyncWorker.cancel(ctx)
        home = homeState()
    }

    // ---- actions ----

    fun addBurst() = launch {
        val current = today?.today?.bursts ?: 0
        handle(repo.logDay(LocalDate.now(), null, current + 1, null, null), "Burst added. Shabash!") { refresh() }
    }

    fun setMood(value: Int) = launch { handle(repo.mood(value), null) { refresh() } }

    fun calmDone() = launch { handle(repo.calmMinute(), "A minute of calm, counted.") { refresh() } }

    fun cheer(userId: Int, kind: String) = launch {
        val id = openCircleId ?: return@launch
        handle(repo.cheer(id, userId, kind), if (kind == "cheer") "Shabash sent!" else "Nudge sent.") { launch { loadCircle(id) } }
    }

    fun updateGoals(fields: JSONObject) = launch {
        busy = true
        handle(repo.updateMe(fields), "Goals saved.") { refresh() }
        busy = false
    }
}
