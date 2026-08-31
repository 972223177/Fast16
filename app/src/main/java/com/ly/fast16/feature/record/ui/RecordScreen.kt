package com.ly.fast16.feature.record.ui

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
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelProgressBar
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

/** 记录页用户意图（切月 / 点日期 / 补打卡） */
sealed interface RecordIntent {
    data object SwitchMonth : RecordIntent
    data object TapDate : RecordIntent
    data object BackfillCheckIn : RecordIntent
}

// ---------- State ----------

/** 记录页 UI 状态：统计概览 + 月历 */
sealed interface RecordUiState {
    data object Loading : RecordUiState

    /** 统计概览（连续天数 / 完成率）+ 月历（M0 空态占位） */
    data class Content(
        val streak: Int = 0,
        val monthRate: Float = 0f,
        val weekRate: Float = 0f,
        val monthLabel: String = "2026-08",
    ) : RecordUiState
}

// ---------- Reducer（纯函数） ----------

/** 记录页 Reducer：纯函数 */
object RecordReducer {
    fun reduce(state: RecordUiState, intent: RecordIntent): RecordUiState =
        when (intent) {
            RecordIntent.SwitchMonth,
            RecordIntent.TapDate,
            RecordIntent.BackfillCheckIn,
            -> state // M0 占位：无状态变化；M1/M2 由打卡数据流驱动
        }
}

// ---------- ViewModel ----------

/** 记录页 ViewModel */
class RecordViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<RecordUiState>(RecordUiState.Content())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    fun onIntent(intent: RecordIntent) {
        _uiState.value = RecordReducer.reduce(_uiState.value, intent)
    }
}

// ---------- Screen ----------

@Composable
/** 记录页 Screen（统计概览 + 空月历占位） */
fun RecordScreen(modifier: Modifier = Modifier) {
    val vm: RecordViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()

    when (val s = state) {
        RecordUiState.Loading -> Unit
        is RecordUiState.Content -> RecordContent(state = s, modifier = modifier)
    }
}

@Composable
private fun RecordContent(
    state: RecordUiState.Content,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.Start,
    ) {
        // 统计概览（FR-14 / 设计方案 §8.7）
        PixelText(text = "连续打卡", color = PixelColors.gray, fontSize = PixelType.Size.xs)
        PixelText(text = "${state.streak}", digital = true, fontSize = PixelType.Size.lg, color = PixelColors.yellow)

        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        PixelCard {
            Column {
                PixelText(text = "本月完成率", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelProgressBar(progress = state.monthRate, segments = 16)
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelText(text = "本周完成率", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelProgressBar(progress = state.weekRate, segments = 16)
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 月历占位（M2 替换为 PixelCalendar + PixelMealDots）
        PixelText(
            text = "${state.monthLabel} · 月历（M2 落地）",
            color = PixelColors.gray,
            fontSize = PixelType.Size.xs,
        )
        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
        PixelCard(backgroundColor = PixelColors.bg.copy(alpha = 0.4f)) {
            PixelText(text = "空月历 · 记录你的三餐打卡", color = PixelColors.gray)
        }
    }
}

@PreviewPixel
@Composable
private fun RecordScreenPreview() {
    PixelTheme {
        RecordContent(state = RecordUiState.Content())
    }
}
