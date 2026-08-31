package com.ly.fast16.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
    fontFamily: FontFamily = PixelType.cjk,
    digital: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontFamily = if (digital) PixelType.digital else fontFamily,
        letterSpacing = if (digital) 2.sp else TextUnit.Unspecified,
        maxLines = maxLines,
        style = style ?: TextStyle.Default,
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
