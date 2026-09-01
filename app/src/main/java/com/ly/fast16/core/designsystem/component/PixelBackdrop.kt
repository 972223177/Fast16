package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelMotion
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * 像素动态黑底（隐私同意 gate 等全屏场景的装饰背景，pixel-motion 阶跃帧规范）：
 * - 纯黑底；
 * - 24 颗像素星点**阶跃闪烁**（120ms/帧，相位/周期错开，非平滑插值）；
 * - 4 颗漂移星以**整数像素步进**缓慢斜向移动（网格回绕）；
 * - 一条 CRT 风格扫描亮线从底部**阶跃上升**循环。
 * 所有动画均为固定帧时序（PixelMotion.FRAME_MS），无插值、无渐隐。
 */
@Composable
fun PixelBackdrop(modifier: Modifier = Modifier) {
    val seed = remember { Random(0xFA5F16) }
    val stars = remember { List(24) { Star.random(seed) } }
    val drifters = remember { List(4) { Drifter.random(seed) } }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PixelMotion.FRAME_MS.milliseconds)
            tick++
        }
    }
    Canvas(
        modifier = modifier.background(Color.Black),
    ) {
        val cell = 4.dp.toPx()
        val cols = (size.width / cell).toInt().coerceAtLeast(1)
        val rows = (size.height / cell).toInt().coerceAtLeast(1)

        // CRT 扫描线：从底部阶跃上升循环（整数像素位移）
        val scanRow = rows - tick % (rows + 1)
        drawRect(
            color = Color.White.copy(alpha = 0.06f),
            topLeft = Offset(0f, scanRow * cell),
            size = Size(size.width, cell),
        )

        // 像素星点：阶跃闪烁（on/off 二态 + 错相位，8fps 观感）
        stars.forEach { s ->
            val on = (tick + s.phase) % s.cycle < s.cycle / 2
            drawRect(
                color = s.color.copy(alpha = if (on) s.maxAlpha else s.minAlpha),
                topLeft = Offset(
                    ((s.col % cols) + cols) % cols * cell,
                    ((s.row % rows) + rows) % rows * cell,
                ),
                size = Size(cell * s.sizeInCells, cell * s.sizeInCells),
            )
        }

        // 漂移星：整数像素步进斜向移动（每 stepEvery 帧走一步），网格回绕
        drifters.forEach { d ->
            val col = d.col + (tick / d.stepEvery) * d.vx
            val row = d.row + (tick / d.stepEvery) * d.vy
            drawRect(
                color = d.color,
                topLeft = Offset(
                    ((col % cols) + cols) % cols * cell,
                    ((row % rows) + rows) % rows * cell,
                ),
                size = Size(cell * d.size, cell * d.size),
            )
        }
    }
}

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
            sizeInCells = if (r.nextBoolean()) 1 else 2,
            phase = r.nextInt(0, 6),
            cycle = r.nextInt(3, 7),
            color = listOf(PixelColors.white, PixelColors.yellow, PixelColors.blue, PixelColors.gray)[r.nextInt(4)],
            maxAlpha = 0.9f,
            minAlpha = 0.15f,
        )
    }
}

/** 漂移星（整数像素步进，vx 恒非零保证斜向移动） */
private class Drifter(
    val col: Int,
    val row: Int,
    val vx: Int,
    val vy: Int,
    val stepEvery: Int,
    val size: Int,
    val color: Color,
) {
    companion object {
        fun random(r: Random): Drifter = Drifter(
            col = r.nextInt(0, 40),
            row = r.nextInt(0, 80),
            vx = listOf(-1, 1)[r.nextInt(2)],
            vy = r.nextInt(-1, 2),
            stepEvery = r.nextInt(2, 5),
            size = if (r.nextBoolean()) 1 else 2,
            color = listOf(PixelColors.white, PixelColors.yellow, PixelColors.green)[r.nextInt(3)],
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
