package com.ly.fast16.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import org.koin.androidx.compose.koinViewModel
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------- Intent ----------

/** 设置页用户意图（改偏好 / 重看说明） */
sealed interface SettingsIntent {
    data object ChangePreference : SettingsIntent
    data object ShowOnboarding : SettingsIntent
}

// ---------- State ----------

/** 设置页 UI 状态：设置项列表 */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    /** 设置项列表（FR-9：窗口时长 / 餐间隔 / 备餐默认 / 提醒模式） */
    data class Content(
        val windowHours: Int = 8,
        val minMealGapMinutes: Int = 180,
        val items: List<String> = listOf(
            "进食窗口时长 · 8 小时",
            "餐间隔下限 · 180 分钟",
            "晚餐距窗口结束 · 30 分钟",
            "默认提醒 · 通知",
        ),
    ) : SettingsUiState
}

// ---------- Reducer（纯函数） ----------

/** 设置页 Reducer：纯函数 */
object SettingsReducer {
    fun reduce(state: SettingsUiState, intent: SettingsIntent): SettingsUiState =
        when (intent) {
            SettingsIntent.ChangePreference,
            SettingsIntent.ShowOnboarding,
            -> state // M0 占位；M1 接入 SettingsStore Flow
        }
}

// ---------- ViewModel ----------

/** 设置页 ViewModel */
class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Content())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onIntent(intent: SettingsIntent) {
        _uiState.value = SettingsReducer.reduce(_uiState.value, intent)
    }
}

// ---------- Screen ----------

@Composable
/** 设置页 Screen（设置项列表 + 重看 8:16 说明入口） */
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onShowOnboarding: () -> Unit = {},
) {
    val vm: SettingsViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()

    when (val s = state) {
        SettingsUiState.Loading -> Unit
        is SettingsUiState.Content -> SettingsContent(
            state = s,
            onShowOnboarding = onShowOnboarding,
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Content,
    onShowOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.Start,
    ) {
        PixelText(text = "设置", color = PixelColors.yellow, fontSize = PixelType.Size.md)

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        state.items.forEach { item ->
            PixelCard(
                modifier = Modifier.padding(vertical = PixelShape.Spacing.xs),
            ) {
                PixelText(text = item, color = PixelColors.white)
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        PixelButton(
            text = "查看 8:16 说明",
            onClick = onShowOnboarding,
            primary = false,
        )
    }
}

@PreviewPixel
@Composable
private fun SettingsScreenPreview() {
    PixelTheme {
        SettingsContent(state = SettingsUiState.Content(), onShowOnboarding = {})
    }
}
