package com.ly.fast16.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.component.PixelBubble
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelCharacter
import com.ly.fast16.core.designsystem.component.PixelNumber
import com.ly.fast16.core.designsystem.component.PixelProgressBar
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import kotlinx.coroutines.flow.StateFlow

/** 角色 + 气泡行：订阅高频 liveState，每秒仅本行重组（角色参数不变时 Compose 自动 skip） */
@Composable
internal fun CharacterRow(
    liveState: StateFlow<HomeLiveState>,
    modifier: Modifier = Modifier,
) {
    val live by liveState.collectAsState()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelCharacter(state = live.characterState)
        PixelBubble(
            text = live.bubbleText,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 断食面板：标签 + 主值 + 16 格 seg + 状态副信息（原型 fast-panel）。
 * 四态：有倒计时 / 全部打卡完成 / 窗口结束（有计划）/ 未排窗口（无计划）。
 * 订阅高频 liveState（倒计时数字每秒跳），低频 state 提供 allChecked 与窗口进度；
 * 状态描边随状态着色（黄/绿/灰），主值与副信息填充各态，避免窗口结束/无计划态空荡。
 */
@Composable
internal fun FastingPanel(
    state: HomeUiState.Content,
    liveState: StateFlow<HomeLiveState>,
    modifier: Modifier = Modifier,
) {
    val live by liveState.collectAsState()
    val checkedCount = state.meals.count { it.checkedIn }
    val total = state.meals.size
    val allChecked = state.meals.isNotEmpty() && state.meals.all { it.checkedIn }
    // 下一未完成餐（断食中副信息：开饭时刻提示）
    val nextMeal = state.meals.firstOrNull { !it.checkedIn }
    val panel = when {
        live.fastingSeconds != null -> PanelContent(
            label = "断食中 · 距下一餐",
            value = formatFastingHMS(live.fastingSeconds),
            color = PixelColors.yellow,
            hint = nextMeal?.let { "${it.name} ${it.timeLabel} 开饭" } ?: "",
            border = PixelColors.yellow,
        )
        allChecked -> PanelContent(
            label = "今日已完成 ✦",
            value = "00:00:00",
            color = PixelColors.green,
            hint = "三餐已打卡，明天见！",
            border = PixelColors.green,
        )
        state.hasPlanToday -> PanelContent(
            label = "今日窗口已结束",
            value = if (total > 0) "已打卡 $checkedCount/$total" else "--:--:--",
            color = PixelColors.yellow,
            hint = "明早记得记录早餐",
            border = PixelColors.gray,
        )
        else -> PanelContent(
            label = "今日未排窗口",
            value = "--:--:--",
            color = PixelColors.gray,
            hint = "随时打卡早餐，开启新计划",
            border = PixelColors.gray,
        )
    }
    PixelCard(
        modifier = modifier,
        borderColor = panel.border,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(text = panel.label, color = PixelColors.gray, fontSize = PixelType.Size.xs)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            if (panel.value.all { it in "0123456789:-" }) {
                // 纯计时串走逐位数字（倒计时 / 完成 00:00:00）
                PixelNumber(
                    value = panel.value,
                    color = panel.color,
                    fontSize = PixelType.Size.lg,
                )
            } else {
                // 含中文的主值（已打卡 X/3）走中文字体，避免数字字体回落错位
                PixelText(
                    text = panel.value,
                    color = panel.color,
                    fontSize = PixelType.Size.md,
                )
            }
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            PixelProgressBar(
                progress = if (allChecked) 1f else state.windowProgress,
                segments = 16,
                litColor = PixelColors.green,
            )
            // 状态副信息：填充面板底部，避免窗口结束/未排窗口态显得空荡
            if (panel.hint.isNotEmpty()) {
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelText(text = panel.hint, color = PixelColors.gray, fontSize = PixelType.Size.xs)
            }
        }
    }
}

/** 断食面板四态内容（label/主值/主值色/副信息/描边色） */
internal data class PanelContent(
    val label: String,
    val value: String,
    val color: Color,
    val hint: String,
    val border: Color,
)

/** streak chip（原型 home-streak：🔥 N 天，点击跳记录页） */
@Composable
internal fun StreakChip(streak: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .clickable(onClick = onClick)
            .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text = "🔥 $streak 天", color = PixelColors.yellow, fontSize = PixelType.Size.xs)
    }
}

/** 断食剩余（时:分:秒，原型 03:20:12 大计时） */
internal fun formatFastingHMS(seconds: Long?): String {
    if (seconds == null) return "--:--:--"
    val total = seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
}
