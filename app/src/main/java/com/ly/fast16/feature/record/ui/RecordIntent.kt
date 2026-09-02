package com.ly.fast16.feature.record.ui

import com.ly.fast16.domain.model.MealType
import java.time.LocalDate

/** 记录页用户意图（切月 / 点日期 / 补打卡 / 删除计划） */
sealed interface RecordIntent {
    data class SwitchMonth(val delta: Int) : RecordIntent
    data class TapDate(val date: LocalDate?) : RecordIntent
    data class BackfillCheckIn(val mealType: MealType) : RecordIntent
    data object DeletePlan : RecordIntent
}
