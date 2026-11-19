package com.bas1minute.sync.detox

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Strict mode: the old "pause screen with a countdown" — back, but only for members who turn it on.
 *
 * Default detox (no extra permission) reminds and judges the promise afterwards. Strict mode is opt-in from the
 * Detox screen, because a real lock needs two things a normal app doesn't have:
 *   1. "Display over other apps", so the pause screen can cover the paused app (Android doesn't let a
 *      background app open its own screen any more);
 *   2. a foreground service, so it notices the paused app within a second instead of every few minutes.
 *
 * Kept as light as possible:
 *  - runs ONLY while a strict detox is on, with the countdown notification visible the whole time;
 *  - checks every [POLL_MS] with the screen on; every [SCREEN_OFF_POLL_MS] with it off (nobody can open an app
 *    with the screen off), and the moment the screen turns on it checks straight away;
 *  - holds a wake lock only while the screen is on (some phones, OnePlus especially, otherwise freeze the check),
 *    never through the night.
 * The promise is still judged from Android's own usage history at the end (DetoxWatch / DetoxController), so
 * force-stopping this service can't fake a kept promise.
 *
 * Foreground service type: specialUse (user-started app blocking for a time the user chooses; declared in Play).
 */
class StrictGuardService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var store: DetoxStore
    private var wakeLock: PowerManager.WakeLock? = null
    private var rules: AppRules? = null
    private var reader: UsageReader? = null
    private var sessionId = -1L
    private var lastCountedPkg: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!running) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> { holdWake(); handler.removeCallbacks(tick); handler.post(tick) }
                Intent.ACTION_SCREEN_OFF -> { BlockOverlay.hide(); releaseWake() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = DetoxStore.get(this)
        thread = HandlerThread("strict-detox").apply { start() }
        handler = Handler(thread.looper)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = store.active()
        if (s == null || !store.strictMode || !DetoxController.canDrawOverlays(this)) { stopNow(); return START_NOT_STICKY }
        // Same id and channel as DetoxWatch's countdown, so there is only ever ONE detox notification
        val n = DetoxWatch.ongoingNotification(this, s, strict = true)
        if (Build.VERSION.SDK_INT >= 34) startForeground(DetoxWatch.NOTIF_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(DetoxWatch.NOTIF_ONGOING, n)
        sessionId = s.id
        rules = AppRules(this, store.blockCategories, store.blockedApps, store.allowedOverride)
        reader = UsageReader(this)
        if (isScreenOn()) holdWake()
        if (!running) { running = true; handler.post(tick) }
        return START_STICKY
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            try {
                if (!running) return
                val s = store.active()
                if (s == null || s.id != sessionId) { stopNow(); return }
                val now = System.currentTimeMillis()
                if (now >= s.plannedEndMs || !DetoxController.hasUsageAccess(this@StrictGuardService)) {
                    // DetoxWatch closes the session, posts "kept / broken" and stops everything, including us
                    BlockOverlay.hide()
                    DetoxWatch.tick(this@StrictGuardService, now)
                    stopNow(); return
                }
                if (!isScreenOn()) return
                // A short fresh window each tick: catches an app the moment it opens (see UsageReader)
                val fg = reader?.currentForegroundApp(now)
                val r = rules
                if (fg != null && r != null && r.isBlocked(fg)) {
                    if (fg != lastCountedPkg) { store.recordBlockedAttempt(s.id); lastCountedPkg = fg }
                    // Window work always on the main thread (the overlay's countdown ticks there too)
                    android.os.Handler(mainLooper).post { BlockOverlay.show(this@StrictGuardService, fg) }
                } else if (fg != null) {
                    lastCountedPkg = null
                    if (BlockOverlay.isShowing) android.os.Handler(mainLooper).post { BlockOverlay.hide() }
                }
            } catch (e: Exception) {
                // one bad read must never end the lock for the rest of the session
            } finally {
                if (running) handler.postDelayed(this, if (isScreenOn()) POLL_MS else SCREEN_OFF_POLL_MS)
            }
        }
    }

    private fun isScreenOn(): Boolean =
        runCatching { getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull() ?: true

    @android.annotation.SuppressLint("WakelockTimeout")   // explicit timeout: at most the rest of the session
    private fun holdWake() {
        if (wakeLock?.isHeld == true) return
        val s = store.active() ?: return
        val remaining = (s.plannedEndMs - System.currentTimeMillis()).coerceIn(0L, 12 * 60 * 60_000L)
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bas1minute:strict")
            ?.apply { setReferenceCounted(false); acquire(remaining + 60_000L) }
    }

    private fun releaseWake() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }

    private fun stopNow() {
        running = false
        android.os.Handler(mainLooper).post { BlockOverlay.hide() }
        releaseWake()
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        android.os.Handler(mainLooper).post { BlockOverlay.hide() }
        releaseWake()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        thread.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val POLL_MS = 700L
        private const val SCREEN_OFF_POLL_MS = 60_000L

        /** True while the lock is running — DetoxWatch then skips its gentle "X is open" reminder. */
        @Volatile var running = false
            private set

        /** Starts the lock if this detox is strict and the permission is there. Safe to call any time. */
        fun startIfStrict(ctx: Context) {
            val store = DetoxStore.get(ctx)
            if (store.active() == null || !store.strictMode || !DetoxController.canDrawOverlays(ctx)) return
            // From the app this always works; from a background job it works on most phones because the app holds
            // "Display over other apps". If Android refuses, the gentle reminders keep running instead.
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, StrictGuardService::class.java)) }
        }

        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, StrictGuardService::class.java)) } }
    }
}
