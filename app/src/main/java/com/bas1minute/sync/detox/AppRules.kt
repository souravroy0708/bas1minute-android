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
 * Which apps detox blocks, and which apps count against clean hours.
 *
 * Blocked during detox: everything except calls, messages, this app, the home screen, system screens
 * (permission dialogs, keyboard, Settings) and up to 3 apps the user picked before starting.
 * Settings stays open on purpose so nobody is trapped; turning off the permissions detox needs is caught
 * and counted as a broken promise.
 *
 * Distracting (spoils a clean hour): social media, messengers with feeds, and every browser.
 */
class AppRules(context: Context, extraAllowed: Set<String>) {
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

    private val allowed: Set<String> = buildSet {
        add(ctx.packageName)
        dialerPackage?.let(::add)
        smsPackage?.let(::add)
        addAll(launchers)
        addAll(keyboards)
        addAll(SYSTEM_ALLOWED)
        // Social apps and browsers can never be "always allowed": they reopen everything detox blocks.
        addAll(extraAllowed.filter { it !in SOCIAL && it !in browsers }.take(DetoxLogic.MAX_EXTRA_ALLOWED))
    }

    /** True if opening [pkg] during a detox session should be stopped by the block screen. */
    fun isBlocked(pkg: String): Boolean = pkg !in allowed && !isSystemUi(pkg)

    /** True if using [pkg] for more than a few seconds spoils a clean hour. */
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

    /** Launchable apps the user may pick as "always allowed" (excludes social apps and browsers). */
    fun pickableApps(): List<Pair<String, String>> =
        resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            .filter { it != ctx.packageName && it !in allowed && !isDistracting(it) }
            .map { it to label(it) }
            .sortedBy { it.second.lowercase() }

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
         * Social media, short video and messaging: the same apps the Today screen-time ring counts
         * (ScreenTimeReader.SOCIAL, browsers are in KNOWN_BROWSERS), so "clean hours" and screen time agree.
         */
        val SOCIAL = setOf(
            "jp.naver.line.android", "com.Slack", "tv.twitch.android.app", "com.josh.app",
            "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.facebook.lite",
            "com.facebook.orca", "com.facebook.mlite", "com.whatsapp", "com.whatsapp.w4b",
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

        /** Calls, messages, contacts, emergency, camera, alarms and system screens that must keep working. */
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
    }
}
