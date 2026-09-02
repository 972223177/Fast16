package com.ly.fast16.feature.record.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.updateAll
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.designsystem.component.PixelToast
import com.ly.fast16.core.widget.Fast16Widget
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 记录页 Screen 入口：VM 绑定 → State 收集 → RecordContent。
 * 补打卡 / 删计划 → 桌面小组件同步；删除成功轻提示。
 */
@Composable
fun RecordScreen(
    modifier: Modifier = Modifier,
    onEditPlan: (Long) -> Unit = {},
) {
    val vm: RecordViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    val toastText by vm.toast.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showToast by remember { mutableStateOf(false) }

    LaunchedEffect(toastText) {
        if (toastText != null) {
            showToast = true
        }
    }

    when (val s = state) {
        RecordUiState.Loading -> PixelLoading()
        is RecordUiState.Content -> RecordContent(
            state = s,
            onIntent = { intent ->
                vm.onIntent(intent)
                // 补打卡 / 删计划 → 桌面小组件同步
                if (intent is RecordIntent.BackfillCheckIn || intent is RecordIntent.DeletePlan) {
                    scope.launch { Fast16Widget().updateAll(context) }
                }
            },
            onEditPlan = onEditPlan,
            modifier = modifier,
        )
    }

    // 删除成功轻提示
    if (toastText != null) {
        PixelToast(
            text = toastText.orEmpty(),
            show = showToast,
            onDismiss = { showToast = false; vm.consumeToast() },
        )
    }
}
