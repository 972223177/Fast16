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

    @Query("UPDATE meals SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)
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
