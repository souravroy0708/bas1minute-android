package com.bas1minute.sync

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Home-screen widget: today's steps vs goal, distance and streak, from the phone's own database. */
class StepsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        /** Call after new phone data is saved (the app, the sync worker). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StepsWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val prefs = Prefs(context)
            val db = LocalDb.get(context)
            val today = LocalDate.now()
            val steps = db.day(today)?.steps ?: 0L
            val goal = prefs.stepGoal
            val last = db.days(today.minusDays(29), today).associateBy { it.date }
            val streak = com.bas1minute.sync.ui.TwentyOneStats.streak((29 downTo 0).map { last[today.minusDays(it.toLong())]?.steps }, goal)
            val nf = NumberFormat.getIntegerInstance(Locale("en", "IN"))

            return RemoteViews(context.packageName, R.layout.widget_steps).apply {
                setTextViewText(R.id.widget_steps, nf.format(steps))
                setTextViewText(R.id.widget_sub, "of ${nf.format(goal)} steps · ${Distance.format(Distance.km(steps, prefs.stepLengthCm))}")
                setTextViewText(R.id.widget_streak, if (streak > 0) "🔥 $streak-day streak" else if (steps >= goal) "Goal done ✓" else "")
                setProgressBar(R.id.widget_progress, 100, ((steps * 100) / goal.coerceAtLeast(1)).toInt().coerceIn(0, 100), false)
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
