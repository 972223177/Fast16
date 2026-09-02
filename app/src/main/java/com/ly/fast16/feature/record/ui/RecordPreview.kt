package com.ly.fast16.feature.record.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.domain.model.MealType
import java.time.LocalDate
import java.time.YearMonth

/** Preview 假数据（典型态：有统计 + 选中某日） */
private fun recordPreviewState() = RecordUiState.Content(
    month = YearMonth.of(2026, 8),
    selected = LocalDate.of(2026, 8, 30),
    checked = mapOf(
        LocalDate.of(2026, 8, 30) to setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
        LocalDate.of(2026, 8, 29) to setOf(MealType.BREAKFAST, MealType.LUNCH),
        LocalDate.of(2026, 8, 28) to setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
    ),
    streak = 12,
    monthRate = 0.83f,
    weekRate = 1f,
    weekBars = listOf(3, 2, 3, 1, 3, 3, 3),
    selectedChecked = setOf(MealType.BREAKFAST),
    hasPlanOnSelected = true,
    selectedPlanId = 1L,
    today = LocalDate.of(2026, 8, 31),
)

@PreviewPixel
@Composable
private fun RecordScreenPreview() {
    PixelTheme {
        RecordContent(
            state = recordPreviewState(),
            onIntent = {},
            onEditPlan = {},
        )
    }
}

/** 主流屏一屏可见验证：360×800dp + 系统栏/底栏 */
@Preview(
    name = "Record · 主流屏 360x800",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun RecordScreenMainstreamPreview() {
    PixelTheme {
        Scaffold(
            containerColor = PixelColors.bg,
            bottomBar = { Box(modifier = Modifier.height(62.dp)) },
        ) { innerPadding ->
            RecordContent(
                state = recordPreviewState(),
                onIntent = {},
                onEditPlan = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** 无计划但已打卡（删除计划后）：弹窗顶部显示「打卡记录独立保留」说明 */
@Preview(
    name = "Record · 无计划已打卡",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun RecordScreenNoPlanPreview() {
    PixelTheme {
        RecordContent(
            state = recordPreviewState().copy(hasPlanOnSelected = false, selectedPlanId = -1L),
            onIntent = {},
            onEditPlan = {},
        )
    }
}
