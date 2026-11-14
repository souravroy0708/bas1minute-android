package com.bas1minute.sync.detox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDecisionTest {
    private val social = setOf("com.instagram.android", "com.facebook.katana", "com.google.android.youtube", "com.twitter.android")
    private val browsers = setOf("com.android.chrome", "org.mozilla.firefox")
    private val hardAllowed = setOf("com.android.dialer", "com.google.android.apps.messaging", "com.bas1minute.sync")

    private fun decide(
        pkg: String, categories: Set<String> = setOf("social", "browsers"),
        blocked: Set<String> = emptySet(), overrides: Set<String> = emptySet(),
    ) = DetoxLogic.isPackageBlocked(pkg, categories, blocked, overrides, hardAllowed, social, browsers)

    @Test fun defaultCategories_blockKnownSocialAndBrowsers() {
        assertTrue(decide("com.instagram.android"))
        assertTrue(decide("com.google.android.youtube"))
        assertTrue(decide("com.android.chrome"))
    }

    @Test fun defaultCategories_leaveEverythingElseAlone_failOpen() {
        // Music, UPI, maps, work apps, games — anything not chosen stays usable by default.
        assertFalse(decide("com.spotify.music"))
        assertFalse(decide("com.phonepe.app"))
        assertFalse(decide("com.google.android.apps.maps"))
        assertFalse(decide("com.google.android.gm"))
        assertFalse(decide("com.whatsapp"))     // named exception: not in the social reference list at all
    }

    @Test fun hardAllowed_canNeverBeBlocked_evenIfAddedByMistake() {
        assertFalse(decide("com.android.dialer", blocked = setOf("com.android.dialer")))
        assertFalse(decide("com.bas1minute.sync", blocked = setOf("com.bas1minute.sync")))
    }

    @Test fun turningOffACategory_stopsBlockingItsApps() {
        assertFalse(decide("com.instagram.android", categories = setOf("browsers")))
        assertFalse(decide("com.android.chrome", categories = setOf("social")))
        assertFalse(decide("com.instagram.android", categories = emptySet()))
    }

    @Test fun individualAppCanBeAddedToBlockEvenOutsideBothCategories() {
        assertFalse(decide("com.ex.ample.game"))
        assertTrue(decide("com.ex.ample.game", blocked = setOf("com.ex.ample.game")))
    }

    @Test fun allowedOverride_exemptsOneAppFromItsCategory_withoutTurningTheWholeCategoryOff() {
        assertTrue(decide("com.facebook.katana"))
        assertFalse(decide("com.instagram.android", overrides = setOf("com.instagram.android")))
        assertTrue(decide("com.facebook.katana", overrides = setOf("com.instagram.android")))   // sibling still blocked
    }

    @Test fun overrideWinsEvenIfTheSameAppWasAlsoIndividuallyBlocked() {
        // Explicit allow always wins over an explicit block for the same app — least surprising behaviour.
        assertFalse(decide("com.instagram.android", blocked = setOf("com.instagram.android"), overrides = setOf("com.instagram.android")))
    }
}
