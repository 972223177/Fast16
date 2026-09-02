package com.ly.fast16.feature.create.ui

import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.AppSettings
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.schedule.CandidateGenerator
import com.ly.fast16.domain.schedule.PlanValidator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/** 纯函数 reducer：候选生成 / 约束校验均无 IO、无时间源（MVI） */
object CreateReducer {

    /**
     * 早餐时间合理范围 [04:00, 14:00]（8:16 常规窗口）。
     *
     * **跨午夜红线**：早餐过晚会生成跨午夜的候选（如 20:00 早餐 → 午餐次日 00:00），
     * 而候选经 [syncFromCandidate] 转 LocalTime 后会被 [revalidate] 按原 date 组装回当天，
     * 校验必然失败（午餐 < 早餐+minGap）→ 生成按钮禁用，流程走不通。
     * 在合理范围内候选永不跨午夜（14:00 早餐 → 最晚晚餐 21:30，当天内）。
     */
    val BREAKFAST_MIN: LocalTime = LocalTime.of(4, 0)
    val BREAKFAST_MAX: LocalTime = LocalTime.of(14, 0)

    /** 超范围时回退的合理默认早餐 */
    val DEFAULT_BREAKFAST: LocalTime = LocalTime.of(8, 0)

    /** 默认早餐：当前时间在合理范围用当前时间，否则 08:00（晚上进 Create 不默认 20:00） */
    fun defaultBreakfast(now: LocalTime): LocalTime =
        if (now in BREAKFAST_MIN..BREAKFAST_MAX) now else DEFAULT_BREAKFAST

    fun initial(
        settings: AppSettings,
        date: LocalDate,
        zone: ZoneId,
        defaultBreakfast: LocalTime,
        isEdit: Boolean = false,
    ): CreateUiState.Content = CreateUiState.Content(
        isEdit = isEdit,
        date = date,
        zone = zone,
        breakfastTime = defaultBreakfast,
        windowHours = settings.windowHours,
        minGapMinutes = settings.minMealGapMinutes,
        bufferEndMinutes = settings.dinnerBufferMinutes,
        prepLunchDefault = settings.defaultPrepLunchMinutes,
        prepDinnerDefault = settings.defaultPrepDinnerMinutes,
        defaultReminderMode = settings.defaultReminderMode,
        lunchTime = defaultBreakfast,
        dinnerTime = defaultBreakfast,
        prepLunch = settings.defaultPrepLunchMinutes,
        prepDinner = settings.defaultPrepDinnerMinutes,
        lunchReminderMode = settings.defaultReminderMode,
        dinnerReminderMode = settings.defaultReminderMode,
    ).regen()

    /** 编辑预填：从现有计划构造编辑初始状态（Record 详情「编辑计划」入口） */
    fun editInitial(
        settings: AppSettings,
        plan: MealPlan,
        meals: List<Meal>,
        zone: ZoneId,
    ): CreateUiState.Content {
        val base = initial(
            settings = settings,
            date = plan.date,
            zone = zone,
            defaultBreakfast = Time.timeOf(plan.windowStart, zone),
            isEdit = true,
        )
        val lunch = meals.firstOrNull { it.type == MealType.LUNCH }
        val dinner = meals.firstOrNull { it.type == MealType.DINNER }
        val lunchTime = lunch?.let { Time.timeOf(it.mealTime, zone) } ?: base.lunchTime
        // WR-03(复审)：候选高亮与实际预填值对齐——选中与预填午餐最接近的候选
        val lunchInstant = Time.at(plan.date, lunchTime, zone)
        val selectedIndex = base.candidates.indices.minByOrNull { idx ->
            abs(Duration.between(base.candidates[idx].lunch, lunchInstant).toMillis())
        } ?: 0
        return base.copy(
            selectedIndex = selectedIndex,
            lunchTime = lunchTime,
            dinnerTime = dinner?.let { Time.timeOf(it.mealTime, zone) } ?: base.dinnerTime,
            prepLunch = lunch?.prepMinutes ?: base.prepLunch,
            prepDinner = dinner?.prepMinutes ?: base.prepDinner,
            // WR-05：编辑继承原餐次提醒模式（不被全局默认覆盖）
            lunchReminderMode = lunch?.reminderMode ?: base.lunchReminderMode,
            dinnerReminderMode = dinner?.reminderMode ?: base.dinnerReminderMode,
        ).revalidate()
    }

    fun reduce(state: CreateUiState, intent: CreateIntent): CreateUiState {
        val s = state as? CreateUiState.Content ?: return state
        return when (intent) {
            CreateIntent.NextStep -> s.copy(step = 2)
            CreateIntent.PrevStep -> s.copy(step = 1)
            // 日期 ±1 天：新建模式不可早于今天（预约未来）；编辑模式不限制（保持原日期）
            is CreateIntent.ShiftDate -> {
                val newDate = s.date.plusDays(intent.delta.toLong())
                if (!s.isEdit && newDate.isBefore(SystemTimeProvider.today())) {
                    s // 不允许选过去日期
                } else {
                    s.copy(date = newDate).regen()
                }
            }
            // 早餐 clamp 到合理范围（UI stepper 已限，reducer 双保险防跨午夜候选）
            is CreateIntent.PickBreakfast ->
                s.copy(breakfastTime = intent.time.coerceIn(BREAKFAST_MIN, BREAKFAST_MAX)).regen()
            is CreateIntent.SelectCandidate -> s.copy(selectedIndex = intent.index).syncFromCandidate()
            is CreateIntent.AdjustLunchTime -> s.copy(lunchTime = intent.time).revalidate()
            is CreateIntent.AdjustDinnerTime -> s.copy(dinnerTime = intent.time).revalidate()
            is CreateIntent.AdjustPrepLunch -> s.copy(prepLunch = intent.minutes).revalidate()
            is CreateIntent.AdjustPrepDinner -> s.copy(prepDinner = intent.minutes).revalidate()
            CreateIntent.Generate -> s // 生成副作用由 ViewModel 处理
        }
    }

    /** 早餐变化 / 日期变化 → 重新生成 3 套候选并同步选中项 */
    private fun CreateUiState.Content.regen(): CreateUiState.Content {
        val breakfast = Time.at(date, breakfastTime, zone)
        val candidates = CandidateGenerator.generateCandidates(
            breakfast = breakfast,
            window = Duration.ofHours(windowHours.toLong()),
            minGap = Duration.ofMinutes(minGapMinutes.toLong()),
            bufferEnd = Duration.ofMinutes(bufferEndMinutes.toLong()),
        )
        val sel = selectedIndex.coerceIn(0, candidates.lastIndex)
        return copy(candidates = candidates, selectedIndex = sel)
            .syncFromCandidate()
            .revalidate()
    }

    /** 选中候选 → 同步午/晚时间。备餐分钟是用户独立偏好（initial/editInitial 已给默认），
     *  不随候选重置——避免切候选/改早餐时静默丢弃已微调值 */
    private fun CreateUiState.Content.syncFromCandidate(): CreateUiState.Content {
        val c = candidates.getOrNull(selectedIndex) ?: return this
        return copy(
            lunchTime = Time.timeOf(c.lunch, zone),
            dinnerTime = Time.timeOf(c.dinner, zone),
        ).revalidate()
    }

    /** 微调 → 实时约束校验（PlanValidator） */
    private fun CreateUiState.Content.revalidate(): CreateUiState.Content {
        val breakfast = Time.at(date, breakfastTime, zone)
        val lunch = Time.at(date, lunchTime, zone)
        val dinner = Time.at(date, dinnerTime, zone)
        return copy(
            errors = PlanValidator.validate(
                lunch = lunch,
                dinner = dinner,
                breakfast = breakfast,
                window = Duration.ofHours(windowHours.toLong()),
                minGap = Duration.ofMinutes(minGapMinutes.toLong()),
                bufferEnd = Duration.ofMinutes(bufferEndMinutes.toLong()),
            ),
        )
    }
}
