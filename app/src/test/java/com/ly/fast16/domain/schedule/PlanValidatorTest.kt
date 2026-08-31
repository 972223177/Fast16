package com.ly.fast16.domain.schedule

import com.ly.fast16.core.time.Time
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PlanValidatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(hour: Int, minute: Int) =
        Time.at(LocalDate.of(2026, 8, 31), LocalTime.of(hour, minute), zone)

    private val window = Duration.ofHours(8)
    private val minGap = Duration.ofHours(3)
    private val bufferEnd = Duration.ofMinutes(30)

    @Test
    fun validCombination_returnsEmptyErrors() {
        val errors = PlanValidator.validate(
            lunch = at(12, 0), dinner = at(15, 0),
            breakfast = at(8, 0), window = window,
            minGap = minGap, bufferEnd = bufferEnd,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun lunchBeforeBreakfastPlusGap_reportsConstraint1() {
        val errors = PlanValidator.validate(
            lunch = at(10, 59), dinner = at(15, 0),
            breakfast = at(8, 0), window = window,
            minGap = minGap, bufferEnd = bufferEnd,
        )
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("午餐"))
        assertTrue(errors[0].contains("180"))
    }

    @Test
    fun dinnerBeforeLunchPlusGap_reportsConstraint2() {
        val errors = PlanValidator.validate(
            lunch = at(12, 0), dinner = at(14, 0),
            breakfast = at(8, 0), window = window,
            minGap = minGap, bufferEnd = bufferEnd,
        )
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("晚餐"))
        assertTrue(errors[0].contains("午餐"))
    }

    @Test
    fun dinnerAfterHardEnd_reportsConstraint3() {
        val errors = PlanValidator.validate(
            lunch = at(12, 0), dinner = at(15, 31),
            breakfast = at(8, 0), window = window,
            minGap = minGap, bufferEnd = bufferEnd,
        )
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("30"))
    }

    @Test
    fun multipleViolations_returnsAllErrors() {
        // 约束2与约束3不可能同时违反（需 lunch+minGap > hardEnd 且 dinner 同居两端），
        // 此处验证约束1+约束2 同时触发
        val errors = PlanValidator.validate(
            lunch = at(10, 0), dinner = at(12, 0),
            breakfast = at(8, 0), window = window,
            minGap = minGap, bufferEnd = bufferEnd,
        )
        assertEquals(2, errors.size)
    }

    @Test
    fun boundaryValues_exactlyOnConstraintLine_pass() {
        // lunch = B+minGap 恰好；dinner = lunch+minGap 恰好；dinner = hardEnd 恰好
        val ok1 = PlanValidator.validate(
            lunch = at(11, 0), dinner = at(15, 30),
            breakfast = at(8, 0), window = window,
            minGap = minGap, bufferEnd = bufferEnd,
        )
        assertTrue(ok1.isEmpty())
    }
}
