package com.ly.fast16.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// ---------- Intent（基建文档 §2.3 HomeScreen） ----------

/** 首页用户/系统意图（基建文档 §2.3 HomeScreen） */
sealed interface HomeIntent {
    /** 进入新建计划流程 */
    data object NewPlan : HomeIntent

    /** 刷新（系统事件：闹钟触发 / 打卡完成转 Intent） */
    data object Refresh : HomeIntent
}

// ---------- State ----------

/** 首页 UI 状态：Loading / Content */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val hasPlanToday: Boolean = false,
        val characterText: String = "来记录早餐，开启今天第一餐吧！",
    ) : HomeUiState
}

// ---------- 一次性事件（导航走 Channel，不塞进持久 State） ----------

/** 首页一次性事件（导航走 Channel，不塞进持久 State） */
sealed interface HomeEvent {
    data object NavigateToCreate : HomeEvent
}

// ---------- Reducer（纯函数：无 IO / 无时间源 / 无 Android 依赖） ----------

/** 首页 Reducer：纯函数（无 IO / 无时间源 / 无 Android 依赖） */
object HomeReducer {
    fun reduce(state: HomeUiState, intent: HomeIntent): HomeUiState = when (intent) {
        HomeIntent.NewPlan -> state // 导航副作用由 ViewModel 经 Channel 处理
        HomeIntent.Refresh -> HomeUiState.Content()
    }
}

// ---------- ViewModel ----------

/** 首页 ViewModel：订阅 Intent → 副作用 → reduce → StateFlow */
class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    init {
        _uiState.value = HomeReducer.reduce(HomeUiState.Loading, HomeIntent.Refresh)
    }

    fun onIntent(intent: HomeIntent) {
        _uiState.value = HomeReducer.reduce(_uiState.value, intent)
        when (intent) {
            HomeIntent.NewPlan -> viewModelScope.launch { _events.send(HomeEvent.NavigateToCreate) }
            HomeIntent.Refresh -> Unit
        }
    }
}

// ---------- Screen ----------

@Composable
/** 首页 Screen（MVI 壳） */
fun HomeScreen(
    onNewPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: HomeViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                HomeEvent.NavigateToCreate -> onNewPlan()
            }
        }
    }

    when (val s = state) {
        HomeUiState.Loading -> Unit
        is HomeUiState.Content -> HomeContent(
            state = s,
            onNewPlan = { vm.onIntent(HomeIntent.NewPlan) },
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onNewPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 角色占位方块（M1 替换为小健 PixelSprite；M2 正式美术）
        PixelCard(
            modifier = Modifier.size(72.dp),
            backgroundColor = PixelColors.blue.copy(alpha = 0.25f),
        ) { }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        PixelText(text = state.characterText, color = PixelColors.white)

        Spacer(modifier = Modifier.height(PixelShape.Spacing.xl))

        PixelButton(text = "记录早餐", onClick = onNewPlan, primary = true)
    }
}

@PreviewPixel
@Composable
private fun HomeScreenPreview() {
    PixelTheme {
        HomeContent(
            state = HomeUiState.Content(),
            onNewPlan = {},
        )
    }
}
