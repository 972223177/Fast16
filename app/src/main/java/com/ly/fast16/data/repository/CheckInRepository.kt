package com.ly.fast16.data.repository

import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.CheckInDao
import com.ly.fast16.data.local.CheckInEntity
import com.ly.fast16.domain.model.MealType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 打卡记录仓储接口（打卡三入口统一汇流：通知动作 / App 内 / Widget）。
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

/**
 * Room 本地实现。
 */
class LocalCheckInRepository(
    private val checkInDao: CheckInDao,
) : CheckInRepository {

    override fun watchMonth(month: YearMonth): Flow<Map<LocalDate, Set<MealType>>> =
        checkInDao.watchMonth(month.toString()).map { list ->
            list.groupBy(
                keySelector = { Time.parseDate(it.date) },
                valueTransform = { it.mealType },
            ).mapValues { (_, types) -> types.toSet() }
        }

    override suspend fun checkIn(date: LocalDate, mealType: MealType, at: Instant) {
        checkInDao.checkIn(
            CheckInEntity(
                date = Time.formatDate(date),
                mealType = mealType,
                checkedAtEpoch = at.toEpochMilli(),
            ),
        )
    }

    override suspend fun uncheckIn(date: LocalDate, mealType: MealType) {
        checkInDao.uncheckIn(Time.formatDate(date), mealType.value)
    }

    override suspend fun completedDays(from: LocalDate, to: LocalDate): Set<LocalDate> =
        checkInDao.completedDayDates(
            from = Time.formatDate(from),
            to = Time.formatDate(to),
        ).mapTo(mutableSetOf()) { Time.parseDate(it) }
}
