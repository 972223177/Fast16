package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 三餐时间轴行（原型 index.html meal-row 直接移植）：
 * 碗图标 + 餐名 + 时刻（黄）+ 备餐（灰）+ 状态点 + 打卡按钮。
 * 状态点语义：● 已打卡（绿）/ ○ 待打卡（黄）/ - 无计划（灰）。
 */
@Composable
fun PixelMealRow(
    name: String,
    timeLabel: String,
    prepMinutes: Int,
    checkedIn: Boolean,
    hasMeal: Boolean,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .padding(PixelShape.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 碗图标（8×8 → 34×34）
        PixelSprite(
            rows = ICON_BOWL,
            modifier = Modifier.size(34.dp),
            contentDescription = "$name 图标",
        )

        Spacer(modifier = Modifier.width(PixelShape.Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            PixelText(text = name, color = PixelColors.white, fontSize = PixelType.Size.xs)
            Spacer(modifier = Modifier.height(4.dp))
            PixelText(
                text = timeLabel,
                color = PixelColors.yellow,
                fontSize = PixelType.Size.xs,
                digital = true,
            )
            Spacer(modifier = Modifier.height(4.dp))
            PixelText(
                text = if (prepMinutes > 0) "备餐 $prepMinutes 分" else "直接开吃",
                color = PixelColors.gray,
                fontSize = PixelType.Size.xs,
            )
        }

        // 状态点（16×16）
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = when {
                        checkedIn -> PixelColors.green
                        hasMeal -> PixelColors.yellow
                        else -> PixelColors.bg.copy(alpha = 0.5f)
                    },
                )
                .border(BorderStroke(2.dp, Color.Black)),
        )

        Spacer(modifier = Modifier.width(PixelShape.Spacing.md))

        SmallPixelButton(
            text = if (checkedIn) "已打卡" else "打卡",
            tone = if (checkedIn) SmallTone.GHOST else SmallTone.BLUE,
            onClick = onCheckIn,
        )
    }
}

private enum class SmallTone { BLUE, GHOST }

/** 小号像素按钮（原型 pbtn sm：蓝底黑字 / 幽灵面板底白字黑边） */
@Composable
private fun SmallPixelButton(text: String, tone: SmallTone, onClick: () -> Unit) {
    val bg = when (tone) {
        SmallTone.BLUE -> PixelColors.blue
        SmallTone.GHOST -> PixelColors.panel
    }
    val fg = when (tone) {
        SmallTone.BLUE -> PixelColors.bg
        SmallTone.GHOST -> PixelColors.white
    }
    Box(
        modifier = Modifier
            .background(bg, PixelShape.stair)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = PixelShape.Spacing.md, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text = text, color = fg, fontSize = PixelType.Size.xs)
    }
}

@PreviewPixel
@Composable
private fun PixelMealRowPreview() {
    PixelTheme {
        Column {
            PixelMealRow(name = "早餐", timeLabel = "08:00", prepMinutes = 0, checkedIn = true, hasMeal = true, onCheckIn = {})
            PixelMealRow(name = "午餐", timeLabel = "12:07", prepMinutes = 30, checkedIn = false, hasMeal = true, onCheckIn = {})
        }
    }
}
