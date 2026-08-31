package com.ly.fast16.domain.schedule

import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealStatus
import java.time.Instant

/**
 * 餐次状态推进（完整定义 §2.3）。
 *
 * 状态落库但可由「时间 + 打卡」重算：前台时做一次 reconcile，
 * 避免闹钟漏触发导致状态滞留。
 */
object MealStateMachine {

    fun advance(meal: Meal, now: Instant, hasCheckIn: Boolean): MealStatus = when {
        hasCheckIn -> MealStatus.COMPLETED
        meal.prepMinutes > 0 && now >= meal.prepTime && now < meal.mealTime -> MealStatus.PREPARING
        now >= meal.mealTime -> MealStatus.EATING
        else -> MealStatus.SCHEDULED
    }
}
