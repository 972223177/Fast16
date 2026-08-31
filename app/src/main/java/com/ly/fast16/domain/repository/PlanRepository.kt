package com.ly.fast16.domain.repository

import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 就餐计划仓储接口（跨 feature 共享，feature 间禁止横向依赖的唯一入口）。
 * 定义在 domain 层（依赖纪律：domain ← data，data 提供实现）。
 */
interface PlanRepository {

    /** 观测某日计划（含三餐），无计划时为 null */
    fun watchPlanByDate(date: LocalDate): Flow<Pair<MealPlan, List<Meal>>?>

    /** 观测某日三餐 */
    fun watchMealsByDate(date: LocalDate): Flow<List<Meal>>

    /** 生成 / 覆盖某日计划（计划 + 三餐原子写入，返回 planId） */
    suspend fun savePlan(plan: MealPlan, meals: List<Meal>): Long

    /** 删除计划（FK CASCADE 级联删三餐） */
    suspend fun deletePlan(planId: Long)

    /** 更新单餐状态（reconcile / 打卡联动置 COMPLETED） */
    suspend fun updateMealStatus(mealId: Long, status: MealStatus)

    /** 按 id 取单餐（闹钟触发 / 通知打卡解析用） */
    suspend fun getMealById(id: Long): Meal?
}
