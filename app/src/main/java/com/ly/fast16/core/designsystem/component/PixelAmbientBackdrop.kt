package com.ly.fast16.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.token.PixelColors
import kotlin.math.sin

/**
 * 像素氛围背景：云 + 星光，缓慢**垂直漂浮** + 透明度呼吸。
 *
 * 与底栏轮播（水平横穿）**运动正交**、元素不同（云/星 vs 时钟/碗/星），视觉不冲突。
 * 常驻淡背景：有内容时传 [dimmed] = true 整体降透明度，空态时更明显。
 *
 * **可见性要点**：元素网格放大（云 36×16dp、星 20×20dp）、alpha 抬高、横向分散——
 * 避免上一版「元素过小过淡，只能看到两朵小方块」的问题。
 */
@Composable
fun PixelAmbientBackdrop(
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
        ),
        label = "ambientPhase",
    )
    // 有内容时整体变淡（常驻但不抢信息）
    val alphaScale = if (dimmed) 0.55f else 1f
    Canvas(modifier = modifier) {
        val rad = phase * DEG_TO_RAD
        AMBIENT_SPRITES.forEach { s ->
            val sway = sin(rad * s.floatFreq + s.phase)
            val breath = 0.72f + 0.28f * sin(rad * s.breathFreq + s.phase * 1.7f)
            val alpha = (s.baseAlpha * alphaScale * breath).coerceIn(0f, 1f)
            val x = s.xRatio * size.width + s.offsetX.toPx()
            val y = s.baseY.toPx() + sway * s.floatAmp.toPx()
            drawGrid(
                rows = s.rows,
                origin = Offset(x, y),
                cellPx = s.cell.toPx(),
                color = s.color.copy(alpha = alpha),
            )
        }
    }
}

/** 字符网格 → 像素方块（'.' 透明）；与底栏背景绘制同构 */
private fun DrawScope.drawGrid(
    rows: List<String>,
    origin: Offset,
    cellPx: Float,
    color: Color,
) {
    rows.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch ->
            if (ch != '.') {
                drawRect(
                    color = color,
                    topLeft = Offset(origin.x + c * cellPx, origin.y + r * cellPx),
                    size = Size(cellPx, cellPx),
                )
            }
        }
    }
}

/** 背景元素：网格 + 横向比例/偏移 + 纵向基线 + 漂浮与呼吸参数 */
private data class AmbientSprite(
    val rows: List<String>,
    val xRatio: Float,
    val offsetX: Dp,
    val baseY: Dp,
    val cell: Dp,
    val baseAlpha: Float,
    val floatAmp: Dp,
    val floatFreq: Float,
    val breathFreq: Float,
    val phase: Float,
    val color: Color,
)

/** 像素云（9×4，cell 4 → 36×16dp；蓝系低 alpha，夜空云感） */
private val CLOUD = listOf(
    "...KKK...",
    ".KKKKKKK.",
    "KKKKKKKKK",
    "..KKKKK..",
)

/** 像素大星（5×5，cell 4 → 20×20dp） */
private val STAR = listOf(
    "..K..",
    ".K.K.",
    "K...K",
    ".K.K.",
    "..K..",
)

/** 像素小星（3×3，cell 3 → 9×9dp 点缀） */
private val STAR_SMALL = listOf(
    ".K.",
    "K.K",
    ".K.",
)

private val AMBIENT_SPRITES = listOf(
    // 云：上 / 中下 / 底部（蓝系，覆盖全高，避免下半部空素）
    AmbientSprite(CLOUD, 0.06f, 0.dp, 130.dp, 4.dp, 0.30f, 14.dp, 1.0f, 0.8f, 0.0f, PixelColors.blue),
    AmbientSprite(CLOUD, 0.72f, -16.dp, 300.dp, 4.dp, 0.26f, 16.dp, 0.7f, 1.2f, 2.1f, PixelColors.blue),
    AmbientSprite(CLOUD, 0.30f, 0.dp, 430.dp, 4.dp, 0.22f, 12.dp, 0.85f, 1.0f, 4.2f, PixelColors.blue),
    // 大星：上 / 中 / 中下 / 底部
    AmbientSprite(STAR, 0.20f, 0.dp, 80.dp, 4.dp, 0.42f, 12.dp, 1.3f, 1.6f, 1.0f, PixelColors.yellow),
    AmbientSprite(STAR, 0.46f, 0.dp, 190.dp, 4.dp, 0.36f, 14.dp, 0.9f, 0.7f, 3.4f, PixelColors.white),
    AmbientSprite(STAR, 0.88f, 0.dp, 340.dp, 4.dp, 0.32f, 10.dp, 1.1f, 1.0f, 5.0f, PixelColors.yellow),
    AmbientSprite(STAR, 0.14f, 0.dp, 420.dp, 4.dp, 0.30f, 13.dp, 0.8f, 1.2f, 2.0f, PixelColors.white),
    // 小星点缀
    AmbientSprite(STAR_SMALL, 0.62f, 0.dp, 240.dp, 3.dp, 0.30f, 8.dp, 1.4f, 1.3f, 2.7f, PixelColors.white),
)

private const val DEG_TO_RAD = (Math.PI / 180f).toFloat()
