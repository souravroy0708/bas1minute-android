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

enum class Tab { Today, Insights, Circles, Spin, Profile }

/** Holds what the screens show. Reads phone data and the website; falls back to saved copies offline. */
class AppController(private val ctx: android.content.Context, private val scope: CoroutineScope) {
    val prefs = Prefs(ctx)
    val repo = Repo(ctx)

    /** Name and PIN set on first launch (privacy first: no phone, email or password). */
    var setupDone by mutableStateOf(prefs.setupDone || prefs.isConfigured)
    /** App lock: true when the PIN isn't required or was entered this session. */
    var unlocked by mutableStateOf(!prefs.appLock)
    var permissionsDone by mutableStateOf(prefs.onboardingDone)
    var tab by mutableStateOf(Tab.Today)
    var openCircleId by mutableStateOf<Int?>(null)
    /** "Log a day" opens on top of Today. */
    var logOpen by mutableStateOf(false)
    /** Native About / Privacy page open on top of Profile: "idea", "different", "free" or "privacy". */
    var aboutPage by mutableStateOf<String?>(null)
    /** Guided 1-minute burst timer open on top of Today. */
    var burstOpen by mutableStateOf(false)
    /** Spin-the-health-wheel screen open on top of Today (legacy; Spin is now a tab). */
    var wheelOpen by mutableStateOf(false)
    /** 1-minute calm open on top of Today. */
    var calmOpen by mutableStateOf(false)
    /** Bumped after each spin so screens re-read spins left and tips found. */
    var wheelVersion by mutableIntStateOf(0)
    /** Last full two-way sync result, for the Profile card. */
    var fullSyncMessage by mutableStateOf<String?>(null)

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
        if (!setupDone) return@launch
        // Cheers and circles need the name-only account; everything else is on the phone.
        if (!repo.ensureAccount()) { offline = true; refreshCounters(); return@launch }
        val t = repo.today()
        t.value?.let { today = it }
        offline = t.offline
        if (t.authLost) return@launch onAuthLost()
        if (tab == Tab.Circles) refreshCircles()
        refreshCounters()
    }

    fun refreshCircles() = launch {
        if (!repo.ensureAccount()) { offline = true; message = "Circles need the internet the first time. Connect and try again."; return@launch }
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

    /**
     * "Sync with server": send everything waiting (days + offline actions), then download the last
     * 90 days so this phone matches the server (new phone, reinstall).
     */
    fun fullSync() {
        if (syncing) return
        if (!prefs.shareActivity) { message = "Sharing is off, so everything stays on this phone."; return }
        syncing = true
        home = homeState()
        launch {
            val up = Sync.run(ctx)
            val restored = repo.pullHistory(90)
            val waitingNow = repo.waitingActions() + LocalDb.get(ctx).pendingCount()
            fullSyncMessage = when {
                restored == null -> up.message + " Couldn’t download your history right now."
                waitingNow > 0 -> "${up.message} $waitingNow item${if (waitingNow == 1) "" else "s"} still waiting."
                else -> "All synced. Your data is safe on the server" + if (restored > 0) ", and $restored day${if (restored == 1) "" else "s"} were restored to this phone." else "."
            }
            message = fullSyncMessage
            syncing = false
            refresh()
        }
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

    /** First launch: name and a 4-digit PIN. Creates the name-only account in the background (fine offline). */
    fun finishSetup(name: String, pin: String) {
        prefs.name = name.trim()
        prefs.firstName = name.trim().split(" ").first()
        prefs.pinHash = prefs.hashPin(pin)
        prefs.setupDone = true
        setupDone = true
        unlocked = true
        launch { repo.ensureAccount(); refresh() }
        prefs.pendingJoin.takeIf { it.isNotBlank() }?.let { joinFromLink(it) }
    }

    /** "Forgot PIN?": set a new one, no questions asked (the PIN is a convenience lock). */
    fun resetPin(pin: String) {
        prefs.pinHash = prefs.hashPin(pin)
        unlocked = true
        message = "New PIN set."
    }

    fun finishPermissions() {
        prefs.onboardingDone = true
        permissionsDone = true
        sync()
    }

    /** Share steps and screen time with the server (for circles and backup), or stop; optionally delete what was shared. */
    fun setSharing(on: Boolean, deleteShared: Boolean = false) = launch {
        busy = true
        when (val r = repo.setShare(on)) {
            is Res.Fail -> message = r.message
            else -> {
                prefs.shareActivity = on
                if (on) {
                    LocalDb.get(ctx).clearAccountData()   // mark saved days to be sent
                    message = "Sharing on. Only your steps and screen time are sent."
                    sync()
                } else {
                    message = if (deleteShared) {
                        when (val d = repo.deleteSharedData()) {
                            is Res.Ok -> "Sharing off. ${d.value} shared day${if (d.value == 1) "" else "s"} deleted from the server."
                            is Res.Fail -> "Sharing off. " + d.message
                            else -> "Sharing off."
                        }
                    } else "Sharing off. Nothing more leaves this phone."
                }
            }
        }
        busy = false
        home = homeState()
        refresh()
    }

    /** Delete everything on this phone and the circle account on the server, then start fresh. */
    fun deleteEverything() = launch {
        when (val r = repo.deleteEverything()) {
            is Res.Fail -> message = r.message
            else -> {
                today = null; circles = null; circle = null; openCircleId = null
                burstOpen = false; logOpen = false; aboutPage = null; wheelOpen = false
                phone = Snapshot()
                setupDone = false; permissionsDone = false; unlocked = true
                tab = Tab.Today
            }
        }
    }

    private fun onAuthLost() {
        // The name-only account was removed on the server: make a new one next time (circles must be rejoined).
        prefs.token = ""
        com.bas1minute.sync.api.SecretStore(ctx).clear()
    }

    // ---- Health wheel ----

    fun spinsLeft(): Int {
        val today = LocalDate.now().toString()
        return if (prefs.spinDay != today) Wellbeing.SPINS_PER_DAY else (Wellbeing.SPINS_PER_DAY - prefs.spinCount).coerceAtLeast(0)
    }

    /** Picks the segment and tip for one spin (prefers tips not seen yet), and uses up a spin. Null when none left. */
    fun takeSpin(random: kotlin.random.Random = kotlin.random.Random.Default): Pair<Int, Wellbeing.Tip>? {
        if (spinsLeft() <= 0) return null
        val today = LocalDate.now().toString()
        if (prefs.spinDay != today) { prefs.spinDay = today; prefs.spinCount = 0 }
        prefs.spinCount = prefs.spinCount + 1
        val segment = random.nextInt(WheelMath.SEGMENTS)
        val category = Wellbeing.CATEGORIES[segment].id
        val pool = Wellbeing.tipsIn(category)
        val fresh = pool.filter { it.id.toString() !in prefs.discoveredTips }
        val tip = (fresh.ifEmpty { pool }).random(random)
        return segment to tip
    }

    /** Called when the wheel stops, so the tip counts as discovered only once it’s shown. */
    fun tipShown(tip: Wellbeing.Tip) {
        prefs.discoveredTips = prefs.discoveredTips + tip.id.toString()
        wheelVersion++
    }

    /** Invite link tapped (https://…/join/CODE/ or bas1minute://join/CODE): join without typing the code. */
    fun joinFromLink(code: String) {
        val clean = code.uppercase().filter { it.isLetterOrDigit() }.take(12)
        if (clean.length < 6) return
        if (!setupDone) { prefs.pendingJoin = clean; return }   // joins right after name + PIN
        launch {
            if (!repo.ensureAccount()) { prefs.pendingJoin = clean; message = "Connect to the internet to join the circle."; return@launch }
            val res = repo.joinCircle(clean)
            handle(res, "You joined the circle. Say hello with a Shabash!") {
                prefs.pendingJoin = ""
                if (res is Res.Ok) {
                    tab = Tab.Circles
                    openCircle(res.value.getJSONObject("circle").getInt("id"))
                }
            }
        }
    }

    fun setAutoSync(on: Boolean) {
        prefs.autoSync = on
        if (on) SyncWorker.schedule(ctx) else SyncWorker.cancel(ctx)
        home = homeState()
    }

    // ---- actions ----

    fun addBurst() = launch {
        handle(repo.addBurst(), "Burst added. Shabash!") { refresh() }
    }

    /** One glass = 250 ml. Water is a running total for today, so replaying offline is safe. */
    fun addWater(ml: Int = 250) = launch {
        val current = phone.today?.row?.waterMl ?: 0
        handle(repo.logDay(LocalDate.now(), null, null, null, null, waterMl = (current + ml).coerceAtMost(10_000)), "+$ml ml") { refresh() }
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
