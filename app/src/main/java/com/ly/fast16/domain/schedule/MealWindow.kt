package com.ly.fast16.domain.schedule

import com.ly.fast16.domain.model.MealType
import java.time.LocalTime

/**
 * 按时段推断餐次（无计划「打卡现在这餐」入口用）。
 *
 * 无计划时用户只点一个「打卡」按钮，由系统按**本地时段**自动判定当前是哪一餐——
 * 用户不选餐次（避免误记），按 8:16 常规作息推断：
 * - 早餐 04:00–10:59
 * - 午餐 11:00–14:59
 * - 晚餐 15:00–21:59
 * - 深夜 22:00–03:59 无法判定 → null（不提供打卡）
 *
 * 边界为产品默认值，可按需调整；纯函数无时间源，可单测。
 * 与 MealStateMachine / CharacterStateDeriver 同属 domain/schedule 派生族。
 */
object MealWindow {

    /** 早餐时段起始 */
    val BREAKFAST_START: LocalTime = LocalTime.of(4, 0)

    /** 午餐时段起始 */
    val LUNCH_START: LocalTime = LocalTime.of(11, 0)

    /** 晚餐时段起始 */
    val DINNER_START: LocalTime = LocalTime.of(15, 0)

    /** 晚餐时段结束（含），此后为「非用餐时段」 */
    val DINNER_END: LocalTime = LocalTime.of(21, 59)

    /** 按本地时刻推断餐次；深夜（22:00–03:59）返回 null */
    fun detect(now: LocalTime): MealType? = when (now) {
        in BREAKFAST_START..LUNCH_START.minusNanos(1) -> MealType.BREAKFAST
        in LUNCH_START..DINNER_START.minusNanos(1) -> MealType.LUNCH
        in DINNER_START..DINNER_END -> MealType.DINNER
        else -> null
    }
}
