package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelMotion
import com.ly.fast16.core.designsystem.token.PixelType
import kotlinx.coroutines.delay

/**
 * 像素打字机文本（同类像素产品对话框灵魂——Undertale / Stardew Valley / 宝可梦均为 typewriter）：
 * 逐字打印（默认 60ms/字，PixelMotion.Pop.TYPE_MS），打字中尾部闪烁像素光标 ▊，打完熄灭。
 * 按 codePoint 步进（emoji 等 surrogate pair 不截断）；无插值、逐字阶跃（像素铁律）。
 *
 * @param digital true 时走像素字（数字/英文标题，Press Start 2P + 2sp 字距），与 PixelText 对齐
 * @param onFinished 整段打印完成后回调
 */
@Composable
fun PixelTypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PixelColors.white,
    fontSize: TextUnit = PixelType.Size.sm,
    fontFamily: FontFamily = PixelType.cjk,
    digital: Boolean = false,
    charMs: Long = PixelMotion.Pop.TYPE_MS,
    style: TextStyle? = null,
    showCursor: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onFinished: () -> Unit = {},
) {
    var shown by remember(text) { mutableStateOf("") }
    var cursorOn by remember { mutableStateOf(true) }
    val typing = shown.length < text.length

    // 打字机：按 codePoint 前进（emoji 不截断）
    LaunchedEffect(text) {
        shown = ""
        var cp = 0
        while (cp < text.length) {
            delay(charMs)
            cp = text.offsetByCodePoints(cp, 1)
            shown = text.substring(0, cp)
        }
        onFinished()
    }
    // 光标闪烁：打字中 200ms 亮/灭循环，打完熄灭
    LaunchedEffect(shown) {
        while (shown.length < text.length) {
            cursorOn = true
            delay(PixelMotion.Blink.HALF_PERIOD_MS)
            cursorOn = false
            delay(PixelMotion.Blink.HALF_PERIOD_MS)
        }
        cursorOn = false
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = shown,
            color = color,
            fontSize = fontSize,
            fontFamily = if (digital) PixelType.digital else fontFamily,
            letterSpacing = if (digital) 2.sp else TextUnit.Unspecified,
            // PS2P 字体 padding 大 → 裁掉保证垂直居中
            style = if (digital) {
                (style ?: TextStyle.Default).merge(
                    TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
            } else {
                style ?: TextStyle.Default
            },
            maxLines = maxLines,
        )
        if (typing && showCursor && cursorOn) {
            // 像素光标 ▊：8×8 黑块，紧随文字尾部
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Black),
            )
        }
    }
}

@PreviewPixel
@Composable
private fun PixelTypewriterTextPreview() {
    PixelTheme {
        PixelTypewriterText(
            text = "🍙 记录早餐，开启今天第一餐吧！",
            color = PixelColors.yellow,
        )
    }
}
