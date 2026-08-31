package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape

/**
 * 近 7 天完成柱状图（原型 index.html stat-bars 直接移植）：
 * 每列一根绿柱（黑边），高度 = 完成餐数（0..3）映射（每餐 +12dp 基准 6dp）。
 *
 * @param values 最近 7 天每日完成餐数（旧 → 新）
 */
@Composable
fun PixelStatBars(
    values: List<Int>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { v ->
            val clamped = v.coerceIn(0, 3)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((6 + clamped * 12).dp)
                        .background(PixelColors.green)
                        .border(BorderStroke(PixelShape.borderWidth, Color.Black)),
                )
            }
        }
    }
}

@PreviewPixel
@Composable
private fun PixelStatBarsPreview() {
    PixelTheme {
        PixelStatBars(values = listOf(3, 2, 3, 1, 3, 3, 3))
    }
}
