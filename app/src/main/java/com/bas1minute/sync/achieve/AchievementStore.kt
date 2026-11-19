package com.bas1minute.sync.achieve

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
import com.bas1minute.sync.AppFeature
import com.bas1minute.sync.FeatureTips
import com.bas1minute.sync.LocalDb
import com.bas1minute.sync.MainActivity
import com.bas1minute.sync.Prefs
import com.bas1minute.sync.R
import java.time.LocalDate

/** Reads the step history for badges/records, and posts "New badge!" once per newly earned badge. */
object AchievementStore {
    private const val CHANNEL = "badges"
    private const val NOTIF = 7800

    fun stepDays(ctx: Context): List<StepDay> {
        val db = LocalDb.get(ctx)
        val first = db.firstStepsDay() ?: return emptyList()
        return db.history("steps", first, LocalDate.now(), LocalDb.Bucket.DAY, 0)
            .map { StepDay(it.start, it.total) }
    }

    fun badges(ctx: Context): List<Badge> = Achievements.badges(stepDays(ctx), Prefs(ctx).stepGoal)

    /** From the 15-minute tick. The first run only records what's already earned, so nobody gets a flood. */
    fun checkNew(ctx: Context) {
        val prefs = Prefs(ctx)
        val earned = badges(ctx).filter { it.earnedOn != null }
        val seen = prefs.seenBadges
        val fresh = earned.filter { it.id !in seen }
        if (fresh.isEmpty()) return
        prefs.seenBadges = seen + fresh.map { it.id }
        if (!prefs.badgesInitialised) { prefs.badgesInitialised = true; return }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val b = fresh.last()
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Badges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When you earn a new badge"
            })
        }
        val open = PendingIntent.getActivity(ctx, NOTIF, Intent(ctx, MainActivity::class.java)
            .putExtra(FeatureTips.EXTRA_FEATURE, AppFeature.HISTORY.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            NotificationManagerCompat.from(ctx).notify(NOTIF, NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New badge: ${b.title} ${b.emoji}")
                .setContentText("${b.detail}. Shabash! Tap to see all your badges.")
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
