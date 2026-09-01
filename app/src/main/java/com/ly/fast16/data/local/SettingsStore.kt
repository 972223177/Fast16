package com.ly.fast16.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ly.fast16.domain.model.ReminderMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 字体模式：PIXEL = 像素风（Fusion Pixel / Press Start 2P）；SYSTEM = 系统默认字体 */
enum class FontMode {
    PIXEL, SYSTEM,
}

/** 全局偏好快照（完整定义 §1.5） */
data class AppSettings(
    val windowHours: Int,
    val minMealGapMinutes: Int,
    val dinnerBufferMinutes: Int,
    val defaultPrepLunchMinutes: Int,
    val defaultPrepDinnerMinutes: Int,
    val defaultPrepBreakfastMinutes: Int,
    val defaultReminderMode: ReminderMode,
    val widgetShowFasting: Boolean,
    val onboardingSeen: Boolean,
    /** 字体模式（默认系统字体，可切换像素风） */
    val fontMode: FontMode = FontMode.SYSTEM,
)

/**
 * DataStore 偏好封装（设计方案 §6.2）：Flow 读 + suspend 写。
 * 只存偏好/Widget 配置；打卡记录走 Room（决策 #5）。
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val WINDOW_HOURS = intPreferencesKey("window_hours")
        val MIN_MEAL_GAP_MINUTES = intPreferencesKey("min_meal_gap_minutes")
        val DINNER_BUFFER_MINUTES = intPreferencesKey("dinner_buffer_minutes")
        val DEFAULT_PREP_LUNCH_MINUTES = intPreferencesKey("default_prep_lunch_minutes")
        val DEFAULT_PREP_DINNER_MINUTES = intPreferencesKey("default_prep_dinner_minutes")
        val DEFAULT_PREP_BREAKFAST_MINUTES = intPreferencesKey("default_prep_breakfast_minutes")
        val DEFAULT_REMINDER_MODE = stringPreferencesKey("default_reminder_mode")
        val WIDGET_SHOW_FASTING = booleanPreferencesKey("widget_show_fasting")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val FONT_MODE = stringPreferencesKey("font_mode")
    }

    /** 偏好流（缺省值对齐 §1.5） */
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            windowHours = p[Keys.WINDOW_HOURS] ?: 8,
            minMealGapMinutes = p[Keys.MIN_MEAL_GAP_MINUTES] ?: 180,
            dinnerBufferMinutes = p[Keys.DINNER_BUFFER_MINUTES] ?: 30,
            defaultPrepLunchMinutes = p[Keys.DEFAULT_PREP_LUNCH_MINUTES] ?: 30,
            defaultPrepDinnerMinutes = p[Keys.DEFAULT_PREP_DINNER_MINUTES] ?: 45,
            defaultPrepBreakfastMinutes = p[Keys.DEFAULT_PREP_BREAKFAST_MINUTES] ?: 0,
            defaultReminderMode = p[Keys.DEFAULT_REMINDER_MODE]
                ?.let { runCatching { ReminderMode.valueOf(it) }.getOrNull() }
                ?: ReminderMode.NOTIFY,
            widgetShowFasting = p[Keys.WIDGET_SHOW_FASTING] ?: true,
            onboardingSeen = p[Keys.ONBOARDING_SEEN] ?: false,
            fontMode = p[Keys.FONT_MODE]
                ?.let { runCatching { FontMode.valueOf(it) }.getOrNull() }
                ?: FontMode.SYSTEM,
        )
    }

    suspend fun setWindowHours(hours: Int) = edit { it[Keys.WINDOW_HOURS] = hours }

    suspend fun setMinMealGapMinutes(minutes: Int) = edit { it[Keys.MIN_MEAL_GAP_MINUTES] = minutes }

    suspend fun setDinnerBufferMinutes(minutes: Int) = edit { it[Keys.DINNER_BUFFER_MINUTES] = minutes }

    suspend fun setDefaultPrepLunchMinutes(minutes: Int) =
        edit { it[Keys.DEFAULT_PREP_LUNCH_MINUTES] = minutes }

    suspend fun setDefaultPrepDinnerMinutes(minutes: Int) =
        edit { it[Keys.DEFAULT_PREP_DINNER_MINUTES] = minutes }

    suspend fun setDefaultPrepBreakfastMinutes(minutes: Int) =
        edit { it[Keys.DEFAULT_PREP_BREAKFAST_MINUTES] = minutes }

    suspend fun setDefaultReminderMode(mode: ReminderMode) =
        edit { it[Keys.DEFAULT_REMINDER_MODE] = mode.name }

    suspend fun setWidgetShowFasting(show: Boolean) = edit { it[Keys.WIDGET_SHOW_FASTING] = show }

    suspend fun setOnboardingSeen(seen: Boolean) = edit { it[Keys.ONBOARDING_SEEN] = seen }

    suspend fun setFontMode(mode: FontMode) = edit { it[Keys.FONT_MODE] = mode.name }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
