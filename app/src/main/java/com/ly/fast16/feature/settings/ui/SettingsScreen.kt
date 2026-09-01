package com.ly.fast16.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.designsystem.component.PixelDialog
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.data.local.SettingsStore
import com.ly.fast16.feature.onboarding.ui.OnboardingDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// ---------- Intent ----------

/** 设置页用户意图（改提醒模式 / 重看说明；其余偏好只读展示） */
sealed interface SettingsIntent {
    data object ShowOnboarding : SettingsIntent

    /** 修改默认提醒模式（写 SettingsStore，副作用在 ViewModel） */
    data class SetReminderMode(val mode: ReminderMode) : SettingsIntent
}

// ---------- State ----------

/** 设置页 UI 状态：设置项当前值列表 */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(
        val windowHours: Int,
        val minMealGapMinutes: Int,
        val dinnerBufferMinutes: Int,
        val defaultPrepBreakfastMinutes: Int,
        val defaultPrepLunchMinutes: Int,
        val defaultPrepDinnerMinutes: Int,
        val defaultReminderMode: String,
    ) : SettingsUiState
}

// ---------- Reducer（纯函数；M1 无状态变更，偏好可配归 M3） ----------

object SettingsReducer {
    fun reduce(state: SettingsUiState, intent: SettingsIntent): SettingsUiState =
        when (intent) {
            SettingsIntent.ShowOnboarding -> state // 重看弹窗由 Screen 层控制
            is SettingsIntent.SetReminderMode -> state // 写 SettingsStore，flow 驱动 UI 自动更新（SSOT）
        }
}

// ---------- ViewModel ----------

/** 设置页 ViewModel：订阅 SettingsStore（SSOT，UI 只读展示当前值） */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.map { s ->
                SettingsUiState.Content(
                    windowHours = s.windowHours,
                    minMealGapMinutes = s.minMealGapMinutes,
                    dinnerBufferMinutes = s.dinnerBufferMinutes,
                    defaultPrepBreakfastMinutes = s.defaultPrepBreakfastMinutes,
                    defaultPrepLunchMinutes = s.defaultPrepLunchMinutes,
                    defaultPrepDinnerMinutes = s.defaultPrepDinnerMinutes,
                    defaultReminderMode = s.defaultReminderMode.name,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onIntent(intent: SettingsIntent) {
        _uiState.value = SettingsReducer.reduce(_uiState.value, intent)
        if (intent is SettingsIntent.SetReminderMode) {
            // 写 DataStore；settings flow 变化驱动 UI 自动刷新（SSOT）
            viewModelScope.launch { settingsStore.setDefaultReminderMode(intent.mode) }
        }
    }
}

// ---------- Screen ----------

/** 设置页 Screen（设置项列表 + 默认提醒只读项 M3 挂点 + 重看 8:16 说明） */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onShowOnboarding: () -> Unit = {},
) {
    val vm: SettingsViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    var showOnboarding by remember { mutableStateOf(false) }

    if (showOnboarding) {
        OnboardingDialog(onDismiss = { showOnboarding = false })
    }

    when (val s = state) {
        SettingsUiState.Loading -> Unit
        is SettingsUiState.Content -> SettingsContent(
            state = s,
            onShowOnboarding = {
                showOnboarding = true
                onShowOnboarding()
            },
            onSetReminderMode = { vm.onIntent(SettingsIntent.SetReminderMode(it)) },
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Content,
    onShowOnboarding: () -> Unit,
    onSetReminderMode: (ReminderMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showReminderDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.Start,
    ) {
        PixelText(text = "SETTINGS", color = PixelColors.white, fontSize = PixelType.Size.xs, digital = true)
        Spacer(modifier = Modifier.height(2.dp))
        PixelText(text = "偏好与关于", color = PixelColors.gray, fontSize = PixelType.Size.xs)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(PixelColors.panel.copy(alpha = 0.6f)),
        )
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 设置项（原型 set-row：label 左 / value 右）
        SettingRow(label = "进食窗口", value = "${state.windowHours} 小时")
        SettingRow(label = "餐间隔下限", value = "${state.minMealGapMinutes} 分钟")
        SettingRow(label = "晚餐距窗口结束缓冲", value = "${state.dinnerBufferMinutes} 分钟")
        SettingRow(
            label = "默认备餐时长",
            value = "早${state.defaultPrepBreakfastMinutes} · 午${state.defaultPrepLunchMinutes} · 晚${state.defaultPrepDinnerMinutes}",
        )
        // 提醒模式（M3 可配：点击弹窗 4 选，写 SettingsStore）
        SettingRow(
            label = "提醒模式",
            value = reminderLabel(state.defaultReminderMode),
            onClick = { showReminderDialog = true },
        )

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 查看 8:16 说明（可点，带 ▶）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PixelColors.panel)
                .border(BorderStroke(PixelShape.borderWidth, Color.Black))
                .clickable(onClick = onShowOnboarding)
                .padding(PixelShape.Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelText(text = "查看 8:16 说明", color = PixelColors.white, fontSize = PixelType.Size.sm)
                PixelText(text = "▶", color = PixelColors.gray, fontSize = PixelType.Size.xs)
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))

        // 关于
        SettingRow(label = "关于 Fast16", value = "v0.1.0")
    }

    // 提醒模式选择弹窗（像素 chip，选中黄底黑字）
    if (showReminderDialog) {
        PixelDialog(onDismiss = { showReminderDialog = false }, title = "提醒模式") {
            Column {
                PixelText(
                    text = "到点提醒的呈现方式：",
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                ReminderMode.entries.forEach { mode ->
                    val selected = mode.name == state.defaultReminderMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = PixelShape.Spacing.xs)
                            .background(if (selected) PixelColors.yellow else PixelColors.panel)
                            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
                            .clickable {
                                onSetReminderMode(mode)
                                showReminderDialog = false
                            }
                            .padding(PixelShape.Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelText(
                            text = reminderLabel(mode.name),
                            color = if (selected) PixelColors.bg else PixelColors.white,
                            fontSize = PixelType.Size.sm,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (selected) {
                            PixelText(text = "●", color = PixelColors.bg, fontSize = PixelType.Size.xs)
                        }
                    }
                }
            }
        }
    }
}

/** 设置行（原型 set-row：面板底黑边，label 左 / 像素 value 右；可选点击） */
@Composable
private fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(PixelShape.Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelText(text = label, color = PixelColors.white, fontSize = PixelType.Size.sm)
        PixelText(text = value, color = PixelColors.gray, fontSize = PixelType.Size.xs, digital = true)
    }
}

private fun reminderLabel(mode: String): String = when (mode) {
    "NOTIFY" -> "通知"
    "VIBRATE" -> "震动"
    "SOUND" -> "铃声"
    "ALARM" -> "闹钟"
    else -> "无"
}

@PreviewPixel
@Composable
private fun SettingsScreenPreview() {
    PixelTheme {
        SettingsContent(
            state = SettingsUiState.Content(
                windowHours = 8,
                minMealGapMinutes = 180,
                dinnerBufferMinutes = 30,
                defaultPrepBreakfastMinutes = 0,
                defaultPrepLunchMinutes = 30,
                defaultPrepDinnerMinutes = 45,
                defaultReminderMode = "NOTIFY",
            ),
            onShowOnboarding = {},
            onSetReminderMode = {},
        )
    }
}
