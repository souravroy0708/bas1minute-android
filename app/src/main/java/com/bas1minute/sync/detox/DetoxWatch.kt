package com.bas1minute.sync.detox

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.R
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Digital detox without super permissions. Replaces the old always-on DetoxService (foreground service of type
 * specialUse + a wake lock + a 0.6-second poll + a "Display over other apps" pause screen).
 *
 * How a detox works now
 *  - Starting one posts a plain ongoing countdown notification and schedules two WorkManager jobs:
 *      • a check every ~[TICK_MINUTES] minutes (best effort — Android may stretch it in Doze, which is fine:
 *        with the screen off nobody is using a paused app anyway);
 *      • one job at the exact planned end, to close the session and say "Promise kept" / "broken".
 *  - Each check reads which app is in the foreground from Usage access. If it's a paused app, a heads-up
 *    reminder appears with a one-tap "Close it" (goes to the home screen). Nothing is drawn over the app.
 *  - The promise itself is judged at the end from Android's own usage history (DetoxController.finishWithHistory),
 *    exactly as before, so force-stopping Bas 1 Minute can never fake a kept promise. A stretch shorter than
 *    DetoxLogic.SLIP_MS is a slip, not a break.
 *
 * Permissions this needs: Usage access (required) and notifications (optional — without them the promise is
 * still judged correctly, there are just no reminders). No boot receiver of our own: WorkManager re-schedules
 * its jobs after a restart by itself, and a session whose time ran out while the phone was off is closed by the
 * first job or app open that sees it.
 */
class DetoxWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try { DetoxWatch.tick(applicationContext, System.currentTimeMillis()) } catch (e: Exception) { /* never crash in the background */ }
        return Result.success()
    }
}

object DetoxWatch {
    const val TICK_MINUTES = 5L
    private const val WORK_TICK = "bas1minute-detox-tick"
    private const val WORK_END = "bas1minute-detox-end"

    private const val CH_ONGOING = "detox_running"
    private const val CH_RESULT = "detox_result"
    private const val CH_REMIND = "detox_remind"
    const val NOTIF_ONGOING = 7300
    private const val NOTIF_REMIND = 7301
    private const val NOTIF_RESULT = 7302

    const val EXTRA_OPEN = "open"
    const val OPEN_DETOX = "detox"

    /** Called once when a session starts (by hand or by a Schedule). */
    fun begin(ctx: Context, s: DetoxSession) {
        showOngoing(ctx, s)
        scheduleTick(ctx, ExistingWorkPolicy.REPLACE)
        scheduleEnd(ctx, s)
        StrictGuardService.startIfStrict(ctx)   // Strict mode only: the pause screen
    }

    /** Makes sure the jobs exist for the running session — safe to call on every app open and every 15-min tick. */
    fun ensure(ctx: Context, s: DetoxSession) {
        if (!StrictGuardService.running) showOngoing(ctx, s)
        scheduleTick(ctx, ExistingWorkPolicy.KEEP)
        scheduleEnd(ctx, s)
        if (!StrictGuardService.running) StrictGuardService.startIfStrict(ctx)
    }

    fun stop(ctx: Context) {
        StrictGuardService.stop(ctx)
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork(WORK_TICK)
        wm.cancelUniqueWork(WORK_END)
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ONGOING)
        NotificationManagerCompat.from(ctx).cancel(NOTIF_REMIND)
    }

    /** One check. Runs from the worker; also safe to call from anywhere else. */
    fun tick(ctx: Context, now: Long) {
        val store = DetoxStore.get(ctx)
        val s = store.active() ?: run { stop(ctx); return }
        val rules = AppRules(ctx, store.blockCategories, store.blockedApps, store.allowedOverride)

        if (now >= s.plannedEndMs) {
            val done = DetoxController.finishWithHistory(ctx, s, now)
            stop(ctx); notifyResult(ctx, done, rules)
            return
        }
        if (!DetoxController.hasUsageAccess(ctx)) {
            val broken = DetoxLogic.breakNow(s, now, BreakReason.PERMISSION_REVOKED)
            store.update(broken)
            stop(ctx); notifyResult(ctx, broken, rules)
            return
        }

        val fg = runCatching { UsageReader(ctx).currentForegroundApp(now, UsageReader.PERIODIC_LOOKBACK_MS) }.getOrNull()
        if (!StrictGuardService.running) StrictGuardService.startIfStrict(ctx)   // e.g. Android stopped it
        if (fg != null && rules.isBlocked(fg) && !StrictGuardService.running) {
            store.recordBlockedAttempt(s.id)
            remind(ctx, rules.label(fg))
        } else {
            NotificationManagerCompat.from(ctx).cancel(NOTIF_REMIND)
        }
        scheduleTick(ctx, ExistingWorkPolicy.APPEND_OR_REPLACE)   // runs after this job finishes
    }

    // ---- scheduling ------------------------------------------------------------------------------------

    private fun scheduleTick(ctx: Context, policy: ExistingWorkPolicy) {
        val req = OneTimeWorkRequestBuilder<DetoxWatchWorker>().setInitialDelay(TICK_MINUTES, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_TICK, policy, req)
    }

    private fun scheduleEnd(ctx: Context, s: DetoxSession) {
        val delay = (s.plannedEndMs - System.currentTimeMillis()).coerceAtLeast(0L) + 2_000L
        val req = OneTimeWorkRequestBuilder<DetoxWatchWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_END, ExistingWorkPolicy.REPLACE, req)
    }

    // ---- notifications ---------------------------------------------------------------------------------

    private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CH_ONGOING) == null) nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Digital detox (running)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Countdown while a digital detox is on"; setShowBadge(false)
            })
        if (nm.getNotificationChannel(CH_REMIND) == null) nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Digital detox reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When a paused app is open during your detox"
            })
        if (nm.getNotificationChannel(CH_RESULT) == null) nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Digital detox results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a detox promise is kept or broken"
            })
    }

    fun openDetox(ctx: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_DETOX)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "Close it": the member taps, so starting the home screen from here is allowed on every Android version. */
    fun goHome(ctx: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        ctx, code,
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The one countdown notification; also used by StrictGuardService as its foreground notification. */
    fun ongoingNotification(ctx: Context, s: DetoxSession, strict: Boolean): android.app.Notification {
        channels(ctx)
        val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.plannedEndMs))
        return NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Digital detox on until $endsAt")
            .setContentText(if (strict) "Strict mode: paused apps are locked. Calls and messages still work."
                else "Calls and messages are fine. We'll remind you if a paused app is opened.")
            .setWhen(s.plannedEndMs).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openDetox(ctx, 7303))
            .build()
    }

    private fun showOngoing(ctx: Context, s: DetoxSession) {
        if (!canNotify(ctx)) return
        channels(ctx)
        val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.plannedEndMs))
        val n = NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Digital detox on until $endsAt")
            .setContentText("Calls and messages are fine. We'll remind you if a paused app is opened.")
            .setWhen(s.plannedEndMs).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openDetox(ctx, 7303))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ONGOING, n) }
    }

    private fun remind(ctx: Context, appLabel: String) {
        if (!canNotify(ctx)) return
        channels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$appLabel is open during your detox")
            .setContentText("Close it now and your promise still stands.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openDetox(ctx, 7304))
            .addAction(0, "Close it", goHome(ctx, 7305))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_REMIND, n) }
    }

    fun notifyResult(ctx: Context, s: DetoxSession, rules: AppRules?) {
        if (!canNotify(ctx)) return
        channels(ctx)
        val (title, text) = when (s.outcome) {
            Outcome.KEPT -> "Promise kept: ${DetoxLogic.formatMinutes(s.plannedMinutes)} detox done" to
                "Shabash! You stayed off your phone the whole time."
            else -> "Promise broken" to when (s.reason) {
                BreakReason.PERMISSION_REVOKED -> "Usage access was turned off, so the detox couldn't be checked."
                BreakReason.USED_BLOCKED_APP -> "${rules?.label(s.brokenBy ?: "") ?: "An app"} was used during detox."
                else -> "Detox ended early after ${DetoxLogic.formatMinutes(s.keptMinutes(System.currentTimeMillis()))}."
            }
        }
        val n = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setAutoCancel(true).setContentIntent(openDetox(ctx, 7306))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_RESULT, n) }
    }
}
