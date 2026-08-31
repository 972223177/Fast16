package com.ly.fast16.domain.schedule

import com.ly.fast16.domain.model.CharacterState
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealStatus
import java.time.Instant

/**
 * 像素小人状态派生（设计方案 §8.6 / 完整定义 §2.4）。
 *
 * 派生优先级：无计划 → IDLE；进行中 PREP > EATING；三餐全完成 → REST；
 * 其余（两餐之间 / 窗口外）→ FASTING。与餐次状态机同源，不单独持久化。
 */
object CharacterStateDeriver {

    fun derive(now: Instant, plan: MealPlan?, meals: List<Meal>): CharacterState {
        if (plan == null || meals.isEmpty()) return CharacterState.IDLE

        meals.firstOrNull { m ->
            m.prepMinutes > 0 && now >= m.prepTime && now < m.mealTime
        }?.let { return CharacterState.PREP }

        meals.lastOrNull { m -> now >= m.mealTime && m.status != MealStatus.COMPLETED }
            ?.let { return CharacterState.EATING }

        if (meals.all { it.status == MealStatus.COMPLETED }) return CharacterState.REST
        return CharacterState.FASTING
    }
}
