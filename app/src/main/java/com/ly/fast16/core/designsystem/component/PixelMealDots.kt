package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType

/** 三餐状态点（设计方案 §8.5 / FR-13）：● 已打卡 / ○ 待打卡 / - 无计划 */
enum class MealDotState { CHECKED_IN, PENDING, NO_PLAN }

/**
 * 三餐状态点行（Home 时间轴 / 记录页共用）：
 * 每格 = 餐名 + 状态点（语义色）+ 点击打卡回调；已打卡跳一格动效由调用方触发。
 */
@Composable
fun PixelMealDots(
    states: List<Pair<String, MealDotState>>,
    onCheckIn: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.md),
    ) {
        states.forEachIndexed { index, (label, state) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .border(
                        BorderStroke(PixelShape.borderWidth, Color.Black),
                        PixelShape.stair,
                    )
                    .background(PixelColors.panel.copy(alpha = 0.4f))
                    .clickable { onCheckIn(index) }
                    .size(width = 56.dp, height = 48.dp),
            ) {
                PixelText(
                    text = label,
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                when (state) {
                    MealDotState.CHECKED_IN -> Dot(color = PixelColors.green, filled = true)
                    MealDotState.PENDING -> Dot(color = PixelColors.yellow, filled = false)
                    MealDotState.NO_PLAN -> Dot(color = PixelColors.gray, filled = false)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color, filled: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(if (filled) color else Color.Transparent)
            .border(BorderStroke(2.dp, color)),
    )
}

@PreviewPixel
@Composable
private fun PixelMealDotsPreview() {
    PixelTheme {
        PixelMealDots(
            states = listOf(
                "早" to MealDotState.CHECKED_IN,
                "午" to MealDotState.PENDING,
                "晚" to MealDotState.NO_PLAN,
            ),
            onCheckIn = {},
        )
    }
}
