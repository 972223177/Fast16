package com.ly.fast16.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import com.ly.fast16.domain.model.ReminderMode

/**
 * 就餐计划表（完整定义 §1.1 DDL 等价）。
 * date 唯一索引：一天最多一个计划。
 */
@Entity(
    tableName = "meal_plans",
    indices = [Index(value = ["date"], unique = true)],
)
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "date") val date: String,               // ISO yyyy-MM-dd
    @ColumnInfo(name = "window_start_epoch") val windowStartEpoch: Long, // 早餐时间（毫秒）
    @ColumnInfo(name = "window_end_epoch") val windowEndEpoch: Long,     // windowStart + windowHours
    @ColumnInfo(name = "status") val status: PlanStatus,
    @ColumnInfo(name = "created_at_epoch") val createdAtEpoch: Long,
)

/**
 * 餐次表（完整定义 §1.2 DDL 等价）。
 * prep_time 不落库，由 meal_time_epoch − prep_minutes 派生（单一事实源）。
 */
@Entity(
    tableName = "meals",
    indices = [Index(value = ["plan_id"])],
    foreignKeys = [
        ForeignKey(
            entity = MealPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "plan_id") val planId: Long,
    @ColumnInfo(name = "type") val type: MealType,
    @ColumnInfo(name = "meal_time_epoch") val mealTimeEpoch: Long, // 「开始吃」时刻（毫秒）
    @ColumnInfo(name = "prep_minutes") val prepMinutes: Int,       // 0 = 无 PREP 阶段
    @ColumnInfo(name = "status") val status: MealStatus,
    @ColumnInfo(name = "reminder_mode") val reminderMode: ReminderMode,
)

/**
 * 打卡记录表（完整定义 §1.3 DDL 等价）。
 * (date, meal_type) 复合唯一 → @Upsert 覆盖，天然幂等。
 */
@Entity(
    tableName = "check_ins",
    indices = [Index(value = ["date", "meal_type"], unique = true)],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "date") val date: String,           // ISO yyyy-MM-dd
    @ColumnInfo(name = "meal_type") val mealType: MealType,
    @ColumnInfo(name = "checked_at_epoch") val checkedAtEpoch: Long,
)
