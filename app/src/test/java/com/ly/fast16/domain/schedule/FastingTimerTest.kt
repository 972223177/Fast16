package com.ly.fast16.domain.schedule

import com.ly.fast16.core.time.Time
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FastingTimerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 8, 31)

    private fun at(hour: Int, minute: Int) = Time.at(date, LocalTime.of(hour, minute), zone)

    private fun meal(mealHour: Int, mealMinute: Int = 0, prepMin: Int = 0) = Meal(
        id = 1, planId = 1, type = MealType.LUNCH,
        mealTime = at(mealHour, mealMinute), prepMinutes = prepMin,
    )

    @Test
    fun withPrep_nextAction_returnsPrepTime() {
        val meals = listOf(meal(12, 0, prepMin = 30))
        assertEquals(at(11, 30), FastingTimer.nextAction(at(10, 0), meals))
    }

    @Test
    fun withoutPrep_nextAction_returnsMealTime() {
        val meals = listOf(meal(12, 0, prepMin = 0))
        assertEquals(at(12, 0), FastingTimer.nextAction(at(10, 0), meals))
    }

    @Test
    fun multipleMeals_picksNearestFutureAction() {
        val meals = listOf(
            meal(12, 0, prepMin = 30),  // prep 11:30
            meal(15, 0, prepMin = 45),  // prep 14:15
        )
        assertEquals(at(11, 30), FastingTimer.nextAction(at(10, 0), meals))
        assertEquals(at(14, 15), FastingTimer.nextAction(at(12, 30), meals))
    }

    @Test
    fun pastActions_areFiltered() {
        assertEquals(at(15, 0), FastingTimer.nextAction(at(12, 30), listOf(meal(12, 0, prepMin = 30), meal(15, 0))))
    }

    @Test
    fun allActionsPast_returnsNull() {
        val meals = listOf(meal(12, 0, prepMin = 30))
        assertNull(FastingTimer.nextAction(at(14, 0), meals))
    }

    @Test
    fun completedMeal_notIncludedInNextAction() {
        // 已打卡完成的餐不产生倒计时——倒计时指向下一未完成餐
        val meals = listOf(
            meal(12, 0).copy(status = MealStatus.COMPLETED),
            meal(15, 0),
        )
        assertEquals(at(15, 0), FastingTimer.nextAction(at(10, 0), meals))
    }

    @Test
    fun allCompleted_returnsNull() {
        // 提前全部打卡 → 无未完成餐 → 倒计时消失
        val meals = listOf(meal(12, 0).copy(status = MealStatus.COMPLETED))
        assertNull(FastingTimer.nextAction(at(10, 0), meals))
        assertNull(FastingTimer.fastingRemaining(at(10, 0), meals))
    }

    @Test
    fun fastingRemaining_returnsDurationToNextAction() {
        assertEquals(
            Duration.ofMinutes(90),
            FastingTimer.fastingRemaining(at(10, 0), listOf(meal(12, 0, prepMin = 30))),
        )
    }

    @Test
    fun fastingRemaining_noFutureAction_returnsNull() {
        assertNull(FastingTimer.fastingRemaining(at(14, 0), listOf(meal(12, 0))))
    }
}
