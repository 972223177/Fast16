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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * 像素弹窗容器（基建文档 §2.4 / 设计方案 §8.8）。
 *
 * 借鉴同类像素产品（Undertale / Stardew Valley / 宝可梦）对话框范式：
 * - **框体瞬现**：不做展开动画（主流像素产品框体即直接出现）；
 * - **标题打字机逐字打印**（PixelTypewriterText，60ms/字）+ 闪烁像素光标——像素对话框的灵魂；
 * - 退场：点外部/返回播 60ms × 3 步 scale 收起后真正关闭（外部 state 未变前本弹窗仍驻留，无白块残留）。
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
    val steps = PixelMotion.Pop.STEPS
    // 退场阶段 closing=true，exitFrame 从 steps 递减到 0（scale 收起）
    var closing by remember { mutableStateOf(false) }
    var exitFrame by remember { mutableIntStateOf(steps) }
    val scope = rememberCoroutineScope()

    // 点外部/返回：播 scale 收起（steps → 0 阶跃，60ms × EXIT_STEPS），播完再回调 onDismiss
    val requestClose = {
        if (!closing) {
            closing = true
            scope.launch {
                val exitSteps = PixelMotion.Pop.EXIT_STEPS
                for (i in exitSteps - 1 downTo 1) {
                    exitFrame = steps * i / exitSteps
                    delay(PixelMotion.Pop.STEP_MS)
                }
                exitFrame = 0
                onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = requestClose) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val scale = (if (closing) exitFrame else steps).toFloat() / steps
            Box(
                modifier = Modifier
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .pixelPanel()
                    .padding(PixelShape.Spacing.xl),
            ) {
                Column {
                    // 标题打字机（框体瞬现，文字逐字——像素对话框灵魂）
                    PixelTypewriterText(
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

/** 面板公共样式：像素阶梯圆角 + 2px 黑描边 + 米白底 */
private fun Modifier.pixelPanel(): Modifier = this
    .background(color = PixelColors.panel, shape = PixelShape.stair)
    .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)

@PreviewPixel
@Composable
private fun PixelDialogPreview() {
    PixelTheme {
        PixelDialog(
            onDismiss = {},
            title = "🍙 什么是 8:16 饮食法？",
        ) {
            PixelText(text = "8 小时进食、16 小时断食")
            PixelButton(text = "知道了，开始使用", onClick = {})
        }
    }
}
