package com.bas1minute.sync.detox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The budget math itself is exhaustively tested in SiteGuardLogicTest, since AppTimerLogic deliberately
 * delegates to it rather than duplicating it — these are just sanity checks that the delegation itself works. */
class AppTimerLogicTest {
    @Test fun isOverBudget_delegatesCorrectly() {
        assertTrue(AppTimerLogic.isOverBudget(30 * 60_000L, 30))
        assertFalse(AppTimerLogic.isOverBudget(29 * 60_000L, 30))
    }

    @Test fun remainingLabel_delegatesCorrectly() {
        assertEquals("30 of 30 min left today", AppTimerLogic.remainingLabel(0L, 30))
        assertEquals("Used up for today", AppTimerLogic.remainingLabel(30 * 60_000L, 30))
    }

    @Test fun clampBudget_delegatesCorrectly() {
        assertEquals(5, AppTimerLogic.clampBudget(1))
        assertEquals(120, AppTimerLogic.clampBudget(999))
    }
}
