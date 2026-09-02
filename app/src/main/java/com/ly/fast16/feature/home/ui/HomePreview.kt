package com.ly.fast16.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.domain.model.CharacterState
import com.ly.fast16.domain.model.MealType
import kotlinx.coroutines.flow.MutableStateFlow

/** Preview 假数据（有计划的典型态） */
private fun previewContentState() = HomeUiState.Content(
    hasPlanToday = true,
    characterState = CharacterState.FASTING,
    bubbleText = "断食中，再坚持一下就开饭！",
    fastingSeconds = 3 * 3600 + 20 * 60 + 12,
    meals = listOf(
        MealUi(MealType.BREAKFAST, "早餐", "08:00", prepMinutes = 0, checkedIn = true, hasMeal = true),
        MealUi(MealType.LUNCH, "午餐", "12:07", prepMinutes = 30, checkedIn = false, hasMeal = true),
        MealUi(MealType.DINNER, "晚餐", "15:30", prepMinutes = 45, checkedIn = false, hasMeal = true),
    ),
    subtitle = "今天 · 早餐 08:00 ｜ 窗口结束 16:00",
    streak = 12,
    windowProgress = 0.625f,
)

@PreviewPixel
@Composable
private fun HomeScreenPreview() {
    PixelTheme {
        HomeContent(
            state = previewContentState(),
            liveState = remember {
                MutableStateFlow(HomeLiveState(3 * 3600 + 20 * 60 + 12, CharacterState.FASTING, "断食中，坚持就是胜利！"))
            },
            onNewPlan = {},
            onCheckIn = {},
            onUncheckIn = {},
            onCheckInNow = {},
            onEditPrep = { _, _ -> },
            onOpenRecord = {},
            onEditPlan = {},
        )
    }
}

/**
 * 一屏可见验证用 Preview 框架：复刻 Fast16NavHost 的 Scaffold 结构，
 * 让可视高度与真机一致（扣掉底栏），用来目视确认内容是否一屏放下。
 * 由带不同 device spec 的 @Preview 入口调用（Android Studio 按注解渲染各尺寸）。
 */
@Composable
private fun HomeContentFramePreview() {
    PixelTheme {
        Scaffold(
            containerColor = PixelColors.bg,
            bottomBar = { Box(modifier = Modifier.height(62.dp)) },
        ) { innerPadding ->
            HomeContent(
                state = previewContentState(),
                liveState = remember {
                    MutableStateFlow(HomeLiveState(3 * 3600 + 20 * 60 + 12, CharacterState.FASTING, "断食中，坚持就是胜利！"))
                },
                onNewPlan = {},
                onCheckIn = {},
                onUncheckIn = {},
                onCheckInNow = {},
                onEditPrep = { _, _ -> },
                onOpenRecord = {},
                onEditPlan = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** 主流屏：360×800dp（可视 ≈662dp），目标是一屏放下、无滚动 */
@Preview(
    name = "Home · 主流屏 360x800",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun HomeScreenMainstreamPreview() {
    HomeContentFramePreview()
}

/** 矮屏：360×640dp（可视 ≈506dp），目标是不裁切、可滚动阅读 */
@Preview(
    name = "Home · 矮屏 360x640",
    device = "spec:width=360dp,height=640dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun HomeScreenSmallDevicePreview() {
    HomeContentFramePreview()
}

/** 无计划空态假数据（detected 可指定当前时段；checked = 今日已打卡餐次） */
private fun emptyPreviewState(detected: MealType?, checked: Set<MealType>) = HomeUiState.Content(
    hasPlanToday = false,
    characterState = CharacterState.IDLE,
    bubbleText = "来记录早餐，开启今天第一餐吧！",
    fastingSeconds = null,
    meals = emptyList(),
    subtitle = "今天 · 还没有计划",
    streak = 0,
    windowProgress = 0f,
    planId = -1L,
    detectedMealType = detected,
    checkedInToday = checked,
)

/** 空态 · 未打卡：显示时段引导 + 可点打卡按钮 */
@Preview(
    name = "Home · 空态未打卡",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun HomeEmptyPreview() {
    PixelTheme {
        HomeContent(
            state = emptyPreviewState(detected = MealType.DINNER, checked = emptySet()),
            liveState = remember { MutableStateFlow(HomeLiveState(null, CharacterState.IDLE, "现在是断食时段，好好休息吧！")) },
            onNewPlan = {},
            onCheckIn = {},
            onUncheckIn = {},
            onCheckInNow = {},
            onEditPrep = { _, _ -> },
            onOpenRecord = {},
            onEditPlan = {},
        )
    }
}

/** 空态 · 已打卡：显示「今日已记录」反馈 + 按钮禁用（打卡结果可见） */
@Preview(
    name = "Home · 空态已打卡",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun HomeEmptyCheckedPreview() {
    PixelTheme {
        HomeContent(
            state = emptyPreviewState(detected = MealType.DINNER, checked = setOf(MealType.DINNER)),
            liveState = remember { MutableStateFlow(HomeLiveState(null, CharacterState.IDLE, "现在是断食时段，好好休息吧！")) },
            onNewPlan = {},
            onCheckIn = {},
            onUncheckIn = {},
            onCheckInNow = {},
            onEditPrep = { _, _ -> },
            onOpenRecord = {},
            onEditPlan = {},
        )
    }
}
