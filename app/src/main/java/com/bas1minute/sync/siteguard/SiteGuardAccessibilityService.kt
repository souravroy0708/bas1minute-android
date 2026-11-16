package com.bas1minute.sync.siteguard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bas1minute.sync.Prefs
import com.bas1minute.sync.detox.AppRules
import java.time.LocalDate

/**
 * Reads only the address bar of a browser — never page content, never any other app (the service config,
 * res/xml/site_guard_config.xml, restricts which apps it can even receive events from at the OS level) —
 * to track a small daily time budget per blocked website. Once a site's budget for today is spent,
 * SiteGuardOverlay covers it and the back button sends the browser away from the page; every other site in
 * the same browser is completely untouched throughout.
 */
class SiteGuardAccessibilityService : AccessibilityService() {
    private var lastHost: String? = null
    private var lastSampleAt = 0L
    private var lastBlockedKey: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in AppRules.KNOWN_BROWSERS) return
        val prefs = Prefs(this)
        if (!prefs.siteGuardEnabled) return

        val host = extractUrlHost(rootInActiveWindow)
        val now = System.currentTimeMillis()
        if (host == null) { lastHost = null; return }

        val domain = SiteGuardLogic.matchedDomain(host, prefs.siteGuardDomains)
        if (domain == null) { lastHost = host; lastSampleAt = now; return }   // not a blocked site: nothing to track

        val creditMs = if (lastHost == host) SiteGuardLogic.creditableMs(now - lastSampleAt) else 0L
        lastHost = host; lastSampleAt = now
        val key = "${LocalDate.now()}:$domain"
        if (creditMs > 0) prefs.addSiteTime(key, creditMs)

        if (SiteGuardLogic.isOverBudget(prefs.siteTimeMs(key), prefs.siteGuardBudgetMinutes)) {
            if (lastBlockedKey != key) {
                lastBlockedKey = key
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            SiteGuardOverlay.show(this, domain)
        } else if (lastBlockedKey != null) {
            lastBlockedKey = null   // moved to a different, not-yet-over-budget site; stop suppressing its own back-press
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastHost = null; lastBlockedKey = null
    }

    /** Finds the address bar in the current window: known view ids for major browsers first (fast, precise),
     * then a generic scan for any short text node that's shaped like a hostname (new/updated browser builds,
     * or ones not in the known-id list, still get caught by this — less precise, but nothing is ever blocked
     * from a false match here, since the result still has to match an entry in the member's own domain list). */
    private fun extractUrlHost(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        for (id in KNOWN_URL_BAR_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val text = nodes?.firstOrNull()?.text?.toString()
            if (!text.isNullOrBlank()) SiteGuardLogic.normalizeHost(text)?.let { return it }
        }
        return scanForUrlLikeText(root, depth = 0)
    }

    private fun scanForUrlLikeText(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 6) return null   // toolbars sit near the top of the tree; no need to go deep
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) SiteGuardLogic.normalizeHost(text)?.let { return it }
        for (i in 0 until node.childCount) {
            scanForUrlLikeText(node.getChild(i), depth + 1)?.let { return it }
        }
        return null
    }

    companion object {
        /** Whether the member has actually granted this in system Settings — separate from [Prefs.siteGuardEnabled],
         * which only pauses/resumes the feature once that real grant exists. An app can never grant this for itself. */
        fun isGrantedByUser(ctx: android.content.Context): Boolean {
            val expected = "${ctx.packageName}/${SiteGuardAccessibilityService::class.java.name}"
            val enabled = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        private val KNOWN_URL_BAR_IDS = listOf(
            "com.android.chrome:id/url_bar", "com.chrome.beta:id/url_bar", "com.chrome.dev:id/url_bar",
            "com.brave.browser:id/url_bar", "com.microsoft.emmx:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.opera.browser:id/url_field", "com.vivaldi.browser:id/url_bar",
        )
    }
}
