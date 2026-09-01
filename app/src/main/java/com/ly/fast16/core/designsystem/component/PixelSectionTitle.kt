package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 分区标题（UI 拥挤优化）：黄字小标题 + 分隔线，为长页提供信息层级与留白。
 * 用于 Home「今日三餐」/ Record「统计概览·打卡日历」/ Settings 分组。
 */
@Composable
fun PixelSectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        PixelText(text = text, color = PixelColors.yellow, fontSize = PixelType.Size.xs)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(PixelColors.panel.copy(alpha = 0.8f)),
        )
    }
}

@PreviewPixel
@Composable
private fun PixelSectionTitlePreview() {
    PixelTheme {
        PixelSectionTitle(text = "今日三餐")
    }
}
