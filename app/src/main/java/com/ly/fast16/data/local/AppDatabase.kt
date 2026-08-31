package com.ly.fast16.data.local

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * Room 数据库（完整定义 §1.6）：
 * 3 实体 + 3 DAO；schemaVersion 从 1 起，schema JSON 导出到 app/schemas 作迁移测试基准。
 *
 * Room 3.0（Kotlin-first / KMP 架构）：Android 单平台模块无需 @ConstructedBy + expect
 * （那是 KMP 多平台声明的专属路径），KSP 直接生成 *Dao_Impl / AppDatabase_Impl；
 * 构建走 SQLiteDriver（见 core/di/AppModules）。
 */
@Database(
    entities = [MealPlanEntity::class, MealEntity::class, CheckInEntity::class],
    version = 1,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mealPlanDao(): MealPlanDao
    abstract fun mealDao(): MealDao
    abstract fun checkInDao(): CheckInDao

    companion object {
        const val NAME = "fast16.db"
    }
}
