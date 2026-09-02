package com.ly.fast16.feature.settings.ui

import androidx.compose.runtime.Composable
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme

@PreviewPixel
@Composable
private fun SettingsScreenPreview() {
    PixelTheme {
        SettingsContent(
            state = SettingsUiState.Content(
                windowHours = 8,
                minMealGapMinutes = 180,
                dinnerBufferMinutes = 30,
                defaultPrepLunchMinutes = 30,
                defaultPrepDinnerMinutes = 45,
                defaultReminderMode = "NOTIFY",
            ),
            onShowOnboarding = {},
            onSetReminderMode = {},
            onSetSetting = {},
            onSetFontMode = {},
            onRequestNotification = {},
        )
    }
}
