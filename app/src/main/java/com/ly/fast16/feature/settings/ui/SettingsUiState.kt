package com.ly.fast16.feature.settings.ui

import android.content.Intent
import com.ly.fast16.data.local.FontMode

/** 设置页 UI 状态：设置项当前值列表 */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(
        val windowHours: Int,
        val minMealGapMinutes: Int,
        val dinnerBufferMinutes: Int,
        val defaultPrepLunchMinutes: Int,
        val defaultPrepDinnerMinutes: Int,
        val defaultReminderMode: String,
        /** 通知权限是否已授权（33+ 运行时；<33 恒 true） */
        val notificationGranted: Boolean = true,
        /** 精确闹钟是否可精确排程（31+；false = 提醒可能被 Doze 延迟） */
        val exactAlarmEnabled: Boolean = true,
        /** 精确闹钟授权引导 Intent（canScheduleExact 时 null） */
        val exactAlarmGrantIntent: Intent? = null,
        /** 字体模式（像素风 / 系统默认；默认系统） */
        val fontMode: FontMode = FontMode.SYSTEM,
    ) : SettingsUiState
}
