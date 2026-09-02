package com.ly.fast16.feature.create.ui

import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.domain.schedule.Candidate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 新建计划 UI 状态：Loading / Content（step 1=早餐，2=方案微调） */
sealed interface CreateUiState {
    data object Loading : CreateUiState

    data class Content(
        val step: Int = 1,
        /** 编辑模式（CreateRoute.planId ≥ 0）：标题切 EDIT PLAN，生成走更新+重排 */
        val isEdit: Boolean = false,
        /** 计划日期（新建=今天或未来「预约」；编辑=原计划日期） */
        val date: LocalDate,
        val zone: ZoneId,
        val breakfastTime: LocalTime,
        // Settings 快照（候选生成参数 / 生成时继承 reminderMode）
        val windowHours: Int,
        val minGapMinutes: Int,
        val bufferEndMinutes: Int,
        val prepLunchDefault: Int,
        val prepDinnerDefault: Int,
        val defaultReminderMode: ReminderMode,
        // 候选与微调
        val candidates: List<Candidate> = emptyList(),
        val selectedIndex: Int = 0,
        val lunchTime: LocalTime,
        val dinnerTime: LocalTime,
        val prepLunch: Int,
        val prepDinner: Int,
        /** 午/晚餐提醒模式（编辑模式继承原值；新建=全局默认） */
        val lunchReminderMode: ReminderMode = ReminderMode.NOTIFY,
        val dinnerReminderMode: ReminderMode = ReminderMode.NOTIFY,
        val errors: List<String> = emptyList(),
    ) : CreateUiState
}

/** 新建计划一次性事件（生成成功回首页 / 失败提示） */
sealed interface CreateEvent {
    data object Generated : CreateEvent

    /** 生成失败（保存/排程异常）——Screen 层 Toast 反馈 */
    data object Failed : CreateEvent
}
