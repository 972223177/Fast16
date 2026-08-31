package com.ly.fast16.data.repository

import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.CheckInDao
import com.ly.fast16.data.local.CheckInEntity
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Room 本地实现（domain/repository.CheckInRepository）。
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
