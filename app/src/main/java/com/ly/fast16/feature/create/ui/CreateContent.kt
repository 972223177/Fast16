package com.ly.fast16.feature.create.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelPageTitle
import com.ly.fast16.core.designsystem.component.PixelStepper
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PixelVerticalScrollbar
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.time.Time

/** Create 主组合：步骤骨架（标题/取消/分隔线）+ step 1 早餐 / step 2 方案微调 */
@Composable
internal fun CreateContent(
    state: CreateUiState.Content,
    modifier: Modifier = Modifier,
    onIntent: (CreateIntent) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = PixelShape.contentMaxWidth)
            .verticalScroll(scrollState)
            .padding(
                start = PixelShape.Spacing.lg,
                end = PixelShape.Spacing.lg,
                top = PixelShape.Spacing.lg,
                bottom = PixelShape.Spacing.xxl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部：NEW PLAN + 步骤标签 + 取消（原型 create-head）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 页面标题规范：打字机大标题 + 副标题（PixelPageTitle）
            PixelPageTitle(
                title = if (state.isEdit) "EDIT PLAN" else "NEW PLAN",
                subtitle = if (state.isEdit) {
                    if (state.step == 1) "编辑计划 · 第 1 步" else "编辑计划 · 第 2 步"
                } else {
                    if (state.step == 1) "第 1 步 · 日期与早餐" else "第 2 步 · 选方案 + 微调"
                },
            )
            // 取消（ghost sm，回首页）
            Box(
                modifier = Modifier
                    .background(PixelColors.panel, PixelShape.stair)
                    .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
                    .clickable(onClick = onBack)
                    .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                PixelText(text = "取消", color = PixelColors.white, fontSize = PixelType.Size.xs)
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 分隔线（原型 .hr，位于 NEW PLAN 头之后）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(PixelColors.panel.copy(alpha = 0.6f)),
        )

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        if (state.step == 1) {
            BreakfastStep(state = state, onIntent = onIntent)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))
            PixelButton(
                text = "下一步：选午/晚餐 ▶",
                onClick = { onIntent(CreateIntent.NextStep) },
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PlanStep(state = state, onIntent = onIntent)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))
            PixelButton(
                text = "生成就餐计划 ✔",
                onClick = { onIntent(CreateIntent.Generate) },
                primary = true,
                enabled = state.errors.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelText(
                    text = state.errors.joinToString("；"),
                    color = PixelColors.red,
                    fontSize = PixelType.Size.xs,
                )
            }
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelButton(
                text = "上一步",
                onClick = { onIntent(CreateIntent.PrevStep) },
                primary = false,
            )
        }
    }
    // 像素滚动条：矮屏内容被裁时提示可滚动（右上端、贯穿高度）
    PixelVerticalScrollbar(
        scrollState = scrollState,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(vertical = PixelShape.Spacing.sm),
    )
    }
}

@Composable
private fun BreakfastStep(state: CreateUiState.Content, onIntent: (CreateIntent) -> Unit) {
    // 计划日期（预约未来计划：今天起不可过去；编辑模式固定原日期，左箭头禁用）
    // 自动打卡早餐已移除——打卡统一走 Home「打卡现在这餐」（按时段判定 + 自动生成）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val canShiftBack = state.isEdit || state.date > SystemTimeProvider.today()
        DateArrow(symbol = "◀", enabled = canShiftBack) { onIntent(CreateIntent.ShiftDate(-1)) }
        PixelText(
            text = "${state.date.monthValue}月${state.date.dayOfMonth}日" +
                if (state.date == SystemTimeProvider.today()) " · 今天" else "",
            color = PixelColors.white,
            fontSize = PixelType.Size.xs,
        )
        DateArrow(symbol = "▶", enabled = true) { onIntent(CreateIntent.ShiftDate(1)) }
    }

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    PixelText(
        text = "早餐时间是进食窗口起点，默认取当前时间。",
        color = PixelColors.gray,
        fontSize = PixelType.Size.xs,
    )

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    // 早餐时间 stepper（原型 step1：- 08:00 +，±5 分钟）
    // 范围 [BREAKFAST_MIN, BREAKFAST_MAX]：超范围候选跨午夜 → 校验失败流程走不通（CreateReducer KDoc）
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(text = "早餐时间", color = PixelColors.gray, fontSize = PixelType.Size.xs)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelStepper(
                value = Time.hhmm(state.breakfastTime),
                onDecrease = {
                    if (state.breakfastTime > CreateReducer.BREAKFAST_MIN) {
                        onIntent(CreateIntent.PickBreakfast(state.breakfastTime.minusMinutes(5)))
                    }
                },
                onIncrease = {
                    if (state.breakfastTime < CreateReducer.BREAKFAST_MAX) {
                        onIntent(CreateIntent.PickBreakfast(state.breakfastTime.plusMinutes(5)))
                    }
                },
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelText(
                text = "建议 ${Time.hhmm(CreateReducer.BREAKFAST_MIN)} – ${Time.hhmm(CreateReducer.BREAKFAST_MAX)}",
                color = PixelColors.gray,
                fontSize = PixelType.Size.xs,
            )
        }
    }
}

/** 日期切换箭头（◀ ▶；禁用态灰显不可点——新建不可选过去日期） */
@Composable
private fun DateArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(PixelColors.panel, PixelShape.stair)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(
            text = symbol,
            color = if (enabled) PixelColors.yellow else PixelColors.gray,
            fontSize = PixelType.Size.xs,
        )
    }
}

@Composable
private fun PlanStep(state: CreateUiState.Content, onIntent: (CreateIntent) -> Unit) {
    PixelText(
        text = "系统按 ${state.windowHours} 小时窗口推理了 3 套候选，选一套再微调备餐时长。",
        color = PixelColors.gray,
        fontSize = PixelType.Size.xs,
    )

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    // 候选三选一（原型 cand 卡：方案名 + 午/晚时刻，选中主黄描边）
    state.candidates.forEachIndexed { index, candidate ->
        val selected = index == state.selectedIndex
        PixelCard(
            borderColor = if (selected) PixelColors.yellow else Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PixelShape.Spacing.xs)
                .clickable { onIntent(CreateIntent.SelectCandidate(index)) },
        ) {
            Column {
                PixelText(
                    text = candidate.label,
                    color = PixelColors.white,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.xs))
                PixelText(
                    text = "午 ${Time.hhmm(candidate.lunch, state.zone)} · 晚 ${Time.hhmm(candidate.dinner, state.zone)}",
                    color = PixelColors.yellow,
                    fontSize = PixelType.Size.xs,
                    digital = true,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    // 备餐时长微调（原型 step2 stepper：午餐/晚餐备餐）
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelText(text = "午餐备餐", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                PixelStepper(
                    value = "${state.prepLunch}",
                    onDecrease = { onIntent(CreateIntent.AdjustPrepLunch((state.prepLunch - 5).coerceAtLeast(0))) },
                    onIncrease = { onIntent(CreateIntent.AdjustPrepLunch((state.prepLunch + 5).coerceAtMost(180))) },
                    valueMinWidth = 32.dp,
                )
            }
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelText(text = "晚餐备餐", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                PixelStepper(
                    value = "${state.prepDinner}",
                    onDecrease = { onIntent(CreateIntent.AdjustPrepDinner((state.prepDinner - 5).coerceAtLeast(0))) },
                    onIncrease = { onIntent(CreateIntent.AdjustPrepDinner((state.prepDinner + 5).coerceAtMost(180))) },
                    valueMinWidth = 32.dp,
                )
            }
        }
    }
}
