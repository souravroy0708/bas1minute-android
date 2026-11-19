package com.bas1minute.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteThinningTest {
    @Test fun straightLine_keepsOnlyEnds() {
        val lat = List(100) { 17.40 + it * 0.00001 }
        val lng = List(100) { 78.40 }
        val keep = RouteThinning.keep(lat, lng, 4.0)
        assertEquals(2, keep.count { it })
        assertTrue(keep.first() && keep.last())
    }

    @Test fun corner_isKept() {
        // East 50 points, then north 50 points: the corner must survive
        val lat = List(50) { 17.40 } + List(50) { 17.40 + (it + 1) * 0.00002 }
        val lng = List(50) { 78.40 + it * 0.00002 } + List(50) { 78.40 + 49 * 0.00002 }
        val keep = RouteThinning.keep(lat, lng, 4.0)
        assertTrue(keep[49])
        assertTrue(keep.count { it } <= 5)
    }

    @Test fun tinyRoutes_keepEverything() {
        assertEquals(2, RouteThinning.keep(listOf(1.0, 2.0), listOf(1.0, 2.0), 4.0).count { it })
    }
}
