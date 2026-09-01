package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.background
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

/**
 * 备餐时长阶梯滑杆（基建 §2.4 M1 / 设计方案 §8.5 / FR-4）：
 * 分块阶梯条（每格 [stepMinutes]），点击格选中（逐格点亮，无平滑拖动）；
 * 下方读数用 Press Start 2P 数字（PixelNumber 风格）。
 *
 * @param valueMinutes 当前值（分钟，0..[maxMinutes]）
 */
@Composable
fun PixelSlider(
    valueMinutes: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxMinutes: Int = 120,
    stepMinutes: Int = 5,
) {
    val segmentCount = maxMinutes / stepMinutes
    val litSegments = (valueMinutes / stepMinutes).coerceIn(0, segmentCount)

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(segmentCount) { i ->
                val lit = i < litSegments
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 14.dp)
                        .background(if (lit) PixelColors.yellow else PixelColors.panel)
                        .clickable { onValueChange((i + 1) * stepMinutes) },
                )
            }
        }
        PixelText(
            text = "$valueMinutes 分钟",
            digital = true,
            color = PixelColors.green,
            fontSize = PixelType.Size.xs,
        )
    }
}

@PreviewPixel
@Composable
private fun PixelSliderPreview() {
    PixelTheme {
        PixelSlider(valueMinutes = 45, onValueChange = {})
    }
}
