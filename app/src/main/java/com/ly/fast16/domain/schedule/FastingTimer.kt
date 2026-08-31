package com.ly.fast16.domain.schedule

import com.ly.fast16.domain.model.Meal
import java.time.Duration
import java.time.Instant

/**
 * 断食计时（完整定义 §2.5）：首页「断食剩余」与下一动作时刻。
 *
 * 下一动作时刻 = 距 now 最近的「未来 prepTime（有备餐）或 mealTime」。
 */
object FastingTimer {

    /** 最近的未来动作时刻（无 → null，如当日三餐全部完成） */
    fun nextAction(now: Instant, meals: List<Meal>): Instant? =
        meals.map { m ->
            if (m.prepMinutes > 0) m.prepTime else m.mealTime
        }.filter { it > now }.minOrNull()

    /** 断食剩余时长（无未来动作 → null） */
    fun fastingRemaining(now: Instant, meals: List<Meal>): Duration? =
        nextAction(now, meals)?.let { Duration.between(now, it) }
}
