package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 分区标题（UI 拥挤优化）：黄字小标题 + 分隔线，为长页提供信息层级与留白。
 * 用于 Home「今日三餐」/ Record「统计概览」「打卡日历」/ Settings 分组。
 */
@Composable
fun PixelSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    /**
     * 标题行右侧附加内容（一屏可见优化：把原本独占一行的次要信息/操作并入标题行，
     * 省下整行高度 + 其上下留白）。
     */
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelText(text = text, color = PixelColors.yellow, fontSize = PixelType.Size.xs)
            trailing?.invoke()
        }
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
