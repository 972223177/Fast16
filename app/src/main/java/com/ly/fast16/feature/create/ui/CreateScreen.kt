package com.ly.fast16.feature.create.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.widget.Fast16Widget
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 新建 / 编辑计划 Screen 入口：VM 绑定 → 事件（生成成功刷新 Widget + 礼花 / 失败 Toast）。
 * 全屏无底栏；planId ≥ 0 为编辑模式。
 */
@Composable
fun CreateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    planId: Long = -1L,
) {
    val vm: CreateViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    var showFailToast by remember { mutableStateOf(false) }

    // 编辑模式：planId 进入时加载现有计划预填
    LaunchedEffect(planId) {
        if (planId >= 0) vm.loadForEdit(planId)
    }

    var showConfetti by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                CreateEvent.Generated -> {
                    // IN-02：计划变更后刷新桌面小组件（updatePeriodMillis=0，事件驱动）
                    Fast16Widget().updateAll(context)
                    showConfetti = true // 生成成功礼花，播完再返回
                }

                CreateEvent.Failed -> showFailToast = true
            }
        }
    }

    when (val s = state) {
        CreateUiState.Loading -> PixelLoading()
        is CreateUiState.Content -> CreateContent(
            state = s,
            onIntent = vm::onIntent,
            onBack = onBack,
            modifier = modifier,
        )
    }

    // 生成失败轻提示
    if (showFailToast) {
        PixelToast(
            text = "生成失败，请重试",
            show = true,
            onDismiss = { showFailToast = false },
        )
    }

    // 生成成功礼花 overlay：遮罩 + 大礼花，播完自动返回首页
    if (showConfetti) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PixelColors.bg.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            PixelConfetti(
                burst = PixelConfettiBurst.PLAN_CREATED,
                onFinished = { showConfetti = false; onBack() },
                modifier = Modifier.size(180.dp),
            )
        }
    }
}
