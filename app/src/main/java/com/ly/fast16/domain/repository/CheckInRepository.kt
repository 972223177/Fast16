package com.ly.fast16.domain.repository

import com.ly.fast16.domain.model.MealType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 打卡记录仓储接口（打卡三入口统一汇流：通知动作 / App 内 / Widget）。
 * 定义在 domain 层（依赖纪律：domain ← data，data 提供实现）。
 */
interface CheckInRepository {

    /** 月历打卡流：date → 当日已打卡餐次集合 */
    fun watchMonth(month: YearMonth): Flow<Map<LocalDate, Set<MealType>>>

    /** 打卡（UPSERT 幂等，重复打卡覆盖时间戳） */
    suspend fun checkIn(date: LocalDate, mealType: MealType, at: Instant)

    /** 撤销打卡（可选能力） */
    suspend fun uncheckIn(date: LocalDate, mealType: MealType)

    /** 区间内「完成日」集合（当日三餐均打卡），供 domain/stats 消费 */
    suspend fun completedDays(from: LocalDate, to: LocalDate): Set<LocalDate>
}
