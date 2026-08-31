package com.ly.fast16.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StreakCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 31)

    @Test
    fun todayAndYesterdayCompleted_streakIs2() {
        val days = setOf(today, today.minusDays(1), today.minusDays(3))
        assertEquals(2, StreakCalculator.computeStreak(days, today))
    }

    @Test
    fun todayIncomplete_countsFromYesterday() {
        val days = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, StreakCalculator.computeStreak(days, today))
    }

    @Test
    fun yesterdayAlsoIncomplete_streakIs0() {
        val days = setOf(today.minusDays(2))
        assertEquals(0, StreakCalculator.computeStreak(days, today))
    }

    @Test
    fun emptySet_streakIs0() {
        assertEquals(0, StreakCalculator.computeStreak(emptySet(), today))
    }

    @Test
    fun longConsecutiveStreak_countsCorrectly() {
        val days = (0..9L).map { today.minusDays(it) }.toSet()
        assertEquals(10, StreakCalculator.computeStreak(days, today))
    }

    @Test
    fun gapBreaksCount_onlyRecentSegment() {
        val days = setOf(
            today, today.minusDays(1),
            // gap: today-2 缺
            today.minusDays(3), today.minusDays(4),
        )
        assertEquals(2, StreakCalculator.computeStreak(days, today))
    }

    @Test
    fun monthCompletion_doneDaysDividedByMonthLength() {
        val month = YearMonth.of(2026, 8) // 31 天
        val days = setOf(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 15),
            LocalDate.of(2026, 8, 31),
            LocalDate.of(2026, 7, 30), // 跨月不计入
        )
        assertEquals(3f / 31f, StreakCalculator.monthCompletion(days, month), 1e-6f)
    }

    @Test
    fun monthCompletion_emptySet_isZero() {
        assertEquals(0f, StreakCalculator.monthCompletion(emptySet(), YearMonth.of(2026, 8)), 1e-6f)
    }

    @Test
    fun weekCompletion_doneDaysDividedByWeekLength() {
        val week = (0..6L).map { today.minusDays(it) }
        val days = setOf(week[0], week[1], week[3])
        assertEquals(3f / 7f, StreakCalculator.weekCompletion(days, week), 1e-6f)
    }

    @Test
    fun weekCompletion_emptyWeek_returnsZero() {
        assertEquals(0f, StreakCalculator.weekCompletion(setOf(today), emptyList()), 1e-6f)
    }
}
