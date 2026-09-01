package com.ly.fast16.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 就餐计划 DAO（完整定义 §1.4 最小契约）。
 * 「今日」由调用方传 date（ISO yyyy-MM-dd），DAO 不取系统时钟（可注入、可测）。
 */
@Dao
interface MealPlanDao {

    /** 按日观测计划（date 唯一，至多一条） */
    @Query("SELECT * FROM meal_plans WHERE date = :date LIMIT 1")
    fun watchByDate(date: String): Flow<MealPlanEntity?>

    /** 按日取当前值（非流） */
    @Query("SELECT * FROM meal_plans WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): MealPlanEntity?

    /** 新建 / 覆盖（date 唯一索引冲突即更新 —— SSOT upsert 纪律） */
    @Upsert
    suspend fun upsert(plan: MealPlanEntity): Long

    @Query("DELETE FROM meal_plans WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 今日起（含）所有 ACTIVE 计划（自愈重排 rescheduleAll 用；status=0 = ACTIVE） */
    @Query("SELECT * FROM meal_plans WHERE date >= :date AND status = 0 ORDER BY date ASC")
    suspend fun getActiveFrom(date: String): List<MealPlanEntity>

    /** 按 id 取计划（Create 编辑模式预填用） */
    @Query("SELECT * FROM meal_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MealPlanEntity?
}

/**
 * 餐次 DAO（完整定义 §1.4 最小契约）。
 */
@Dao
interface MealDao {

    @Query("SELECT * FROM meals WHERE plan_id = :planId ORDER BY meal_time_epoch ASC")
    fun watchByPlan(planId: Long): Flow<List<MealEntity>>

    /** 按日期观测三餐（join meal_plans） */
    @Query(
        """
        SELECT meals.* FROM meals
        INNER JOIN meal_plans ON meals.plan_id = meal_plans.id
        WHERE meal_plans.date = :date
        ORDER BY meals.meal_time_epoch ASC
        """,
    )
    fun watchByDate(date: String): Flow<List<MealEntity>>

    @Upsert
    suspend fun upsertAll(meals: List<MealEntity>)

    /** 删除某计划全部餐次（savePlan 覆盖前先删，防同日叠加残留旧行） */
    @Query("DELETE FROM meals WHERE plan_id = :planId")
    suspend fun deleteByPlan(planId: Long)

    @Query("UPDATE meals SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    /** 更新备餐时长（prep_time 为派生字段，不落库；tile 内备餐编辑用） */
    @Query("UPDATE meals SET prep_minutes = :prepMinutes WHERE id = :id")
    suspend fun updatePrep(id: Long, prepMinutes: Int)

    /** 按 id 取单餐（闹钟触发 / 通知打卡解析用） */
    @Query("SELECT * FROM meals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MealEntity?

    /** 今日起（含）ACTIVE 计划全部餐次（自愈重排用，join plan 过滤 date + status） */
    @Query(
        """
        SELECT meals.* FROM meals
        INNER JOIN meal_plans ON meals.plan_id = meal_plans.id
        WHERE meal_plans.date >= :date AND meal_plans.status = 0
        ORDER BY meals.meal_time_epoch ASC
        """,
    )
    suspend fun getByDateFrom(date: String): List<MealEntity>
}

/**
 * 打卡 DAO（完整定义 §1.4 最小契约）。
 * 打卡幂等：@Upsert 命中 (date, meal_type) 唯一索引即覆盖 checked_at_epoch。
 */
@Dao
interface CheckInDao {

    /** 月历流：某月全部打卡（ym = yyyy-MM 前缀匹配） */
    @Query("SELECT * FROM check_ins WHERE date LIKE :yearMonth || '%' ORDER BY date ASC")
    fun watchMonth(yearMonth: String): Flow<List<CheckInEntity>>

    /** 区间「完成日」流（当日三餐均打卡）：跨月连续打卡统计专用，打卡变更自动刷新 */
    @Query(
        """
        SELECT date FROM check_ins
        WHERE date BETWEEN :from AND :to
        GROUP BY date
        HAVING COUNT(DISTINCT meal_type) = 3
        ORDER BY date ASC
        """,
    )
    fun watchCompletedDayDates(from: String, to: String): Flow<List<String>>

    /** 区间打卡流（date → 餐次）：近 7 天柱状图等锚定真实 today 的统计用 */
    @Query("SELECT * FROM check_ins WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun watchRange(from: String, to: String): Flow<List<CheckInEntity>>

    @Upsert
    suspend fun checkIn(checkIn: CheckInEntity)

    @Query("DELETE FROM check_ins WHERE date = :date AND meal_type = :mealTypeValue")
    suspend fun uncheckIn(date: String, mealTypeValue: Int)

    /**
     * 区间内的「完成日」集合：当日三餐均存在打卡记录才计入
     * （HAVING COUNT(DISTINCT meal_type) = 3），供 domain/stats 纯函数消费。
     */
    @Query(
        """
        SELECT date FROM check_ins
        WHERE date BETWEEN :from AND :to
        GROUP BY date
        HAVING COUNT(DISTINCT meal_type) = 3
        ORDER BY date ASC
        """,
    )
    suspend fun completedDayDates(from: String, to: String): List<String>
}
