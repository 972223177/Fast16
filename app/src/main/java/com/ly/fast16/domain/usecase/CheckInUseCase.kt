package com.ly.fast16.domain.usecase

import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.repository.CheckInRepository
import com.ly.fast16.domain.repository.PlanRepository
import com.ly.fast16.domain.schedule.MealStateMachine
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate

/**
 * 打卡用例（设计方案 §6.4 / 流程图 §8 三入口汇流）。
 *
 * 打卡写 `check_ins`（@Upsert 幂等）→ 当日存在对应 Meal 则联动置 COMPLETED；
 * 打卡与计划解耦：无计划也能补打卡。
 *
 * **未来日期禁止打卡**：补打卡语义 = 补「已发生」的漏记；未来预打卡会污染
 * streak / 完成率 / 日历三点等统计聚合（check_ins 无「未来排除」派生逻辑）。
 * Home / Widget / Create 三入口均传当天日期，此校验只拦任意日期的补打卡入口。
 */
interface CheckInUseCase {

    /**
     * 打卡（date + mealType 幂等，重复打卡覆盖时间戳）。
     * @throws IllegalArgumentException date 晚于今天（未来日期禁止预打卡）
     */
    suspend fun checkIn(date: LocalDate, mealType: MealType, at: Instant)

    /** 通知动作打卡（按 Meal 实体打卡，含 Meal 联动） */
    suspend fun checkInMeal(meal: Meal, at: Instant)

    /** 撤销打卡（可选） */
    suspend fun uncheckIn(date: LocalDate, mealType: MealType)
}

class DefaultCheckInUseCase(
    private val checkInRepository: CheckInRepository,
    private val planRepository: PlanRepository,
) : CheckInUseCase {

    override suspend fun checkIn(date: LocalDate, mealType: MealType, at: Instant) {
        require(!date.isAfter(SystemTimeProvider.today())) { "不能为未来日期补打卡" }
        checkInRepository.checkIn(date, mealType, at)
        linkMealCompleted(date, mealType)
    }

    override suspend fun checkInMeal(meal: Meal, at: Instant) {
        // 打卡归属 = 计划日期（非 mealTime 派生日期）：晚餐跨午夜时 mealTime 落次日，
        // 若按 mealTime 归属会破坏「当日三餐」聚合；计划已删则退回 mealTime 日期
        val planDate = planRepository.getPlanById(meal.planId)?.first?.date
            ?: SystemTimeProvider.dateOf(meal.mealTime)
        checkInRepository.checkIn(planDate, meal.type, at)
        planRepository.updateMealStatus(meal.id, MealStatus.COMPLETED)
    }

    override suspend fun uncheckIn(date: LocalDate, mealType: MealType) {
        checkInRepository.uncheckIn(date, mealType)
        // 联动回退：对应 Meal 按「当前时刻 + 无打卡」重算状态——
        // 撤销后可能仍处于 EATING/PREPARING（时间已到），不能简单置回 SCHEDULED
        val now = SystemTimeProvider.now()
        planRepository.watchMealsByDate(date).first()
            .firstOrNull { it.type == mealType }
            ?.let { meal ->
                planRepository.updateMealStatus(
                    meal.id,
                    MealStateMachine.advance(meal, now, hasCheckIn = false),
                )
            }
    }

    /** 当日对应 Meal 联动置 COMPLETED（无对应 Meal 时静默，允许补打卡） */
    private suspend fun linkMealCompleted(date: LocalDate, mealType: MealType) {
        planRepository.watchMealsByDate(date).first()
            .firstOrNull { it.type == mealType }
            ?.let { planRepository.updateMealStatus(it.id, MealStatus.COMPLETED) }
    }

    /** Meal.date 从 mealTime 派生（系统时区，走 core/device 统一入口） */
    private fun Meal.date(): LocalDate = SystemTimeProvider.dateOf(mealTime)
}
