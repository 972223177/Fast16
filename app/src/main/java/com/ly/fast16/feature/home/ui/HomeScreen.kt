package com.ly.fast16.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.ly.fast16.core.designsystem.component.PixelConfetti
import com.ly.fast16.core.designsystem.component.PixelConfettiBurst
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.designsystem.component.PixelToast
import com.ly.fast16.core.widget.Fast16Widget
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 首页 Screen 入口：绑定 ViewModel → 收集 State → 分发事件 → HomeContent 渲染。
 * 打卡成功 → 桌面小组件同步；轻提示 / 礼花 overlay 在此层。
 */
@Composable
fun HomeScreen(
    onNewPlan: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecord: () -> Unit = {},
    onEditPlan: (Long) -> Unit = {},
) {
    val vm: HomeViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    val toastText by vm.toast.collectAsState()
    val confetti by vm.confetti.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showToast by remember { mutableStateOf(false) }

    LaunchedEffect(toastText) {
        if (toastText != null) {
            showToast = true
        }
    }

    when (val s = state) {
        HomeUiState.Loading -> PixelLoading()
        is HomeUiState.Content -> HomeContent(
            state = s,
            liveState = vm.liveState,
            onNewPlan = { vm.onIntent(HomeIntent.NewPlan); onNewPlan() },
            onCheckIn = {
                vm.onIntent(HomeIntent.CheckIn(it))
                scope.launch { Fast16Widget().updateAll(context) } // 打卡 → 桌面小组件同步
            },
            onUncheckIn = {
                vm.onIntent(HomeIntent.UncheckIn(it))
                scope.launch { Fast16Widget().updateAll(context) } // 撤销 → 桌面小组件同步
            },
            onCheckInNow = {
                vm.onIntent(HomeIntent.CheckInNow)
                scope.launch { Fast16Widget().updateAll(context) } // 打卡/自动生成 → 小组件同步
            },
            onEditPrep = { type, minutes ->
                vm.onIntent(HomeIntent.EditPrep(type, minutes))
                scope.launch { Fast16Widget().updateAll(context) } // 备餐变化 → 小组件同步
            },
            onOpenRecord = onOpenRecord,
            onEditPlan = onEditPlan,
            modifier = modifier,
        )
    }

    // 打卡成功轻提示
    if (toastText != null) {
        PixelToast(
            text = toastText.orEmpty(),
            show = showToast,
            onDismiss = { showToast = false; vm.consumeToast() },
        )
    }

    // 打卡成功礼花：全屏居中 overlay，播完移除（日常小礼花 / 三餐全打卡大礼花）
    confetti?.let { burst ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            PixelConfetti(
                burst = burst,
                onFinished = vm::consumeConfetti,
                modifier = Modifier.size(160.dp),
            )
        }
    }
}
