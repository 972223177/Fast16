package com.ly.fast16.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ly.fast16.core.designsystem.theme.LocalPixelFonts
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 像素文本：统一收口字号/字体/颜色令牌（基建文档 §2.1）。
 *
 * @param digital true 时走像素字（数字/计时，Press Start 2P 占位路径）。
 */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PixelColors.white,
    fontSize: TextUnit = PixelType.Size.sm,
    fontFamily: FontFamily? = null,
    digital: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle? = null,
) {
    // 字体按模式动态取（PixelTheme 经 LocalPixelFonts 注入）：不传 fontFamily 时
    // 中文走当前 cjk、数字走当前 digital（像素风 = Fusion Pixel / Press Start 2P；系统 = 默认）
    val fonts = LocalPixelFonts.current
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontFamily = if (digital) fonts.digital else fontFamily ?: fonts.cjk,
        letterSpacing = if (digital) 2.sp else TextUnit.Unspecified,
        maxLines = maxLines,
        style = (style ?: TextStyle.Default).let { base ->
            // 紧凑行高（一屏可见优化）：仅对数字/英文的 Press Start 2P 生效。
            // 该字体 OS/2 usWinDescent=374（upem 1000），Android 带字体 padding 的默认行高
            // 达 1.374em（12sp → 16.5px），而字形 bbox ymin=0 无降部 → 关掉 font padding
            // 降到 1.0em（12px）不会裁切，每行省 ~4.5px，多行堆叠/大计时收益明显。
            // 中文 Fusion Pixel 天然 1.0em，且是子集字体（🔥、› 等会回落系统字体，
            // 回落字形带降部），关 padding 有裁切风险 → 中文保持默认不干预。
            if (digital) {
                base.merge(TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
            } else {
                base
            }
        },
    )
}

@PreviewPixel
@Composable
private fun PixelTextPreview() {
    PixelTheme {
        PixelText(text = "12:00", digital = true, fontSize = PixelType.Size.lg)
        PixelText(text = "记录早餐，开启今天第一餐吧！", color = PixelColors.yellow)
    }
}
