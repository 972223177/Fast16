package com.ly.fast16.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.system.ExactAlarmGate
import com.ly.fast16.core.system.NotificationPermission
import com.ly.fast16.data.local.AppSettings
import com.ly.fast16.data.local.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 纯 reducer：偏好写 SettingsStore（SSOT），settings flow 驱动 UI 自动更新 */
object SettingsReducer {
    fun reduce(state: SettingsUiState, intent: SettingsIntent): SettingsUiState =
        when (intent) {
            SettingsIntent.ShowOnboarding -> state // 重看弹窗由 Screen 层控制
            // 其余写 SettingsStore，settings flow 驱动 UI 自动更新（SSOT）
            is SettingsIntent.SetReminderMode,
            is SettingsIntent.SetWindowHours,
            is SettingsIntent.SetMinMealGap,
            is SettingsIntent.SetDinnerBuffer,
            is SettingsIntent.SetPrepLunch,
            is SettingsIntent.SetPrepDinner,
            is SettingsIntent.SetFontMode,
            -> state
        }
}

/** 设置页 ViewModel：订阅 SettingsStore（SSOT）+ 权限状态（ExactAlarmGate / NotificationPermission） */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val exactAlarmGate: ExactAlarmGate,
    private val notificationPermission: NotificationPermission,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s -> _uiState.value = buildContent(s) }
        }
    }

    /** 权限变化后手动刷新（通知授权回调），SSOT 不变（设置本身未改） */
    fun refreshPermissions() {
        viewModelScope.launch {
            val s = settingsStore.settings.first()
            _uiState.value = buildContent(s)
        }
    }

    private fun buildContent(s: AppSettings): SettingsUiState.Content = SettingsUiState.Content(
        windowHours = s.windowHours,
        minMealGapMinutes = s.minMealGapMinutes,
        dinnerBufferMinutes = s.dinnerBufferMinutes,
        defaultPrepLunchMinutes = s.defaultPrepLunchMinutes,
        defaultPrepDinnerMinutes = s.defaultPrepDinnerMinutes,
        defaultReminderMode = s.defaultReminderMode.name,
        notificationGranted = notificationPermission.isGranted,
        exactAlarmEnabled = exactAlarmGate.canScheduleExact,
        exactAlarmGrantIntent = exactAlarmGate.grantIntent(),
        fontMode = s.fontMode,
    )

    fun onIntent(intent: SettingsIntent) {
        _uiState.value = SettingsReducer.reduce(_uiState.value, intent)
        viewModelScope.launch {
            when (intent) {
                is SettingsIntent.SetReminderMode -> settingsStore.setDefaultReminderMode(intent.mode)
                is SettingsIntent.SetWindowHours -> settingsStore.setWindowHours(intent.hours)
                is SettingsIntent.SetMinMealGap -> settingsStore.setMinMealGapMinutes(intent.minutes)
                is SettingsIntent.SetDinnerBuffer -> settingsStore.setDinnerBufferMinutes(intent.minutes)
                is SettingsIntent.SetPrepLunch -> settingsStore.setDefaultPrepLunchMinutes(intent.minutes)
                is SettingsIntent.SetPrepDinner -> settingsStore.setDefaultPrepDinnerMinutes(intent.minutes)
                is SettingsIntent.SetFontMode -> settingsStore.setFontMode(intent.mode)
                SettingsIntent.ShowOnboarding -> Unit
            }
        }
    }
}
