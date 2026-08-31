package com.ly.fast16.domain.schedule

import java.time.Duration
import java.time.Instant

/**
 * 微调约束校验（FR-4 / 设计方案 §7.3 / 完整定义 §2.2）。
 *
 * 违反任一约束返回对应像素风提示文案（Create 页红框闪烁 + 文案）；
 * 空列表 = 校验通过。
 */
object PlanValidator {

    fun validate(
        lunch: Instant,
        dinner: Instant,
        breakfast: Instant,
        window: Duration,
        minGap: Duration,
        bufferEnd: Duration,
    ): List<String> {
        val errors = mutableListOf<String>()
        val earliestLunch = breakfast.plus(minGap)
        if (lunch < earliestLunch) {
            errors += "午餐需在早餐后至少 ${minGap.toMinutes()} 分钟"
        }
        if (dinner < lunch.plus(minGap)) {
            errors += "晚餐需在午餐后至少 ${minGap.toMinutes()} 分钟"
        }
        val latestDinner = breakfast.plus(window).minus(bufferEnd)
        if (dinner > latestDinner) {
            errors += "晚餐不能晚于窗口结束前 ${bufferEnd.toMinutes()} 分钟"
        }
        return errors
    }
}
