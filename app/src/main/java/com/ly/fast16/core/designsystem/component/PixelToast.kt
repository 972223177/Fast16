package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelMotion
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import kotlinx.coroutines.delay

/**
 * 像素轻提示（基建 §2.4 M1 / spec 3.10 .toast）：
 * 墨底 + 黄描边 + 黄字，弹出式；[durationMs] 后自动消失并回调 [onDismiss]。
 */
@Composable
fun PixelToast(
    text: String,
    show: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 1600,
) {
    var visible by remember { mutableStateOf(show) }
    LaunchedEffect(show) {
        if (show) {
            visible = true
            delay(durationMs)
            visible = false
            onDismiss()
        }
    }
    if (!visible) return

    Box(
        modifier = modifier.graphicsLayer(alpha = if (visible) 1f else 0f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.9f), PixelShape.stair)
                .border(BorderStroke(PixelShape.borderWidth, PixelColors.yellow), PixelShape.stair)
                .padding(
                    horizontal = PixelShape.Spacing.lg,
                    vertical = PixelShape.Spacing.sm,
                ),
        ) {
            PixelText(text = text, color = PixelColors.yellow, fontSize = PixelType.Size.xs)
        }
    }
}

@PreviewPixel
@Composable
private fun PixelToastPreview() {
    PixelTheme {
        PixelToast(text = "已打卡", show = true, onDismiss = {})
    }
}
