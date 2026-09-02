package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelMotion
import kotlin.random.Random

/**
 * 像素动态黑底（全屏场景装饰背景，pixel-motion 阶跃帧规范）：
 * - 纯黑底；
 * - 24 颗像素星点**低亮度缓慢明灭**（呼吸感，白/灰为主，不抢内容焦点；
 *   相位/周期错开，120ms/帧阶跃，非平滑插值），逻辑网格 40×80 比例映射铺满全屏。
 * 帧号由**真实帧时间戳取整**（withFrameNanos 对齐 vsync，无 delay 累加漂移），
 * 只在帧号变化时触发背景重绘——任意刷新率下节拍稳定不顿挫。
 * 注：原 CRT 扫描亮线（贯穿全宽的移动分割线）与斜向漂移光点（飞虫感）已移除。
 */
@Composable
fun PixelBackdrop(modifier: Modifier = Modifier) {
    val seed = remember { Random(0xFA5F16) }
    val stars = remember { List(24) { Star.random(seed) } }
    // 帧号 = vsync 帧时间 / 帧周期（120ms）；读发生在 Canvas draw 阶段 → 仅失效重绘、不重组
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var lastFrame = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                val frame = frameNanos / (PixelMotion.FRAME_MS * 1_000_000L)
                if (frame != lastFrame) {
                    lastFrame = frame
                    tick = frame
                }
            }
        }
    }
    Canvas(
        modifier = modifier.background(Color.Black),
    ) {
        val cell = 4.dp.toPx()
        val cols = (size.width / cell).toInt().coerceAtLeast(1)
        val rows = (size.height / cell).toInt().coerceAtLeast(1)

        // 像素星点：逻辑网格 40×80 按比例映射到全屏（避免集中在左上角），
        // 阶跃闪烁（on/off 二态 + 错相位，8fps 观感）
        stars.forEach { s ->
            val on = ((tick + s.phase) % s.cycle).toInt() < s.cycle / 2
            val gx = s.col * cols / STAR_GRID_W
            val gy = s.row * rows / STAR_GRID_H
            drawRect(
                color = s.color.copy(alpha = if (on) s.maxAlpha else s.minAlpha),
                topLeft = Offset(gx * cell, gy * cell),
                size = Size(cell * s.sizeInCells, cell * s.sizeInCells),
            )
        }
    }
}

/** 星点逻辑网格（随机坐标范围；绘制时按实际 cols/rows 比例映射铺满全屏） */
private const val STAR_GRID_W = 40
private const val STAR_GRID_H = 80

/** 静态闪烁星点（网格坐标 + 闪烁相位/周期） */
private class Star(
    val col: Int,
    val row: Int,
    val sizeInCells: Int,
    val phase: Int,
    val cycle: Int,
    val color: Color,
    val maxAlpha: Float,
    val minAlpha: Float,
) {
    companion object {
        fun random(r: Random): Star = Star(
            col = r.nextInt(0, 40),
            row = r.nextInt(0, 80),
            // 大部分 1 格小星（1/4 为 2 格），避免大亮块抢焦点
            sizeInCells = if (r.nextInt(4) == 0) 2 else 1,
            phase = r.nextInt(0, 8),
            // 600ms~1.2s 缓慢明灭（呼吸感），替代高频闪跳
            cycle = r.nextInt(5, 11),
            // 低饱和低亮度：以白/灰为主，衬在内容之下
            color = listOf(
                PixelColors.white,
                PixelColors.white,
                PixelColors.gray,
                PixelColors.white,
            )[r.nextInt(4)],
            maxAlpha = 0.35f,
            minAlpha = 0.05f,
        )
    }
}

@PreviewPixel
@Composable
private fun PixelBackdropPreview() {
    PixelTheme {
        PixelBackdrop(modifier = Modifier.background(Color.Black))
    }
}
