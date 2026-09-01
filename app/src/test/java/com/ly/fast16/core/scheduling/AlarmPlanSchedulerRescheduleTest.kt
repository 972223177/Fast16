package com.ly.fast16.core.scheduling

import android.app.AlarmManager
import com.ly.fast16.core.system.ExactAlarmGate
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmPlanSchedulerRescheduleTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 9, 1)

    private class FakePlanRepository(
        private val plans: List<Pair<MealPlan, List<Meal>>>,
    ) : PlanRepository {
        override fun watchPlanByDate(date: LocalDate): Flow<Pair<MealPlan, List<Meal>>?> =
            flowOf(plans.firstOrNull { it.first.date == date })
        override fun watchMealsByDate(date: LocalDate): Flow<List<Meal>> =
            flowOf(plans.firstOrNull { it.first.date == date }?.second ?: emptyList())
        override suspend fun savePlan(plan: MealPlan, meals: List<Meal>): Long = plan.id
        override suspend fun deletePlan(planId: Long) {}
        override suspend fun updateMealStatus(mealId: Long, status: MealStatus) {}
        override suspend fun getMealById(id: Long): Meal? =
            plans.flatMap { it.second }.firstOrNull { it.id == id }
        override suspend fun getPlanById(planId: Long): Pair<MealPlan, List<Meal>>? =
            plans.firstOrNull { it.first.id == planId }
        override suspend fun getActivePlansFrom(date: LocalDate): List<Pair<MealPlan, List<Meal>>> =
            plans.filter { !it.first.date.isBefore(date) }
    }

    private fun plan(id: Long, date: LocalDate) = MealPlan(
        id = id,
        date = date,
        windowStart = Time.at(date, LocalTime.of(8, 0), zone),
        windowEnd = Time.at(date, LocalTime.of(16, 0), zone),
        status = PlanStatus.ACTIVE,
    )

    private fun meal(id: Long, planId: Long, date: LocalDate, type: MealType, prep: Int) = Meal(
        id = id,
        planId = planId,
        type = type,
        mealTime = Time.at(date, LocalTime.of(8 + type.ordinal * 4, 0), zone),
        prepMinutes = prep,
        reminderMode = ReminderMode.NOTIFY,
    )

    /** 每计划 3 餐：早餐(prep=0→仅 EATING) + 午/晚(prep=30→PREP+EATING) = 5 个闹钟 */
    private fun mealsFor(planId: Long, date: LocalDate): List<Meal> = listOf(
        meal(planId * 10 + 1, planId, date, MealType.BREAKFAST, 0),
        meal(planId * 10 + 2, planId, date, MealType.LUNCH, 30),
        meal(planId * 10 + 3, planId, date, MealType.DINNER, 30),
    )

    @Test
    fun rescheduleAll_schedulesAllActivePlans() {
        val todayPlans = plan(1, today) to mealsFor(1, today)
        val tomorrowPlans = plan(2, today.plusDays(1)) to mealsFor(2, today.plusDays(1))
        val scheduler = AlarmPlanScheduler(
            context = RuntimeEnvironment.getApplication(),
            exactAlarmGate = ExactAlarmGate(RuntimeEnvironment.getApplication()),
            planRepository = FakePlanRepository(listOf(todayPlans, tomorrowPlans)),
        )

        runBlocking { scheduler.rescheduleAll() }

        val alarmManager =
            RuntimeEnvironment.getApplication().getSystemService(AlarmManager::class.java)
        // 2 计划 × 5 闹钟（早1+午2+晚2）
        assertEquals(10, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun rescheduleAll_noActivePlans_noAlarms() {
        val scheduler = AlarmPlanScheduler(
            context = RuntimeEnvironment.getApplication(),
            exactAlarmGate = ExactAlarmGate(RuntimeEnvironment.getApplication()),
            planRepository = FakePlanRepository(emptyList()),
        )

        runBlocking { scheduler.rescheduleAll() }

        val alarmManager =
            RuntimeEnvironment.getApplication().getSystemService(AlarmManager::class.java)
        assertEquals(0, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun rescheduleAll_pastPlan_ignored() {
        // 昨日计划不属于「今日及未来」，不应重排
        val past = plan(9, today.minusDays(1)) to mealsFor(9, today.minusDays(1))
        val scheduler = AlarmPlanScheduler(
            context = RuntimeEnvironment.getApplication(),
            exactAlarmGate = ExactAlarmGate(RuntimeEnvironment.getApplication()),
            planRepository = FakePlanRepository(listOf(past)),
        )

        runBlocking { scheduler.rescheduleAll() }

        val alarmManager =
            RuntimeEnvironment.getApplication().getSystemService(AlarmManager::class.java)
        assertEquals(0, shadowOf(alarmManager).getScheduledAlarms().size)
    }
}
