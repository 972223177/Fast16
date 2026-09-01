package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors

/**
 * 像素精灵（设计方案 §8.6 角色资产 / spec §6）：按字符网格逐格绘制。
 *
 * [rows] 每行一个字符串（字符 → [palette] 颜色，'.' = 透明）。
 *
 * **整数像素对齐**：每格按 `floor(格索引 × 格宽/高)` 落在整数 px 边界，
 * 任意 density / 任意非整数倍尺寸下格子都不会落在分数像素上 → 像素锐利、不变形
 * （compat-api24-37 §5 大屏/多 density 自适应）。
 * 与 UI 原型 index.html 的 spriteCanvas 同构，数据直接移植。
 */
@Composable
fun PixelSprite(
    rows: List<String>,
    modifier: Modifier = Modifier,
    palette: Map<Char, Color> = PixelSpritePalette.character,
    contentDescription: String? = null,
) {
    val cols = rows.maxOfOrNull { it.length } ?: 0
    val rowCount = rows.size
    if (cols == 0 || rowCount == 0) return

    Canvas(modifier = modifier.then(
        // P2：无障碍描述（像素图标无文本，辅助功能朗读用）
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        },
    )) {
        val cellW = size.width / cols
        val cellH = size.height / rowCount
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, char ->
                palette[char]?.let { color ->
                    val x0 = (x * cellW).toInt().toFloat()
                    val y0 = (y * cellH).toInt().toFloat()
                    val x1 = ((x + 1) * cellW).toInt().toFloat()
                    val y1 = ((y + 1) * cellH).toInt().toFloat()
                    drawRect(
                        color = color,
                        topLeft = Offset(x0, y0),
                        size = Size(x1 - x0, y1 - y0),
                    )
                }
            }
        }
    }
}

/** 角色精灵调色板（UI 8 色之外补充：S=肤色、H=发色，角色资产非 UI 令牌） */
object PixelSpritePalette {
    val character: Map<Char, Color> = mapOf(
        'K' to PixelColors.bg,      // 黑描边/发线
        'G' to PixelColors.green,
        'W' to PixelColors.white,
        'Y' to PixelColors.yellow,
        'R' to PixelColors.red,
        'B' to PixelColors.blue,
        'S' to Color(0xFFF2CFA0),   // 肤色
        'H' to Color(0xFF4A3628),   // 发色
    )
}

@PreviewPixel
@Composable
private fun PixelSpritePreview() {
    PixelTheme {
        PixelSprite(
            rows = listOf(
                ".GGGGGG..",
                "GWWWWWWG.",
                "WWWWWWWW.",
                "WWWWWWWW.",
                "KWWWWWWWK",
                "KWWWWWWWK",
                ".KKKKKKK.",
                "..K...K..",
            ),
            modifier = Modifier.size(34.dp),
        )
    }
}
