package com.ly.fast16.domain.usecase

import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.repository.CheckInRepository
import com.ly.fast16.domain.repository.PlanRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate

/**
 * 打卡用例（设计方案 §6.4 / 流程图 §8 三入口汇流）。
 *
 * 打卡写 `check_ins`（@Upsert 幂等）→ 当日存在对应 Meal 则联动置 COMPLETED；
 * 打卡与计划解耦：无计划也能补打卡。
 */
interface CheckInUseCase {

    /** 打卡（date + mealType 幂等，重复打卡覆盖时间戳） */
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
        checkInRepository.checkIn(date, mealType, at)
        linkMealCompleted(date, mealType)
    }

    override suspend fun checkInMeal(meal: Meal, at: Instant) {
        checkInRepository.checkIn(meal.date(), meal.type, at)
        planRepository.updateMealStatus(meal.id, MealStatus.COMPLETED)
    }

    override suspend fun uncheckIn(date: LocalDate, mealType: MealType) {
        checkInRepository.uncheckIn(date, mealType)
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
