package com.bas1minute.sync

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One day of phone use, worked out on the phone. Only the totals and two times are ever sent. */
data class DayUsage(
    val screenMinutes: Int,
    val socialMinutes: Int,
    /** App name → minutes, social apps only. Shown on this phone only, never sent. */
    val socialApps: Map<String, Int>,
)

/** The night before a morning: when the phone was last used and first used again. */
data class Night(val lastUse: LocalTime, val firstUse: LocalTime, val phoneFreeMinutes: Int)

class ScreenTimeReader(private val context: Context) {

    private companion object {
        const val RESUMED = 1   // ACTIVITY_RESUMED (API 29+), same value as MOVE_TO_FOREGROUND before
        const val PAUSED = 2    // ACTIVITY_PAUSED (API 29+), same value as MOVE_TO_BACKGROUND before
        const val STOPPED = 23  // ACTIVITY_STOPPED (API 29+)
        const val MIN_NIGHT_MINUTES = 180

        /**
         * Apps counted as "screen time" on the home screen: social media, short video, messaging and browsers
         * (the apps people lose time in), popular in India. Plus anything Android labels as a social app.
         */
        val SOCIAL = setOf(
            // Messaging
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.facebook.orca", "com.facebook.mlite",
            "com.Slack", "jp.naver.line.android", "com.discord",
            // Browsers
            "com.android.chrome", "com.chrome.beta", "com.sec.android.app.sbrowser", "org.mozilla.firefox",
            "com.opera.browser", "com.opera.mini.native", "com.microsoft.emmx", "com.brave.browser",
            "com.mi.globalbrowser", "com.heytap.browser", "com.UCMobile.intl", "com.google.android.googlequicksearchbox",
            // Social and short video
            "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.facebook.lite",
            "com.google.android.youtube", "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage",
            "com.pinterest", "com.linkedin.android", "com.tumblr", "in.mohalla.sharechat", "in.mohalla.video",
            "com.eterno.shortvideos", "com.roposo.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.next.innovation.takatak", "com.josh.app", "tv.twitch.android.app", "com.discord",
        )
    }

    private val pm = context.packageManager
    private val categoryCache = HashMap<String, Boolean>()
    private val labelCache = HashMap<String, String>()

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Kept for older callers: screen minutes only. */
    fun minutesFor(date: LocalDate): Int? = dayUsage(date)?.screenMinutes

    /**
     * Screen time and social media time for a day. Null when usage access is off, or Android has
     * no usage data for that day (it trims old history), so saved numbers are never replaced by 0.
     */
    fun dayUsage(date: LocalDate): DayUsage? {
        if (!hasAccess()) return null
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        if (end <= start) return null
        val intervals = foregroundIntervals(start, end) ?: return null
        var total = 0L
        val perPackage = HashMap<String, Long>()
        for ((pkg, s, e) in intervals) {
            total += e - s
            perPackage[pkg] = (perPackage[pkg] ?: 0L) + (e - s)
        }
        val social = perPackage.filterKeys { isSocial(it) }
        return DayUsage(
            screenMinutes = (total / 60_000L).toInt().coerceIn(0, 24 * 60),
            socialMinutes = (social.values.sum() / 60_000L).toInt().coerceIn(0, 24 * 60),
            socialApps = social.mapKeys { label(it.key) }.mapValues { (it.value / 60_000L).toInt() }
                .filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMap(),
        )
    }

    /**
     * For the morning of [date]: the longest break in phone use between 6 pm the evening before and
     * 1 pm that day. Its start is the last use before sleep; its end is the first use after waking.
     * Needs at least 3 hours, and phone use after the break (so it isn't counted while you're still asleep).
     */
    fun night(date: LocalDate): Night? {
        if (!hasAccess()) return null
        val zone = ZoneId.systemDefault()
        val start = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val end = minOf(date.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        if (end <= start) return null
        val intervals = foregroundIntervals(start, end)?.map { it.second to it.third }?.sortedBy { it.first } ?: return null
        if (intervals.size < 2) return null

        // Merge overlapping use, then find the longest gap between uses
        val merged = ArrayList<LongArray>()
        for ((s, e) in intervals) {
            val last = merged.lastOrNull()
            if (last != null && s <= last[1]) last[1] = maxOf(last[1], e) else merged += longArrayOf(s, e)
        }
        var best: Pair<Long, Long>? = null
        for (i in 0 until merged.size - 1) {
            val gapStart = merged[i][1]
            val gapEnd = merged[i + 1][0]
            if (best == null || gapEnd - gapStart > best.second - best.first) best = gapStart to gapEnd
        }
        val (lastUse, firstUse) = best ?: return null
        val minutes = ((firstUse - lastUse) / 60_000L).toInt()
        if (minutes < MIN_NIGHT_MINUTES) return null
        fun toTime(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().withSecond(0).withNano(0)
        return Night(toTime(lastUse), toTime(firstUse), minutes)
    }

    /** (package, start, end) for every stretch an app was on screen, or null if Android has no data. */
    private fun foregroundIntervals(start: Long, end: Long): List<Triple<String, Long, Long>>? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ignored = setOfNotNull(context.packageName, homePackage(), "com.android.systemui")
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val openSince = HashMap<String, Pair<String, Long>>()
        val out = ArrayList<Triple<String, Long, Long>>()
        var seenAny = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            seenAny = true
            val pkg = event.packageName ?: continue
            if (pkg in ignored) continue
            val key = pkg + "/" + (event.className ?: "")
            when (event.eventType) {
                RESUMED -> openSince[key] = pkg to event.timeStamp
                PAUSED, STOPPED -> openSince.remove(key)?.let { (p, s) -> if (event.timeStamp > s) out += Triple(p, s, event.timeStamp) }
            }
        }
        if (!seenAny) return null
        openSince.values.forEach { (p, s) -> if (end > s) out += Triple(p, s, end) }
        return out
    }

    private fun isSocial(pkg: String): Boolean = categoryCache.getOrPut(pkg) {
        pkg in SOCIAL || try {
            pm.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_SOCIAL
        } catch (e: Exception) {
            false
        }
    }

    private fun label(pkg: String): String = labelCache.getOrPut(pkg) {
        try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg.substringAfterLast('.') }
    }

    private fun homePackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }
}
