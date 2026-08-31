package com.ly.fast16.domain.schedule

import com.ly.fast16.core.time.Time
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CandidateGeneratorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(hour: Int, minute: Int) =
        Time.at(LocalDate.of(2026, 8, 31), LocalTime.of(hour, minute), zone)

    private fun time(instant: java.time.Instant): String =
        Time.timeOf(instant, zone).toString()

    // 文档默认参数：B=8:00，W=8h，minGap=3h，bufferEnd=30m → L=4h30m
    private val window = Duration.ofHours(8)
    private val minGap = Duration.ofHours(3)
    private val bufferEnd = Duration.ofMinutes(30)

    @Test
    fun balancedPlan_lunchAtWindowMidpoint_dinnerPushedToHardEnd() {
        val c = CandidateGenerator.generateCandidates(
            breakfast = at(8, 0),
            window = window,
            minGap = minGap,
            bufferEnd = bufferEnd,
        )[0]
        assertEquals("均衡", c.label)
        assertEquals("12:00", time(c.lunch))
        // dinner = min(hardEnd 15:30, lunch+minGap+L/2 = 17:15) = 15:30
        assertEquals("15:30", time(c.dinner))
    }

    @Test
    fun earlyLunchPlan_lunchAtQuarterOfFlexRange() {
        val c = CandidateGenerator.generateCandidates(
            breakfast = at(8, 0),
            window = window,
            minGap = minGap,
            bufferEnd = bufferEnd,
        )[1]
        assertEquals("午餐偏早", c.label)
        // lunch = 8:00 + 3:00 + 4:30/4(1:07:30) = 12:07:30 → 整分钟向下对齐 12:07
        assertEquals("12:07", time(c.lunch))
        assertEquals("15:07", time(c.dinner))
    }

    @Test
    fun lateDinnerPlan_dinnerAtHardEnd_lunchPulledBackForGap() {
        val c = CandidateGenerator.generateCandidates(
            breakfast = at(8, 0),
            window = window,
            minGap = minGap,
            bufferEnd = bufferEnd,
        )[2]
        assertEquals("晚餐偏晚", c.label)
        // 晚餐 = 硬尾 15:30；午餐回拉到 硬尾−minGap = 12:30（保证间隔约束2成立）
        assertEquals("12:30", time(c.lunch))
        assertEquals("15:30", time(c.dinner))
    }

    @Test
    fun candidates_satisfyAllThreeConstraints() {
        val breakfast = at(8, 0)
        val hardEnd = breakfast.plus(window).minus(bufferEnd)
        CandidateGenerator.generateCandidates(
            breakfast = breakfast,
            window = window,
            minGap = minGap,
            bufferEnd = bufferEnd,
        ).forEach { c ->
            assert(c.lunch >= breakfast.plus(minGap)) { "约束1: lunch ≥ B+minGap" }
            assert(c.dinner >= c.lunch.plus(minGap)) { "约束2: dinner ≥ lunch+minGap" }
            assert(c.dinner <= hardEnd) { "约束3: dinner ≤ B+W−bufferEnd" }
        }
    }

    @Test
    fun earlyLunch_dinnerEqualsLunchPlusGap() {
        val c = CandidateGenerator.generateCandidates(
            breakfast = at(8, 0),
            window = window,
            minGap = minGap,
            bufferEnd = bufferEnd,
        )[1]
        assertEquals(c.lunch.plus(minGap), c.dinner)
    }

    @Test
    fun candidateOrderAndLabels_areFixed() {
        val labels = CandidateGenerator.generateCandidates(
            breakfast = at(8, 0),
            window = window,
            minGap = minGap,
            bufferEnd = bufferEnd,
        )
            .map { it.label }
        assertEquals(listOf("均衡", "午餐偏早", "晚餐偏晚"), labels)
    }

    @Test
    fun nonWholeMinuteFlex_roundsDownToMinute() {
        // W=7h37m → L = 7:37 − 0:30 − 3:00 = 4:07；L/4 = 1:01:45 → 午餐秒位被截断
        val w = Duration.ofMinutes(7 * 60 + 37)
        val c = CandidateGenerator.generateCandidates(
            breakfast = at(8, 0),
            window = w,
            minGap = minGap,
            bufferEnd = bufferEnd,
        )[1]
        assertEquals(0, c.lunch.atZone(zone).toLocalTime().second)
        assertEquals(0, c.dinner.atZone(zone).toLocalTime().second)
    }

    @Test
    fun degenerateParams_constraintsConflict_withinWindow() {
        // W=3h, minGap=3h → 硬尾(10:30) 早于最早午餐(11:00)，约束1/3互斥
        val breakfast = at(8, 0)
        val winEnd = breakfast.plus(Duration.ofHours(3))
        val c = CandidateGenerator.generateCandidates(
            breakfast = breakfast,
            window = Duration.ofHours(3),
            minGap = minGap,
            bufferEnd = bufferEnd,
        )
        c.forEach {
            assert(!it.lunch.isBefore(breakfast)) { "午餐不得早于早餐" }
            assert(!it.dinner.isAfter(winEnd)) { "晚餐不得晚于窗口结束" }
            assert(!it.dinner.isBefore(breakfast)) { "晚餐不得早于早餐" }
        }
    }
}
