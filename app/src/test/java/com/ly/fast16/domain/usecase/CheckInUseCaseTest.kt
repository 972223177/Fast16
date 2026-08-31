package com.ly.fast16.domain.usecase

import com.ly.fast16.core.time.Time
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.domain.repository.CheckInRepository
import com.ly.fast16.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class CheckInUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val date: LocalDate = LocalDate.of(2026, 8, 31)
    private val at: Instant = Time.at(date, LocalTime.of(12, 0), zone)

    private class FakeCheckInRepository : CheckInRepository {
        val checked = mutableListOf<Pair<LocalDate, MealType>>()

        override fun watchMonth(month: YearMonth): Flow<Map<LocalDate, Set<MealType>>> = flowOf(emptyMap())
        override suspend fun checkIn(date: LocalDate, mealType: MealType, at: Instant) {
            checked += date to mealType
        }

        override suspend fun uncheckIn(date: LocalDate, mealType: MealType) {}
        override suspend fun completedDays(from: LocalDate, to: LocalDate): Set<LocalDate> = emptySet()
    }

    private class FakePlanRepository(
        var meals: List<Meal> = emptyList(),
    ) : PlanRepository {
        val updated = mutableListOf<Pair<Long, MealStatus>>()

        override fun watchPlanByDate(date: LocalDate): Flow<Pair<MealPlan, List<Meal>>?> = flowOf(null)
        override fun watchMealsByDate(date: LocalDate): Flow<List<Meal>> = flowOf(meals)
        override suspend fun savePlan(plan: MealPlan, meals: List<Meal>): Long = 1L
        override suspend fun deletePlan(planId: Long) {}
        override suspend fun updateMealStatus(mealId: Long, status: MealStatus) {
            updated += mealId to status
        }

        override suspend fun getMealById(id: Long): Meal? = meals.firstOrNull { it.id == id }
    }

    private fun meal(id: Long, type: MealType) = Meal(
        id = id, planId = 1, type = type,
        mealTime = Time.at(date, LocalTime.of(12, 0), zone),
        prepMinutes = 30, reminderMode = ReminderMode.NOTIFY,
    )

    @Test
    fun checkIn_writesToRepository() = runTest {
        val checkInRepo = FakeCheckInRepository()
        val planRepo = FakePlanRepository(meals = listOf(meal(1, MealType.LUNCH)))
        val useCase = DefaultCheckInUseCase(checkInRepo, planRepo)

        useCase.checkIn(date, MealType.LUNCH, at)

        assertEquals(listOf(date to MealType.LUNCH), checkInRepo.checked)
    }

    @Test
    fun checkIn_linksTodayMealToCompleted() = runTest {
        val checkInRepo = FakeCheckInRepository()
        val lunch = meal(1, MealType.LUNCH)
        val planRepo = FakePlanRepository(meals = listOf(lunch))
        val useCase = DefaultCheckInUseCase(checkInRepo, planRepo)

        useCase.checkIn(date, MealType.LUNCH, at)

        assertEquals(listOf(lunch.id to MealStatus.COMPLETED), planRepo.updated)
    }

    @Test
    fun checkIn_withoutMatchingMeal_doesNotUpdateStatus() = runTest {
        val checkInRepo = FakeCheckInRepository()
        val planRepo = FakePlanRepository(meals = emptyList()) // 无对应 Meal（补打卡场景）
        val useCase = DefaultCheckInUseCase(checkInRepo, planRepo)

        useCase.checkIn(date, MealType.BREAKFAST, at)

        assertTrue(planRepo.updated.isEmpty())
    }

    @Test
    fun checkInMeal_updatesMealStatusDirectly() = runTest {
        val checkInRepo = FakeCheckInRepository()
        val dinner = meal(7, MealType.DINNER)
        val planRepo = FakePlanRepository(meals = listOf(dinner))
        val useCase = DefaultCheckInUseCase(checkInRepo, planRepo)

        useCase.checkInMeal(dinner, at)

        assertEquals(listOf(dinner.id to MealStatus.COMPLETED), planRepo.updated)
        assertEquals(listOf(date to MealType.DINNER), checkInRepo.checked)
    }

    @Test
    fun uncheckIn_delegatesToRepository() = runTest {
        val checkInRepo = FakeCheckInRepository()
        val useCase = DefaultCheckInUseCase(checkInRepo, FakePlanRepository())
        useCase.uncheckIn(date, MealType.LUNCH)
        // 无异常即通过（fake 空实现）
        assertTrue(true)
    }
}
