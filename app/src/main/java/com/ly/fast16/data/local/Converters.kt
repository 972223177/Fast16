package com.ly.fast16.data.local

import androidx.room3.ColumnTypeConverter
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import com.ly.fast16.domain.model.ReminderMode

/**
 * 枚举列统一存 Int（完整定义 §1.6 Room 工程约定）。
 * Room 3.0 注解更名为 [ColumnTypeConverter]（迁移自 androidx.room2 @TypeConverter）。
 * 枚举 Int 值对齐 完整定义 §1.0，定义在 domain/model。
 */
class Converters {

    @ColumnTypeConverter
    fun mealTypeToInt(value: MealType): Int = value.value

    @ColumnTypeConverter
    fun intToMealType(value: Int): MealType = MealType.from(value)

    @ColumnTypeConverter
    fun planStatusToInt(value: PlanStatus): Int = value.value

    @ColumnTypeConverter
    fun intToPlanStatus(value: Int): PlanStatus = PlanStatus.from(value)

    @ColumnTypeConverter
    fun mealStatusToInt(value: MealStatus): Int = value.value

    @ColumnTypeConverter
    fun intToMealStatus(value: Int): MealStatus = MealStatus.from(value)

    @ColumnTypeConverter
    fun reminderModeToInt(value: ReminderMode): Int = value.value

    @ColumnTypeConverter
    fun intToReminderMode(value: Int): ReminderMode = ReminderMode.from(value)
}
