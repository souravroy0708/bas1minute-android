package com.bas1minute.sync

import android.content.Context
import android.content.Intent
import android.database.Cursor
import androidx.core.content.FileProvider
import com.bas1minute.sync.detox.AppRules
import com.bas1minute.sync.detox.DetoxStore
import com.bas1minute.sync.detox.Outcome
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * "Export my data": everything the phone has saved, for any date range, in two shapes:
 *  - FOR AI: one Markdown file — a short summary, ready-made questions, then each table as CSV — sized to paste
 *    or attach in Claude, ChatGPT or Gemini for a deeper look at steps, sleep and phone habits;
 *  - SPREADSHEET: a .zip of CSV files that open in Google Sheets or Excel.
 * Built entirely on the phone. It goes only where the member sends it. GPS routes are never included (only
 * each walk's distance and time), and the diary is left out unless the member ticks it.
 */
object AiExport {

    enum class Part(val title: String, val defaultOn: Boolean) {
        DAYS("Steps, screen time, sleep, water, weight (daily)", true),
        APPS("Phone use by app (monthly)", true),
        WALKS("Walks, runs and rides (no GPS points)", true),
        DETOX("Digital detox sessions", true),
        GOALS("Goals and life goals", true),
        DIARY("Diary reflections and notes (private)", false),
    }

    data class Options(val from: LocalDate, val to: LocalDate, val parts: Set<Part>)

    data class Result(val file: File, val days: Int)

    private val SKIP_DAY_COLS = setOf("dirty", "updated_at", "synced_at")
    private val D = DateTimeFormatter.ofPattern("d MMM yyyy")

    // ------------------------------------------------------------------------------------------------ tables

    private class Table(val name: String, val header: List<String>, val rows: List<List<String>>)

    private fun csvCell(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun Table.csv(): String = buildString {
        append(header.joinToString(",") { csvCell(it) }).append('\n')
        rows.forEach { r -> append(r.joinToString(",") { csvCell(it) }).append('\n') }
    }

    private fun Cursor.str(i: Int): String = if (isNull(i)) "" else getString(i)

    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().withNano(0).toString().take(5)

    private fun tables(ctx: Context, o: Options): List<Table> {
        val db = LocalDb.get(ctx).readableDatabase
        val prefs = Prefs(ctx)
        val f = o.from.toString(); val t = o.to.toString()
        val out = ArrayList<Table>()

        if (Part.DAYS in o.parts) {
            db.rawQuery("SELECT * FROM days WHERE date BETWEEN ? AND ? ORDER BY date", arrayOf(f, t)).use { c ->
                val cols = c.columnNames.withIndex().filter { it.value !in SKIP_DAY_COLS && !it.value.endsWith("_source") }
                val stepsIdx = c.columnNames.indexOf("steps")
                val header = cols.map { it.value } + "distance_km"
                val rows = buildList {
                    while (c.moveToNext()) {
                        val km = if (stepsIdx >= 0 && !c.isNull(stepsIdx)) "%.2f".format(Locale.US, c.getLong(stepsIdx) * prefs.stepLengthCm / 100_000.0) else ""
                        add(cols.map { (i, _) -> c.str(i) } + km)
                    }
                }
                out += Table("daily", header, rows)
            }
        }
        if (Part.APPS in o.parts) {
            val rules = AppRules(ctx, emptySet(), emptySet())
            val rows = ArrayList<List<String>>()
            // Recent months come from the daily per-app table, older ones from the monthly roll-up
            db.rawQuery(
                "SELECT m, app, SUM(mins) FROM (" +
                    "SELECT substr(date, 1, 7) AS m, app, minutes AS mins FROM app_usage WHERE date BETWEEN ? AND ? " +
                    "UNION ALL SELECT month AS m, app, minutes AS mins FROM app_usage_month WHERE month BETWEEN ? AND ?" +
                    ") GROUP BY m, app ORDER BY m, SUM(mins) DESC",
                arrayOf(f, t, f.take(7), t.take(7)),
            ).use { c -> while (c.moveToNext()) rows += listOf(c.str(0), rules.label(c.str(1)), c.str(1), c.str(2)) }
            out += Table("apps_by_month", listOf("month", "app", "package", "minutes"), rows)
        }
        if (Part.WALKS in o.parts) {
            val fromMs = o.from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val toMs = o.to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val rows = ArrayList<List<String>>()
            db.rawQuery("SELECT type, title, started_at, ended_at, distance_m, moving_ms, elapsed_ms, elev_gain_m, max_speed FROM activities " +
                "WHERE ended_at IS NOT NULL AND started_at >= ? AND started_at < ? ORDER BY started_at", arrayOf(fromMs.toString(), toMs.toString())).use { c ->
                while (c.moveToNext()) {
                    val km = c.getDouble(4) / 1000.0; val movingMin = c.getLong(5) / 60_000.0
                    val pace = if (km > 0.05) "%.1f".format(Locale.US, movingMin / km) else ""
                    rows += listOf(date(c.getLong(2)).toString(), time(c.getLong(2)), c.str(0), c.str(1), "%.2f".format(Locale.US, km),
                        "%.0f".format(Locale.US, movingMin), "%.0f".format(Locale.US, c.getLong(6) / 60_000.0), pace,
                        "%.0f".format(Locale.US, c.getDouble(7)), "%.1f".format(Locale.US, c.getDouble(8) * 3.6))
                }
            }
            out += Table("walks_runs", listOf("date", "start_time", "type", "title", "distance_km", "moving_min", "total_min",
                "pace_min_per_km", "elevation_gain_m", "max_speed_kmh"), rows)
        }
        if (Part.DETOX in o.parts) {
            val rules = AppRules(ctx, emptySet(), emptySet())
            val rows = DetoxStore.get(ctx).sessions()
                .filter { it.outcome != Outcome.ACTIVE && date(it.startMs) in o.from..o.to }
                .sortedBy { it.startMs }
                .map { s ->
                    listOf(date(s.startMs).toString(), time(s.startMs), s.plannedMinutes.toString(),
                        ((s.endMs ?: s.plannedEndMs) - s.startMs).div(60_000).toString(), s.outcome.name.lowercase(),
                        s.reason?.name?.lowercase() ?: "", s.brokenBy?.let { rules.label(it) } ?: "", s.blockedAttempts.toString(), s.why)
                }
            out += Table("detox_sessions", listOf("date", "start_time", "planned_min", "actual_min", "outcome", "reason", "broken_by_app",
                "times_stopped", "why"), rows)
        }
        if (Part.GOALS in o.parts) {
            val goals = ArrayList<List<String>>()
            db.rawQuery("SELECT g.id, g.kind, g.title, g.target, g.created_at, g.archived_at, " +
                "(SELECT COUNT(*) FROM goal_checkins c WHERE c.goal_id = g.id AND c.done = 1 AND c.date BETWEEN ? AND ?) FROM goals g ORDER BY g.created_at",
                arrayOf(f, t)).use { c ->
                while (c.moveToNext()) goals += listOf(c.str(2), c.str(1), c.str(3), date(c.getLong(4)).toString(),
                    if (c.isNull(5)) "" else date(c.getLong(5)).toString(), c.str(6))
            }
            out += Table("daily_goals", listOf("goal", "kind", "target", "created", "archived", "manual_checkins_done_in_range"), goals)
            val life = ArrayList<List<String>>()
            db.rawQuery("SELECT l.horizon, l.title, l.why, l.plan, l.target_date, l.started_at, l.achieved_at, " +
                "(SELECT COUNT(*) FROM life_goal_milestones m WHERE m.goal_id = l.id), " +
                "(SELECT COUNT(*) FROM life_goal_milestones m WHERE m.goal_id = l.id AND m.done = 1) " +
                "FROM life_goals l WHERE l.archived_at IS NULL ORDER BY l.started_at", null).use { c ->
                while (c.moveToNext()) life += listOf(c.str(0), c.str(1), c.str(2), c.str(3), c.str(4), date(c.getLong(5)).toString(),
                    if (c.isNull(6)) "" else date(c.getLong(6)).toString(), c.str(7), c.str(8))
            }
            out += Table("life_goals", listOf("horizon", "goal", "why", "plan", "target_date", "started", "achieved", "milestones", "milestones_done"), life)
        }
        if (Part.DIARY in o.parts) {
            val rows = ArrayList<List<String>>()
            db.rawQuery("SELECT date, type, title, category, effort, satisfaction, story FROM diary_entries WHERE date BETWEEN ? AND ? ORDER BY date",
                arrayOf(f, t)).use { c -> while (c.moveToNext()) rows += (0..6).map { c.str(it) } }
            out += Table("diary", listOf("date", "type", "title", "category", "effort_1to5", "satisfaction_1to5", "note"), rows)
        }
        return out
    }

    // ------------------------------------------------------------------------------------------------ files

    private fun shareDir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.filter { it.name.startsWith("bas1minute-") }?.forEach { it.delete() } }

    private fun stem(o: Options) = "bas1minute-${o.from}-to-${o.to}"

    /** One Markdown file made for AI chat apps. */
    fun forAi(ctx: Context, o: Options): Result {
        val prefs = Prefs(ctx)
        val tabs = tables(ctx, o)
        val n = NumberFormat.getIntegerInstance(Locale("en", "IN"))
        val daily = tabs.firstOrNull { it.name == "daily" }
        val md = buildString {
            append("# My Bas 1 Minute data: ${o.from.format(D)} to ${o.to.format(D)}\n\n")
            append("Exported from the Bas 1 Minute app on ${LocalDate.now().format(D)}. Everything below was recorded on my own phone.\n")
            append("My daily step goal is ${n.format(prefs.stepGoal)} steps")
            append(". Social-media time counts apps like Instagram, YouTube and WhatsApp; screen time is the whole phone. Times are in minutes.\n\n")

            if (daily != null && daily.rows.isNotEmpty()) {
                val col = { name: String -> daily.header.indexOf(name) }
                fun nums(name: String) = col(name).takeIf { it >= 0 }?.let { i -> daily.rows.mapNotNull { it[i].toDoubleOrNull() } } ?: emptyList()
                val steps = nums("steps"); val screen = nums("screen_minutes"); val social = nums("social_minutes"); val sleep = nums("sleep_minutes")
                append("## Summary\n\n")
                append("- Days with data: ${daily.rows.size}\n")
                if (steps.isNotEmpty()) {
                    append("- Steps: average ${n.format(steps.average().toLong())} a day; best ${n.format(steps.max().toLong())}; ")
                    append("goal reached on ${steps.count { it >= prefs.stepGoal }} of ${steps.size} days\n")
                }
                if (screen.isNotEmpty()) append("- Screen time: average ${screen.average().toInt()} min a day\n")
                if (social.isNotEmpty()) append("- Social media: average ${social.average().toInt()} min a day\n")
                if (sleep.isNotEmpty()) append("- Sleep: average ${"%.1f".format(Locale.US, sleep.average() / 60)} h a night\n")
                append('\n')
            }

            append("## Questions to ask (copy one, or ask your own)\n\n")
            append("- Look at my daily data. Which days of the week am I most and least active, and why might that be?\n")
            append("- Is there a link between my screen time or late phone use and my steps or sleep the next day?\n")
            append("- Which apps take the most of my time, and how has that changed month by month?\n")
            append("- Based on my best weeks, what 3 small, specific habits would help me reach my step goal more often?\n")
            append("- Make a simple 4-week plan to cut social media by 30 minutes a day, using my own numbers.\n\n")
            append("Please answer using only the data below, and say when the data isn't enough to be sure.\n\n")

            for (tb in tabs) {
                append("## ").append(tb.name.replace('_', ' ')).append(" (${tb.rows.size} rows)\n\n")
                if (tb.rows.isEmpty()) { append("No data in this range.\n\n"); continue }
                append("```csv\n").append(tb.csv()).append("```\n\n")
            }
        }
        val file = File(shareDir(ctx), "${stem(o)}-for-ai.md")
        file.writeText(md)
        return Result(file, daily?.rows?.size ?: 0)
    }

    /** A .zip of CSV files for Google Sheets or Excel. */
    fun spreadsheets(ctx: Context, o: Options): Result {
        val tabs = tables(ctx, o)
        val file = File(shareDir(ctx), "${stem(o)}-csv.zip")
        ZipOutputStream(file.outputStream()).use { z ->
            for (tb in tabs) {
                z.putNextEntry(ZipEntry("${tb.name}.csv")); z.write(tb.csv().toByteArray()); z.closeEntry()
            }
        }
        return Result(file, tabs.firstOrNull { it.name == "daily" }?.rows?.size ?: 0)
    }

    fun shareIntent(ctx: Context, r: Result, mime: String): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", r.file)
        val send = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "My Bas 1 Minute data")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Send to Claude, ChatGPT, Drive…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The first day this phone has any data (for "All time"). */
    fun firstDay(ctx: Context): LocalDate = runCatching {
        LocalDb.get(ctx).readableDatabase.rawQuery("SELECT MIN(date) FROM days", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) LocalDate.parse(c.getString(0)) else null
        }
    }.getOrNull() ?: LocalDate.now()
}
