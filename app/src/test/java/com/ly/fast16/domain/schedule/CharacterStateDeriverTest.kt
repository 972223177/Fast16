package com.ly.fast16.domain.schedule

import com.ly.fast16.core.time.Time
import com.ly.fast16.domain.model.CharacterState
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CharacterStateDeriverTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 8, 31)

    private fun at(hour: Int, minute: Int) = Time.at(date, LocalTime.of(hour, minute), zone)

    private val plan = MealPlan(
        id = 1, date = date,
        windowStart = at(8, 0), windowEnd = at(16, 0),
        status = PlanStatus.ACTIVE,
    )

    private fun meal(type: MealType, mealHour: Int, prepMin: Int, status: MealStatus = MealStatus.SCHEDULED) =
        Meal(id = 1, planId = 1, type = type, mealTime = at(mealHour, 0), prepMinutes = prepMin, status = status)

    @Test
    fun noPlanOrNoMeals_idle() {
        assertEquals(CharacterState.IDLE, CharacterStateDeriver.derive(at(10, 0), null, listOf(meal(MealType.LUNCH, 12, 30))))
        assertEquals(CharacterState.IDLE, CharacterStateDeriver.derive(at(10, 0), plan, emptyList()))
    }

    @Test
    fun inPrepWindow_prepHasTopPriority() {
        // 午 12:00 备餐 30min，now=11:45
        val meals = listOf(meal(MealType.LUNCH, 12, 30))
        assertEquals(CharacterState.PREP, CharacterStateDeriver.derive(at(11, 45), plan, meals))
    }

    @Test
    fun mealTimeReachedNotCompleted_eating() {
        val meals = listOf(meal(MealType.LUNCH, 12, 30))
        assertEquals(CharacterState.EATING, CharacterStateDeriver.derive(at(12, 0), plan, meals))
    }

    @Test
    fun allMealsCompleted_rest() {
        val meals = listOf(
            meal(MealType.BREAKFAST, 8, 0, MealStatus.COMPLETED),
            meal(MealType.LUNCH, 12, 30, MealStatus.COMPLETED),
            meal(MealType.DINNER, 15, 0, MealStatus.COMPLETED),
        )
        assertEquals(CharacterState.REST, CharacterStateDeriver.derive(at(16, 0), plan, meals))
    }

    @Test
    fun betweenMealsOrOutsideWindow_fasting() {
        // 早餐已完成，还没到午餐备餐
        val meals = listOf(
            meal(MealType.BREAKFAST, 8, 0, MealStatus.COMPLETED),
            meal(MealType.LUNCH, 12, 30),
            meal(MealType.DINNER, 15, 0),
        )
        assertEquals(CharacterState.FASTING, CharacterStateDeriver.derive(at(10, 0), plan, meals))
    }

    @Test
    fun previousDoneButLaterPending_pastMealTime_eating() {
        val meals = listOf(
            meal(MealType.BREAKFAST, 8, 0, MealStatus.COMPLETED),
            meal(MealType.LUNCH, 12, 30),
        )
        assertEquals(CharacterState.EATING, CharacterStateDeriver.derive(at(13, 0), plan, meals))
    }

    @Test
    fun partialCompletion_lastPendingPast_eatingNotFasting() {
        val meals = listOf(
            meal(MealType.BREAKFAST, 8, 0, MealStatus.COMPLETED),
            meal(MealType.LUNCH, 12, 30, MealStatus.COMPLETED),
            meal(MealType.DINNER, 15, 0),
        )
        // 晚餐 15:00 已到且未完成 → 派生优先级 EATING 先于 REST 判定（完整定义 §2.4）
        assertEquals(CharacterState.EATING, CharacterStateDeriver.derive(at(16, 0), plan, meals))
    }
}
