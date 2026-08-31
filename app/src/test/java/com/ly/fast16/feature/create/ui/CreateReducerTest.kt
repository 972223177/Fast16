package com.ly.fast16.feature.create.ui

import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.AppSettings
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
    fun toggleAutoCheckIn_flipsFlag() {
        val s1 = initial()
        assertTrue(s1.autoCheckIn)
        val s2 = CreateReducer.reduce(s1, CreateIntent.ToggleAutoCheckIn) as CreateUiState.Content
        assertFalse(s2.autoCheckIn)
    }
}
