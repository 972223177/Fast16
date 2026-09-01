package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
 *
 * @param onEditPrep 点击备餐信息编辑备餐时长（null = 不可编辑，备餐文字灰显；
 *       非 null 时文字转黄——与候选卡/箭头同「可交互」语义）
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
    onEditPrep: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 半透明面板底：Home 氛围背景（云/星光）透出；内容对比仍足够
            .background(PixelColors.panel.copy(alpha = 0.9f))
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .padding(PixelShape.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 碗图标（8×8 → 26×26；一屏可见优化：由 34 下调，图标非文字不涉点阵清晰度）
        PixelSprite(
            rows = ICON_BOWL,
            modifier = Modifier.size(26.dp),
            contentDescription = "$name 图标",
        )

        Spacer(modifier = Modifier.width(PixelShape.Spacing.md))

        // 一屏可见优化：餐名/时刻/备餐由 3 行堆叠压成单行 —— 原 3 行文字撑到 ~53dp
        // 是 tile 高度瓶颈，单行后降到 ~14dp，三餐合计省 ~120dp。
        // 备餐为次要信息，宽度不足时单行截断（maxLines=1），绝不换行把 tile 撑回多行。
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.xs),
        ) {
            PixelText(
                text = name,
                color = PixelColors.white,
                fontSize = PixelType.Size.xs,
                maxLines = 1,
            )
            PixelText(
                text = timeLabel,
                color = PixelColors.yellow,
                fontSize = PixelType.Size.xs,
                digital = true,
                maxLines = 1,
            )
            // 备餐信息可点编辑：黄色 = 可交互（与候选卡/箭头同语义），灰色 = 不可编辑
            PixelText(
                text = if (prepMinutes > 0) "备餐${prepMinutes}分" else "直接开吃",
                color = if (onEditPrep != null) PixelColors.yellow else PixelColors.gray,
                fontSize = PixelType.Size.xs,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onEditPrep != null) {
                            Modifier.clickable(onClick = onEditPrep)
                        } else {
                            Modifier
                        },
                    ),
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
