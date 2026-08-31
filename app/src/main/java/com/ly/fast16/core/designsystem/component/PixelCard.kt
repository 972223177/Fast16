package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape

/**
 * 像素卡片（基建文档 §2.4）：
 * 面板底 + 像素阶梯圆角（cornerSteps=3）+ 2px 黑描边。
 *
 * @param borderColor 描边色（默认像素黑；选中态传 PixelColors.yellow 做主黄描边）
 */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = PixelColors.panel,
    borderColor: Color = Color.Black,
    contentPadding: Dp = PixelShape.Spacing.lg,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(color = backgroundColor, shape = PixelShape.stair)
            .border(BorderStroke(PixelShape.borderWidth, borderColor), PixelShape.stair)
            .padding(contentPadding),
        content = content,
    )
}

@PreviewPixel
@Composable
private fun PixelCardPreview() {
    PixelTheme {
        PixelCard {
            PixelText(text = "均衡 · 午 12:00 / 晚 15:30")
        }
    }
}
