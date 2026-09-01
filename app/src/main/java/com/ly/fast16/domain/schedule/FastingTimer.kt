package com.ly.fast16.domain.schedule

import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealStatus
import java.time.Duration
import java.time.Instant

/**
 * 断食计时（完整定义 §2.5）：首页「断食剩余」与下一动作时刻。
 *
 * 下一动作时刻 = 距 now 最近的「未来 prepTime（有备餐）或 mealTime」，
 * **但已打卡完成的餐（COMPLETED）不参与倒计时**——打卡 = 这餐已完成，
 * 无需等待；提前全部打卡则无未来动作 → 倒计时消失（与打卡状态统一）。
 */
object FastingTimer {

    /** 最近的未来「未完成餐」动作时刻（无 → null，如当日三餐全部打卡完成或时间已过） */
    fun nextAction(now: Instant, meals: List<Meal>): Instant? =
        meals.asSequence()
            .filter { it.status != MealStatus.COMPLETED } // 已打卡完成的餐不产生倒计时
            .map { m -> if (m.prepMinutes > 0) m.prepTime else m.mealTime }
            .filter { it > now }
            .minOrNull()

    /** 断食剩余时长（无未来动作 → null） */
    fun fastingRemaining(now: Instant, meals: List<Meal>): Duration? =
        nextAction(now, meals)?.let { Duration.between(now, it) }
}
