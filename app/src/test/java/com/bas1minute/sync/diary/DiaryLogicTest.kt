package com.bas1minute.sync.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiaryLogicTest {
    private fun milestone(date: LocalDate, title: String = "Event") =
        DiaryEntry(date.toEpochDay(), EntryType.Milestone, date, title, Category.Family, createdAt = 0)

    private fun reflection(date: LocalDate, effort: Int? = 3, satisfaction: Int? = 3) =
        DiaryEntry(date.toEpochDay() + 100000, EntryType.Reflection, date, "Reflection", Category.Other,
            effort = effort, satisfaction = satisfaction, createdAt = 0)

    @Test fun onThisDay_matchesMonthAndDayAcrossYears_excludesThisYearAndFuture() {
        val today = LocalDate.of(2026, 11, 13)
        val entries = listOf(
            milestone(LocalDate.of(2010, 11, 13), "Graduated"),
            milestone(LocalDate.of(2026, 11, 13), "Also today, same year"),   // excluded: not an anniversary of itself
            milestone(LocalDate.of(2027, 11, 13), "Future"),                  // excluded: in the future
            milestone(LocalDate.of(2015, 11, 14), "Wrong day"),
            milestone(LocalDate.of(2005, 3, 13), "Wrong month"),
        )
        val got = DiaryLogic.onThisDay(entries, today)
        assertEquals(1, got.size)
        assertEquals("Graduated", got[0].first.title)
        assertEquals(16, got[0].second)
    }

    @Test fun onThisDay_sortedLongestAgoFirst() {
        val today = LocalDate.of(2026, 6, 1)
        val entries = listOf(milestone(LocalDate.of(2020, 6, 1), "Recent"), milestone(LocalDate.of(2000, 6, 1), "Old"))
        val got = DiaryLogic.onThisDay(entries, today)
        assertEquals("Old", got[0].first.title)
        assertEquals("Recent", got[1].first.title)
    }

    @Test fun agoLabel_yearsMonthsDaysAndToday() {
        val today = LocalDate.of(2026, 11, 13)
        assertEquals("3 years ago", DiaryLogic.agoLabel(LocalDate.of(2023, 11, 13), today))
        assertEquals("1 year ago", DiaryLogic.agoLabel(LocalDate.of(2025, 11, 13), today))
        assertEquals("2 months ago", DiaryLogic.agoLabel(LocalDate.of(2026, 9, 13), today))
        assertEquals("today", DiaryLogic.agoLabel(today, today))
        assertEquals("5 days ago", DiaryLogic.agoLabel(today.minusDays(5), today))
        assertEquals("upcoming", DiaryLogic.agoLabel(today.plusDays(1), today))
    }

    @Test fun reflectionStreak_consecutiveDaysAndTodayOptional() {
        val today = LocalDate.of(2026, 11, 13)
        val dates = setOf(today, today.minusDays(1), today.minusDays(2))
        val s = DiaryLogic.reflectionStreak(dates, today)
        assertEquals(3, s.current)
        assertEquals(3, s.best)
    }

    @Test fun reflectionStreak_todayMissingIsNotABreak_yesterdayMissingIs() {
        val today = LocalDate.of(2026, 11, 13)
        val dates = setOf(today.minusDays(1), today.minusDays(2))   // reflected yesterday & day before, not yet today
        assertEquals(2, DiaryLogic.reflectionStreak(dates, today).current)
        val gapped = setOf(today.minusDays(2))   // yesterday missing
        assertEquals(0, DiaryLogic.reflectionStreak(gapped, today).current)
    }

    @Test fun reflectionStreak_bestSurvivesAPastGap() {
        val today = LocalDate.of(2026, 11, 20)
        val dates = (0..4).map { today.minusDays(10 + it.toLong()) }.toSet() + setOf(today, today.minusDays(1))
        val s = DiaryLogic.reflectionStreak(dates, today)
        assertEquals(2, s.current)
        assertEquals(5, s.best)
    }

    @Test fun reflectionStreak_empty() {
        assertEquals(ReflectionStreak(0, 0), DiaryLogic.reflectionStreak(emptySet(), LocalDate.now()))
    }

    @Test fun averages_onlyFromReflectionsThatHaveThatRating() {
        val list = listOf(reflection(LocalDate.now(), effort = 4, satisfaction = 2), reflection(LocalDate.now().minusDays(1), effort = 2, satisfaction = 4))
        val (e, s) = DiaryLogic.averages(list)
        assertEquals(3.0, e!!, 0.001)
        assertEquals(3.0, s!!, 0.001)
    }

    @Test fun averages_emptyIsNull() {
        val (e, s) = DiaryLogic.averages(emptyList())
        assertNull(e); assertNull(s)
    }

    @Test fun effortVsSatisfaction_readsTheGap() {
        assertTrue(DiaryLogic.effortVsSatisfactionNote(4.5, 2.5)!!.contains("more effort"))
        assertTrue(DiaryLogic.effortVsSatisfactionNote(2.0, 4.0)!!.contains("lighter"))
        assertTrue(DiaryLogic.effortVsSatisfactionNote(3.0, 3.2)!!.contains("tracking closely"))
        assertNull(DiaryLogic.effortVsSatisfactionNote(null, 3.0))
    }

    @Test fun clamping() {
        assertEquals(1, DiaryLogic.clampRating(0))
        assertEquals(5, DiaryLogic.clampRating(9))
        assertEquals("hello", DiaryLogic.clampText("  hello world  ", 5))
    }

    @Test fun categoryLookup() {
        assertEquals(Category.Birth, Category.of("birth"))
        assertEquals(Category.Other, Category.of("nonsense"))
    }
}
