package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelMotion
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import kotlinx.coroutines.delay

/**
 * 像素气泡（设计方案 §8.6）：白底 + 2px 黑描边 + 三角尾巴；文案与通知同源（SpeechCatalog）。
 * 弹出动效：2-3 帧**阶跃**展开（尺寸跳变，非平滑 scale，pixel-motion §2 气泡规范）。
 *
 * @param text 文案（≤20 字，调用方从 SpeechCatalog 取）
 */
@Composable
fun PixelBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    // 弹出帧：0=0%，1=60%，2=100%（整数百分比阶跃）
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        frame = 0
        delay(PixelMotion.Pop.STEP_MS)
        frame = 1
        delay(PixelMotion.Pop.STEP_MS)
        frame = 2
    }
    val scale = when (frame) {
        0 -> 0f
        1 -> 0.6f
        else -> 1f
    }

    Box(modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .background(color = PixelColors.white, shape = PixelShape.stair)
                    .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
                    .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                // spec 3.12：正文居中（多行换行居中）
                PixelText(
                    text = text,
                    color = PixelColors.bg,
                    fontSize = PixelType.Size.xs,
                    maxLines = 3,
                )
            }
            // 三角尾巴（spec 3.12 ::after：居中下方，8×8 方块像素化）
            Box(
                modifier = Modifier
                    .offset(y = (-2).dp)
                    .width(8.dp)
                    .height(8.dp)
                    .background(Color.Black),
            )
        }
    }
}

@PreviewPixel
@Composable
private fun PixelBubblePreview() {
    PixelTheme {
        PixelBubble(text = "来记录早餐，开启今天第一餐吧！")
    }
}
