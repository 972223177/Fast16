package com.ly.fast16.feature.record.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.repository.CheckInRepository
import com.ly.fast16.domain.repository.PlanRepository
import com.ly.fast16.domain.stats.StreakCalculator
import com.ly.fast16.domain.usecase.CheckInUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** 记录页 ViewModel：订阅当月打卡流 → 统计 + 月历 + 补打卡 + 计划删除 */
class RecordViewModel(
    private val checkInRepository: CheckInRepository,
    private val checkInUseCase: CheckInUseCase,
    private val planRepository: PlanRepository,
    private val planScheduler: PlanScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val monthFlow = MutableStateFlow(YearMonth.from(SystemTimeProvider.today()))
    private val selectedFlow = MutableStateFlow<LocalDate?>(null)

    private val _uiState = MutableStateFlow<RecordUiState>(RecordUiState.Loading)
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    /** 删除计划成功轻提示 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            val today = SystemTimeProvider.today()
            // 统计流预合并（combine 单次最多 5 源）：
            // 跨月滚动窗口（今起 366 天）= 连续打卡/完成率；近 7 天区间（锚定真实 today）= 柱状图
            val statsFlow = combine(
                checkInRepository.watchCompletedDays(today.minusDays(366), today),
                checkInRepository.watchRange(today.minusDays(6), today),
            ) { completed, week -> completed to week }
            combine(
                monthFlow.flatMapLatest { checkInRepository.watchMonth(it) },
                statsFlow,
                monthFlow,
                selectedFlow,
                selectedFlow.flatMapLatest { date ->
                    if (date == null) flowOf(null) else planRepository.watchPlanByDate(date)
                },
            ) { checked, (completedDays, weekChecked), month, selected, planPair ->
                buildContent(checked, completedDays, weekChecked, month, selected, planPair)
            }.collect { _uiState.value = it }
        }
    }

    fun onIntent(intent: RecordIntent) {
        when (intent) {
            is RecordIntent.SwitchMonth -> monthFlow.update { it.plusMonths(intent.delta.toLong()) }
            is RecordIntent.TapDate -> selectedFlow.value = intent.date
            is RecordIntent.BackfillCheckIn -> viewModelScope.launch {
                val date = selectedFlow.value ?: return@launch
                // 已打卡 → 二次点击撤销；未打卡 → 补打卡
                val already = (_uiState.value as? RecordUiState.Content)
                    ?.selectedChecked?.contains(intent.mealType) == true
                if (already) {
                    checkInUseCase.uncheckIn(date, intent.mealType)
                } else {
                    checkInUseCase.checkIn(date, intent.mealType, clock.instant())
                }
            }

            RecordIntent.DeletePlan -> viewModelScope.launch {
                val date = selectedFlow.value ?: return@launch
                val plan = planRepository.watchPlanByDate(date).first()?.first ?: return@launch
                // 删除计划 + 撤销全部排程闹钟（打卡记录独立保留，与计划解耦）
                planRepository.deletePlan(plan.id)
                planScheduler.cancel(plan.id)
                selectedFlow.value = null
                _toast.value = "已删除当日计划，提醒已撤销"
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    /** 纯派生：连续天数 / 本月/周完成率 / 近 7 天柱状图 / 选中日打卡集 + 计划存在性 */
    private fun buildContent(
        checked: Map<LocalDate, Set<MealType>>,
        completedDays: Set<LocalDate>,
        weekChecked: Map<LocalDate, Set<MealType>>,
        month: YearMonth,
        selected: LocalDate?,
        planPair: Pair<MealPlan, List<Meal>>?,
    ): RecordUiState.Content {
        val today = SystemTimeProvider.today()
        val streak = StreakCalculator.computeStreak(completedDays, today)
        val monthRate = StreakCalculator.monthCompletion(completedDays, month)
        // 本周（周一起始，含今天）
        val weekStart = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val week = (0L..6L).map { weekStart.plusDays(it) }
        val weekRate = StreakCalculator.weekCompletion(completedDays, week)
        // 近 7 天柱状图：每天完成餐数（0..3），基于真实 today 区间流（不受显示月份影响）
        val weekBars = (6 downTo 0).map { back ->
            weekChecked[today.minusDays(back.toLong())]?.size ?: 0
        }
        return RecordUiState.Content(
            month = month,
            selected = selected,
            checked = checked,
            streak = streak,
            monthRate = monthRate,
            weekRate = weekRate,
            weekBars = weekBars,
            selectedChecked = selected?.let { checked[it].orEmpty() } ?: emptySet(),
            hasPlanOnSelected = planPair != null,
            selectedPlanId = planPair?.first?.id ?: -1L,
            today = today,
        )
    }
}
