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
 *
 * **打卡联动**：已打卡完成的餐（COMPLETED）不参与 PREP/EATING 判定——
 * 提前打卡 = 该餐已完成，不再产生备餐/开饭状态，与 FastingTimer（倒计时跳过
 * COMPLETED）口径一致，保证「倒计时 / 角色 / 气泡」三者联动。
 */
object CharacterStateDeriver {

    fun derive(now: Instant, plan: MealPlan?, meals: List<Meal>): CharacterState {
        if (plan == null || meals.isEmpty()) return CharacterState.IDLE

        // PREP：未完成且有备餐的餐处于备餐窗口才进入备餐态（排除已打卡完成）
        meals.firstOrNull { m ->
            m.prepMinutes > 0 && m.status != MealStatus.COMPLETED &&
                now >= m.prepTime && now < m.mealTime
        }?.let { return CharacterState.PREP }

        // 显式取「已到点且未完成」的最晚一餐（maxByOrNull 不依赖输入有序——原 lastOrNull 隐含升序契约）
        meals.filter { m -> now >= m.mealTime && m.status != MealStatus.COMPLETED }
            .maxByOrNull { it.mealTime }
            ?.let { return CharacterState.EATING }

        if (meals.all { it.status == MealStatus.COMPLETED }) return CharacterState.REST
        return CharacterState.FASTING
    }
}
