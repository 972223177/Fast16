package com.ly.fast16.core.plan

import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.core.time.Time
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.domain.repository.PlanRepository
import com.ly.fast16.domain.schedule.CandidateGenerator
import com.ly.fast16.domain.schedule.MealWindow
import com.ly.fast16.domain.schedule.PlanValidator
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 早餐打卡自动生成计划的结果 */
sealed interface AutoPlanResult {
    /** 已创建今日计划并排程午/晚闹钟 */
    data object Created : AutoPlanResult

    /** 当日已有计划，不重复创建 */
    data object AlreadyHasPlan : AutoPlanResult

    /** 时间太晚，8:16 窗口无法排布午/晚（候选校验失败，不生成计划） */
    data object TooLate : AutoPlanResult
}

/**
 * 「打卡早餐即计划」自动生成器（core 共享；Home「打卡现在这餐」入口调用）：
 * 由当前时刻作为早餐 → 均衡候选 → 校验 → 落库 + 排程午/晚闹钟。
 *
 * **备餐策略**：午/晚备餐用调用方传入的默认值（Settings 缺省）作**生成起点**，
 * 生成后用户可在对应打卡 tile 内独立编辑（prepMinutes 是 per-meal 字段，非全局写死）。
 *
 * 依赖限 domain 接口 + core 调度，不依赖 data 层（Settings 由调用方读出后以参数传入），
 * 分层纪律：core → domain ← data。
 */
class AutoPlanGenerator(
    private val planRepository: PlanRepository,
    private val scheduler: PlanScheduler,
) {
    /**
     * 以 [now] 为早餐时刻生成今日计划。
     *
     * @return [AutoPlanResult.Created] 落库 + 排程成功；
     *         [AutoPlanResult.AlreadyHasPlan] 当日已有计划；
     *         [AutoPlanResult.TooLate] 时间太晚（深夜/窗口无法排布），不生成。
     */
    suspend fun generateFromBreakfast(
        now: Instant,
        zone: ZoneId,
        windowHours: Int,
        minGapMinutes: Int,
        bufferEndMinutes: Int,
        prepLunchDefault: Int,
        prepDinnerDefault: Int,
        reminderMode: ReminderMode,
    ): AutoPlanResult {
        val date = Time.dateOf(now, zone)
        if (planRepository.watchPlanByDate(date).first() != null) return AutoPlanResult.AlreadyHasPlan

        val breakfast = Time.alignToMinute(now)
        // 产品规则：仅「早餐时段」（MealWindow 04:00–10:59）打卡才自动生成计划——
        // 午餐/晚餐时段走单餐记录；深夜更不可能排布（8:16 窗口跨午夜是假象，约束会通过
        // 但「大半夜吃饭」不可接受）。与 Home 空态按钮口径一致（非早餐时段不触发生成）。
        if (MealWindow.detect(Time.timeOf(breakfast, zone)) != MealType.BREAKFAST) {
            return AutoPlanResult.TooLate
        }

        val window = Duration.ofHours(windowHours.toLong())
        val minGap = Duration.ofMinutes(minGapMinutes.toLong())
        val bufferEnd = Duration.ofMinutes(bufferEndMinutes.toLong())

        // 均衡方案（索引 0）；校验失败 → 太晚不生成（早餐打卡仍由调用方记录）
        val candidate = CandidateGenerator
            .generateCandidates(breakfast, window, minGap, bufferEnd)
            .first()
        val errors = PlanValidator.validate(
            lunch = candidate.lunch,
            dinner = candidate.dinner,
            breakfast = breakfast,
            window = window,
            minGap = minGap,
            bufferEnd = bufferEnd,
        )
        if (errors.isNotEmpty()) return AutoPlanResult.TooLate

        val plan = MealPlan(
            date = date,
            windowStart = breakfast,
            windowEnd = breakfast.plus(window),
            status = PlanStatus.ACTIVE,
        )
        val meals = listOf(
            Meal(type = MealType.BREAKFAST, mealTime = breakfast, prepMinutes = 0, reminderMode = reminderMode),
            Meal(type = MealType.LUNCH, mealTime = candidate.lunch, prepMinutes = prepLunchDefault, reminderMode = reminderMode),
            Meal(type = MealType.DINNER, mealTime = candidate.dinner, prepMinutes = prepDinnerDefault, reminderMode = reminderMode),
        )
        val planId = planRepository.savePlan(plan, meals)
        // 排程必须用落库后的真实 Meal（含 id）——AlarmReceiver 依 EXTRA_MEAL_ID 查库
        val savedMeals = planRepository.watchMealsByDate(date).first()
        scheduler.schedule(plan.copy(id = planId), savedMeals)
        return AutoPlanResult.Created
    }
}
