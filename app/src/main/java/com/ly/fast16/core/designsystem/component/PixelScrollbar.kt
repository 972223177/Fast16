package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 像素垂直滚动条（自绘——不依赖 foundation Scrollbar API，compose BOM 2026.08 已迁移该 API）：
 * 4dp 方形轨道 + 深灰滑块；滑块高度/位移按「视口/内容」比例计算并整数化（无亚像素）。
 * 矮屏内容被裁时提示「可滚动」，符合像素铁律（矩形、无圆角、无平滑渐变）。
 *
 * 用法：与 `verticalScroll(scrollState)` 配对，通常 `Modifier.align(Alignment.CenterEnd).fillMaxHeight()`。
 */
@Composable
fun PixelVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val maxValue = scrollState.maxValue.coerceAtLeast(0)
    BoxWithConstraints(modifier = modifier) {
        val viewport = maxHeight.value.coerceAtLeast(1f)
        // 内容高 = 视口 + 可滚距离；滑块高 = 视口 × 视口/内容（最短 24dp，防内容极长时消失）
        val content = viewport + maxValue
        val thumbH = (viewport * viewport / content).coerceAtLeast(24f).coerceAtMost(maxHeight.value)
        val thumbOffset = if (maxValue > 0) {
            (maxHeight.value - thumbH) * scrollState.value / maxValue
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(Color.Black.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .offset(y = thumbOffset.roundToInt().dp)
                    .width(4.dp)
                    .height(thumbH.roundToInt().dp)
                    .background(Color.Black.copy(alpha = 0.55f)),
            )
        }
    }
}
