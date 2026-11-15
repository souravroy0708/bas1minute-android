package com.bas1minute.sync.siteguard

/*
 * Blocks specific TIME-WASTING WEBSITES inside any browser — facebook.com, instagram.com, youtube.com and
 * the like — while leaving every other site (work, email, banking, search) completely untouched. This is a
 * different kind of guard from Digital Detox: Detox blocks whole apps for a promise the member starts and
 * ends; this quietly tracks a small daily time budget PER WEBSITE and, once it's spent, that one site stops
 * loading for the rest of the day — no session to start, nothing to remember to turn on.
 *
 * Reading which website is open requires Accessibility access to the browser's own address bar — the one
 * Android permission with real reach, so it's used as narrowly as this file's own logic: only to pull a plain
 * hostname out of a URL bar string and compare it to a short list, never to read page content, and the
 * accessibility service itself is configured to only ever receive events from browser apps (see
 * SiteGuardAccessibilityService) — never WhatsApp, a banking app, or anything else.
 *
 * Plain Kotlin, no Android imports, so the actual decision logic is unit tested; only the Android glue that
 * feeds it real accessibility events lives outside this file.
 */
object SiteGuardLogic {
    const val DEFAULT_DAILY_BUDGET_MIN = 15
    const val MIN_BUDGET_MIN = 5
    const val MAX_BUDGET_MIN = 120
    const val MAX_DOMAINS = 20

    /** If two samples of the same site are further apart than this, the gap isn't credited — the browser
     * was very likely backgrounded or the phone was asleep in between, not actually being looked at. */
    const val MAX_SAMPLE_GAP_MS = 5_000L

    val DEFAULT_BLOCKED_DOMAINS = setOf(
        "facebook.com", "instagram.com", "youtube.com", "x.com", "twitter.com",
        "tiktok.com", "reddit.com", "snapchat.com", "pinterest.com",
    )

    fun clampBudget(minutes: Int): Int = minutes.coerceIn(MIN_BUDGET_MIN, MAX_BUDGET_MIN)

    /**
     * Pulls a bare, comparable hostname out of whatever text is sitting in a browser's address bar —
     * "https://m.facebook.com/home.php?x=1" and "facebook.com" should both normalize the same way.
     * Returns null for anything that isn't a plausible hostname (a search query typed but not yet a URL,
     * an empty bar, or text this clearly isn't address-bar content at all).
     */
    fun normalizeHost(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isEmpty() || s.contains(' ')) return null   // a typed search query, not a URL
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringBefore(':')   // a port, if any
        for (prefix in listOf("www.", "m.", "mobile.", "touch.")) {
            if (s.startsWith(prefix)) { s = s.removePrefix(prefix); break }
        }
        if (!s.contains('.')) return null
        if (!s.matches(Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$"))) return null
        return s
    }

    /** Which blocked domain (if any) this host belongs to — the domain itself, or any of its subdomains. */
    fun matchedDomain(host: String, blockedDomains: Set<String>): String? =
        blockedDomains.firstOrNull { host == it || host.endsWith(".$it") }

    /** How much of a gap between two samples of the SAME site should count as time spent on it. */
    fun creditableMs(elapsedMs: Long, maxGapMs: Long = MAX_SAMPLE_GAP_MS): Long =
        if (elapsedMs in 0..maxGapMs) elapsedMs else 0L

    fun isOverBudget(usedMs: Long, budgetMinutes: Int): Boolean = usedMs >= budgetMinutes * 60_000L

    fun remainingLabel(usedMs: Long, budgetMinutes: Int): String {
        val remainingMin = ((budgetMinutes * 60_000L - usedMs) / 60_000L).coerceAtLeast(0)
        return if (remainingMin <= 0) "Used up for today" else "$remainingMin of $budgetMinutes min left today"
    }

    /** A sanitized, de-duplicated domain someone typed into the "add a site" field, or null if it's not a
     * plausible bare domain (e.g. they pasted a full URL with a path, or left it blank). */
    fun sanitizeEnteredDomain(raw: String): String? = normalizeHost(raw)
}
