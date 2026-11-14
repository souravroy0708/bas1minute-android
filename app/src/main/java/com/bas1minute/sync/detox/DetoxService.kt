package com.bas1minute.sync.detox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.R
import java.text.DateFormat
import java.util.Date

/**
 * Runs only while a detox session is active. Every 0.6 s it checks which app is on screen; if it's blocked,
 * it shows [BlockOverlay] on top — a system overlay window, not a launched Activity, which is what actually
 * lets it appear reliably on Android 14+ (see BlockOverlay's own notes). Shows an ongoing countdown
 * notification, and stops itself when the time is up, the user ends early, or a needed permission is revoked.
 *
 * Foreground service type: specialUse (user-started app blocking for a fixed time; declared in Play Console).
 *
 * Holds a partial wake lock for as long as the session runs. Without one, several Android skins (OnePlus's
 * OxygenOS among the most aggressive) throttle a background Service's own Handler thread even while its
 * foreground notification stays up and visible — the countdown and notification keep going (those are drawn
 * by the system, not this loop), but the every-0.6s check that shows the block screen silently stops firing.
 * The giveaway: a session still correctly marks itself "guilty" after the fact (that check re-reads Android's
 * own usage history once, at the end, regardless of whether this loop ran), while nothing was ever blocked
 * live in the moment.
 */
class DetoxService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var store: DetoxStore
    private var wakeLock: PowerManager.WakeLock? = null
    private var rules: AppRules? = null
    private var reader: UsageReader? = null
    private var sessionId = -1L
    private var running = false

    private var lastCountedPkg: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = DetoxStore.get(this)
        thread = HandlerThread("detox").apply { start() }
        handler = Handler(thread.looper)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = store.active()
        if (s == null) {
            stopNow()
            return START_NOT_STICKY
        }
        val n = ongoingNotification(s)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ONGOING, n)
        }
        sessionId = s.id
        rules = AppRules(this, store.extraAllowed)
        reader = UsageReader(this)
        acquireWake(s.plannedEndMs)
        if (!running) {
            running = true
            handler.post(tick)
        }
        return START_STICKY
    }

    /** Keeps the CPU awake for the rest of the session, so the 0.6s check keeps firing on the phones that need it most. */
    @android.annotation.SuppressLint("WakelockTimeout")   // has an explicit timeout below; never held indefinitely
    private fun acquireWake(untilMs: Long) {
        if (wakeLock?.isHeld == true) return
        val remaining = (untilMs - System.currentTimeMillis()).coerceIn(0L, 12 * 60 * 60_000L)
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bas1minute:detox")
            ?.apply { setReferenceCounted(false); acquire(remaining + 60_000L) }   // +1 min margin past the session end
    }

    private fun releaseWake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            // The reschedule lives in a finally block: one bad tick (a transient exception from the usage-stats
            // API, seen on a few OEM builds) must never silently end the loop for the rest of the session.
            try {
                if (!running) return
                val s = store.active()
                if (s == null || s.id != sessionId) { stopNow(); return }
                val now = System.currentTimeMillis()

                if (now >= s.plannedEndMs) {
                    BlockOverlay.hide()
                    val done = DetoxController.finishWithHistory(this@DetoxService, s, now)
                    notifyResult(done)
                    stopNow()
                    return
                }
                if (!DetoxController.isReady(this@DetoxService)) {
                    BlockOverlay.hide()
                    val broken = DetoxLogic.breakNow(s, now, BreakReason.PERMISSION_REVOKED)
                    store.update(broken)
                    notifyResult(broken)
                    stopNow()
                    return
                }

                // Re-read the last ~20s fresh every tick (not an advancing cursor): see UsageReader.currentForegroundApp.
                val fg = reader?.currentForegroundApp(now)
                val r = rules
                if (fg != null && r != null && r.isBlocked(fg)) {
                    if (fg != lastCountedPkg) {
                        store.recordBlockedAttempt(s.id)
                        lastCountedPkg = fg
                    }
                    // A no-op if this app is already covered; BlockOverlay itself checks for Display-over-other-apps.
                    BlockOverlay.show(this@DetoxService, fg)
                } else if (fg != null) {
                    lastCountedPkg = null
                    if (BlockOverlay.isShowing) BlockOverlay.hide()
                }
            } catch (e: Exception) {
                // Swallow and keep polling rather than let one bad tick end live blocking for the rest of the session.
            } finally {
                if (running) handler.postDelayed(this, POLL_MS)
            }
        }
    }

    private fun stopNow() {
        running = false
        BlockOverlay.hide()
        releaseWake()
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        BlockOverlay.hide()
        releaseWake()
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }

    // ---- notifications ---------------------------------------------------------------------------------

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Digital detox (running)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Countdown while a digital detox is on"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Digital detox results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a detox promise is kept or broken"
            },
        )
    }

    private fun openApp(): PendingIntent {
        val launch = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_DETOX)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 7301, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun smallIcon(): Int = R.drawable.ic_notification

    private fun ongoingNotification(s: DetoxSession): Notification {
        val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.plannedEndMs))
        return Notification.Builder(this, CH_ONGOING)
            .setSmallIcon(smallIcon())
            .setContentTitle("Digital detox on until $endsAt")
            .setContentText("Calls and messages still work. Every other app is paused.")
            .setWhen(s.plannedEndMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openApp())
            .build()
    }

    private fun notifyResult(s: DetoxSession) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val (title, text) = when (s.outcome) {
            Outcome.KEPT -> "Promise kept: ${DetoxLogic.formatMinutes(s.plannedMinutes)} detox done" to
                "Shabash! You stayed off your phone the whole time."
            else -> "Promise broken" to when (s.reason) {
                BreakReason.PERMISSION_REVOKED -> "A permission detox needs was turned off."
                BreakReason.USED_BLOCKED_APP -> "${rules?.label(s.brokenBy ?: "") ?: "An app"} was used during detox."
                else -> "Detox ended early after ${DetoxLogic.formatMinutes(s.keptMinutes(System.currentTimeMillis()))}."
            }
        }
        runCatching {
            nm.notify(
                NOTIF_RESULT,
                Notification.Builder(this, CH_RESULT)
                    .setSmallIcon(smallIcon())
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(openApp())
                    .build(),
            )
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_DETOX = "detox"
        private const val CH_ONGOING = "detox_running"
        private const val CH_RESULT = "detox_result"
        private const val NOTIF_ONGOING = 7300
        private const val NOTIF_RESULT = 7302
        private const val POLL_MS = 600L
    }
}
