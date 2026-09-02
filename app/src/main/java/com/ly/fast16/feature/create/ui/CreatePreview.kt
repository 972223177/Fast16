package com.ly.fast16.feature.create.ui

import androidx.compose.runtime.Composable
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.data.local.AppSettings
import com.ly.fast16.domain.model.ReminderMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@PreviewPixel
@Composable
private fun CreateScreenPreview() {
    PixelTheme {
        CreateContent(
            state = CreateReducer.initial(
                settings = AppSettings(
                    windowHours = 8, minMealGapMinutes = 180, dinnerBufferMinutes = 30,
                    defaultPrepLunchMinutes = 30, defaultPrepDinnerMinutes = 45,
                    defaultReminderMode = ReminderMode.NOTIFY,
                    widgetShowFasting = true, onboardingSeen = true,
                ),
                date = LocalDate.of(2026, 8, 31),
                zone = ZoneId.of("Asia/Shanghai"),
                defaultBreakfast = LocalTime.of(8, 0),
            ),
            onIntent = {},
            onBack = {},
        )
    }
}
