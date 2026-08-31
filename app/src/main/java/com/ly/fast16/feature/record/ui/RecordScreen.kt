package com.ly.fast16.feature.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCalendar
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelProgressBar
import com.ly.fast16.core.designsystem.component.PixelStatBars
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.repository.CheckInRepository
import com.ly.fast16.domain.stats.StreakCalculator
import com.ly.fast16.domain.usecase.CheckInUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

// ---------- Intent ----------

/** 记录页用户意图（切月 / 点日期 / 补打卡） */
sealed interface RecordIntent {
    data class SwitchMonth(val delta: Int) : RecordIntent
    data class TapDate(val date: LocalDate?) : RecordIntent
    data class BackfillCheckIn(val mealType: MealType) : RecordIntent
}

// ---------- State ----------

/** 记录页 UI 状态：统计概览 + 月历 */
sealed interface RecordUiState {
    data object Loading : RecordUiState

    data class Content(
        val month: YearMonth,
        val selected: LocalDate?,
        /** date → 当日已打卡餐次集合（驱动日历三点与详情） */
        val checked: Map<LocalDate, Set<MealType>> = emptyMap(),
        /** 连续打卡天数 */
        val streak: Int = 0,
        /** 本月完成率 0..1 */
        val monthRate: Float = 0f,
        /** 本周完成率 0..1 */
        val weekRate: Float = 0f,
        /** 近 7 天每日完成餐数（旧 → 新，柱状图） */
        val weekBars: List<Int> = emptyList(),
        /** 选中日已打卡集合（详情补打卡） */
        val selectedChecked: Set<MealType> = emptySet(),
        val today: LocalDate,
    ) : RecordUiState
}

// ---------- ViewModel ----------

/** 记录页 ViewModel：订阅当月打卡流 → 统计 + 月历 + 补打卡 */
class RecordViewModel(
    private val checkInRepository: CheckInRepository,
    private val checkInUseCase: CheckInUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val monthFlow = MutableStateFlow(YearMonth.from(SystemTimeProvider.today()))
    private val selectedFlow = MutableStateFlow<LocalDate?>(null)

    private val _uiState = MutableStateFlow<RecordUiState>(RecordUiState.Loading)
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                monthFlow.flatMapLatest { checkInRepository.watchMonth(it) },
                monthFlow,
                selectedFlow,
            ) { checked, month, selected ->
                buildContent(checked, month, selected)
            }.collect { _uiState.value = it }
        }
    }

    fun onIntent(intent: RecordIntent) {
        when (intent) {
            is RecordIntent.SwitchMonth -> monthFlow.update { it.plusMonths(intent.delta.toLong()) }
            is RecordIntent.TapDate -> selectedFlow.value = intent.date
            is RecordIntent.BackfillCheckIn -> viewModelScope.launch {
                val date = selectedFlow.value ?: return@launch
                checkInUseCase.checkIn(date, intent.mealType, clock.instant())
            }
        }
    }

    /** 纯派生：连续天数 / 本月/周完成率 / 近 7 天柱状图 / 选中日打卡集 */
    private fun buildContent(
        checked: Map<LocalDate, Set<MealType>>,
        month: YearMonth,
        selected: LocalDate?,
    ): RecordUiState.Content {
        val today = SystemTimeProvider.today()
        val completedDays = checked.filterValues { it.size >= 3 }.keys
        val streak = StreakCalculator.computeStreak(completedDays, today)
        val monthRate = StreakCalculator.monthCompletion(completedDays, month)
        // 本周（周一起始，含今天）
        val weekStart = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val week = (0L..6L).map { weekStart.plusDays(it) }
        val weekRate = StreakCalculator.weekCompletion(completedDays, week)
        // 近 7 天柱状图：每天完成餐数（0..3）
        val weekBars = (6 downTo 0).map { back ->
            checked[today.minusDays(back.toLong())]?.size ?: 0
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
            today = today,
        )
    }
}

// ---------- Screen ----------

@Composable
fun RecordScreen(modifier: Modifier = Modifier) {
    val vm: RecordViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()

    when (val s = state) {
        RecordUiState.Loading -> Unit
        is RecordUiState.Content -> RecordContent(state = s, onIntent = vm::onIntent, modifier = modifier)
    }
}

@Composable
private fun RecordContent(
    state: RecordUiState.Content,
    onIntent: (RecordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.Start,
    ) {
        PixelText(text = "RECORD", color = PixelColors.white, fontSize = PixelType.Size.xs, digital = true)
        Spacer(modifier = Modifier.height(2.dp))
        PixelText(text = "统计概览 · 日历打卡（点日期可补打卡）", color = PixelColors.gray, fontSize = PixelType.Size.xs)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(PixelColors.panel.copy(alpha = 0.6f)),
        )
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 统计概览（原型 stats-panel：连续打卡大数字 + 完成率 seg + 近 7 天柱状图）
        PixelCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.width(96.dp)) {
                        PixelText(text = "连续打卡", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                        // spec 2.2：连续打卡数字 40px
                        PixelText(
                            text = "${state.streak}",
                            color = PixelColors.green,
                            fontSize = 40.sp,
                            digital = true,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatLine(label = "本月完成率", rate = state.monthRate, segments = 16)
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                        StatLine(label = "本周完成率", rate = state.weekRate, segments = 12)
                    }
                }

                Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

                PixelText(text = "近 7 天完成", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelStatBars(values = state.weekBars)
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 月历（原型 cal-grid）
        PixelCalendar(
            month = state.month,
            checked = state.checked,
            selected = state.selected,
            today = state.today,
            onMonthChange = { onIntent(RecordIntent.SwitchMonth(if (it > state.month) 1 else -1)) },
            onSelectDay = { onIntent(RecordIntent.TapDate(it)) },
        )

        // 选中日详情（原型 cal-detail：三餐补打卡）
        state.selected?.let { selectedDate ->
            Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelText(text = selectedDate.toString(), color = PixelColors.white, fontSize = PixelType.Size.xs, digital = true)
                        Box(
                            modifier = Modifier
                                .background(PixelColors.panel)
                                .border(BorderStroke(PixelShape.borderWidth, Color.Black))
                                .clickable { onIntent(RecordIntent.TapDate(null)) }
                                .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.xs),
                        ) {
                            PixelText(text = "关闭", color = PixelColors.white, fontSize = PixelType.Size.xs)
                        }
                    }
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                    MealType.entries.forEach { type ->
                        val done = type in state.selectedChecked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = PixelShape.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PixelText(
                                text = mealName(type),
                                color = PixelColors.white,
                                fontSize = PixelType.Size.xs,
                                modifier = Modifier.weight(1f),
                            )
                            PixelButton(
                                text = if (done) "已打卡" else "补打卡",
                                onClick = { onIntent(RecordIntent.BackfillCheckIn(type)) },
                                primary = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 完成率行：label + 百分比 + seg（本月 16 格 / 本周 12 格，原型 stat-lbl） */
@Composable
private fun StatLine(label: String, rate: Float, segments: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PixelText(text = label, color = PixelColors.gray, fontSize = PixelType.Size.xs)
            PixelText(
                text = "${(rate * 100).toInt()}%",
                color = PixelColors.gray,
                fontSize = PixelType.Size.xs,
                digital = true,
            )
        }
        Spacer(modifier = Modifier.height(PixelShape.Spacing.xs))
        PixelProgressBar(progress = rate, segments = segments, litColor = PixelColors.green)
    }
}

private fun mealName(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
}

@PreviewPixel
@Composable
private fun RecordScreenPreview() {
    PixelTheme {
        RecordContent(
            state = RecordUiState.Content(
                month = YearMonth.of(2026, 8),
                selected = null,
                checked = mapOf(
                    LocalDate.of(2026, 8, 30) to setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
                    LocalDate.of(2026, 8, 29) to setOf(MealType.BREAKFAST, MealType.LUNCH),
                    LocalDate.of(2026, 8, 28) to setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
                ),
                streak = 12,
                monthRate = 0.83f,
                weekRate = 1f,
                weekBars = listOf(3, 2, 3, 1, 3, 3, 3),
                selectedChecked = emptySet(),
                today = LocalDate.of(2026, 8, 31),
            ),
            onIntent = {},
        )
    }
}
