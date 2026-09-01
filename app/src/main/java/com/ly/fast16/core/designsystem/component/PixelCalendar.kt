package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.ly.fast16.domain.model.MealType
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历网格（原型 index.html cal-grid 直接移植）：
 * 周一起始 7 列；每天 = 日期数字 + 早/午/晚三状态点（已打卡绿实心 / 未打灰空）；
 * 今天黄 outline，选中日白 outline。点日期回调 [onSelectDay]（null = 取消选中）。
 */
@Composable
fun PixelCalendar(
    month: YearMonth,
    checked: Map<LocalDate, Set<MealType>>,
    selected: LocalDate?,
    today: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 表头：◀ 2026 · 08 ▶
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavArrow(symbol = "◀") { onMonthChange(month.minusMonths(1)) }
            PixelText(
                text = "${month.year} · %02d".format(month.monthValue),
                color = PixelColors.white,
                fontSize = PixelType.Size.xs,
                digital = true,
            )
            NavArrow(symbol = "▶") { onMonthChange(month.plusMonths(1)) }
        }

        // 周几表头（周一 = 一）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WEEKDAYS.forEach { w ->
                Box(
                    modifier = Modifier.weight(1f).padding(vertical = PixelShape.Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelText(text = w, color = PixelColors.gray, fontSize = PixelType.Size.xs)
                }
            }
        }

        // 网格：月初偏移（周一起始）+ 当月天数，补全到整周
        val first = month.atDay(1)
        val offset = (first.dayOfWeek.value + 6) % 7 // 周一=0
        val days = month.lengthOfMonth()
        val cells = MutableList(offset) { null as LocalDate? }
        (1..days).forEach { cells.add(month.atDay(it)) }
        while (cells.size % 7 != 0) cells.add(null)

        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                week.forEach { date ->
                    val cell = date?.let { d ->
                        val cks = checked[d].orEmpty()
                        val isToday = d == today
                        val isSelected = d == selected
                        CalendarCell(
                            day = d.dayOfMonth,
                            dots = listOf(
                                MealType.BREAKFAST in cks,
                                MealType.LUNCH in cks,
                                MealType.DINNER in cks,
                            ),
                            isToday = isToday,
                            isSelected = isSelected,
                            onClick = { onSelectDay(if (isSelected) null else d) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (cell != null) {
                        cell
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
private fun NavArrow(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = PixelShape.Spacing.sm, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text = symbol, color = PixelColors.yellow, fontSize = PixelType.Size.md)
    }
}

@Composable
private fun CalendarCell(
    day: Int,
    dots: List<Boolean>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = when {
        isSelected -> PixelColors.white
        isToday -> PixelColors.yellow
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .padding(2.dp)
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .border(BorderStroke(2.dp, outline))
            .clickable(onClick = onClick)
            // 内边距 6dp：日期数字不贴格边（原 4dp 过小）
            .padding(vertical = PixelShape.Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelText(text = "$day", color = PixelColors.white, fontSize = PixelType.Size.xs, digital = true)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dots.forEach { lit ->
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(if (lit) PixelColors.green else PixelColors.bg.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

@PreviewPixel
@Composable
private fun PixelCalendarPreview() {
    PixelTheme {
        PixelCalendar(
            month = YearMonth.of(2026, 8),
            checked = mapOf(
                LocalDate.of(2026, 8, 30) to setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
                LocalDate.of(2026, 8, 29) to setOf(MealType.BREAKFAST, MealType.LUNCH),
            ),
            selected = null,
            today = LocalDate.of(2026, 8, 31),
            onMonthChange = {},
            onSelectDay = {},
        )
    }
}
