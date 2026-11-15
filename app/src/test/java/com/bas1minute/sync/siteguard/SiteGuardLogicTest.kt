package com.bas1minute.sync.siteguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteGuardLogicTest {
    @Test fun normalizeHost_stripsSchemePathQueryAndCommonSubdomainPrefixes() {
        assertEquals("facebook.com", SiteGuardLogic.normalizeHost("https://www.facebook.com/profile?x=1"))
        assertEquals("facebook.com", SiteGuardLogic.normalizeHost("m.facebook.com/home.php"))
        assertEquals("instagram.com", SiteGuardLogic.normalizeHost("http://instagram.com"))
        assertEquals("youtube.com", SiteGuardLogic.normalizeHost("https://m.youtube.com/watch?v=abc#t=10"))
        assertEquals("example.com", SiteGuardLogic.normalizeHost("example.com:8080/path"))
    }

    @Test fun normalizeHost_rejectsNonUrlText() {
        assertNull(SiteGuardLogic.normalizeHost(""))
        assertNull(SiteGuardLogic.normalizeHost("   "))
        assertNull(SiteGuardLogic.normalizeHost("best recipe for pasta"))   // a typed search query, not a URL
        assertNull(SiteGuardLogic.normalizeHost("just some text"))
        assertNull(SiteGuardLogic.normalizeHost("localhost"))   // no dot: not a real public hostname
    }

    @Test fun matchedDomain_matchesExactAndAnySubdomain() {
        val blocked = setOf("facebook.com", "youtube.com")
        assertEquals("facebook.com", SiteGuardLogic.matchedDomain("facebook.com", blocked))
        assertEquals("facebook.com", SiteGuardLogic.matchedDomain("business.facebook.com", blocked))
        assertEquals("youtube.com", SiteGuardLogic.matchedDomain("music.youtube.com", blocked))
        assertNull(SiteGuardLogic.matchedDomain("notfacebook.com", blocked))   // must not match on a bare substring
        assertNull(SiteGuardLogic.matchedDomain("gmail.com", blocked))
    }

    @Test fun creditableMs_countsOrdinaryGapsButNotImplausiblyLongOnes() {
        assertEquals(2_000L, SiteGuardLogic.creditableMs(2_000L))
        assertEquals(0L, SiteGuardLogic.creditableMs(60_000L))   // a minute between samples: likely backgrounded, not watched
        assertEquals(0L, SiteGuardLogic.creditableMs(-500L))     // a clock oddity, never credit negative time
    }

    @Test fun isOverBudget_atOrAboveTheBudgetInMinutes() {
        assertTrue(SiteGuardLogic.isOverBudget(15 * 60_000L, 15))
        assertTrue(SiteGuardLogic.isOverBudget(20 * 60_000L, 15))
        assertTrue(!SiteGuardLogic.isOverBudget(14 * 60_000L + 59_000L, 15))
    }

    @Test fun remainingLabel_countsDownAndFloorsAtUsedUp() {
        assertEquals("15 of 15 min left today", SiteGuardLogic.remainingLabel(0L, 15))
        assertEquals("5 of 15 min left today", SiteGuardLogic.remainingLabel(10 * 60_000L, 15))
        assertEquals("Used up for today", SiteGuardLogic.remainingLabel(15 * 60_000L, 15))
        assertEquals("Used up for today", SiteGuardLogic.remainingLabel(99 * 60_000L, 15))
    }

    @Test fun clampBudget_staysWithinSensibleBounds() {
        assertEquals(5, SiteGuardLogic.clampBudget(1))
        assertEquals(120, SiteGuardLogic.clampBudget(999))
        assertEquals(30, SiteGuardLogic.clampBudget(30))
    }

    @Test fun sanitizeEnteredDomain_acceptsABareDomainRejectsJunk() {
        assertEquals("example.com", SiteGuardLogic.sanitizeEnteredDomain("www.example.com"))
        assertNull(SiteGuardLogic.sanitizeEnteredDomain("not a domain"))
        assertNull(SiteGuardLogic.sanitizeEnteredDomain(""))
    }
}
