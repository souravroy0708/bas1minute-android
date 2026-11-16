package com.bas1minute.sync.schedule

import android.content.Context
import com.bas1minute.sync.detox.DetoxController
import com.bas1minute.sync.detox.DetoxStore
import java.time.LocalDateTime

/**
 * The auto-start/auto-end half of Schedules. Reuses DetoxController.start() directly: a scheduled detox
 * blocks exactly what the member already chose to block in Detox itself, nothing to set up twice. Never
 * interferes with a detox the member started by hand.
 *
 * No periodic job of its own: this rides on the single shared 15-minute tick in phone/BatteryLog.kt
 * (BatteryWorker) — see its own doc for why, and SessionGuardChecker for the same pattern. Returning
 * immediately when there are no schedules (the common case for most members) costs nothing beyond that
 * already-happening tick, rather than adding a whole separate always-on job just in case one gets added later.
 */
object ScheduleChecker {
    /** Whether a detox is active right now because of a schedule — callers use this to skip their own checks
     * for the same tick, the same way they already would during a detox started by hand. */
    fun check(ctx: Context, now: Long): Boolean {
        val scheduleStore = ScheduleStore.get(ctx)
        val schedules = scheduleStore.all()
        if (schedules.isEmpty()) return false
        val dateTime = LocalDateTime.now()
        val dayOfWeek = dateTime.dayOfWeek.value   // 1 (Monday) .. 7 (Sunday) — matches Schedule.days' own numbering
        val minuteOfDay = dateTime.hour * 60 + dateTime.minute
        val active = ScheduleLogic.activeSchedule(schedules, dayOfWeek, minuteOfDay)

        val detoxStore = DetoxStore.get(ctx)
        val running = detoxStore.active()

        if (active == null) {
            // No schedule should be running right now: if one of OUR OWN scheduled sessions is still active
            // (its window ended since the last check, 15 minutes ago at most), end it — never a session the
            // member started by hand themselves, which leaves activeScheduleId at -1.
            if (running != null && scheduleStore.activeScheduleId != -1L) {
                DetoxController.finishWithHistory(ctx, running, now)
                scheduleStore.activeScheduleId = -1L
            }
            return false
        }

        if (running != null) return true   // something's already running (scheduled or by hand) — leave it alone

        val minutes = ScheduleLogic.minutesUntilEnd(active, dayOfWeek, minuteOfDay).coerceIn(1, 12 * 60)
        val session = DetoxController.start(ctx, minutes, why = "Scheduled: ${active.label}")
        if (session != null) scheduleStore.activeScheduleId = active.id
        return session != null
    }
}
