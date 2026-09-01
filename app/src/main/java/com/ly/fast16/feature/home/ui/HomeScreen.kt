package com.ly.fast16.feature.home.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.character.SpeechCatalog
import com.ly.fast16.core.designsystem.component.PixelBubble
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelCharacter
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.designsystem.component.PixelMealRow
import com.ly.fast16.core.designsystem.component.PixelNumber
import com.ly.fast16.core.designsystem.component.PixelSectionTitle
import com.ly.fast16.core.designsystem.component.PixelProgressBar
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PixelToast
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.time.Time
import com.ly.fast16.domain.model.CharacterState
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPhase
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealStatus
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.repository.CheckInRepository
import com.ly.fast16.domain.repository.PlanRepository
import com.ly.fast16.domain.schedule.CharacterStateDeriver
import com.ly.fast16.domain.schedule.FastingTimer
import com.ly.fast16.domain.schedule.MealStateMachine
import com.ly.fast16.domain.stats.StreakCalculator
import com.ly.fast16.domain.usecase.CheckInUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth

// ---------- Intent ----------

/** 首页用户/系统意图（基建文档 §2.3 HomeScreen） */
sealed interface HomeIntent {
    /** 新建计划 */
    data object NewPlan : HomeIntent

    /** 打卡某餐 */
    data class CheckIn(val mealType: MealType) : HomeIntent

    /** 撤销打卡（已打卡餐二次点击） */
    data class UncheckIn(val mealType: MealType) : HomeIntent
}

// ---------- State ----------

/** 首页 UI 状态：Loading / Content */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val hasPlanToday: Boolean,
        val characterState: CharacterState,
        val bubbleText: String,
        /** 断食剩余秒（null = 无未来动作） */
        val fastingSeconds: Long?,
        val meals: List<MealUi>,
        /** 顶部副标题：「今天 · 早餐 08:00 ｜ 窗口结束 16:00」 */
        val subtitle: String,
        /** 连续打卡天数（当月口径，原型右上 🔥 chip） */
        val streak: Int,
        /** 进食窗口进度 0..1（断食面板 16 格 seg） */
        val windowProgress: Float,
        /** 今日计划 id（-1 = 无计划；编辑入口用） */
        val planId: Long = -1L,
    ) : HomeUiState
}

/** 三餐时间轴行数据（原型 meal-row：名称 / 时刻 / 备餐 / 状态 / 打卡） */
data class MealUi(
    val type: MealType,
    val name: String,
    val timeLabel: String,
    val prepMinutes: Int,
    val checkedIn: Boolean,
    val hasMeal: Boolean,
)

// ---------- ViewModel ----------

/** 首页 ViewModel（FR-7）：订阅 DAO Flow → reconcile → 角色/断食派生 → 打卡联动 */
class HomeViewModel(
    private val planRepository: PlanRepository,
    private val checkInRepository: CheckInRepository,
    private val checkInUseCase: CheckInUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 打卡成功轻提示 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            val today = SystemTimeProvider.today()
            combine(
                planRepository.watchPlanByDate(today),
                planRepository.watchMealsByDate(today),
                checkInRepository.watchMonth(YearMonth.from(today)),
                secondTicker(),
            ) { planPair, meals, monthChecked, _ ->
                val now = clock.instant()
                val checkedSet = monthChecked[today].orEmpty()
                // 当月「完成日」集合（三餐均打卡）→ 连续打卡天数（原型 🔥 chip）
                val completedDays = monthChecked.filterValues { it.size >= 3 }.keys
                val streak = StreakCalculator.computeStreak(completedDays, today)
                derive(now, planPair?.first, meals, checkedSet, streak)
            }.collect { _uiState.value = it }
        }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.NewPlan -> Unit // 导航由 Screen 层处理
            is HomeIntent.CheckIn -> viewModelScope.launch {
                val today = SystemTimeProvider.today()
                checkInUseCase.checkIn(today, intent.mealType, clock.instant())
                _toast.value = "${SpeechCatalog.mealName(intent.mealType)} 已打卡"
            }

            is HomeIntent.UncheckIn -> viewModelScope.launch {
                val today = SystemTimeProvider.today()
                checkInUseCase.uncheckIn(today, intent.mealType)
                _toast.value = "已撤销 ${SpeechCatalog.mealName(intent.mealType)} 打卡"
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    /** 每秒整秒刷新（断食剩余「分:秒」秒级跳字，像素风阶跃） */
    private fun secondTicker() = flow {
        while (true) {
            emit(Unit)
            delay(1000)
        }
    }

    /** 纯派生：reconcile + 角色 + 断食剩余 + 三餐状态 + 窗口进度（无 IO/时间源之外的副作用） */
    private fun derive(
        now: Instant,
        plan: MealPlan?,
        meals: List<Meal>,
        checkedSet: Set<MealType>,
        streak: Int,
    ): HomeUiState.Content {
        val hasPlan = plan != null
        // 前台 reconcile：MealStateMachine.advance 校准滞留状态（派生，不写库）
        val reconciled = meals.map { meal ->
            meal.copy(status = MealStateMachine.advance(meal, now, meal.type in checkedSet))
        }
        val character = CharacterStateDeriver.derive(now = now, plan = plan, meals = reconciled)
        val fasting = FastingTimer.fastingRemaining(now, reconciled)
        val bubble = bubbleText(character, reconciled)
        val mealUis = reconciled.map { meal ->
            val zone = SystemTimeProvider.zone
            val t = Time.timeOf(meal.mealTime, zone)
            MealUi(
                type = meal.type,
                name = SpeechCatalog.mealName(meal.type),
                timeLabel = "%02d:%02d".format(t.hour, t.minute),
                prepMinutes = meal.prepMinutes,
                checkedIn = meal.type in checkedSet,
                hasMeal = true,
            )
        }
        // 顶部副标题：「今天 · 早餐 08:00 ｜ 窗口结束 16:00」（原型 home-date）
        val subtitle = if (hasPlan) {
            val bf = reconciled.firstOrNull { it.type == MealType.BREAKFAST }?.let {
                val t = Time.timeOf(it.mealTime, SystemTimeProvider.zone)
                "%02d:%02d".format(t.hour, t.minute)
            }
            val end = Time.timeOf(plan.windowEnd, SystemTimeProvider.zone)
            "今天 · 早餐 ${bf ?: "--:--"} ｜ 窗口结束 %02d:%02d".format(end.hour, end.minute)
        } else {
            "今天 · 还没有计划"
        }
        // 进食窗口进度（断食面板 seg）：已过窗口 / 总窗口；无计划回退 4/16 格演示值
        val windowProgress = if (hasPlan && plan.windowEnd > plan.windowStart) {
            val total = Duration.between(plan.windowStart, plan.windowEnd).toMillis()
            val done = Duration.between(plan.windowStart, now).toMillis()
            (done.toFloat() / total).coerceIn(0f, 1f)
        } else {
            0.25f
        }
        return HomeUiState.Content(
            hasPlanToday = hasPlan,
            characterState = character,
            bubbleText = bubble,
            fastingSeconds = fasting?.seconds,
            meals = mealUis,
            subtitle = subtitle,
            streak = streak,
            windowProgress = windowProgress,
            planId = plan?.id ?: -1L,
        )
    }

    private fun bubbleText(character: CharacterState, meals: List<Meal>): String = when (character) {
        CharacterState.IDLE -> "来记录早餐，开启今天第一餐吧！"
        CharacterState.PREP -> meals.firstOrNull { it.prepMinutes > 0 }?.let {
            SpeechCatalog.text(MealPhase.PREP, it.type)
        } ?: "备餐时间！做起来"

        CharacterState.EATING -> meals.lastOrNull()?.let {
            SpeechCatalog.text(MealPhase.EATING, it.type)
        } ?: "开饭啦！慢慢吃"

        CharacterState.REST -> "今天完成啦，干得漂亮！"
        CharacterState.FASTING -> "断食中，再坚持一下就开饭！"
    }
}

// ---------- Screen ----------

/** 首页 Screen（今日计划流 + 打卡 + 断食计时） */
@Composable
fun HomeScreen(
    onNewPlan: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecord: () -> Unit = {},
    onEditPlan: (Long) -> Unit = {},
) {
    val vm: HomeViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    val toastText by vm.toast.collectAsState()
    var showToast by remember { mutableStateOf(false) }

    LaunchedEffect(toastText) {
        if (toastText != null) {
            showToast = true
        }
    }

    when (val s = state) {
        HomeUiState.Loading -> PixelLoading()
        is HomeUiState.Content -> HomeContent(
            state = s,
            onNewPlan = { vm.onIntent(HomeIntent.NewPlan); onNewPlan() },
            onCheckIn = { vm.onIntent(HomeIntent.CheckIn(it)) },
            onUncheckIn = { vm.onIntent(HomeIntent.UncheckIn(it)) },
            onOpenRecord = onOpenRecord,
            onEditPlan = onEditPlan,
            modifier = modifier,
        )
    }

    // 打卡成功轻提示
    if (toastText != null) {
        PixelToast(
            text = toastText.orEmpty(),
            show = showToast,
            onDismiss = { showToast = false; vm.consumeToast() },
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onNewPlan: () -> Unit,
    onCheckIn: (MealType) -> Unit,
    onUncheckIn: (MealType) -> Unit,
    onOpenRecord: () -> Unit,
    onEditPlan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = PixelShape.contentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 头部：TODAY + 副标题 + streak chip（原型 home-head / home-streak）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                PixelText(text = "TODAY", color = PixelColors.white, fontSize = PixelType.Size.xs, digital = true)
                Spacer(modifier = Modifier.height(2.dp))
                PixelText(text = state.subtitle, color = PixelColors.gray, fontSize = PixelType.Size.xs)
            }
            StreakChip(streak = state.streak, onClick = onOpenRecord)
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
        HorizontalRule()
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 角色「小健」+ 气泡（文案同源 SpeechCatalog）
        PixelCharacter(state = state.characterState)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
        PixelBubble(text = state.bubbleText)

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 断食面板：标签 + 大计时（时:分:秒）+ 16 格 seg（原型 fast-panel）
        PixelCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(text = "断食中 · 距下一餐", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                // spec 2.2：断食倒计时 34px
                PixelNumber(
                    value = formatFastingHMS(state.fastingSeconds),
                    color = PixelColors.yellow,
                    fontSize = 34.sp,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelProgressBar(
                    progress = state.windowProgress,
                    segments = 16,
                    litColor = PixelColors.green,
                )
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 分区标题（UI 拥挤优化：信息层级 + 留白）
        PixelSectionTitle(text = "今日三餐")
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 三餐时间轴（原型 meal-row ×3）或空态
        if (state.hasPlanToday) {
            // P2：今日打卡进度小字
            PixelText(
                text = "已打卡 ${state.meals.count { it.checkedIn }}/${state.meals.size}",
                color = PixelColors.gray,
                fontSize = PixelType.Size.xs,
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            state.meals.forEach { meal ->
                PixelMealRow(
                    name = meal.name,
                    timeLabel = meal.timeLabel,
                    prepMinutes = meal.prepMinutes,
                    checkedIn = meal.checkedIn,
                    hasMeal = meal.hasMeal,
                    // 已打卡 → 二次点击撤销；未打卡 → 打卡
                    onCheckIn = {
                        if (meal.checkedIn) onUncheckIn(meal.type) else onCheckIn(meal.type)
                    },
                    modifier = Modifier.padding(vertical = PixelShape.Spacing.sm),
                )
            }

            // 今日计划编辑入口（复用 Create 编辑模式）
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            PixelButton(
                text = "编辑计划",
                onClick = { onEditPlan(state.planId) },
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PixelCard(backgroundColor = PixelColors.panel.copy(alpha = 0.4f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelText(text = "今天还没有就餐计划", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                    PixelText(text = "记录早餐时间，自动安排午/晚", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                    PixelButton(text = "＋ 新建计划", onClick = onNewPlan, primary = true)
                }
            }
        }
    }
    }
}

/** streak chip（原型 home-streak：🔥 N 天，点击跳记录页） */
@Composable
private fun StreakChip(streak: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .clickable(onClick = onClick)
            .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text = "🔥 $streak 天", color = PixelColors.yellow, fontSize = PixelType.Size.xs)
    }
}

/** 分隔线（原型 hr：2px 面板深色） */
@Composable
private fun HorizontalRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(PixelColors.panel.copy(alpha = 0.6f)),
    )
}

/** 断食剩余（时:分:秒，原型 03:20:12 大计时） */
private fun formatFastingHMS(seconds: Long?): String {
    if (seconds == null) return "--:--:--"
    val total = seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
}

@PreviewPixel
@Composable
private fun HomeScreenPreview() {
    PixelTheme {
        HomeContent(
            state = HomeUiState.Content(
                hasPlanToday = true,
                characterState = CharacterState.FASTING,
                bubbleText = "断食中，再坚持一下就开饭！",
                fastingSeconds = 3 * 3600 + 20 * 60 + 12,
                meals = listOf(
                    MealUi(MealType.BREAKFAST, "早餐", "08:00", prepMinutes = 0, checkedIn = true, hasMeal = true),
                    MealUi(MealType.LUNCH, "午餐", "12:07", prepMinutes = 30, checkedIn = false, hasMeal = true),
                    MealUi(MealType.DINNER, "晚餐", "15:30", prepMinutes = 45, checkedIn = false, hasMeal = true),
                ),
                subtitle = "今天 · 早餐 08:00 ｜ 窗口结束 16:00",
                streak = 12,
                windowProgress = 0.625f,
            ),
            onNewPlan = {},
            onCheckIn = {},
            onUncheckIn = {},
            onOpenRecord = {},
            onEditPlan = {},
        )
    }
}
