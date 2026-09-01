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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCalendar
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelDialog
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.designsystem.component.PixelPageTitle
import com.ly.fast16.core.designsystem.component.PixelProgressBar
import com.ly.fast16.core.designsystem.component.PixelSectionTitle
import com.ly.fast16.core.designsystem.component.PixelStatBars
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PixelToast
import com.ly.fast16.core.designsystem.component.PixelVerticalScrollbar
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.core.widget.Fast16Widget
import androidx.glance.appwidget.updateAll
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
import org.koin.androidx.compose.koinViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

// ---------- Intent ----------

/** 记录页用户意图（切月 / 点日期 / 补打卡 / 删除计划） */
sealed interface RecordIntent {
    data class SwitchMonth(val delta: Int) : RecordIntent
    data class TapDate(val date: LocalDate?) : RecordIntent
    data class BackfillCheckIn(val mealType: MealType) : RecordIntent
    data object DeletePlan : RecordIntent
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
        /** 选中日是否有就餐计划（详情显示编辑/删除入口） */
        val hasPlanOnSelected: Boolean = false,
        /** 选中日计划 id（编辑 / 删除用；无计划为 -1） */
        val selectedPlanId: Long = -1L,
        val today: LocalDate,
    ) : RecordUiState
}

// ---------- ViewModel ----------

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

// ---------- Screen ----------

@Composable
fun RecordScreen(
    modifier: Modifier = Modifier,
    onEditPlan: (Long) -> Unit = {},
) {
    val vm: RecordViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    val toastText by vm.toast.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showToast by remember { mutableStateOf(false) }

    LaunchedEffect(toastText) {
        if (toastText != null) {
            showToast = true
        }
    }

    when (val s = state) {
        RecordUiState.Loading -> PixelLoading()
        is RecordUiState.Content -> RecordContent(
            state = s,
            onIntent = { intent ->
                vm.onIntent(intent)
                // 补打卡 / 删计划 → 桌面小组件同步
                if (intent is RecordIntent.BackfillCheckIn || intent is RecordIntent.DeletePlan) {
                    scope.launch { Fast16Widget().updateAll(context) }
                }
            },
            onEditPlan = onEditPlan,
            modifier = modifier,
        )
    }

    // 删除成功轻提示
    if (toastText != null) {
        PixelToast(
            text = toastText.orEmpty(),
            show = showToast,
            onDismiss = { showToast = false; vm.consumeToast() },
        )
    }
}

@Composable
private fun RecordContent(
    state: RecordUiState.Content,
    onIntent: (RecordIntent) -> Unit,
    onEditPlan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = PixelShape.contentMaxWidth),
    ) {
        // 固定标题栏：不随内容滚动出屏
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PixelShape.Spacing.lg,
                    end = PixelShape.Spacing.lg,
                    top = PixelShape.Spacing.lg,
                ),
            horizontalAlignment = Alignment.Start,
        ) {
            // 页面标题规范：打字机大标题 + 副标题（PixelPageTitle）
            PixelPageTitle(title = "RECORD", subtitle = "统计概览 · 日历打卡")
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(PixelColors.panel.copy(alpha = 0.6f)),
            )
        }

        // 滚动区（标题栏以下）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    state = scrollState,
                    // 与 Home 一致：关掉 overscroll 边缘拉伸，避免「还能滚」的错觉
                    overscrollEffect = null,
                )
                .padding(
                    start = PixelShape.Spacing.lg,
                    end = PixelShape.Spacing.lg,
                    top = PixelShape.Spacing.lg,
                    // 底部与 PixelBottomBar 相邻，无需 xxl 留白
                    bottom = PixelShape.Spacing.lg,
                ),
            horizontalAlignment = Alignment.Start,
        ) {
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 分区标题：统计概览
        PixelSectionTitle(text = "统计概览")
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 统计概览（原型 stats-panel：连续打卡大数字 + 完成率 seg + 近 7 天柱状图）
        PixelCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.width(96.dp)) {
                        PixelText(text = "连续打卡", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                        // 连续打卡大数字（一屏可见优化：spec 2.2 的 40px 下调至 Size.lg = 28px）
                        PixelText(
                            text = "${state.streak}",
                            color = PixelColors.green,
                            fontSize = PixelType.Size.lg,
                            digital = true,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatLine(label = "本月完成率", rate = state.monthRate, segments = 16)
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                        StatLine(label = "本周完成率", rate = state.weekRate, segments = 12)
                    }
                }

                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

                PixelText(text = "近 7 天完成", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelStatBars(values = state.weekBars)
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 分区标题：打卡日历
        PixelSectionTitle(text = "打卡日历")
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 月历（原型 cal-grid）；未来日期禁用（maxSelectable = today → 禁止未来预打卡）
        PixelCalendar(
            month = state.month,
            checked = state.checked,
            selected = state.selected,
            today = state.today,
            maxSelectable = state.today,
            onMonthChange = { onIntent(RecordIntent.SwitchMonth(if (it > state.month) 1 else -1)) },
            onSelectDay = { onIntent(RecordIntent.TapDate(it)) },
        )

        }
        // 像素滚动条：仅在内容真的溢出时出现（统计+日历一屏可见 → 不画无意义的空轨道）
        if (scrollState.maxValue > 0) {
            PixelVerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = PixelShape.Spacing.sm),
            )
        }
        }
    }
    }

    // 选中日补打卡：像素弹窗（一屏可见优化：原为日历下方内嵌面板，占 ~230dp 撑高页面；
    // 改弹窗后不占滚动布局，统计 + 日历一屏放下）。点外部 / 关闭按钮 → 取消选中并收起。
    state.selected?.let { selectedDate ->
        PixelDialog(
            onDismiss = { onIntent(RecordIntent.TapDate(null)) },
            title = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 · 打卡",
        ) {
            Column {
                // 无计划日期的语义说明：删除计划后打卡记录独立保留（check_ins 与计划解耦），
                // 三绿点 = 当日打卡事实，非「计划完成」；「可撤销」= 用户主动纠错（误补卡），
                // 与「系统不自动清」不冲突
                if (!state.hasPlanOnSelected) {
                    PixelText(
                        text = if (state.selectedChecked.isNotEmpty()) {
                            "该日无计划，打卡记录独立保留 · 可撤销"
                        } else {
                            "该日无计划，可直接补打卡"
                        },
                        color = PixelColors.gray,
                        fontSize = PixelType.Size.xs,
                    )
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                }
                MealType.entries.forEachIndexed { index, type ->
                    val done = type in state.selectedChecked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = PixelShape.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelText(
                            text = mealName(type),
                            color = PixelColors.white,
                            fontSize = PixelType.Size.xs,
                            modifier = Modifier.weight(1f),
                        )
                        // 未打卡用蓝底主操作；已打卡灰底描边（与设计稿一致）
                        PixelButton(
                            text = if (done) "已打卡" else "补打卡",
                            onClick = { onIntent(RecordIntent.BackfillCheckIn(type)) },
                            primary = !done,
                        )
                    }
                    // 餐间像素分隔线（最后一行无）
                    if (index != MealType.entries.lastIndex) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.Black),
                        )
                    }
                }

                // 计划管理区（该日有计划时：编辑 / 删除）
                if (state.hasPlanOnSelected) {
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelButton(
                            text = "编辑计划",
                            onClick = { onEditPlan(state.selectedPlanId) },
                            primary = false,
                            modifier = Modifier.weight(1f),
                        )
                        // 删除：红色按钮 → 确认弹窗；与 PixelButton 规格对齐（等高不显扁）
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(PixelColors.red, PixelShape.stair)
                                .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
                                .clickable { showDeleteConfirm = true }
                                .padding(horizontal = PixelShape.Spacing.lg, vertical = PixelShape.Spacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelText(text = "删除计划", color = PixelColors.bg, fontSize = PixelType.Size.sm)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelButton(
                    text = "关闭",
                    onClick = { onIntent(RecordIntent.TapDate(null)) },
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // 删除计划确认（PixelDialog）
    if (showDeleteConfirm) {
        PixelDialog(onDismiss = { showDeleteConfirm = false }, title = "删除今日计划？") {
            Column {
                PixelText(
                    text = "删除后当日提醒将撤销，已打卡记录保留。",
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelButton(
                    text = "确认删除",
                    onClick = {
                        showDeleteConfirm = false
                        onIntent(RecordIntent.DeletePlan)
                    },
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelButton(
                    text = "取消",
                    onClick = { showDeleteConfirm = false },
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
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

/** Preview 假数据（典型态：有统计 + 选中某日） */
private fun recordPreviewState() = RecordUiState.Content(
    month = YearMonth.of(2026, 8),
    selected = LocalDate.of(2026, 8, 30),
    checked = mapOf(
        LocalDate.of(2026, 8, 30) to setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
        LocalDate.of(2026, 8, 29) to setOf(MealType.BREAKFAST, MealType.LUNCH),
        LocalDate.of(2026, 8, 28) to setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
    ),
    streak = 12,
    monthRate = 0.83f,
    weekRate = 1f,
    weekBars = listOf(3, 2, 3, 1, 3, 3, 3),
    selectedChecked = setOf(MealType.BREAKFAST),
    hasPlanOnSelected = true,
    selectedPlanId = 1L,
    today = LocalDate.of(2026, 8, 31),
)

@PreviewPixel
@Composable
private fun RecordScreenPreview() {
    PixelTheme {
        RecordContent(
            state = recordPreviewState(),
            onIntent = {},
            onEditPlan = {},
        )
    }
}

/** 主流屏一屏可见验证：360×800dp + 系统栏/底栏 */
@Preview(
    name = "Record · 主流屏 360x800",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun RecordScreenMainstreamPreview() {
    PixelTheme {
        Scaffold(
            containerColor = PixelColors.bg,
            bottomBar = { Box(modifier = Modifier.height(62.dp)) },
        ) { innerPadding ->
            RecordContent(
                state = recordPreviewState(),
                onIntent = {},
                onEditPlan = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** 无计划但已打卡（删除计划后）：弹窗顶部显示「打卡记录独立保留」说明 */
@Preview(
    name = "Record · 无计划已打卡",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun RecordScreenNoPlanPreview() {
    PixelTheme {
        RecordContent(
            state = recordPreviewState().copy(hasPlanOnSelected = false, selectedPlanId = -1L),
            onIntent = {},
            onEditPlan = {},
        )
    }
}
