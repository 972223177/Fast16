package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelMotion
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * 像素弹窗容器（基建文档 §2.4 / 设计方案 §8.8）：
 * 进入动效 = **黑屏闪白一帧（60ms）→ 弹窗 scale 0→1 阶跃 3 步**（60ms × 3）。
 * 关闭为像素淡出（M0 由 Dialog 默认退出，M1 细化退场帧）。
 *
 * @param onDismiss 点外部 / 返回键关闭（M1 起「跳过也置 onboarding_seen」）
 */
@Composable
fun PixelDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit,
) {
    // 弹出动画：帧索引 0→3（0=黑屏闪白帧，1..3=scale 阶跃）
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(PixelMotion.Pop.STEP_MS.milliseconds)
        frame = 1
        delay(PixelMotion.Pop.STEP_MS.milliseconds)
        frame = 2
        delay(PixelMotion.Pop.STEP_MS.milliseconds)
        frame = 3
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // 黑屏闪白一帧（frame==0）：像素风进场「闪帧」
            if (frame == 0) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(PixelColors.white),
                )
            } else {
                val scale = frame.toFloat() / PixelMotion.Pop.STEPS
                Box(
                    modifier = Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .background(color = PixelColors.panel, shape = PixelShape.stair)
                        .border(
                            BorderStroke(PixelShape.borderWidth, Color.Black),
                            PixelShape.stair,
                        )
                        .padding(PixelShape.Spacing.xl),
                ) {
                    Column {
                        Text(
                            text = title,
                            color = PixelColors.yellow,
                            style = PixelType.cjkText,
                        )
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                        content()
                    }
                }
            }
        }
    }
}

@PreviewPixel
@Composable
private fun PixelDialogPreview() {
    PixelTheme {
        PixelDialog(
            onDismiss = {},
            title = "什么是 8:16 饮食法？",
        ) {
            PixelText(text = "8 小时进食、16 小时断食")
            PixelButton(text = "知道了，开始使用", onClick = {})
        }
    }
}
