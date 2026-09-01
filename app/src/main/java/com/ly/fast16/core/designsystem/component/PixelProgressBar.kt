package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.background
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

/**
 * 像素分块进度条（设计方案 §8.5 / spec 3.4 .seg）：分块逐格点亮，不做平滑填充。
 * 进度 = floor(progress × segments)，整数格点亮。
 *
 * @param segments 总格数（断食进度常用 16）
 * @param segmentWidth / [segmentHeight] 单格尺寸（一屏可见优化：由 8×16 下调至 6×12）
 * @param litColor 点亮色（进行中/成功用绿，待处理用黄）
 * @param unlitColor 未点亮色（spec .seg i：#23263a）
 */
@Composable
fun PixelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    segments: Int = 16,
    segmentWidth: Dp = 6.dp,
    segmentHeight: Dp = 12.dp,
    litColor: Color = PixelColors.green,
    unlitColor: Color = Color(0xFF23263A),
) {
    val lit = (progress.coerceIn(0f, 1f) * segments).toInt()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(segments) { i ->
            Box(
                modifier = Modifier
                    .size(width = segmentWidth, height = segmentHeight)
                    .background(if (i < lit) litColor else unlitColor),
            )
        }
    }
}

@PreviewPixel
@Composable
private fun PixelProgressBarPreview() {
    PixelTheme {
        PixelProgressBar(progress = 0.4f)
    }
}
