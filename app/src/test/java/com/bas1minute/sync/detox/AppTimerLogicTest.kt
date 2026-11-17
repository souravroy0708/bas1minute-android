package com.bas1minute.sync.detox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTimerLogicTest {
    @Test fun isOverBudget_atAndPastTheLimit() {
        assertTrue(AppTimerLogic.isOverBudget(30 * 60_000L, 30))
        assertTrue(AppTimerLogic.isOverBudget(45 * 60_000L, 30))
        assertFalse(AppTimerLogic.isOverBudget(29 * 60_000L, 30))
    }

    @Test fun remainingLabel() {
        assertEquals("30 of 30 min left today", AppTimerLogic.remainingLabel(0L, 30))
        assertEquals("Used up for today", AppTimerLogic.remainingLabel(30 * 60_000L, 30))
    }

    @Test fun clampBudget() {
        assertEquals(5, AppTimerLogic.clampBudget(1))
        assertEquals(120, AppTimerLogic.clampBudget(999))
    }

    @Test fun shouldRemind_onlyOverBudget_andCappedPerDay() {
        assertFalse(AppTimerLogic.shouldRemind(10 * 60_000L, 15, 0))
        assertTrue(AppTimerLogic.shouldRemind(15 * 60_000L, 15, 0))
        assertTrue(AppTimerLogic.shouldRemind(40 * 60_000L, 15, 2))
        assertFalse(AppTimerLogic.shouldRemind(40 * 60_000L, 15, AppTimerLogic.MAX_REMINDERS_PER_APP_PER_DAY))
    }
}
