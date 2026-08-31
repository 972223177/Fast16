package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 时间选择器（基建 §2.4 M1 / 设计方案 §8.5）：
 * ±[stepMinutes]（默认 5min）**阶跃**步进，Press Start 2P 数字显示；上/下小像素按钮。
 */
@Composable
fun PixelTimePicker(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    stepMinutes: Int = 5,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(symbol = "▲", enabled = true) {
            onValueChange(value.plusMinutes(stepMinutes.toLong()))
        }
        PixelText(
            text = value.format(DateTimeFormatter.ofPattern("HH:mm")),
            digital = true,
            fontSize = PixelType.Size.md,
            color = PixelColors.white,
        )
        StepButton(symbol = "▼", enabled = true) {
            onValueChange(value.minusMinutes(stepMinutes.toLong()))
        }
    }
}

/** 像素步进按钮（12×12，绿底，按下 1px 下沉由 clickable 简化表达） */
@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) PixelColors.green else PixelColors.panel.copy(alpha = 0.5f)
    val fg = if (enabled) PixelColors.bg else PixelColors.gray
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(bg, PixelShape.stair)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text = symbol, color = fg, fontSize = PixelType.Size.xs)
    }
}

@PreviewPixel
@Composable
private fun PixelTimePickerPreview() {
    PixelTheme {
        Column {
            PixelTimePicker(value = LocalTime.of(12, 0), onValueChange = {})
        }
    }
}
