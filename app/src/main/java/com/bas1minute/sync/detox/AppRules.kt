package com.bas1minute.sync.detox

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * What Digital Detox blocks — a member's own block-list, not a fixed wall.
 *
 * Two built-in categories, Social media and Browsers, are ON by default when starting a detox, because
 * those are what most people mean by "stop wasting time on my phone." Everything else keeps working unless
 * the member explicitly adds it: calls, messages, WhatsApp, music, UPI and payment apps, maps, office and
 * work apps, games, shopping — none of it is touched by default. A member can also turn either category
 * off, exempt one app out of an on category (e.g. keep LinkedIn while still blocking Instagram), or add any
 * other individual app to block. Only true system essentials can never be blocked at all, so nobody can
 * lock themselves out of calling for help or fixing a stuck permission.
 */
class AppRules(
    context: Context,
    private val blockCategories: Set<String>,
    private val blockedApps: Set<String>,
    private val allowedOverride: Set<String> = emptySet(),
) {
    private val ctx = context.applicationContext
    private val pm: PackageManager = ctx.packageManager

    val dialerPackage: String? = runCatching {
        (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
    }.getOrNull()

    val smsPackage: String? = runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()

    private val launchers: Set<String> = resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    private val keyboards: Set<String> = runCatching {
        (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    private val browsers: Set<String> =
        resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE)) +
            KNOWN_BROWSERS

    /** Apps that can never be blocked, no matter what the member chooses: the phone's own escape hatches. */
    private val hardAllowed: Set<String> = buildSet {
        add(ctx.packageName)
        dialerPackage?.let(::add)
        smsPackage?.let(::add)
        addAll(launchers)
        addAll(keyboards)
        addAll(SYSTEM_ALLOWED)
    }

    /** True if opening [pkg] during a detox session should be stopped by the block screen. */
    fun isBlocked(pkg: String): Boolean =
        !isSystemUi(pkg) && DetoxLogic.isPackageBlocked(pkg, blockCategories, blockedApps, allowedOverride, hardAllowed, SOCIAL, browsers)

    /** True if using [pkg] for more than a few seconds spoils a clean hour — independent of detox block choices. */
    fun isDistracting(pkg: String): Boolean = distractCache.getOrPut(pkg) {
        pkg in SOCIAL || pkg in browsers || runCatching {
            pm.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_SOCIAL
        }.getOrDefault(false)
    }
    private val distractCache = HashMap<String, Boolean>()

    fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }
    private val labels = HashMap<String, String>()

    /** Installed apps that fall in the Social media category right now, so the member can exempt one by name. */
    fun installedSocial(): List<Pair<String, String>> = installedOf(SOCIAL)

    /** Installed browsers right now, same reason. */
    fun installedBrowsers(): List<Pair<String, String>> = installedOf(browsers)

    /** Apps a member can add to their own block list: every other launchable app — nothing pre-excluded
     * beyond what can never be blocked, so a music or work app can still be added if someone really wants to. */
    fun pickableToBlock(): List<Pair<String, String>> =
        resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            .filter { it != ctx.packageName && it !in hardAllowed && it !in blockedApps }
            .map { it to label(it) }
            .sortedBy { it.second.lowercase() }

    /** A short label for an app known to be music / UPI / maps / work, so the UI can explain why it isn't blocked. */
    fun referenceCategoryOf(pkg: String): String? = when (pkg) {
        in MUSIC_APPS -> "Music"
        in UPI_APPS -> "UPI & payments"
        in MAPS_APPS -> "Maps"
        in WORK_APPS -> "Work"
        "com.whatsapp", "com.whatsapp.w4b" -> "Messaging"
        else -> null
    }

    private fun installedOf(pkgs: Set<String>): List<Pair<String, String>> =
        pkgs.filter(::isInstalled).map { it to label(it) }.sortedBy { it.second.lowercase() }

    private fun isInstalled(pkg: String): Boolean = runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    private fun isSystemUi(pkg: String) = pkg == "android" || pkg.startsWith("com.android.systemui")

    private fun resolve(intent: Intent): Set<String> {
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return list.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    companion object {
        /**
         * Social media and short video — blocked by default. WhatsApp and Slack are deliberately NOT here:
         * WhatsApp is a named exception (messaging, not a feed to scroll), and Slack is work communication —
         * both stay open unless the member specifically chooses to block them. This list is Detox's own, and
         * can now differ from ScreenTimeReader.SOCIAL (which exists only to measure screen time, not to block).
         */
        val SOCIAL = setOf(
            "jp.naver.line.android", "tv.twitch.android.app", "com.josh.app",
            "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.facebook.lite",
            "com.facebook.orca", "com.facebook.mlite",
            "com.google.android.youtube", "com.snapchat.android", "com.twitter.android", "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill", "com.linkedin.android", "com.reddit.frontpage", "org.telegram.messenger",
            "org.thunderdog.challegram", "com.pinterest", "com.discord", "in.mohalla.sharechat", "in.mohalla.video",
            "com.eterno.shortvideos", "com.roposo.android", "com.next.innovation.takatak", "com.tumblr",
            "com.quora.android", "com.bereal.ft", "com.google.android.apps.youtube.kids",
        )

        val KNOWN_BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "org.mozilla.firefox", "org.mozilla.focus", "com.brave.browser", "com.microsoft.emmx",
            "com.opera.browser", "com.opera.mini.native", "com.opera.gx", "com.sec.android.app.sbrowser",
            "com.UCMobile.intl", "com.uc.browser.en", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
            "com.kiwibrowser.browser", "com.mi.globalbrowser", "com.heytap.browser", "com.android.browser",
            "com.google.android.googlequicksearchbox",
        )

        /** Calls, messages, contacts, emergency, camera, alarms and system screens: can never be blocked. */
        val SYSTEM_ALLOWED = setOf(
            "com.android.phone", "com.android.server.telecom", "com.android.incallui", "com.android.dialer",
            "com.google.android.dialer", "com.samsung.android.incallui", "com.samsung.android.dialer",
            "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts",
            "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
            "com.android.emergency", "com.google.android.apps.safetyhub", "com.android.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.google.android.apps.healthdata", "com.android.healthconnect.controller",
            "com.google.android.gms", "com.android.camera", "com.android.camera2",
            "com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.deskclock",
            "com.google.android.deskclock", "com.sec.android.app.clockpackage",
        )

        // Reference-only lists below: NOT consulted by isBlocked (the fail-open model already leaves them
        // alone by default) — used purely so the setup screen can explain *why* an app isn't blocked, and tag
        // it if the member searches for it in "add another app to block".

        val MUSIC_APPS = setOf(
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.gaana", "com.bsbportal.music",
            "com.jio.media.jiobeats", "com.apple.android.music", "com.amazon.mp3", "com.soundcloud.android",
            "com.resso.android", "com.hungama.myplay.activity", "com.anghami", "in.startv.hotstar.dplus",
        )

        val UPI_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm",
            "in.org.npci.upiapp", "in.amazon.mShop.android.shopping", "com.dreamplug.androidapp",
            "com.csam.icici.bank.imobile", "com.sbi.upi", "in.org.npci.upiapp.bhim", "com.mobikwik_new",
        )

        val MAPS_APPS = setOf("com.google.android.apps.maps", "com.waze")

        val WORK_APPS = setOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.microsoft.teams", "com.Slack",
            "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
            "com.google.android.calendar", "us.zoom.videomeetings", "com.google.android.apps.meetings",
            "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
            "com.notion.id", "com.asana.app", "com.trello",
        )
    }
}
