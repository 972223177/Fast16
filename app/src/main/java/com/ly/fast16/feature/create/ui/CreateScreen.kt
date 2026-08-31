package com.ly.fast16.feature.create.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.koin.androidx.compose.koinViewModel
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// ---------- Intent ----------

/** 新建计划用户意图（记录早餐→选方案→微调→生成） */
sealed interface CreateIntent {
    data object NextStep : CreateIntent
    data object Generate : CreateIntent
}

// ---------- State ----------

/** 新建计划 UI 状态：Loading / Content（step 1=早餐，2=方案） */
sealed interface CreateUiState {
    data object Loading : CreateUiState

    /** @param step 1=输入早餐时间，2=选取/微调候选（FR-1~FR-4） */
    data class Content(
        val step: Int = 1,
        val breakfastLabel: String = "--:--",
        val candidates: List<String> = listOf("均衡", "午餐偏早", "晚餐偏晚"),
        val selectedIndex: Int = 0,
    ) : CreateUiState
}

/** 新建计划一次性事件（生成成功） */
sealed interface CreateEvent {
    data object Generated : CreateEvent
}

// ---------- Reducer（纯函数） ----------

/** 新建计划 Reducer：纯函数 */
object CreateReducer {
    fun reduce(state: CreateUiState, intent: CreateIntent): CreateUiState =
        when (intent) {
            CreateIntent.NextStep -> (state as? CreateUiState.Content)?.copy(step = 2) ?: state
            CreateIntent.Generate -> state // 生成副作用由 VM 处理
        }
}

// ---------- ViewModel ----------

/** 新建计划 ViewModel */
class CreateViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<CreateUiState>(CreateUiState.Content())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val _events = Channel<CreateEvent>(Channel.BUFFERED)
    val events: Flow<CreateEvent> = _events.receiveAsFlow()

    fun onIntent(intent: CreateIntent) {
        _uiState.value = CreateReducer.reduce(_uiState.value, intent)
        when (intent) {
            CreateIntent.NextStep -> Unit
            CreateIntent.Generate -> viewModelScope.launch { _events.send(CreateEvent.Generated) }
        }
    }
}

// ---------- Screen ----------

@Composable
/** 新建计划 Screen（全屏无底栏） */
fun CreateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: CreateViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()

    when (val s = state) {
        CreateUiState.Loading -> Unit
        is CreateUiState.Content -> CreateContent(
            state = s,
            onNext = { vm.onIntent(CreateIntent.NextStep) },
            onBack = onBack,
            onGenerate = {
                vm.onIntent(CreateIntent.Generate)
                onBack()
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun CreateContent(
    state: CreateUiState.Content,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelText(
            text = if (state.step == 1) "步骤 1 · 记录早餐" else "步骤 2 · 选择方案",
            color = PixelColors.yellow,
            fontSize = PixelType.Size.md,
        )

        Spacer(modifier = Modifier.height(PixelShape.Spacing.xl))

        when (state.step) {
            1 -> PixelCard {
                PixelText(
                    text = "早餐时间",
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelText(
                    text = state.breakfastLabel,
                    digital = true,
                    fontSize = PixelType.Size.lg,
                )
            }

            else -> {
                state.candidates.forEachIndexed { index, label ->
                    val selected = index == state.selectedIndex
                    PixelCard(
                        borderColor = if (selected) PixelColors.yellow else Color.Black,
                        modifier = Modifier.padding(vertical = PixelShape.Spacing.xs),
                    ) {
                        PixelText(
                            text = label,
                            color = if (selected) PixelColors.white else PixelColors.gray,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.xl))

        Row {
            PixelButton(
                text = "上一步",
                onClick = onBack,
                primary = false,
                modifier = Modifier.width(120.dp),
            )
            Spacer(modifier = Modifier.width(PixelShape.Spacing.md))
            PixelButton(
                text = if (state.step == 1) "下一步" else "生成计划",
                onClick = if (state.step == 1) onNext else onGenerate,
                primary = true,
                modifier = Modifier.width(120.dp),
            )
        }
    }
}

@PreviewPixel
@Composable
private fun CreateScreenPreview() {
    PixelTheme {
        CreateContent(state = CreateUiState.Content(), onNext = {}, onBack = {}, onGenerate = {})
    }
}
