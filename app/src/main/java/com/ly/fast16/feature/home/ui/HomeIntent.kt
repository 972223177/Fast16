package com.ly.fast16.feature.home.ui

import com.ly.fast16.domain.model.MealType

/** 首页用户/系统意图（基建文档 §2.3 HomeScreen） */
sealed interface HomeIntent {
    /** 新建计划 */
    data object NewPlan : HomeIntent

    /** 打卡某餐 */
    data class CheckIn(val mealType: MealType) : HomeIntent

    /** 撤销打卡（已打卡餐二次点击） */
    data class UncheckIn(val mealType: MealType) : HomeIntent

    /** 无计划空态「打卡现在这餐」：按 MealWindow 时段自动判定餐次 */
    data object CheckInNow : HomeIntent

    /** 编辑某餐备餐时长（分钟，0–180；tile 备餐可点编辑） */
    data class EditPrep(val mealType: MealType, val minutes: Int) : HomeIntent
}
