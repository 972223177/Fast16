package com.ly.fast16.domain.schedule

import com.ly.fast16.core.time.Time
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MealStateMachineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(hour: Int, minute: Int) =
        Time.at(LocalDate.of(2026, 8, 31), LocalTime.of(hour, minute), zone)

    // 午餐 12:00，备餐 30min → prepTime 11:30
    private val meal = Meal(
        id = 1, planId = 1, type = MealType.LUNCH,
        mealTime = at(12, 0), prepMinutes = 30,
    )

    @Test
    fun checkedIn_completedRegardlessOfTime() {
        assertEquals(MealStatus.COMPLETED, MealStateMachine.advance(meal, at(9, 0), hasCheckIn = true))
    }

    @Test
    fun atPrepNotMealTime_preparing() {
        assertEquals(MealStatus.PREPARING, MealStateMachine.advance(meal, at(11, 30), hasCheckIn = false))
        assertEquals(MealStatus.PREPARING, MealStateMachine.advance(meal, at(11, 59), hasCheckIn = false))
    }

    @Test
    fun atMealTime_eating() {
        assertEquals(MealStatus.EATING, MealStateMachine.advance(meal, at(12, 0), hasCheckIn = false))
        assertEquals(MealStatus.EATING, MealStateMachine.advance(meal, at(13, 0), hasCheckIn = false))
    }

    @Test
    fun beforePrepTime_scheduled() {
        assertEquals(MealStatus.SCHEDULED, MealStateMachine.advance(meal, at(11, 29), hasCheckIn = false))
    }

    @Test
    fun noPrep_skipsPreparing() {
        val noPrep = meal.copy(prepMinutes = 0)
        assertEquals(MealStatus.SCHEDULED, MealStateMachine.advance(noPrep, at(11, 59), hasCheckIn = false))
        assertEquals(MealStatus.EATING, MealStateMachine.advance(noPrep, at(12, 0), hasCheckIn = false))
    }

    @Test
    fun prepTime_derivedAsMealTimeMinusPrepMinutes() {
        assertEquals(at(11, 30), meal.prepTime)
        assertEquals(at(12, 0), meal.copy(prepMinutes = 0).prepTime)
    }
}
