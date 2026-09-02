package com.ly.fast16.feature.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCalendar
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelDialog
import com.ly.fast16.core.designsystem.component.PixelDivider
import com.ly.fast16.core.designsystem.component.PixelPageScaffold
import com.ly.fast16.core.designsystem.component.PixelPageTitle
import com.ly.fast16.core.designsystem.component.PixelProgressBar
import com.ly.fast16.core.designsystem.component.PixelSectionTitle
import com.ly.fast16.core.designsystem.component.PixelStatBars
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.domain.model.MealType

/** Record 主组合：固定标题 + 统计概览卡 + 日历 + 补打卡/删除弹窗 */
@Composable
internal fun RecordContent(
    state: RecordUiState.Content,
    onIntent: (RecordIntent) -> Unit,
    onEditPlan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    PixelPageScaffold(
        modifier = modifier,
        header = {
            // 页面标题规范：打字机大标题 + 副标题（PixelPageTitle）
            PixelPageTitle(title = "RECORD", subtitle = "统计概览 · 日历打卡")
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            PixelDivider()
        },
    ) {
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 分区标题：统计概览
        PixelSectionTitle(text = "统计概览")
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 统计概览（原型 stats-panel：连续打卡大数字 + 完成率 seg + 近 7 天柱状图）
        PixelCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.width(96.dp)) {
                        PixelText(text = "连续打卡", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                        // 连续打卡大数字（一屏可见优化：spec 2.2 的 40px 下调至 Size.lg = 28px）
                        PixelText(
                            text = "${state.streak}",
                            color = PixelColors.green,
                            fontSize = PixelType.Size.lg,
                            digital = true,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatLine(label = "本月完成率", rate = state.monthRate, segments = 16)
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                        StatLine(label = "本周完成率", rate = state.weekRate, segments = 12)
                    }
                }

                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

                PixelText(text = "近 7 天完成", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelStatBars(values = state.weekBars)
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 分区标题：打卡日历
        PixelSectionTitle(text = "打卡日历")
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 月历（原型 cal-grid）；未来日期禁用（maxSelectable = today → 禁止未来预打卡）
        PixelCalendar(
            month = state.month,
            checked = state.checked,
            selected = state.selected,
            today = state.today,
            maxSelectable = state.today,
            onMonthChange = { onIntent(RecordIntent.SwitchMonth(if (it > state.month) 1 else -1)) },
            onSelectDay = { onIntent(RecordIntent.TapDate(it)) },
        )
    }

    // 选中日补打卡：像素弹窗（一屏可见优化：原为日历下方内嵌面板，占 ~230dp 撑高页面；
    // 改弹窗后不占滚动布局，统计 + 日历一屏放下）。点外部 / 关闭按钮 → 取消选中并收起。
    state.selected?.let { selectedDate ->
        PixelDialog(
            onDismiss = { onIntent(RecordIntent.TapDate(null)) },
            title = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 · 打卡",
        ) {
            Column {
                // 无计划日期的语义说明：删除计划后打卡记录独立保留（check_ins 与计划解耦），
                // 三绿点 = 当日打卡事实，非「计划完成」；「可撤销」= 用户主动纠错（误补卡），
                // 与「系统不自动清」不冲突
                if (!state.hasPlanOnSelected) {
                    PixelText(
                        text = if (state.selectedChecked.isNotEmpty()) {
                            "该日无计划，打卡记录独立保留 · 可撤销"
                        } else {
                            "该日无计划，可直接补打卡"
                        },
                        color = PixelColors.gray,
                        fontSize = PixelType.Size.xs,
                    )
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                }
                MealType.entries.forEachIndexed { index, type ->
                    val done = type in state.selectedChecked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = PixelShape.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelText(
                            text = mealName(type),
                            color = PixelColors.white,
                            fontSize = PixelType.Size.xs,
                            modifier = Modifier.weight(1f),
                        )
                        // 未打卡用蓝底主操作；已打卡灰底描边（与设计稿一致）
                        PixelButton(
                            text = if (done) "已打卡" else "补打卡",
                            onClick = { onIntent(RecordIntent.BackfillCheckIn(type)) },
                            primary = !done,
                        )
                    }
                    // 餐间像素分隔线（最后一行无）
                    if (index != MealType.entries.lastIndex) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.Black),
                        )
                    }
                }

                // 计划管理区（该日有计划时：编辑 / 删除）
                if (state.hasPlanOnSelected) {
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelButton(
                            text = "编辑计划",
                            onClick = { onEditPlan(state.selectedPlanId) },
                            primary = false,
                            modifier = Modifier.weight(1f),
                        )
                        // 删除：红色按钮 → 确认弹窗；与 PixelButton 规格对齐（等高不显扁）
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(PixelColors.red, PixelShape.stair)
                                .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
                                .clickable { showDeleteConfirm = true }
                                .padding(horizontal = PixelShape.Spacing.lg, vertical = PixelShape.Spacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelText(text = "删除计划", color = PixelColors.bg, fontSize = PixelType.Size.sm)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelButton(
                    text = "关闭",
                    onClick = { onIntent(RecordIntent.TapDate(null)) },
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // 删除计划确认（PixelDialog）
    if (showDeleteConfirm) {
        PixelDialog(onDismiss = { showDeleteConfirm = false }, title = "删除今日计划？") {
            Column {
                PixelText(
                    text = "删除后当日提醒将撤销，已打卡记录保留。",
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelButton(
                    text = "确认删除",
                    onClick = {
                        showDeleteConfirm = false
                        onIntent(RecordIntent.DeletePlan)
                    },
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelButton(
                    text = "取消",
                    onClick = { showDeleteConfirm = false },
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 完成率行：label + 百分比 + seg（本月 16 格 / 本周 12 格，原型 stat-lbl） */
@Composable
private fun StatLine(label: String, rate: Float, segments: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PixelText(text = label, color = PixelColors.gray, fontSize = PixelType.Size.xs)
            PixelText(
                text = "${(rate * 100).toInt()}%",
                color = PixelColors.gray,
                fontSize = PixelType.Size.xs,
                digital = true,
            )
        }
        Spacer(modifier = Modifier.height(PixelShape.Spacing.xs))
        PixelProgressBar(progress = rate, segments = segments, litColor = PixelColors.green)
    }
}

private fun mealName(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
}
