package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 计时数字（基建 §2.4 M1 / 设计方案 §8.5）：
 * Press Start 2P 数字整秒/整分钟**跳变**（阶跃，无平滑）；用于断食剩余「分:秒」等。
 *
 * @param digits 逐位渲染（保持等宽对位，如 "12:07"），非数字字符（':'）用分隔符样式。
 */
@Composable
fun PixelNumber(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = PixelColors.white,
    fontSize: TextUnit = PixelType.Size.lg,
    digitSpacing: Dp = 4.dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        value.forEachIndexed { index, char ->
            if (char != ':') {
                PixelText(
                    text = char.toString(),
                    digital = true,
                    color = color,
                    fontSize = fontSize,
                )
            } else {
                Spacer(modifier = Modifier.width(digitSpacing))
                PixelText(
                    text = ":",
                    digital = true,
                    color = PixelColors.gray,
                    fontSize = fontSize,
                )
            }
        }
    }
}

@PreviewPixel
@Composable
private fun PixelNumberPreview() {
    PixelTheme {
        PixelNumber(value = "03:20")
    }
}
