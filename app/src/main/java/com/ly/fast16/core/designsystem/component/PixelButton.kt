package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.LocalPixelFonts
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 像素按钮（基建文档 §2.4 / pixel-motion §1）：
 * - primary：绿底深字（「开始吃」/ 主操作）
 * - secondary：面板底 + 白描边
 * - 按压反馈：按下**下沉 2px**（位移，非缩放）+ 描边提亮（~80ms 阶跃，无平滑）
 * - 禁用态：灰字灰描边，不可点
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val pressed by interactionSource.collectIsPressedAsState()
    // 按下下沉 2px（spec 3.2 `.pbtn:active` translateY(2px)，阶跃无动画）
    val yOffset = if (pressed) 2.dp else 0.dp

    val bg = when {
        !enabled -> PixelColors.panel.copy(alpha = 0.5f)
        primary -> PixelColors.green
        else -> PixelColors.panel
    }
    val content = when {
        !enabled -> PixelColors.gray
        primary -> PixelColors.bg
        else -> PixelColors.white
    }
    // 按下瞬间描边提亮（hit-flash 高亮闪帧）
    val borderColor = when {
        !enabled -> PixelColors.gray.copy(alpha = 0.5f)
        pressed -> PixelColors.white
        primary -> PixelColors.bg
        else -> PixelColors.white
    }

    Box(
        modifier = modifier
            .offset(y = yOffset)
            .selectable(
                selected = false,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null, // 像素风：无 M3 ripple，用 2px 下沉 + 描边反馈
                onClick = onClick,
            )
            .background(color = bg, shape = PixelShape.stair)
            .border(BorderStroke(PixelShape.borderWidth, borderColor), PixelShape.stair)
            .padding(horizontal = PixelShape.Spacing.lg, vertical = PixelShape.Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            // 按钮文字按字体模式动态取（像素风 = Fusion Pixel；系统 = 默认）
            style = PixelType.cjkText.copy(fontFamily = LocalPixelFonts.current.cjk),
        )
    }
}

@PreviewPixel
@Composable
private fun PixelButtonPreview() {
    PixelTheme {
        PixelButton(text = "开始吃", onClick = {})
        PixelButton(text = "新建计划", onClick = {}, primary = false)
    }
}
