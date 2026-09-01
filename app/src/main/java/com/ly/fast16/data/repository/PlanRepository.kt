package com.ly.fast16.data.repository

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.AppDatabase
import com.ly.fast16.data.local.MealDao
import com.ly.fast16.data.local.MealEntity
import com.ly.fast16.data.local.MealPlanDao
import com.ly.fast16.data.local.MealPlanEntity
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Room 本地实现（domain/repository.PlanRepository）。
 * SSOT：UI 只订阅 Flow；写入一律 upsert；时钟仅用于 createdAtEpoch，注入 Clock 保证可测。
 */
class LocalPlanRepository(
    private val db: AppDatabase,
    private val clock: Clock,
) : PlanRepository {

    private val planDao: MealPlanDao = db.mealPlanDao()
    private val mealDao: MealDao = db.mealDao()

    override fun watchPlanByDate(date: LocalDate): Flow<Pair<MealPlan, List<Meal>>?> {
        val dateStr = Time.formatDate(date)
        return planDao.watchByDate(dateStr)
            .combine(mealDao.watchByDate(dateStr)) { planEntity, mealEntities ->
                planEntity?.toDomain()?.let { plan -> plan to mealEntities.map { it.toDomain() } }
            }
    }

    override fun watchMealsByDate(date: LocalDate): Flow<List<Meal>> =
        mealDao.watchByDate(Time.formatDate(date)).map { list -> list.map { it.toDomain() } }

    override suspend fun savePlan(plan: MealPlan, meals: List<Meal>): Long =
        db.withWriteTransaction {
            // 编辑覆盖（id>0）保留原 createdAt（@Upsert 全列更新，直接覆盖会重置创建时间）
            val existingCreatedAt = planDao.getById(plan.id)?.createdAtEpoch
            val planId = planDao.upsert(plan.toEntity(createdAtEpoch = existingCreatedAt ?: clock.millis()))
            // 先删旧三餐再插：同日覆盖 / 编辑更新时防残留旧行（date 唯一索引 upsert 复用 planId）
            mealDao.deleteByPlan(planId)
            mealDao.upsertAll(meals.map { it.copy(planId = planId).toEntity() })
            planId
        }

    override suspend fun deletePlan(planId: Long) = planDao.deleteById(planId)

    override suspend fun updateMealStatus(mealId: Long, status: MealStatus) =
        mealDao.updateStatus(mealId, status.value)

    override suspend fun updateMealPrep(mealId: Long, prepMinutes: Int) =
        mealDao.updatePrep(mealId, prepMinutes)

    override suspend fun getMealById(id: Long): Meal? =
        mealDao.getById(id)?.toDomain()

    override suspend fun getPlanById(planId: Long): Pair<MealPlan, List<Meal>>? {
        val plan = planDao.getById(planId) ?: return null
        val meals = mealDao.watchByPlan(planId).first()
        return plan.toDomain() to meals.map { it.toDomain() }
    }

    override suspend fun getActivePlansFrom(date: LocalDate): List<Pair<MealPlan, List<Meal>>> =
        // 快照一致：只读事务包两次查询（避免计划/餐次读取竞态；Room 3 withReadTransaction）
        db.withReadTransaction {
            val dateStr = Time.formatDate(date)
            val plans = planDao.getActiveFrom(dateStr).map { it.toDomain() }
            val meals = mealDao.getByDateFrom(dateStr).map { it.toDomain() }
            plans.map { plan -> plan to meals.filter { it.planId == plan.id } }
        }

    // ---------- Entity ↔ Domain 映射（epoch/String 存储层，Instant/LocalDate 领域层） ----------

    private fun MealPlanEntity.toDomain() = MealPlan(
        id = id,
        date = Time.parseDate(date),
        windowStart = toMinuteInstant(windowStartEpoch),
        windowEnd = toMinuteInstant(windowEndEpoch),
        status = status,
    )

    private fun MealPlan.toEntity(createdAtEpoch: Long) = MealPlanEntity(
        id = id,
        date = Time.formatDate(date),
        windowStartEpoch = windowStart.toEpochMilli(),
        windowEndEpoch = windowEnd.toEpochMilli(),
        status = status,
        createdAtEpoch = createdAtEpoch,
    )

    private fun MealEntity.toDomain() = Meal(
        id = id,
        planId = planId,
        type = type,
        mealTime = toMinuteInstant(mealTimeEpoch),
        prepMinutes = prepMinutes,
        status = status,
        reminderMode = reminderMode,
    )

    private fun Meal.toEntity() = MealEntity(
        id = id,
        planId = planId,
        type = type,
        mealTimeEpoch = mealTime.toEpochMilli(),
        prepMinutes = prepMinutes,
        status = status,
        reminderMode = reminderMode,
    )

    private fun toMinuteInstant(epochMilli: Long): Instant =
        Time.alignToMinute(Instant.ofEpochMilli(epochMilli))
}
