package com.bas1minute.sync.phone

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Phone check: which apps cost you battery, data and privacy in the last 24 hours.
 * Plain Kotlin (no Android) so the rules can be unit tested. PhoneCheckReader collects the inputs.
 *
 * Honest by design: Android lets only system apps read exact battery per app. So battery per app is an
 * ESTIMATE from what Android does share (screen time, background running, data), scaled to the real
 * battery drop this phone measured. The screen always says so.
 */

enum class Perm(val label: String, val can: String, val sensitive: Boolean) {
    Location("Location", "see where you are", true),
    BackgroundLocation("Location all the time", "track where you are even when closed", true),
    Camera("Camera", "use your camera", true),
    Microphone("Microphone", "use your microphone", true),
    Contacts("Contacts", "read your contacts", true),
    Sms("SMS", "read your text messages", true),
    CallLogs("Call logs", "see who you call", true),
    Phone("Phone", "make and manage calls", false),
    Photos("Photos and files", "see your photos and files", false),
    Calendar("Calendar", "read your calendar", false),
    Activity("Physical activity", "see your movement", false),
    Nearby("Nearby devices", "find nearby devices", false),
    Notifications("Notifications", "send you notifications", false),
}

enum class Special(val label: String, val risk: String) {
    Accessibility("Accessibility service", "can see everything on your screen and tap for you"),
    NotificationAccess("Notification access", "can read all your notifications, including OTPs and messages"),
    DeviceAdmin("Device admin", "can lock or wipe the phone and is hard to uninstall"),
}

/** Everything known about one app for the last 24 hours. Bytes are rx + tx. */
data class AppUse(
    val pkg: String,
    val label: String,
    val system: Boolean = false,
    /** Installed from somewhere other than an app store (APK file, browser, WhatsApp…). */
    val sideloaded: Boolean = false,
    val foregroundMs: Long = 0,
    /** Time a background (foreground-service) task ran while the app wasn't on screen. Android 10+. */
    val backgroundMs: Long = 0,
    val lastUsedMs: Long? = null,
    val installedMs: Long? = null,
    val mobileFg: Long = 0,
    val mobileBg: Long = 0,
    val wifiFg: Long = 0,
    val wifiBg: Long = 0,
    val perms: Set<Perm> = emptySet(),
    val special: Set<Special> = emptySet(),
    /** "Unrestricted" / not optimised: Android won't hold it back in the background. */
    val unrestricted: Boolean = false,
) {
    val mobile: Long get() = mobileFg + mobileBg
    val wifi: Long get() = wifiFg + wifiBg
    val data: Long get() = mobile + wifi
    val bgData: Long get() = mobileBg + wifiBg
}

data class BatterySample(val t: Long, val level: Int, val charging: Boolean, val tempC: Float? = null)

data class BatteryReport(
    val dischargedPct: Double = 0.0,
    val chargedPct: Double = 0.0,
    val charges: Int = 0,
    /** % per hour with the screen on / off. Null until there's enough data. */
    val onRate: Double? = null,
    val offRate: Double? = null,
    val hoursCovered: Double = 0.0,
    val screenOnHours: Double = 0.0,
    val hoursLeft: Double? = null,
    val maxTempC: Float? = null,
    val level: Int? = null,
    val charging: Boolean = false,
)

/** One row of "Battery by app". [pct] = estimated % of battery in the window. */
data class AppBattery(val pkg: String?, val label: String, val pct: Double, val screenMs: Long, val backgroundMs: Long, val dataBytes: Long)

enum class Severity(val weight: Int, val label: String) { High(15, "Fix"), Medium(6, "Review"), Info(2, "Good to know") }
enum class Fix { AppSettings, BatteryOptimisation, None }

data class Finding(
    val id: String,
    val severity: Severity,
    val title: String,
    val detail: String,
    val pkg: String? = null,
    val fix: Fix = Fix.AppSettings,
    val fixLabel: String = "Open app settings",
)

data class Report(
    val score: Int,
    val findings: List<Finding>,
    val battery: BatteryReport,
    val appBattery: List<AppBattery>,
    val apps: List<AppUse>,
    val generatedAt: Long,
    val windowMs: Long,
    val mobileKnown: Boolean,
    val backgroundKnown: Boolean,
)

object PhoneCheck {
    const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MB = 1024.0 * 1024

    // Battery model for a typical phone, in % of battery. Scaled to this phone's measured drop.
    private const val SCREEN_PCT_PER_H = 10.0
    private const val BACKGROUND_PCT_PER_H = 3.0
    private const val MOBILE_PCT_PER_MB = 0.012
    private const val WIFI_PCT_PER_MB = 0.004
    private const val IDLE_PCT_PER_H = 0.6

    // ---------------------------------------------------------------- intervals

    /** Merge overlapping [start, end) pairs. */
    fun merge(intervals: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        for ((s, e) in intervals.filter { it.second > it.first }.sortedBy { it.first }) {
            val last = out.lastOrNull()
            if (last != null && s <= last.second) out[out.lastIndex] = last.first to max(last.second, e) else out += s to e
        }
        return out
    }

    fun total(intervals: List<Pair<Long, Long>>): Long = merge(intervals).sumOf { it.second - it.first }

    /** Time in [a] that isn't covered by [b] (background service time not on screen). */
    fun minus(a: List<Pair<Long, Long>>, b: List<Pair<Long, Long>>): Long {
        val mb = merge(b)
        var sum = 0L
        for ((s, e) in merge(a)) {
            var covered = 0L
            for ((bs, be) in mb) { val o = min(e, be) - max(s, bs); if (o > 0) covered += o }
            sum += (e - s - covered)
        }
        return sum
    }

    private fun overlap(s: Long, e: Long, merged: List<Pair<Long, Long>>): Long =
        merged.sumOf { (a, b) -> max(0L, min(e, b) - max(s, a)) }

    // ---------------------------------------------------------------- battery

    /**
     * Drain and charge from level samples, split into screen-on and screen-off rates using when the screen
     * was on. Samples come every ~15 min (longer at night when Android dozes; that's fine).
     */
    fun battery(samples: List<BatterySample>, screenOn: List<Pair<Long, Long>>, now: Long, windowMs: Long = DAY_MS): BatteryReport {
        val from = now - windowMs
        val s = samples.filter { it.t in from..now }.sortedBy { it.t }
        val on = merge(screenOn)
        val latest = samples.maxByOrNull { it.t }
        if (s.size < 2) return BatteryReport(level = latest?.level, charging = latest?.charging ?: false, maxTempC = s.mapNotNull { it.tempC }.maxOrNull())

        var discharged = 0.0; var charged = 0.0; var charges = 0
        var offDrop = 0.0; var offH = 0.0
        var allDrop = 0.0; var allH = 0.0; var allOnH = 0.0; var allOffH = 0.0
        for (i in 1 until s.size) {
            val a = s[i - 1]; val b = s[i]
            val d = (a.level - b.level).toDouble()
            if (b.charging && !a.charging) charges++
            if (d < 0) { charged += -d; continue }
            if (b.charging) continue
            discharged += d
            // Unplugged somewhere in between: real drain, but the rate's time base is unknown
            if (a.charging) continue
            val hours = (b.t - a.t) / 3_600_000.0
            if (hours <= 0) continue
            val onH = overlap(a.t, b.t, on) / 3_600_000.0
            allDrop += d; allH += hours; allOnH += onH; allOffH += hours - onH
            if (onH / hours < 0.1) { offDrop += d; offH += hours }
        }
        val offRate = if (offH >= 1.0) offDrop / offH else null
        val onRate = when {
            allOnH >= 0.5 && offRate != null -> max(0.0, (allDrop - offRate * allOffH) / allOnH)
            allOnH >= 0.5 && allH > 0 -> allDrop / allH
            else -> null
        }

        // Hours left at the recent pace (last 3 hours), else the day's pace
        val last = s.last()
        val recent = s.filter { it.t >= last.t - 3 * 3_600_000L && !it.charging }
        val recentRate = if (recent.size >= 2 && recent.last().t - recent.first().t >= 3_600_000L)
            (recent.first().level - recent.last().level) / ((recent.last().t - recent.first().t) / 3_600_000.0) else null
        val rate = (recentRate?.takeIf { it > 0.3 }) ?: (if (allH >= 2) allDrop / allH else null)
        val hoursLeft = if (!last.charging && rate != null && rate > 0.3) last.level / rate else null

        return BatteryReport(
            dischargedPct = discharged, chargedPct = charged, charges = charges,
            onRate = onRate, offRate = offRate, hoursCovered = (s.last().t - s.first().t) / 3_600_000.0,
            screenOnHours = total(on.map { max(it.first, from) to min(it.second, now) }) / 3_600_000.0,
            hoursLeft = hoursLeft, maxTempC = s.mapNotNull { it.tempC }.maxOrNull(),
            level = latest?.level, charging = latest?.charging ?: false,
        )
    }

    /** Estimated battery per app, scaled so the total matches the real drop when it's known. */
    fun appBattery(apps: List<AppUse>, battery: BatteryReport, windowMs: Long = DAY_MS): List<AppBattery> {
        fun units(a: AppUse) = a.foregroundMs / 3_600_000.0 * SCREEN_PCT_PER_H + a.backgroundMs / 3_600_000.0 * BACKGROUND_PCT_PER_H +
            a.mobile / MB * MOBILE_PCT_PER_MB + a.wifi / MB * WIFI_PCT_PER_MB
        val screenOffH = max(0.0, windowMs / 3_600_000.0 - battery.screenOnHours)
        val idle = screenOffH * IDLE_PCT_PER_H
        val model = apps.sumOf { units(it) } + idle
        val scale = if (battery.hoursCovered >= 6 && battery.dischargedPct > 2 && model > 0)
            (battery.dischargedPct / model).coerceIn(0.4, 2.5) else 1.0
        val rows = apps.map { AppBattery(it.pkg, it.label, units(it) * scale, it.foregroundMs, it.backgroundMs, it.data) }
            .filter { it.pct >= 0.1 }.toMutableList()
        if (idle > 0) rows += AppBattery(null, "Phone idle (screen off)", idle * scale, 0, 0, 0)
        return rows.sortedByDescending { it.pct }
    }

    // ---------------------------------------------------------------- findings

    fun findings(apps: List<AppUse>, b: BatteryReport, now: Long, mobileKnown: Boolean): List<Finding> {
        val out = ArrayList<Finding>()
        val user = apps.filter { !it.system }

        // Battery itself
        b.offRate?.let { r ->
            if (r >= 1.5) out += Finding("drain_off", if (r >= 2.5) Severity.High else Severity.Medium,
                "Battery drains with the screen off",
                "It drops ${pct(r)} an hour while you aren’t using the phone (healthy is under 1%). Check apps running in the background below.",
                fix = Fix.BatteryOptimisation, fixLabel = "Battery settings")
        }
        b.maxTempC?.let { if (it >= 42f) out += Finding("hot", if (it >= 45f) Severity.High else Severity.Medium,
            "Battery got hot (${it.roundToInt()}°C)",
            "Heat wears batteries out. Avoid charging under a pillow or gaming while charging.", fix = Fix.None, fixLabel = "") }
        if (b.charges >= 3) out += Finding("charges", Severity.Info, "Charged ${b.charges} times today",
            "If you need to charge this often, the apps below show where the battery goes.", fix = Fix.None, fixLabel = "")

        for (a in apps) {
            val unused = a.foregroundMs < 2 * 60_000L
            // Background data
            if (mobileKnown && a.mobileBg >= 50 * MB) {
                out += Finding("bgdata_${a.pkg}", if (a.mobileBg >= 200 * MB || unused) Severity.High else Severity.Medium,
                    "${a.label} used ${bytes(a.mobileBg)} of mobile data in the background",
                    (if (unused) "You barely opened it today. " else "") + "Turn off “Background data” for it to save your data pack.",
                    a.pkg, fixLabel = "Open data settings")
            } else if (a.bgData >= 300 * MB && unused) {
                out += Finding("bgwifi_${a.pkg}", Severity.Medium, "${a.label} downloaded ${bytes(a.bgData)} in the background",
                    "Possibly updates or backups. Fine on Wi-Fi, but worth knowing.", a.pkg)
            }
            // Background running
            if (a.backgroundMs >= 3 * 3_600_000L && !a.pkg.startsWith("com.bas1minute")) {
                out += Finding("bgrun_${a.pkg}", if (a.backgroundMs >= 8 * 3_600_000L && unused) Severity.High else Severity.Medium,
                    "${a.label} ran in the background for ${hm(a.backgroundMs)}",
                    if (unused) "You didn’t use it today. Set its battery to “Restricted” or “Optimised”." else
                        "Normal for music, navigation or fitness apps. Otherwise set its battery to “Optimised”.",
                    a.pkg, fixLabel = "Open battery for app")
            }
            if (a.unrestricted && !a.system && (a.backgroundMs >= 3_600_000L || a.bgData >= 20 * MB)) {
                out += Finding("unres_${a.pkg}", Severity.Medium, "${a.label} has no battery limits",
                    "It’s set to “Unrestricted” and was busy in the background today.", a.pkg, fixLabel = "Open battery for app")
            }
        }

        for (a in user) {
            // High-risk special access
            for (sp in a.special) out += Finding("special_${sp.name}_${a.pkg}", if (a.sideloaded) Severity.High else Severity.Medium,
                "${a.label} has ${sp.label.lowercase()}",
                "It ${sp.risk}. Keep it only if you trust this app" + (if (a.sideloaded) " — it wasn’t installed from an app store." else "."), a.pkg)
            // Unused apps keeping sensitive permissions
            val idleDays = a.lastUsedMs?.let { ((now - it) / DAY_MS).toInt() }
            val sensitive = a.perms.filter { it.sensitive && it != Perm.BackgroundLocation }
            if (sensitive.isNotEmpty() && idleDays != null && idleDays >= 30) {
                out += Finding("unused_${a.pkg}", Severity.Medium, "${a.label}: not opened in $idleDays days",
                    "It can still ${list(sensitive.map { it.can })}. Remove those permissions, or uninstall it.", a.pkg)
            }
            if (Perm.BackgroundLocation in a.perms) out += Finding("bgloc_${a.pkg}", Severity.Medium,
                "${a.label} can track your location all the time", "Most apps only need it “while using the app”.", a.pkg)
            if (a.sideloaded && a.special.isEmpty() && (Perm.Sms in a.perms || Perm.CallLogs in a.perms || Perm.Contacts in a.perms)) {
                out += Finding("sideload_${a.pkg}", Severity.Medium, "${a.label} came from outside an app store",
                    "It can ${list(a.perms.filter { it == Perm.Sms || it == Perm.CallLogs || it == Perm.Contacts }.map { it.can })}. Fake apps shared as APK files often steal OTPs.", a.pkg)
            }
        }
        val unres = user.count { it.unrestricted }
        if (unres >= 6) out += Finding("many_unres", Severity.Info, "$unres apps have no battery limits",
            "Each can keep the phone awake. Keep this only for apps that must run all the time.", fix = Fix.BatteryOptimisation, fixLabel = "Battery settings")

        return out.distinctBy { it.id }.sortedWith(compareBy<Finding> { it.severity.ordinal }.thenBy { it.title })
    }

    /** 100 = nothing to fix. Each finding costs points; the same kind of problem counts less the more it repeats. */
    fun score(findings: List<Finding>): Int {
        var s = 100.0
        findings.groupBy { it.severity }.forEach { (sev, list) ->
            list.forEachIndexed { i, _ -> s -= sev.weight * (if (i < 3) 1.0 else 0.4) }
        }
        return s.roundToInt().coerceIn(0, 100)
    }

    fun scoreLabel(score: Int) = when { score >= 85 -> "Healthy"; score >= 60 -> "Needs a look"; else -> "Needs attention" }

    fun analyze(apps: List<AppUse>, samples: List<BatterySample>, screenOn: List<Pair<Long, Long>>, now: Long,
                mobileKnown: Boolean, backgroundKnown: Boolean): Report {
        val b = battery(samples, screenOn, now)
        val f = findings(apps, b, now, mobileKnown)
        return Report(score(f), f, b, appBattery(apps, b), apps, now, DAY_MS, mobileKnown, backgroundKnown)
    }

    // ---------------------------------------------------------------- format

    fun bytes(b: Long): String = when {
        b >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", b / (1024.0 * 1024 * 1024))
        b >= 1024L * 1024 -> String.format(Locale.US, "%.0f MB", b / MB)
        b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        b > 0 -> "$b B"
        else -> "0"
    }

    fun hm(ms: Long): String {
        val m = (ms / 60_000L).toInt(); val h = m / 60
        return when { h > 0 && m % 60 > 0 -> "${h}h ${m % 60}m"; h > 0 -> "${h}h"; else -> "${m}m" }
    }

    fun pct(x: Double): String = if (x < 10) String.format(Locale.US, "%.1f%%", x) else "${x.roundToInt()}%"

    private fun list(items: List<String>): String = when (items.size) {
        0 -> ""; 1 -> items[0]; 2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }
}
