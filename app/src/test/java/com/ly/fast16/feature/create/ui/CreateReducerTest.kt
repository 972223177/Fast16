package com.ly.fast16.feature.create.ui

import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.AppSettings
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import com.ly.fast16.domain.model.ReminderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CreateReducerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 8, 31)

    private val settings = AppSettings(
        windowHours = 8,
        minMealGapMinutes = 180,
        dinnerBufferMinutes = 30,
        defaultPrepLunchMinutes = 30,
        defaultPrepDinnerMinutes = 45,
        defaultPrepBreakfastMinutes = 0,
        defaultReminderMode = ReminderMode.NOTIFY,
        widgetShowFasting = true,
        onboardingSeen = true,
    )

    private fun initial(breakfast: LocalTime = LocalTime.of(8, 0)) =
        CreateReducer.initial(settings, today, zone, breakfast)

    @Test
    fun initial_generatesThreeCandidates() {
        val s = initial()
        assertEquals(listOf("均衡", "午餐偏早", "晚餐偏晚"), s.candidates.map { it.label })
    }

    @Test
    fun defaultBreakfast_clampsOutOfRange() {
        // 晚上进 Create 默认早餐不应是当前时间（候选跨午夜 → 流程死）
        assertEquals(LocalTime.of(8, 0), CreateReducer.defaultBreakfast(LocalTime.of(20, 0)))
        assertEquals(LocalTime.of(8, 0), CreateReducer.defaultBreakfast(LocalTime.of(0, 30)))
        // 合理范围内保持当前时间
        assertEquals(LocalTime.of(8, 0), CreateReducer.defaultBreakfast(LocalTime.of(8, 0)))
        assertEquals(LocalTime.of(4, 0), CreateReducer.defaultBreakfast(LocalTime.of(4, 0)))
        assertEquals(LocalTime.of(14, 0), CreateReducer.defaultBreakfast(LocalTime.of(14, 0)))
    }

    @Test
    fun clampedBreakfast_noErrors_andShiftDateKeepsValid() {
        // 回归：clamp 后（08:00）进 Create，默认候选合法；选明天日期仍合法，流程可走
        val s1 = CreateReducer.initial(settings, today, zone, CreateReducer.defaultBreakfast(LocalTime.of(20, 0)))
        assertTrue("clamp 后默认候选不应有错误，实际=${s1.errors}", s1.errors.isEmpty())

        val s2 = CreateReducer.reduce(s1, CreateIntent.ShiftDate(1)) as CreateUiState.Content
        assertEquals(today.plusDays(1), s2.date)
        assertTrue("明天日期候选不应有错误，实际=${s2.errors}", s2.errors.isEmpty())

        val s3 = CreateReducer.reduce(s2, CreateIntent.NextStep) as CreateUiState.Content
        assertTrue(s3.step == 2 && s3.errors.isEmpty())
    }

    @Test
    fun breakfastStepper_respectsRange() {
        // 早餐范围 [04:00, 14:00]：边界上不可再增减（防止候选跨午夜）
        val min = CreateReducer.initial(settings, today, zone, CreateReducer.BREAKFAST_MIN)
        val atMin = CreateReducer.reduce(min, CreateIntent.PickBreakfast(CreateReducer.BREAKFAST_MIN.minusMinutes(5))) as CreateUiState.Content
        assertEquals(CreateReducer.BREAKFAST_MIN, atMin.breakfastTime)
        val max = CreateReducer.initial(settings, today, zone, CreateReducer.BREAKFAST_MAX)
        val atMax = CreateReducer.reduce(max, CreateIntent.PickBreakfast(CreateReducer.BREAKFAST_MAX.plusMinutes(5))) as CreateUiState.Content
        assertEquals(CreateReducer.BREAKFAST_MAX, atMax.breakfastTime)
    }

    @Test
    fun pickBreakfast_regenCandidatesAndResetSelection() {
        val s1 = initial(LocalTime.of(8, 0))
        val s2 = CreateReducer.reduce(s1, CreateIntent.PickBreakfast(LocalTime.of(9, 0))) as CreateUiState.Content
        // 早餐变化后候选重生成，时间后移
        assertTrue(s2.candidates.first().lunch > s1.candidates.first().lunch)
    }

    @Test
    fun selectCandidate_syncsLunchAndDinnerTimes() {
        val s1 = initial()
        val s2 = CreateReducer.reduce(s1, CreateIntent.SelectCandidate(2)) as CreateUiState.Content
        val sel = s2.candidates[2]
        assertEquals(Time.timeOf(sel.lunch, zone), s2.lunchTime)
        assertEquals(Time.timeOf(sel.dinner, zone), s2.dinnerTime)
    }

    @Test
    fun adjustLunchTooEarly_reportsError() {
        val s1 = initial()
        val s2 = CreateReducer.reduce(s1, CreateIntent.AdjustLunchTime(LocalTime.of(8, 30))) as CreateUiState.Content
        assertFalse("早餐 8:00 + 间隔 180min → 午餐 8:30 应报错", s2.errors.isEmpty())
    }

    @Test
    fun adjustLunchValid_clearsErrors() {
        val s1 = initial()
        val s2 = CreateReducer.reduce(s1, CreateIntent.AdjustLunchTime(LocalTime.of(12, 0))) as CreateUiState.Content
        assertTrue(s2.errors.isEmpty())
    }

    @Test
    fun nextAndPrevStep_togglesStep() {
        val s1 = initial()
        val s2 = CreateReducer.reduce(s1, CreateIntent.NextStep) as CreateUiState.Content
        assertEquals(2, s2.step)
        val s3 = CreateReducer.reduce(s2, CreateIntent.PrevStep) as CreateUiState.Content
        assertEquals(1, s3.step)
    }

    @Test
    fun generate_keepsStateForViewModelSideEffect() {
        val s1 = initial()
        val s2 = CreateReducer.reduce(s1, CreateIntent.Generate)
        assertEquals(s1, s2)
    }

    @Test
    fun shiftDate_forward_movesDateAndRegen() {
        val s1 = initial()
        val s2 = CreateReducer.reduce(s1, CreateIntent.ShiftDate(1)) as CreateUiState.Content
        assertEquals(today.plusDays(1), s2.date)
        // 日期后移 → 候选同步重生成（早餐时刻落在新日期）
        assertTrue(s2.candidates.isNotEmpty())
    }

    @Test
    fun shiftDate_backward_blockedForNewPlan() {
        // 新建模式不可选过去日期：今天减 1 天应被拒绝（date 不变）
        val s1 = initial()
        val s2 = CreateReducer.reduce(s1, CreateIntent.ShiftDate(-1)) as CreateUiState.Content
        assertEquals(today, s2.date)
    }

    @Test
    fun shiftDate_backward_allowedForEdit() {
        // 编辑模式允许原计划日期（可早于今天）
        val s1 = CreateReducer.editInitial(settings, existingPlan(), existingMeals(), zone)
        val s2 = CreateReducer.reduce(s1, CreateIntent.ShiftDate(-1)) as CreateUiState.Content
        assertEquals(today.minusDays(1), s2.date)
    }

    // ---------- 编辑模式（CreateRoute.planId ≥ 0） ----------

    private fun existingPlan() = MealPlan(
        id = 7,
        date = today,
        windowStart = Time.at(today, LocalTime.of(8, 0), zone),
        windowEnd = Time.at(today, LocalTime.of(16, 0), zone),
        status = PlanStatus.ACTIVE,
    )

    private fun existingMeals() = listOf(
        Meal(id = 71, planId = 7, type = MealType.BREAKFAST, mealTime = Time.at(today, LocalTime.of(8, 0), zone), prepMinutes = 0),
        Meal(id = 72, planId = 7, type = MealType.LUNCH, mealTime = Time.at(today, LocalTime.of(12, 0), zone), prepMinutes = 30),
        Meal(id = 73, planId = 7, type = MealType.DINNER, mealTime = Time.at(today, LocalTime.of(15, 30), zone), prepMinutes = 45),
    )

    @Test
    fun editInitial_prefillsFromExistingPlan() {
        val s = CreateReducer.editInitial(settings, existingPlan(), existingMeals(), zone)
        assertTrue(s.isEdit)
        assertEquals(LocalTime.of(8, 0), s.breakfastTime)
        assertEquals(LocalTime.of(12, 0), s.lunchTime)
        assertEquals(LocalTime.of(15, 30), s.dinnerTime)
        assertEquals(30, s.prepLunch)
        assertEquals(45, s.prepDinner)
        // 预填自现有合法计划 → 约束校验应通过
        assertTrue(s.errors.isEmpty())
    }

    @Test
    fun editInitial_missingLunchDinner_fallsBackToDefaults() {
        val meals = listOf(
            Meal(id = 71, planId = 7, type = MealType.BREAKFAST, mealTime = Time.at(today, LocalTime.of(8, 0), zone), prepMinutes = 0),
        )
        val s = CreateReducer.editInitial(settings, existingPlan(), meals, zone)
        // 无午/晚餐 → 回退默认（与 initial 一致）
        assertEquals(s.lunchTime, CreateReducer.initial(settings, today, zone, LocalTime.of(8, 0)).lunchTime)
        assertEquals(settings.defaultPrepLunchMinutes, s.prepLunch)
    }
}
