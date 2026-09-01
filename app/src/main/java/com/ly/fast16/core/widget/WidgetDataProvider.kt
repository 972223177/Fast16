package com.ly.fast16.core.widget

import android.content.Context
import com.ly.fast16.Fast16App
import com.ly.fast16.core.character.SpeechCatalog
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.MealEntity
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.domain.schedule.FastingTimer
import com.ly.fast16.domain.stats.StreakCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.YearMonth

/**
 * Widget 数据快照（design-spec §1 widget / §5.2）：当日计划 + 打卡 + 断食 + 连续天数 + 三餐时刻。
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
        /** 下一未完成餐信息行（如 "下一餐 午餐 12:00 · 距备餐 02:13"；无未完成餐 null） */
        val nextMealLabel: String?,
        /** 下一未完成餐类型（快捷打卡按钮的 action target；全打卡 null） */
        val nextCheckInType: MealType?,
        /** 连续打卡天数（🔥 N 天） */
        val streak: Int,
        /** 三餐时刻（type → "HH:mm"；无计划餐次缺省） */
        val mealTimes: Map<MealType, String>,
        /** 进食窗口进度 0..1（断食面板 seg 用） */
        val windowProgress: Float,
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
            val now = SystemTimeProvider.now()
            // 断食剩余（FastingTimer 已过滤打卡完成餐）
            val fastingRemaining = plan?.let {
                FastingTimer.fastingRemaining(now, mealEntities.map { it.toDomainMeal() })
            }
            val fastingLabel = if (showFasting && fastingRemaining != null) {
                "断食 ${fmtDuration(fastingRemaining)}"
            } else {
                null
            }
            // 连续打卡（跨月 366 天窗口）
            val completedDates = db.checkInDao().completedDayDates(
                Time.formatDate(today.minusDays(366)),
                dateStr,
            )
            val streak = StreakCalculator.computeStreak(
                completedDays = completedDates.mapTo(mutableSetOf()) { Time.parseDate(it) },
                today = today,
            )
            // 窗口进度（已过窗口/总窗口；entity 存 epoch）
            val windowProgress = if (plan != null && plan.windowEndEpoch > plan.windowStartEpoch) {
                val total = plan.windowEndEpoch - plan.windowStartEpoch
                val done = now.toEpochMilli() - plan.windowStartEpoch
                (done.toFloat() / total).coerceIn(0f, 1f)
            } else {
                0f
            }
            // 下一未完成餐（升序首个 status != COMPLETED）—— widget 信息行 + 快捷打卡
            val mealsDomain = mealEntities.map { it.toDomainMeal() }
            val nextMeal = mealsDomain.firstOrNull { it.status != MealStatus.COMPLETED }
            val nextMealLabel = nextMeal?.let { m ->
                val name = SpeechCatalog.mealName(m.type)
                val mealTimeStr = fmtTime(m.mealTime.toEpochMilli())
                if (m.prepMinutes > 0) {
                    // 距备餐 HH:mm（prepTime = mealTime − prepMinutes）
                    val diffMin = (Duration.between(now, m.prepTime).toMinutes()).coerceAtLeast(0)
                    "下一餐 ${name} ${mealTimeStr} · 距备餐 %02d:%02d".format(diffMin / 60, diffMin % 60)
                } else {
                    "下一餐 ${name} ${mealTimeStr}"
                }
            }
            WidgetData(
                hasPlan = plan != null,
                fastingLabel = fastingLabel,
                nextMealLabel = nextMealLabel,
                nextCheckInType = nextMeal?.type,
                streak = streak,
                mealTimes = mealEntities.associate { it.type to fmtTime(it.mealTimeEpoch) },
                windowProgress = windowProgress,
                checked = checked,
                mealCount = mealEntities.size,
                showFasting = showFasting,
            )
        }
    }

    /** epoch → "HH:mm"（系统时区） */
    private fun fmtTime(epochMilli: Long): String {
        val t = Time.timeOf(Instant.ofEpochMilli(epochMilli), SystemTimeProvider.zone)
        return "%02d:%02d".format(t.hour, t.minute)
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
