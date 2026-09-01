package com.ly.fast16.core.widget

import android.content.Context
import com.ly.fast16.Fast16App
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.MealEntity
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.domain.schedule.FastingTimer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.YearMonth

/**
 * Widget 数据快照（design-spec §1 widget / §5.2）：当日计划 + 打卡 + 断食状态。
 *
 * Glance 渲染前一次性同步读取（低频、当日 ≤3 餐，runBlocking 可接受）；
 * 数据源 = Room（AppDatabase 经 Fast16App 暴露）+ SettingsStore（widgetShowFasting）。
 */
object WidgetDataProvider {

    data class WidgetData(
        /** 今日是否有计划 */
        val hasPlan: Boolean,
        /** 断食状态文案（如 "断食 3:20"；无未来动作或未启用为 null） */
        val fastingLabel: String?,
        /** 今日已打卡餐次 */
        val checked: Set<MealType>,
        /** 今日餐数（无计划 0） */
        val mealCount: Int,
        /** 是否显示断食区（SettingsStore.widgetShowFasting） */
        val showFasting: Boolean,
    )

    fun load(context: Context): WidgetData {
        val app = context.applicationContext as Fast16App
        return runBlocking {
            val today = SystemTimeProvider.today()
            val dateStr = Time.formatDate(today)
            val db = app.appDatabase
            val plan = db.mealPlanDao().getByDate(dateStr)
            val mealEntities = db.mealDao().watchByDate(dateStr).first()
            val checked = db.checkInDao()
                .watchMonth(YearMonth.from(today).toString())
                .first()
                .filter { it.date == dateStr }
                .map { it.mealType }
                .toSet()
            val showFasting = app.settingsStore.settings.first().widgetShowFasting
            val fastingLabel = if (showFasting && plan != null && mealEntities.isNotEmpty()) {
                FastingTimer.fastingRemaining(SystemTimeProvider.now(), mealEntities.map { it.toDomainMeal() })
                    ?.let { "断食 ${fmtDuration(it)}" }
            } else {
                null
            }
            WidgetData(
                hasPlan = plan != null,
                fastingLabel = fastingLabel,
                checked = checked,
                mealCount = mealEntities.size,
                showFasting = showFasting,
            )
        }
    }

    /** 断食时长 → "h:mm"（如 3:20；供 widget 状态行显示） */
    internal fun fmtDuration(d: Duration): String {
        val totalMin = (d.seconds / 60).toInt().coerceAtLeast(0)
        return "%d:%02d".format(totalMin / 60, totalMin % 60)
    }

    private fun MealEntity.toDomainMeal() = Meal(
        id = id,
        planId = planId,
        type = type,
        mealTime = Instant.ofEpochMilli(mealTimeEpoch),
        prepMinutes = prepMinutes,
        status = status,
        reminderMode = reminderMode,
    )
}
