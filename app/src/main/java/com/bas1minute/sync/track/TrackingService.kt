package com.bas1minute.sync.track

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.R
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the live screen and the Today card show. Written only by TrackingService. */
object LiveTrack {
    enum class Status { Idle, Recording, Paused }

    data class State(
        val status: Status = Status.Idle,
        val type: ActivityType = ActivityType.Walk,
        val activityId: Long = 0,
        val stats: TrackStats = TrackStats(),
        val segments: List<List<LatLng>> = emptyList(),
        val current: LatLng? = null,
        val accuracyM: Float? = null,
        val startedAt: Long = 0,
    ) {
        val active: Boolean get() = status != Status.Idle
        val start: LatLng? get() = segments.firstOrNull()?.firstOrNull()
    }

    val state = MutableStateFlow(State())

    /** Id of the activity that was just saved, so the app can open its summary. Cleared by the UI. */
    val justFinished = MutableStateFlow<Long?>(null)
}

/**
 * Records a walk or run with the screen off. A foreground service with type "location": Android only needs
 * "while using the app" location, because the person starts it from the app and a notification stays visible.
 * Every accepted point is written to SQLite as it comes, so a closed app loses nothing.
 */
class TrackingService : Service() {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_FINISH = "finish"
        private const val ACTION_DISCARD = "discard"
        private const val ACTION_RECOVER = "recover"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_AUTOPAUSE = "autopause"
        private const val EXTRA_VOICE = "voice"
        private const val EXTRA_ID = "id"
        private const val CHANNEL = "tracking"
        private const val NOTIF_ID = 4711
        const val EXTRA_OPEN_TRACK = "open_track"

        fun start(ctx: Context, type: ActivityType, autoPause: Boolean, voice: Boolean) = send(ctx, ACTION_START) {
            putExtra(EXTRA_TYPE, type.id); putExtra(EXTRA_AUTOPAUSE, autoPause); putExtra(EXTRA_VOICE, voice)
        }
        fun pause(ctx: Context) = send(ctx, ACTION_PAUSE)
        fun resume(ctx: Context) = send(ctx, ACTION_RESUME)
        fun finish(ctx: Context) = send(ctx, ACTION_FINISH)
        fun discard(ctx: Context) = send(ctx, ACTION_DISCARD)
        /** Continue an activity that Android stopped (from the "Unfinished" card). */
        fun recover(ctx: Context, id: Long, voice: Boolean) = send(ctx, ACTION_RECOVER) { putExtra(EXTRA_ID, id); putExtra(EXTRA_VOICE, voice) }

        fun hasLocationPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        private fun send(ctx: Context, action: String, extras: Intent.() -> Unit = {}) {
            val i = Intent(ctx, TrackingService::class.java).setAction(action).apply(extras)
            if (action == ACTION_START || action == ACTION_RECOVER) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var store: TrackStore
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var tracker: Tracker? = null
    private var activityId = 0L
    private var startedAt = 0L
    private var savedSeq = 0
    private var ticker: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastFix: Fix? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                val f = Fix(
                    lat = loc.latitude, lng = loc.longitude,
                    alt = if (loc.hasAltitude()) loc.altitude else null,
                    accuracy = if (loc.hasAccuracy()) loc.accuracy else 50f,
                    speed = if (loc.hasSpeed()) loc.speed else null,
                    timeMs = loc.time, elapsedMs = loc.elapsedRealtimeNanos / 1_000_000,
                )
                onFix(f)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = TrackStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(ActivityType.of(intent.getStringExtra(EXTRA_TYPE)), intent.getBooleanExtra(EXTRA_AUTOPAUSE, true), intent.getBooleanExtra(EXTRA_VOICE, false))
            ACTION_RECOVER -> recover(intent.getLongExtra(EXTRA_ID, 0), intent.getBooleanExtra(EXTRA_VOICE, false))
            ACTION_PAUSE -> tracker?.let { it.pause(SystemClock.elapsedRealtime()); publish(LiveTrack.Status.Paused); saveTotals(); updateNotification() }
            ACTION_RESUME -> tracker?.let { it.resume(SystemClock.elapsedRealtime()); publish(LiveTrack.Status.Recording); updateNotification() }
            ACTION_FINISH -> end(save = true)
            ACTION_DISCARD -> end(save = false)
            // Android restarted the service after closing it (START_STICKY): carry on with the unfinished one
            null -> store.unfinished()?.let { recover(it.id, voice = false) } ?: stopSelf()
        }
        return START_STICKY
    }

    /** False when Android refuses (permission taken away, or started from the background on Android 12+). */
    private fun goForeground(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(), type)
            true
        } catch (e: Exception) {
            // The activity stays in the database as unfinished; the Today card offers Resume / Save.
            tracker = null
            stopSelf()
            false
        }
    }

    private fun begin(type: ActivityType, autoPause: Boolean, voice: Boolean) {
        if (tracker != null) return     // already recording
        if (!hasLocationPermission(this)) { stopSelf(); return }
        startedAt = System.currentTimeMillis()
        activityId = store.create(type, startedAt)
        savedSeq = 0
        tracker = Tracker(type, autoPause).also { t ->
            t.start(SystemClock.elapsedRealtime())
            t.onSplit = { index, ms -> speakSplit(index, ms) }
        }
        if (!goForeground()) return
        if (voice) initVoice()
        publish(LiveTrack.Status.Recording)
        requestUpdates(type)
        startTicker()
    }

    private fun recover(id: Long, voice: Boolean) {
        if (tracker != null) return
        val row = store.row(id) ?: run { stopSelf(); return }
        if (!hasLocationPermission(this)) { stopSelf(); return }
        activityId = id
        startedAt = row.startedAt
        val pts = store.points(id)
        savedSeq = pts.size
        tracker = Tracker(row.type).also { t ->
            t.restore(pts, SystemClock.elapsedRealtime())
            t.onSplit = { index, ms -> speakSplit(index, ms) }
        }
        if (!goForeground()) return
        if (voice) initVoice()
        publish(LiveTrack.Status.Recording)
        requestUpdates(row.type)
        startTicker()
    }

    @SuppressLint("MissingPermission")   // checked in begin()/recover()
    private fun requestUpdates(type: ActivityType) {
        val interval = if (type == ActivityType.Walk) 2000L else 1000L
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(true)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            end(save = true)
        }
    }

    private fun onFix(f: Fix) {
        val t = tracker ?: return
        lastFix = f
        val accepted = t.onFix(f)
        if (accepted != null) {
            val seq = savedSeq++
            val id = activityId
            scope.launch(Dispatchers.IO) { store.addPoints(id, seq, listOf(accepted)) }
        }
        publish(if (t.running) LiveTrack.Status.Recording else LiveTrack.Status.Paused)
    }

    /** Clock for the timer, the notification and the saved totals. */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var n = 0
            while (isActive) {
                delay(1000)
                val t = tracker ?: break
                publish(if (t.running) LiveTrack.Status.Recording else LiveTrack.Status.Paused)
                if (++n % 5 == 0) { updateNotification(); saveTotals() }
            }
        }
    }

    private fun saveTotals() {
        val t = tracker ?: return
        val s = t.stats(SystemClock.elapsedRealtime())
        val id = activityId
        scope.launch(Dispatchers.IO) { store.updateLive(id, s) }
    }

    private fun publish(status: LiveTrack.Status) {
        val t = tracker ?: return
        val f = lastFix
        LiveTrack.state.value = LiveTrack.State(
            status = status, type = t.type, activityId = activityId,
            stats = t.stats(SystemClock.elapsedRealtime()),
            segments = TrackSummary.segments(t.points),
            current = f?.let { LatLng(it.lat, it.lng) } ?: t.points.lastOrNull()?.let { LatLng(it.lat, it.lng) },
            accuracyM = f?.accuracy, startedAt = startedAt,
        )
    }

    private fun end(save: Boolean) {
        val t = tracker
        val id = activityId
        fused.removeLocationUpdates(callback)
        ticker?.cancel()
        tracker = null
        val stats = t?.stats(SystemClock.elapsedRealtime())
        LiveTrack.state.value = LiveTrack.State()
        scope.launch {
            withContext(Dispatchers.IO) {
                // Too short to be a walk: nothing worth keeping
                val keep = save && id != 0L && store.points(id).size >= 2
                if (keep) store.finish(id, t?.type ?: (store.row(id)?.type ?: ActivityType.Walk), stats, System.currentTimeMillis())
                else if (id != 0L) store.delete(id)
                if (keep) LiveTrack.justFinished.value = id
            }
            if (save && t != null && (stats?.distanceM ?: 0.0) >= 1000 && ttsReady) {
                tts?.speak("Activity saved. ${Fmt.km(stats!!.distanceM)} kilometres. Shabash!", TextToSpeech.QUEUE_FLUSH, null, "end")
                delay(3500)
            }
            ServiceCompat.stopForeground(this@TrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---------- voice ----------

    private fun initVoice() {
        if (tts != null) return
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
                ttsReady = true
            }
        }
    }

    private fun speakSplit(index: Int, splitMs: Long) {
        if (!ttsReady) return
        val total = tracker?.stats(SystemClock.elapsedRealtime())?.movingMs ?: 0L
        tts?.speak(Fmt.spokenSplit(index, splitMs, total), TextToSpeech.QUEUE_ADD, null, "split$index")
    }

    // ---------- notification ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Walk and run tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows distance and time while a walk or run is recording"
                setShowBadge(false)
            })
        }
    }

    private fun pending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, TrackingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(): Notification {
        val t = tracker
        val s = t?.stats(SystemClock.elapsedRealtime()) ?: TrackStats()
        val type = t?.type ?: ActivityType.Walk
        val open = PendingIntent.getActivity(this, 1,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_TRACK, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val running = t?.running != false
        val title = when {
            !running -> "${type.label} paused"
            s.autoPaused -> "${type.label} · auto-paused"
            else -> "${type.label} in progress"
        }
        val text = "${Fmt.km(s.distanceM)} km · ${Fmt.duration(s.movingMs)} · " +
            if (type == ActivityType.Cycle) "${Fmt.kmh(s.avgSpeedMs)} km/h" else "${Fmt.pace(s.avgSpeedMs)} /km"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (running) "Pause" else "Resume", pending(if (running) ACTION_PAUSE else ACTION_RESUME, 2))
            .addAction(0, "Finish", pending(ACTION_FINISH, 3))
            .build()
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification())
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }
}
