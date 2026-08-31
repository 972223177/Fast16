package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 像素步进器（原型 index.html stepper 直接移植）：
 * `- 值 +`，按钮为 30×30 面板底黑边方块，值用 Press Start 2P 黄字居中。
 * 用于 Create 流程的早餐时间（±5 分钟）与备餐时长（±5 分钟）。
 *
 * @param value 显示值（如 "08:00" / "30"）
 * @param minWidth 值区最小宽（保持对齐，默认 44dp）
 */
@Composable
fun PixelStepper(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    valueMinWidth: Dp = 44.dp,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(symbol = "-", onClick = onDecrease)
        PixelText(
            text = value,
            color = PixelColors.yellow,
            fontSize = PixelType.Size.xs,
            digital = true,
            modifier = Modifier.size(width = valueMinWidth, height = 24.dp),
        )
        StepperButton(symbol = "+", onClick = onIncrease)
    }
}

/** 步进按钮（30×30 面板底黑边，按下 1px 下沉） */
@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text = symbol, color = PixelColors.white, fontSize = PixelType.Size.xs)
    }
}

@PreviewPixel
@Composable
private fun PixelStepperPreview() {
    PixelTheme {
        PixelStepper(
            value = "08:00",
            onDecrease = {},
            onIncrease = {},
        )
    }
}
