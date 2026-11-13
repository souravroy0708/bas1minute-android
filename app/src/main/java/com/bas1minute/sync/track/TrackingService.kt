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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.Prefs
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
        /** Gps: route on a map. Steps: distance from steps, no location. Timer: time only. */
        val source: TrackSource = TrackSource.Gps,
        /** Shown on the live screen when recording may stop with the screen off (no permission for it). */
        val warning: String? = null,
    ) {
        val active: Boolean get() = status != Status.Idle
        val start: LatLng? get() = segments.firstOrNull()?.firstOrNull()
    }

    val state = MutableStateFlow(State())

    /** Id of the activity that was just saved, so the app can open its summary. Cleared by the UI. */
    val justFinished = MutableStateFlow<Long?>(null)

    /** Why recording couldn't start, shown once by the Move tab. Cleared by the UI. */
    val error = MutableStateFlow<String?>(null)
}

/**
 * Records a walk, run or ride with the screen off, with or without GPS (see StepMath.kt for the three ways).
 *
 *  - Gps:   foreground service type "location". Android only needs "while using the app" location, because the
 *           person starts it from the app and a notification stays visible. Every accepted point is written to
 *           SQLite as it comes, so a closed app loses nothing.
 *  - Steps: foreground service type "health" (needs Physical activity). The step counter feeds StepTracker;
 *           a partial wake lock keeps the clock, auto-pause and voice running with the screen off.
 *  - Timer: like Steps without the sensor. If Android won't allow a foreground service (no permission on
 *           Android 14+), it still records while the app is open and says so on the live screen.
 *
 * Totals are saved every 5 seconds, so a closed app can always be resumed or saved from the Move tab.
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
        private const val EXTRA_SOURCE = "source"
        private const val WAKE_TAG = "bas1minute:track"
        private const val MAX_WAKE_MS = 8 * 60 * 60 * 1000L
        private const val CHANNEL = "tracking"
        private const val NOTIF_ID = 4711
        const val EXTRA_OPEN_TRACK = "open_track"

        fun start(ctx: Context, type: ActivityType, autoPause: Boolean, voice: Boolean, source: TrackSource = TrackSource.Gps) =
            send(ctx, ACTION_START, canForeground(ctx, resolveSource(ctx, source, type))) {
                putExtra(EXTRA_TYPE, type.id); putExtra(EXTRA_AUTOPAUSE, autoPause); putExtra(EXTRA_VOICE, voice); putExtra(EXTRA_SOURCE, source.id)
            }
        fun pause(ctx: Context) = send(ctx, ACTION_PAUSE)
        fun resume(ctx: Context) = send(ctx, ACTION_RESUME)
        fun finish(ctx: Context) = send(ctx, ACTION_FINISH)
        fun discard(ctx: Context) = send(ctx, ACTION_DISCARD)
        /** Continue an activity that Android stopped (from the "Unfinished" card). */
        fun recover(ctx: Context, id: Long, voice: Boolean, source: TrackSource = TrackSource.Gps) =
            send(ctx, ACTION_RECOVER, canForeground(ctx, source)) { putExtra(EXTRA_ID, id); putExtra(EXTRA_VOICE, voice) }

        fun hasLocationPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Physical activity: needed for the step counter (Android 10+) and the "health" service type (Android 14+). */
        fun hasActivityPermission(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        fun hasStepSensor(ctx: Context) =
            ctx.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        /**
         * The best way to record that's actually possible: the person's choice, stepped down when a permission or
         * sensor is missing. Rides can't use steps, so without GPS they record time.
         */
        fun resolveSource(ctx: Context, wanted: TrackSource, type: ActivityType): TrackSource {
            if (wanted == TrackSource.Gps && hasLocationPermission(ctx)) return TrackSource.Gps
            if (type != ActivityType.Cycle && wanted != TrackSource.Timer && hasActivityPermission(ctx) && hasStepSensor(ctx)) return TrackSource.Steps
            return TrackSource.Timer
        }

        /**
         * Whether Android 14+ will let this recording run as a foreground service. If not, the service is started
         * as a plain one (the app is open, so that's allowed): a foreground start that never calls
         * startForeground would crash the app.
         */
        fun canForeground(ctx: Context, source: TrackSource): Boolean = when (source) {
            TrackSource.Gps -> hasLocationPermission(ctx)
            else -> Build.VERSION.SDK_INT < 34 || hasActivityPermission(ctx)
        }

        private fun send(ctx: Context, action: String, foreground: Boolean = false, extras: Intent.() -> Unit = {}) {
            val i = Intent(ctx, TrackingService::class.java).setAction(action).apply(extras)
            if (foreground) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var store: TrackStore
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var tracker: Tracker? = null
    private var stepTracker: StepTracker? = null
    private var source = TrackSource.Gps
    private var warning: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
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

    /** The step counter's running total. Event times are the monotonic clock on almost every phone. */
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val st = stepTracker ?: return
            val now = SystemClock.elapsedRealtime()
            val at = (event.timestamp / 1_000_000).takeIf { kotlin.math.abs(it - now) < 60_000 } ?: now
            if (st.onStepCounter(event.values[0].toLong(), at) > 0) publish(if (st.running) LiveTrack.Status.Recording else LiveTrack.Status.Paused)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ---------- one engine, whichever way we record ----------

    private val type: ActivityType? get() = tracker?.type ?: stepTracker?.type
    private val active: Boolean get() = tracker != null || stepTracker != null
    private val running: Boolean get() = tracker?.running ?: stepTracker?.running ?: false
    private fun stats(): TrackStats? = SystemClock.elapsedRealtime().let { now -> tracker?.stats(now) ?: stepTracker?.stats(now) }
    private fun pauseEngine() { val now = SystemClock.elapsedRealtime(); tracker?.pause(now); stepTracker?.pause(now); releaseWake() }
    private fun resumeEngine() {
        val now = SystemClock.elapsedRealtime(); tracker?.resume(now); stepTracker?.resume(now)
        if (source != TrackSource.Gps) acquireWake()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = TrackStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(ActivityType.of(intent.getStringExtra(EXTRA_TYPE)), intent.getBooleanExtra(EXTRA_AUTOPAUSE, true),
                intent.getBooleanExtra(EXTRA_VOICE, false), TrackSource.of(intent.getStringExtra(EXTRA_SOURCE)))
            ACTION_RECOVER -> recover(intent.getLongExtra(EXTRA_ID, 0), intent.getBooleanExtra(EXTRA_VOICE, false))
            ACTION_PAUSE -> if (active) { pauseEngine(); publish(LiveTrack.Status.Paused); saveTotals(); updateNotification() }
            ACTION_RESUME -> if (active) { resumeEngine(); publish(LiveTrack.Status.Recording); updateNotification() }
            ACTION_FINISH -> end(save = true)
            ACTION_DISCARD -> end(save = false)
            // Android restarted the service after closing it (START_STICKY): carry on with the unfinished one
            null -> store.unfinished()?.let { recover(it.id, voice = false) } ?: stopSelf()
        }
        return START_STICKY
    }

    /**
     * False when Android refuses a GPS recording (permission taken away, or started from the background on
     * Android 12+). Without GPS a refusal isn't fatal: it keeps recording while the app is open, and says so.
     */
    private fun goForeground(): Boolean {
        if (source != TrackSource.Gps && !canForeground(this, source)) {
            warning = "Keep Bas 1 Minute open, or allow Physical activity, so the timer keeps going with the screen off."
            return true
        }
        val fgType = when {
            source == TrackSource.Gps && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            source != TrackSource.Gps && Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            else -> 0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(), fgType)
            true
        } catch (e: Exception) {
            if (source != TrackSource.Gps) {
                warning = "Keep Bas 1 Minute open, or allow Physical activity, so the timer keeps going with the screen off."
                return true
            }
            // The activity stays in the database as unfinished; the Move tab offers Resume / Save.
            tracker = null
            LiveTrack.error.value = "Couldn’t start recording. Allow precise location for Bas 1 Minute and keep the app open when you tap Start."
            stopSelf()
            false
        }
    }

    private fun begin(type: ActivityType, autoPause: Boolean, voice: Boolean, wanted: TrackSource) {
        if (active) return     // already recording
        // Never refuse to record: without location, use steps; without steps, time.
        source = resolveSource(this, wanted, type)
        warning = null
        startedAt = System.currentTimeMillis()
        activityId = store.create(type, startedAt, source)
        savedSeq = 0
        val now = SystemClock.elapsedRealtime()
        if (source == TrackSource.Gps) {
            tracker = Tracker(type, autoPause).also { t -> t.start(now); t.onSplit = { index, ms -> speakSplit(index, ms) } }
        } else {
            stepTracker = StepTracker(type, Prefs(this).stepLengthCm, autoPauseOn = autoPause && source == TrackSource.Steps)
                .also { t -> t.start(now); t.onSplit = { index, ms -> speakSplit(index, ms) } }
        }
        if (!goForeground()) return
        if (voice) initVoice()
        publish(LiveTrack.Status.Recording)
        startSource(type)
        startTicker()
    }

    /** GPS updates, or the step counter plus a wake lock (the clock and voice need the CPU with the screen off). */
    private fun startSource(type: ActivityType) {
        when (source) {
            TrackSource.Gps -> requestUpdates(type)
            TrackSource.Steps -> {
                sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                    // Report every step as it happens (no batching), so pace and auto-pause stay live
                    sensors?.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL, 0)
                }
                acquireWake()
            }
            TrackSource.Timer -> acquireWake()
        }
    }

    @SuppressLint("WakelockTimeout")   // has a timeout; released on pause, finish and destroy
    private fun acquireWake() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
            ?.apply { setReferenceCounted(false); acquire(MAX_WAKE_MS) }
    }

    private fun releaseWake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun recover(id: Long, voice: Boolean) {
        if (active) return
        val row = store.row(id) ?: run { stopSelf(); return }
        activityId = id
        startedAt = row.startedAt
        source = row.source
        warning = null
        val now = SystemClock.elapsedRealtime()
        if (source == TrackSource.Gps) {
            // A route can't continue without location; the Move tab still offers Save.
            if (!hasLocationPermission(this)) { stopSelf(); return }
            val pts = store.points(id)
            savedSeq = pts.size
            tracker = Tracker(row.type).also { t -> t.restore(pts, now); t.onSplit = { index, ms -> speakSplit(index, ms) } }
        } else {
            // Steps may no longer be possible (permission removed): carry on with the timer
            if (source == TrackSource.Steps) source = resolveSource(this, TrackSource.Steps, row.type)
            stepTracker = StepTracker(row.type, Prefs(this).stepLengthCm, autoPauseOn = source == TrackSource.Steps).also { t ->
                t.restore(row.distanceM, row.movingMs, row.steps ?: 0, row.splitsMs, now)
                t.onSplit = { index, ms -> speakSplit(index, ms) }
            }
        }
        if (!goForeground()) return
        if (voice) initVoice()
        publish(LiveTrack.Status.Recording)
        startSource(row.type)
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
                if (!active) break
                publish(if (running) LiveTrack.Status.Recording else LiveTrack.Status.Paused)
                if (++n % 5 == 0) { updateNotification(); saveTotals() }
            }
        }
    }

    private fun saveTotals() {
        val s = stats() ?: return
        val id = activityId
        scope.launch(Dispatchers.IO) { store.updateLive(id, s) }
    }

    private fun publish(status: LiveTrack.Status) {
        val st = stepTracker
        if (st != null) {
            LiveTrack.state.value = LiveTrack.State(
                status = status, type = st.type, activityId = activityId, stats = st.stats(SystemClock.elapsedRealtime()),
                startedAt = startedAt, source = source, warning = warning,
            )
            return
        }
        val t = tracker ?: return
        val f = lastFix
        LiveTrack.state.value = LiveTrack.State(
            status = status, type = t.type, activityId = activityId,
            stats = t.stats(SystemClock.elapsedRealtime()),
            segments = TrackSummary.segments(t.points),
            current = f?.let { LatLng(it.lat, it.lng) } ?: t.points.lastOrNull()?.let { LatLng(it.lat, it.lng) },
            accuracyM = f?.accuracy, startedAt = startedAt, source = TrackSource.Gps,
        )
    }

    private fun end(save: Boolean) {
        val id = activityId
        val endedType = type
        val stats = stats()
        val routeless = source != TrackSource.Gps
        fused.removeLocationUpdates(callback)
        sensors?.unregisterListener(stepListener)
        releaseWake()
        ticker?.cancel()
        tracker = null
        stepTracker = null
        LiveTrack.state.value = LiveTrack.State()
        scope.launch {
            withContext(Dispatchers.IO) {
                // Too short to be a walk: nothing worth keeping. Without GPS, half a minute of recording counts.
                val keep = save && id != 0L &&
                    if (routeless) (stats?.movingMs ?: store.row(id)?.movingMs ?: 0L) >= 30_000 else store.points(id).size >= 2
                if (keep) store.finish(id, endedType ?: (store.row(id)?.type ?: ActivityType.Walk), stats, System.currentTimeMillis())
                else if (id != 0L) store.delete(id)
                if (keep) LiveTrack.justFinished.value = id
            }
            if (save && stats != null && stats.distanceM >= 1000 && ttsReady) {
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
        val total = stats()?.movingMs ?: 0L
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
        val s = stats() ?: TrackStats()
        val type = this.type ?: ActivityType.Walk
        val open = PendingIntent.getActivity(this, 1,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_TRACK, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val running = !active || this.running
        val title = when {
            !running -> "${type.label} paused"
            s.autoPaused -> "${type.label} · auto-paused"
            else -> "${type.label} in progress"
        }
        val text = when (source) {
            TrackSource.Timer -> "${Fmt.duration(s.movingMs)} · time only (no GPS)"
            TrackSource.Steps -> "${Fmt.km(s.distanceM)} km · ${Fmt.duration(s.movingMs)} · ${"%,d".format(s.steps)} steps"
            TrackSource.Gps -> "${Fmt.km(s.distanceM)} km · ${Fmt.duration(s.movingMs)} · " +
                if (type == ActivityType.Cycle) "${Fmt.kmh(s.avgSpeedMs)} km/h" else "${Fmt.pace(s.avgSpeedMs)} /km"
        }
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
        sensors?.unregisterListener(stepListener)
        releaseWake()
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }
}
