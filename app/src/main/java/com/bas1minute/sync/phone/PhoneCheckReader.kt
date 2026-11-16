package com.bas1minute.sync.phone

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.bas1minute.sync.ScreenTimeReader

/**
 * Collects the last 24 hours from what Android lets an app with Usage access see:
 * on-screen time and background services (usage events), data per app split foreground/background
 * (network stats), permissions and special access (package manager), battery exemptions (power manager).
 * Nothing here leaves the phone.
 *
 * Limits, shown on screen: exact battery per app is system-only; background-service times need Android 10+;
 * mobile data per app needs Android 10+; apps are visible if they have a home-screen icon.
 */
class PhoneCheckReader(private val ctx: Context) {
    private val pm = ctx.packageManager

    companion object {
        private const val RESUMED = 1
        private const val PAUSED = 2
        private const val STOPPED = 23
        private const val SCREEN_ON = 15        // SCREEN_INTERACTIVE, Android 9+
        private const val SCREEN_OFF = 16       // SCREEN_NON_INTERACTIVE
        private const val FGS_START = 19        // FOREGROUND_SERVICE_START, Android 10+
        private const val FGS_STOP = 20
        private val STORES = setOf(
            "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.xiaomi.mipicks",
            "com.xiaomi.market", "com.heytap.market", "com.oppo.market", "com.vivo.appstore", "com.huawei.appmarket",
            "com.oneplus.market", "com.google.android.feedback",
        )
        private val PERMS = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to Perm.Location, Manifest.permission.ACCESS_COARSE_LOCATION to Perm.Location,
            "android.permission.ACCESS_BACKGROUND_LOCATION" to Perm.BackgroundLocation,
            Manifest.permission.CAMERA to Perm.Camera, Manifest.permission.RECORD_AUDIO to Perm.Microphone,
            Manifest.permission.READ_CONTACTS to Perm.Contacts, Manifest.permission.WRITE_CONTACTS to Perm.Contacts,
            Manifest.permission.READ_SMS to Perm.Sms, Manifest.permission.RECEIVE_SMS to Perm.Sms, Manifest.permission.SEND_SMS to Perm.Sms,
            Manifest.permission.READ_CALL_LOG to Perm.CallLogs, Manifest.permission.WRITE_CALL_LOG to Perm.CallLogs,
            Manifest.permission.READ_PHONE_STATE to Perm.Phone, Manifest.permission.CALL_PHONE to Perm.Phone,
            "android.permission.READ_PHONE_NUMBERS" to Perm.Phone, "android.permission.ANSWER_PHONE_CALLS" to Perm.Phone,
            Manifest.permission.READ_EXTERNAL_STORAGE to Perm.Photos, "android.permission.READ_MEDIA_IMAGES" to Perm.Photos,
            "android.permission.READ_MEDIA_VIDEO" to Perm.Photos, "android.permission.READ_MEDIA_AUDIO" to Perm.Photos,
            Manifest.permission.READ_CALENDAR to Perm.Calendar, Manifest.permission.WRITE_CALENDAR to Perm.Calendar,
            "android.permission.ACTIVITY_RECOGNITION" to Perm.Activity, Manifest.permission.BODY_SENSORS to Perm.Activity,
            "android.permission.BLUETOOTH_SCAN" to Perm.Nearby, "android.permission.BLUETOOTH_CONNECT" to Perm.Nearby,
            "android.permission.NEARBY_WIFI_DEVICES" to Perm.Nearby, "android.permission.POST_NOTIFICATIONS" to Perm.Notifications,
        )

        /** Last full report, so reopening the screen is instant. */
        @Volatile var cached: Report? = null
    }

    fun hasAccess(): Boolean = ScreenTimeReader(ctx).hasAccess()

    /** Builds the full report (a second or two on the IO thread). Null without Usage access. */
    fun report(): Report? {
        if (!hasAccess()) return null
        try { BatteryLog.record(ctx) } catch (e: Exception) { }
        val now = System.currentTimeMillis()
        val from = now - PhoneCheck.DAY_MS
        val ev = events(from, now)
        val net = network(from, now)
        val last = lastUsed(now)
        val special = specialAccess()
        val packages = installed()

        val pkgs = HashSet<String>().apply {
            addAll(packages.keys); addAll(ev.foreground.keys); addAll(ev.service.keys); addAll(net.keys)
        }
        pkgs.remove("android")
        val power = ctx.getSystemService(PowerManager::class.java)
        val apps = pkgs.mapNotNull { pkg ->
            val pi = packages[pkg]
            val ai = pi?.applicationInfo ?: try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
            val fg = ev.foreground[pkg].orEmpty()
            val svc = ev.service[pkg].orEmpty()
            val n = net[pkg]
            val system = ai == null || (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val installed = pi?.firstInstallTime
            val lastUsed = last[pkg] ?: installed?.takeIf { now - it > 30 * PhoneCheck.DAY_MS }?.let { maxOf(it, now - 60 * PhoneCheck.DAY_MS) }
            AppUse(
                pkg = pkg,
                label = n?.label ?: label(ai, pkg),
                system = system,
                sideloaded = !system && installer(pkg) !in STORES,
                foregroundMs = PhoneCheck.total(fg),
                backgroundMs = PhoneCheck.minus(svc, fg),
                lastUsedMs = lastUsed,
                installedMs = installed,
                mobileFg = n?.mobileFg ?: 0, mobileBg = n?.mobileBg ?: 0, wifiFg = n?.wifiFg ?: 0, wifiBg = n?.wifiBg ?: 0,
                perms = pi?.let { grantedPerms(it) } ?: emptySet(),
                special = special[pkg].orEmpty(),
                unrestricted = !system && try { power.isIgnoringBatteryOptimizations(pkg) } catch (e: Exception) { false },
            )
        }.filter { it.pkg != ctx.packageName || it.data > 0 || it.foregroundMs > 0 }

        // Screen on: Android 9+ reports it directly; older phones use time any app was on screen
        val screen = ev.screen ?: PhoneCheck.merge(ev.foreground.values.flatten())
        val samples = BatteryLog.samples(ctx, from - 60 * 60_000L)
        val mobileKnown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && net.mobileOk
        return PhoneCheck.analyze(apps, samples, screen, now, mobileKnown, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            .also { cached = it }
    }

    // ---------------------------------------------------------------- usage events

    private class Events(
        val foreground: Map<String, List<Pair<Long, Long>>>,
        val service: Map<String, List<Pair<Long, Long>>>,
        val screen: List<Pair<Long, Long>>?,
    )

    private fun events(from: Long, now: Long): Events {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val home = homePackage()
        val fg = HashMap<String, MutableList<Pair<Long, Long>>>()
        val svc = HashMap<String, MutableList<Pair<Long, Long>>>()
        val openFg = HashMap<String, Pair<String, Long>>()
        val openSvc = HashMap<String, Pair<String, Long>>()
        val screen = ArrayList<Pair<Long, Long>>()
        var screenOnSince: Long? = null
        var sawScreenEvent = false
        val e = UsageEvents.Event()
        val q = try { usm.queryEvents(from, now) } catch (x: Exception) { null }
        while (q != null && q.hasNextEvent()) {
            q.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val key = pkg + "/" + (e.className ?: "")
            when (e.eventType) {
                RESUMED -> if (pkg != home) openFg[key] = pkg to e.timeStamp
                PAUSED, STOPPED -> openFg.remove(key)?.let { (p, s) -> fg.getOrPut(p) { ArrayList() }.add(s to e.timeStamp) }
                FGS_START -> openSvc[key] = pkg to e.timeStamp
                FGS_STOP -> {
                    // A service already running when the window began counts from the start of the window
                    val (p, s) = openSvc.remove(key) ?: (pkg to from)
                    svc.getOrPut(p) { ArrayList() }.add(s to e.timeStamp)
                }
                SCREEN_ON -> { sawScreenEvent = true; screenOnSince = e.timeStamp }
                SCREEN_OFF -> {
                    // The first "off" of the day closes a screen-on that began before the window
                    sawScreenEvent = true
                    screen += (screenOnSince ?: from) to e.timeStamp
                    screenOnSince = null
                }
            }
        }
        openFg.values.forEach { (p, s) -> fg.getOrPut(p) { ArrayList() }.add(s to now) }
        openSvc.values.forEach { (p, s) -> svc.getOrPut(p) { ArrayList() }.add(s to now) }
        screenOnSince?.let { screen += it to now }
        val screenKnown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && sawScreenEvent
        return Events(fg, svc, if (screenKnown) screen else null)
    }

    /** Last time each app was opened, as far back as Android keeps (about 2 months here). */
    private fun lastUsed(now: Long): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return try {
            usm.queryAndAggregateUsageStats(now - 60 * PhoneCheck.DAY_MS, now)
                .mapValues { it.value.lastTimeUsed }.filterValues { it > 0 }
        } catch (e: Exception) { emptyMap() }
    }

    // ---------------------------------------------------------------- data

    private class Net(val label: String?) { var mobileFg = 0L; var mobileBg = 0L; var wifiFg = 0L; var wifiBg = 0L }
    private class NetMap(m: Map<String, Net>, val mobileOk: Boolean) : Map<String, Net> by m

    @Suppress("DEPRECATION")
    private fun network(from: Long, now: Long): NetMap {
        val out = HashMap<String, Net>()
        val nsm = ctx.getSystemService(NetworkStatsManager::class.java) ?: return NetMap(out, false)
        fun keyFor(uid: Int): Pair<String, String?> = when (uid) {
            android.os.Process.SYSTEM_UID -> "android.system" to "Android system"
            NetworkStats.Bucket.UID_REMOVED -> "uninstalled" to "Uninstalled apps"
            NetworkStats.Bucket.UID_TETHERING -> "hotspot" to "Hotspot and tethering"
            else -> (pm.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid") to (if (pm.getPackagesForUid(uid) == null) "Other apps" else null)
        }
        fun read(type: Int, mobile: Boolean): Boolean = try {
            val stats = nsm.querySummary(type, null, from, now)
            val b = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b)
                val bytes = b.rxBytes + b.txBytes
                if (bytes <= 0) continue
                val (key, label) = keyFor(b.uid)
                val n = out.getOrPut(key) { Net(label) }
                val foreground = b.state == NetworkStats.Bucket.STATE_FOREGROUND
                if (mobile) { if (foreground) n.mobileFg += bytes else n.mobileBg += bytes }
                else { if (foreground) n.wifiFg += bytes else n.wifiBg += bytes }
            }
            stats.close()
            true
        } catch (e: Exception) { false }
        read(ConnectivityManager.TYPE_WIFI, mobile = false)
        val mobileOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && read(ConnectivityManager.TYPE_MOBILE, mobile = true)
        // Unknown uids are grouped into one row
        val merged = HashMap<String, Net>()
        for ((k, v) in out) {
            val key = if (k.startsWith("uid:")) "other.apps" else k
            val m = merged.getOrPut(key) { Net(v.label) }
            m.mobileFg += v.mobileFg; m.mobileBg += v.mobileBg; m.wifiFg += v.wifiFg; m.wifiBg += v.wifiBg
        }
        return NetMap(merged, mobileOk)
    }

    // ---------------------------------------------------------------- packages and permissions

    @Suppress("DEPRECATION")
    private fun installed(): Map<String, PackageInfo> = try {
        val list = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        // Apps with a home-screen icon, or updated by you (not core system parts)
        list.filter { pi -> pi.packageName != "android" && (pm.getLaunchIntentForPackage(pi.packageName) != null) }
            .associateBy { it.packageName }
    } catch (e: Exception) { emptyMap() }

    private fun grantedPerms(pi: PackageInfo): Set<Perm> {
        val names = pi.requestedPermissions ?: return emptySet()
        val flags = pi.requestedPermissionsFlags ?: return emptySet()
        val out = HashSet<Perm>()
        for (i in names.indices) {
            val granted = i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            if (granted) PERMS[names[i]]?.let { out += it }
        }
        return out
    }

    private fun specialAccess(): Map<String, Set<Special>> {
        val out = HashMap<String, MutableSet<Special>>()
        fun add(pkg: String, s: Special) { out.getOrPut(pkg) { HashSet() } += s }
        try {
            val am = ctx.getSystemService(AccessibilityManager::class.java)
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .forEach { it.resolveInfo?.serviceInfo?.packageName?.let { p -> add(p, Special.Accessibility) } }
        } catch (e: Exception) { }
        try { NotificationManagerCompat.getEnabledListenerPackages(ctx).forEach { add(it, Special.NotificationAccess) } } catch (e: Exception) { }
        try {
            ctx.getSystemService(DevicePolicyManager::class.java)?.activeAdmins?.forEach { add(it.packageName, Special.DeviceAdmin) }
        } catch (e: Exception) { }
        out.remove(ctx.packageName)
        return out
    }

    @Suppress("DEPRECATION")
    private fun installer(pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) { null }

    private fun label(ai: ApplicationInfo?, pkg: String): String =
        try { ai?.let { pm.getApplicationLabel(it).toString() } ?: pkg.substringAfterLast('.') } catch (e: Exception) { pkg.substringAfterLast('.') }

    private fun homePackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }
}
