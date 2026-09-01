package com.ly.fast16.core.plan

import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.core.time.Time
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AutoPlanGeneratorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val date: LocalDate = LocalDate.of(2026, 8, 31)

    private class FakePlanRepository(
        var plan: Pair<MealPlan, List<Meal>>? = null,
    ) : PlanRepository {
        var savedPlan: MealPlan? = null
        var savedMeals: List<Meal>? = null

        override fun watchPlanByDate(date: LocalDate): Flow<Pair<MealPlan, List<Meal>>?> = flowOf(plan)
        override fun watchMealsByDate(date: LocalDate): Flow<List<Meal>> = flowOf(plan?.second ?: emptyList())
        override suspend fun savePlan(plan: MealPlan, meals: List<Meal>): Long {
            savedPlan = plan
            savedMeals = meals
            this.plan = plan.copy(id = 1) to meals
            return 1L
        }

        override suspend fun deletePlan(planId: Long) {}
        override suspend fun updateMealStatus(mealId: Long, status: MealStatus) {}
        override suspend fun updateMealPrep(mealId: Long, prepMinutes: Int) {}
        override suspend fun getMealById(id: Long): Meal? = null
        override suspend fun getPlanById(planId: Long): Pair<MealPlan, List<Meal>>? = null
        override suspend fun getActivePlansFrom(date: LocalDate): List<Pair<MealPlan, List<Meal>>> = emptyList()
    }

    private class FakeScheduler : PlanScheduler {
        var scheduled: Pair<MealPlan, List<Meal>>? = null
        var cancelled: Long? = null

        override fun schedule(plan: MealPlan, meals: List<Meal>) {
            scheduled = plan to meals
        }

        override fun cancel(planId: Long) {
            cancelled = planId
        }

        override suspend fun rescheduleAll() {}
    }

    private fun generator(repo: FakePlanRepository, scheduler: FakeScheduler) =
        AutoPlanGenerator(repo, scheduler)

    private fun now(hour: Int, minute: Int) = Time.at(date, java.time.LocalTime.of(hour, minute), zone)

    @Test
    fun generatesPlan_schedulesAlarms() = runTest {
        val repo = FakePlanRepository()
        val scheduler = FakeScheduler()
        val result = generator(repo, scheduler).generateFromBreakfast(
            now = now(8, 0), zone = zone,
            windowHours = 8, minGapMinutes = 180, bufferEndMinutes = 30,
            prepLunchDefault = 30, prepDinnerDefault = 45, reminderMode = ReminderMode.NOTIFY,
        )

        assertEquals(AutoPlanResult.Created, result)
        val plan = repo.savedPlan
        assertEquals(date, plan?.date)
        assertEquals(now(8, 0), plan?.windowStart)
        // 三餐齐备：早餐 0 备餐，午/晚用默认备餐
        val meals = repo.savedMeals.orEmpty()
        assertEquals(3, meals.size)
        assertEquals(0, meals.first { it.type == MealType.BREAKFAST }.prepMinutes)
        assertEquals(30, meals.first { it.type == MealType.LUNCH }.prepMinutes)
        assertEquals(45, meals.first { it.type == MealType.DINNER }.prepMinutes)
        // 排程已调用（落库后真实 Meal）
        assertTrue(scheduler.scheduled != null)
    }

    @Test
    fun alreadyHasPlan_returnsAlreadyHasPlan() = runTest {
        val existingPlan = MealPlan(
            id = 5, date = date,
            windowStart = now(8, 0), windowEnd = now(16, 0),
            status = PlanStatus.ACTIVE,
        )
        val repo = FakePlanRepository(
            plan = existingPlan to listOf(
                Meal(id = 1, planId = 5, type = MealType.BREAKFAST, mealTime = now(8, 0), prepMinutes = 0),
            ),
        )
        val scheduler = FakeScheduler()
        val result = generator(repo, scheduler).generateFromBreakfast(
            now = now(8, 0), zone = zone,
            windowHours = 8, minGapMinutes = 180, bufferEndMinutes = 30,
            prepLunchDefault = 30, prepDinnerDefault = 45, reminderMode = ReminderMode.NOTIFY,
        )

        assertEquals(AutoPlanResult.AlreadyHasPlan, result)
        assertEquals(null, repo.savedPlan) // 不重复创建
    }

    @Test
    fun tooLate_returnsTooLate() = runTest {
        // 深夜 23:00 打卡早餐：非早餐时段（MealWindow 04–10:59），产品规则拒绝生成——
        // 约束上候选能排布（03:00/06:30 跨午夜）但「大半夜吃饭」不可接受
        val repo = FakePlanRepository()
        val scheduler = FakeScheduler()
        val result = generator(repo, scheduler).generateFromBreakfast(
            now = now(23, 0), zone = zone,
            windowHours = 8, minGapMinutes = 180, bufferEndMinutes = 30,
            prepLunchDefault = 30, prepDinnerDefault = 45, reminderMode = ReminderMode.NOTIFY,
        )

        assertEquals(AutoPlanResult.TooLate, result)
        assertEquals(null, repo.savedPlan)
        assertEquals(null, scheduler.scheduled)
    }

    @Test
    fun lunchTimeBreakfast_returnsTooLate() = runTest {
        // 11:30（午餐时段）打卡早餐：仅记录该餐，不生成完整计划
        val repo = FakePlanRepository()
        val scheduler = FakeScheduler()
        val result = generator(repo, scheduler).generateFromBreakfast(
            now = now(11, 30), zone = zone,
            windowHours = 8, minGapMinutes = 180, bufferEndMinutes = 30,
            prepLunchDefault = 30, prepDinnerDefault = 45, reminderMode = ReminderMode.NOTIFY,
        )

        assertEquals(AutoPlanResult.TooLate, result)
        assertEquals(null, repo.savedPlan)
    }
}
