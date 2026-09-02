package com.ly.fast16.feature.record.ui

import com.ly.fast16.domain.model.MealType
import java.time.LocalDate
import java.time.YearMonth

/** 记录页 UI 状态：统计概览 + 月历 */
sealed interface RecordUiState {
    data object Loading : RecordUiState

    data class Content(
        val month: YearMonth,
        val selected: LocalDate?,
        /** date → 当日已打卡餐次集合（驱动日历三点与详情） */
        val checked: Map<LocalDate, Set<MealType>> = emptyMap(),
        /** 连续打卡天数 */
        val streak: Int = 0,
        /** 本月完成率 0..1 */
        val monthRate: Float = 0f,
        /** 本周完成率 0..1 */
        val weekRate: Float = 0f,
        /** 近 7 天每日完成餐数（旧 → 新，柱状图） */
        val weekBars: List<Int> = emptyList(),
        /** 选中日已打卡集合（详情补打卡） */
        val selectedChecked: Set<MealType> = emptySet(),
        /** 选中日是否有就餐计划（详情显示编辑/删除入口） */
        val hasPlanOnSelected: Boolean = false,
        /** 选中日计划 id（编辑 / 删除用；无计划为 -1） */
        val selectedPlanId: Long = -1L,
        val today: LocalDate,
    ) : RecordUiState
}
