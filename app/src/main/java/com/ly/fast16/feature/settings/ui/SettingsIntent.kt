package com.ly.fast16.feature.settings.ui

import com.ly.fast16.data.local.FontMode
import com.ly.fast16.domain.model.ReminderMode

/** 设置页用户意图（改偏好 / 重看说明；偏好均可配，写 SettingsStore） */
sealed interface SettingsIntent {
    data object ShowOnboarding : SettingsIntent

    /** 修改默认提醒模式（写 SettingsStore，副作用在 ViewModel） */
    data class SetReminderMode(val mode: ReminderMode) : SettingsIntent

    data class SetWindowHours(val hours: Int) : SettingsIntent
    data class SetMinMealGap(val minutes: Int) : SettingsIntent
    data class SetDinnerBuffer(val minutes: Int) : SettingsIntent
    data class SetPrepLunch(val minutes: Int) : SettingsIntent
    data class SetPrepDinner(val minutes: Int) : SettingsIntent

    /** 切换字体模式（像素风 / 系统默认；写 SettingsStore，PixelTheme 实时响应） */
    data class SetFontMode(val mode: FontMode) : SettingsIntent
}
