package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelMotion
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.notification.VibratorCompat
import kotlinx.coroutines.delay

/**
 * 像素轻提示（基建 §2.4 M1 / spec 3.10 .toast）：
 * 墨底 + 黄描边 + 黄字，弹出式；[durationMs] 后自动消失并回调 [onDismiss]。
 *
 * 显示完全由外部 [show] 驱动：`show=false` 立即隐藏（不卡屏），
 * 计时器仅负责到点回调 [onDismiss]（由外部把 show 置 false）。
 */
@Composable
fun PixelToast(
    text: String,
    show: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 1600,
    vibrate: Boolean = true,
) {
    val context = LocalContext.current
    val vibrator = remember { VibratorCompat(context) }
    LaunchedEffect(show) {
        if (show) {
            // P2：轻提示伴随 80ms 轻震（原型 navigator.vibrate(80)）
            if (vibrate) vibrator.buzz()
            delay(durationMs)
            onDismiss()
        }
    }
    if (!show) return

    // 底部居中（像素类应用 toast 惯例）：气泡距屏幕底边 xxl 由外层容器 padding 实现，
    // 气泡内部 padding 对称——高度只随文本内容自适应，不被底部留白拉高
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = PixelShape.Spacing.xxl),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                // 限宽：Text 在无宽度约束时不换行（长文案横向溢出高度不变），限宽后多行自适应高度
                .widthIn(max = 320.dp)
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
