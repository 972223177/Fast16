package com.ly.fast16.feature.create.ui

import java.time.LocalTime

/** 新建计划用户意图（记录早餐→选方案→微调→生成；日期 = 预约未来计划） */
sealed interface CreateIntent {
    data object NextStep : CreateIntent
    data object PrevStep : CreateIntent

    /** 计划日期 ±1 天（新建模式不可早于今天；编辑模式保持原日期） */
    data class ShiftDate(val delta: Int) : CreateIntent

    data class PickBreakfast(val time: LocalTime) : CreateIntent
    data class SelectCandidate(val index: Int) : CreateIntent
    data class AdjustLunchTime(val time: LocalTime) : CreateIntent
    data class AdjustDinnerTime(val time: LocalTime) : CreateIntent
    data class AdjustPrepLunch(val minutes: Int) : CreateIntent
    data class AdjustPrepDinner(val minutes: Int) : CreateIntent
    data object Generate : CreateIntent
}
