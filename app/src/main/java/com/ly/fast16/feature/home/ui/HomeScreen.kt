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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.character.SpeechCatalog
import com.ly.fast16.core.plan.AutoPlanGenerator
import com.ly.fast16.core.plan.AutoPlanResult
import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.data.local.SettingsStore
import com.ly.fast16.core.designsystem.component.PixelBubble
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelCharacter
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.designsystem.component.PixelMealRow
import com.ly.fast16.core.designsystem.component.PixelConfetti
import com.ly.fast16.core.designsystem.component.PixelConfettiBurst
import com.ly.fast16.core.designsystem.component.PixelDialog
import com.ly.fast16.core.designsystem.component.PixelNumber
import com.ly.fast16.core.designsystem.component.PixelSectionTitle
import com.ly.fast16.core.designsystem.component.PixelStepper
import com.ly.fast16.core.designsystem.component.PixelProgressBar
import com.ly.fast16.core.designsystem.component.PixelPageTitle
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PixelVerticalScrollbar
import com.ly.fast16.core.designsystem.component.PixelToast
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.time.Time
import com.ly.fast16.core.widget.Fast16Widget
import androidx.glance.appwidget.updateAll
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
import com.ly.fast16.domain.schedule.MealWindow
import com.ly.fast16.domain.stats.StreakCalculator
import com.ly.fast16.domain.usecase.CheckInUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import kotlin.time.Duration.Companion.milliseconds

// ---------- Intent ----------

/** 首页用户/系统意图（基建文档 §2.3 HomeScreen） */
sealed interface HomeIntent {
    /** 新建计划 */
    data object NewPlan : HomeIntent

    /** 打卡某餐 */
    data class CheckIn(val mealType: MealType) : HomeIntent

    /** 撤销打卡（已打卡餐二次点击） */
    data class UncheckIn(val mealType: MealType) : HomeIntent

    /** 无计划空态「打卡现在这餐」：按 MealWindow 时段自动判定餐次 */
    data object CheckInNow : HomeIntent

    /** 编辑某餐备餐时长（分钟，0–180；tile 备餐可点编辑） */
    data class EditPrep(val mealType: MealType, val minutes: Int) : HomeIntent
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
        /** 无计划时按 MealWindow 时段判定的当前餐次（空态「打卡现在这餐」用；null = 非用餐时段） */
        val detectedMealType: MealType? = null,
        /** 今日已打卡餐次（无计划空态展示打卡反馈；有计划的餐次在 meals 行内展示） */
        val checkedInToday: Set<MealType> = emptySet(),
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
    private val autoPlanGenerator: AutoPlanGenerator,
    private val scheduler: PlanScheduler,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 打卡成功轻提示 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 成功礼花（打卡：小礼花 / 三餐全打卡：大礼花） */
    private val _confetti = MutableStateFlow<PixelConfettiBurst?>(null)
    val confetti: StateFlow<PixelConfettiBurst?> = _confetti.asStateFlow()

    init {
        viewModelScope.launch {
            val today = SystemTimeProvider.today()
            combine(
                planRepository.watchPlanByDate(today),
                planRepository.watchMealsByDate(today),
                checkInRepository.watchMonth(YearMonth.from(today)),
                // 跨月滚动窗口（今起 366 天）：连续打卡不受月末截断
                checkInRepository.watchCompletedDays(today.minusDays(366), today),
                secondTicker(),
            ) { planPair, meals, monthChecked, completedDays, _ ->
                val now = clock.instant()
                val checkedSet = monthChecked[today].orEmpty()
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
                // 礼花：今日三餐全打卡 → 多彩大礼花；否则日常小礼花
                val checkedCount = checkInRepository
                    .watchMonth(YearMonth.from(today)).first()[today].orEmpty().size
                _confetti.value = if (checkedCount >= MealType.entries.size) {
                    PixelConfettiBurst.ALL_COMPLETED
                } else {
                    PixelConfettiBurst.CHECK_IN
                }
            }

            is HomeIntent.UncheckIn -> viewModelScope.launch {
                val today = SystemTimeProvider.today()
                checkInUseCase.uncheckIn(today, intent.mealType)
                _toast.value = "已撤销 ${SpeechCatalog.mealName(intent.mealType)} 打卡"
            }

            // 无计划「打卡现在这餐」：按时段判定餐次；早餐时段无计划 → 自动生成午/晚
            HomeIntent.CheckInNow -> viewModelScope.launch {
                val now = clock.instant()
                val type = MealWindow.detect(Time.timeOf(now, SystemTimeProvider.zone))
                if (type == null) {
                    _toast.value = "现在不是用餐时段"
                    return@launch
                }
                val today = SystemTimeProvider.today()
                // 该餐今日已打卡 → 仅提示，不重复放礼花（单餐打卡的反馈靠空态「已记录」展示）
                if (type in checkInRepository.watchMonth(YearMonth.from(today)).first()[today].orEmpty()) {
                    _toast.value = "${SpeechCatalog.mealName(type)} 今日已打卡"
                    return@launch
                }
                val generatedToast = if (type == MealType.BREAKFAST) {
                    val settings = settingsStore.settings.first()
                    when (autoPlanGenerator.generateFromBreakfast(
                        now = now,
                        zone = SystemTimeProvider.zone,
                        windowHours = settings.windowHours,
                        minGapMinutes = settings.minMealGapMinutes,
                        bufferEndMinutes = settings.dinnerBufferMinutes,
                        prepLunchDefault = settings.defaultPrepLunchMinutes,
                        prepDinnerDefault = settings.defaultPrepDinnerMinutes,
                        reminderMode = settings.defaultReminderMode,
                    )) {
                        AutoPlanResult.Created -> "已记录早餐，自动安排午餐/晚餐"
                        AutoPlanResult.TooLate -> "已过午/晚餐时段，仅记录早餐"
                        AutoPlanResult.AlreadyHasPlan -> null
                    }
                } else {
                    null
                }
                checkInUseCase.checkIn(today, type, now)
                _toast.value = generatedToast ?: "${SpeechCatalog.mealName(type)} 已打卡"
                // 礼花：今日三餐全打卡 → 多彩大礼花；否则日常小礼花
                val checkedCount = checkInRepository
                    .watchMonth(YearMonth.from(today)).first()[today].orEmpty().size
                _confetti.value = if (checkedCount >= MealType.entries.size) {
                    PixelConfettiBurst.ALL_COMPLETED
                } else {
                    PixelConfettiBurst.CHECK_IN
                }
            }

            // 编辑某餐备餐时长：写库 + 重排该计划闹钟（prepTime 派生变化）
            is HomeIntent.EditPrep -> viewModelScope.launch {
                val today = SystemTimeProvider.today()
                val meal = planRepository.watchMealsByDate(today).first()
                    .firstOrNull { it.type == intent.mealType } ?: return@launch
                planRepository.updateMealPrep(meal.id, intent.minutes)
                // 排程必须用落库后的真实 Meal（含 id）——AlarmReceiver 依 EXTRA_MEAL_ID 查库
                val planPair = planRepository.watchPlanByDate(today).first() ?: return@launch
                scheduler.cancel(planPair.first.id)
                scheduler.schedule(planPair.first, planRepository.watchMealsByDate(today).first())
                _toast.value = "${SpeechCatalog.mealName(intent.mealType)} 备餐 ${intent.minutes} 分钟已保存"
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun consumeConfetti() {
        _confetti.value = null
    }

    /** 每秒整秒刷新（断食剩余「分:秒」秒级跳字，像素风阶跃） */
    private fun secondTicker() = flow {
        while (true) {
            emit(Unit)
            delay(1000.milliseconds)
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
            // 无计划时按时段判定当前餐次（空态「打卡现在这餐」按钮文案/可用性）
            detectedMealType = if (hasPlan) null else MealWindow.detect(Time.timeOf(now, SystemTimeProvider.zone)),
            // 今日已打卡（无计划空态反馈：单餐打卡后必须可见「已记录」，否则用户以为没打上）
            checkedInToday = checkedSet,
        )
    }

    private fun bubbleText(character: CharacterState, meals: List<Meal>): String = when (character) {
        CharacterState.IDLE -> "来记录早餐，开启今天第一餐吧！"
        // 气泡取餐与 CharacterStateDeriver 同源：只取「reconcile 后正处于该状态」的餐。
        // 原 firstOrNull { prepMinutes > 0 } / lastOrNull 不看 status，预打卡后会把已完成的
        // 餐当备餐/开饭对象，导致「面板已完成、气泡还在备餐」联动错乱。
        CharacterState.PREP -> meals.firstOrNull { it.status == MealStatus.PREPARING }?.let {
            SpeechCatalog.text(MealPhase.PREP, it.type)
        } ?: "备餐时间！做起来"

        CharacterState.EATING -> meals.filter { it.status == MealStatus.EATING }
            .maxByOrNull { it.mealTime }?.let {
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
    val confetti by vm.confetti.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
            onCheckIn = {
                vm.onIntent(HomeIntent.CheckIn(it))
                scope.launch { Fast16Widget().updateAll(context) } // 打卡 → 桌面小组件同步
            },
            onUncheckIn = {
                vm.onIntent(HomeIntent.UncheckIn(it))
                scope.launch { Fast16Widget().updateAll(context) } // 撤销 → 桌面小组件同步
            },
            onCheckInNow = {
                vm.onIntent(HomeIntent.CheckInNow)
                scope.launch { Fast16Widget().updateAll(context) } // 打卡/自动生成 → 小组件同步
            },
            onEditPrep = { type, minutes ->
                vm.onIntent(HomeIntent.EditPrep(type, minutes))
                scope.launch { Fast16Widget().updateAll(context) } // 备餐变化 → 小组件同步
            },
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

    // 打卡成功礼花：全屏居中 overlay，播完移除（日常小礼花 / 三餐全打卡大礼花）
    confetti?.let { burst ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            PixelConfetti(
                burst = burst,
                onFinished = vm::consumeConfetti,
                modifier = Modifier.size(160.dp),
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onNewPlan: () -> Unit,
    onCheckIn: (MealType) -> Unit,
    onUncheckIn: (MealType) -> Unit,
    onCheckInNow: () -> Unit,
    onEditPrep: (MealType, Int) -> Unit,
    onOpenRecord: () -> Unit,
    onEditPlan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 备餐编辑弹窗当前目标餐次（null = 未打开）
    var editingPrep by remember { mutableStateOf<MealType?>(null) }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = PixelShape.contentMaxWidth)
            .verticalScroll(
                state = scrollState,
                // overscrollEffect = null 关掉边缘拉伸：① 内容本就一屏可见时，滑动不再产生
                // 「还能往下滚」的错觉，消除滚动交互负担；② 边缘拉伸属 M3 平滑动效，与像素风
                // 阶跃规范冲突。矮屏真需要滚动时滚动本身照常工作，只是没有回弹。
                overscrollEffect = null,
            )
            .padding(
                start = PixelShape.Spacing.lg,
                end = PixelShape.Spacing.lg,
                top = PixelShape.Spacing.lg,
                // 底部与 PixelBottomBar 相邻，底栏自带 padding，此处无需再留 xxl
                bottom = PixelShape.Spacing.lg,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 头部：TODAY + 副标题 + streak chip（原型 home-head / home-streak）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 页面标题规范：打字机大标题 + 副标题（PixelPageTitle）
            PixelPageTitle(title = "TODAY", subtitle = state.subtitle)
            StreakChip(streak = state.streak, onClick = onOpenRecord)
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
        HorizontalRule()
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 角色「小健」+ 气泡（文案同源 SpeechCatalog）
        // 一屏可见优化：纵向堆叠（角色 60 + 间距 8 + 气泡 ~43 ≈ 111dp）改横向并排 → 60dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelCharacter(state = state.characterState)
            PixelBubble(
                text = state.bubbleText,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 断食面板：标签 + 大计时（时:分:秒）+ 16 格 seg（原型 fast-panel）
        // 三态：有倒计时（距下一未完成餐）/ 全部打卡完成（今日完成）/ 时间过完未打卡（窗口结束）
        PixelCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val allChecked = state.meals.isNotEmpty() && state.meals.all { it.checkedIn }
            val (panelLabel, panelValue, panelColor) = when {
                state.fastingSeconds != null ->
                    Triple("断食中 · 距下一餐", formatFastingHMS(state.fastingSeconds), PixelColors.yellow)
                allChecked ->
                    Triple("今日已完成 ✦", "00:00:00", PixelColors.green)
                else ->
                    Triple("今日窗口已结束", "--:--:--", PixelColors.gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(text = panelLabel, color = PixelColors.gray, fontSize = PixelType.Size.xs)
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                // 断食倒计时（一屏可见优化：spec 2.2 的 34px 下调至 Size.lg = 28px，仍是画面最大字号）
                PixelNumber(
                    value = panelValue,
                    color = panelColor,
                    fontSize = PixelType.Size.lg,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelProgressBar(
                    progress = if (allChecked) 1f else state.windowProgress,
                    segments = 16,
                    litColor = PixelColors.green,
                )
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 分区标题（信息层级 + 留白）；一屏可见优化：进度小字与编辑入口并入标题行右侧，
        // 省下「小字 14 + 上间距 8」与「全宽按钮 38 + 上间距 12」共 ~72dp
        PixelSectionTitle(
            text = "今日三餐",
            trailing = if (state.hasPlanToday) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.sm),
                    ) {
                        PixelText(
                            text = "已打卡 ${state.meals.count { it.checkedIn }}/${state.meals.size}",
                            color = PixelColors.gray,
                            fontSize = PixelType.Size.xs,
                        )
                        // 今日计划编辑入口（复用 Create 编辑模式）
                        Box(
                            modifier = Modifier
                                .clickable(onClick = { onEditPlan(state.planId) })
                                .padding(all = PixelShape.Spacing.xs),
                        ) {
                            PixelText(
                                text = "编辑 ›",
                                color = PixelColors.blue,
                                fontSize = PixelType.Size.xs,
                            )
                        }
                    }
                }
            } else {
                null
            },
        )
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 三餐时间轴（原型 meal-row ×3）或空态
        if (state.hasPlanToday) {
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
                    // 备餐可点编辑（弹 PixelDialog stepper；prepMinutes 是 per-meal 字段，非全局写死）
                    onEditPrep = { editingPrep = meal.type },
                    modifier = Modifier.padding(vertical = PixelShape.Spacing.xs),
                )
            }
        } else {
            val detected = state.detectedMealType
            // 当前时段餐是否已打卡：已打 → 按钮禁用并显示「已记录」，避免重复礼花
            val alreadyChecked = detected != null && detected in state.checkedInToday
            PixelCard(backgroundColor = PixelColors.panel.copy(alpha = 0.4f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.checkedInToday.isEmpty()) {
                        PixelText(text = "今天还没有就餐计划", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                        // 按时段判定当前餐次：用户不选餐次，系统按本地时间推断（MealWindow）
                        PixelText(
                            text = when (detected) {
                                MealType.BREAKFAST -> "当前是早餐时段 · 打卡自动安排午/晚"
                                MealType.LUNCH -> "当前是午餐时段 · 将只记录这一餐"
                                MealType.DINNER -> "当前是晚餐时段 · 将只记录这一餐"
                                null -> "现在不是用餐时段"
                            },
                            color = PixelColors.gray,
                            fontSize = PixelType.Size.xs,
                        )
                    } else {
                        // 单餐打卡反馈：无计划但今日已记录，展示已记录餐次（打卡结果可见）
                        PixelText(text = "今天还没有就餐计划", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                        PixelText(
                            text = "今日已记录：" + MealType.entries
                                .filter { it in state.checkedInToday }
                                .joinToString(" ") { SpeechCatalog.mealName(it) } + " ✓",
                            color = PixelColors.yellow,
                            fontSize = PixelType.Size.xs,
                        )
                        if (detected != null && !alreadyChecked) {
                            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                            PixelText(
                                text = "还可记录 ${SpeechCatalog.mealName(detected)}",
                                color = PixelColors.gray,
                                fontSize = PixelType.Size.xs,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                    PixelButton(
                        text = when {
                            alreadyChecked -> "${SpeechCatalog.mealName(detected)}已记录 ✓"
                            detected == MealType.BREAKFAST -> "打卡早餐 · 自动安排午/晚"
                            detected == MealType.LUNCH -> "打卡午餐"
                            detected == MealType.DINNER -> "打卡晚餐"
                            else -> "现在不是用餐时段"
                        },
                        onClick = onCheckInNow,
                        primary = true,
                        enabled = !alreadyChecked && detected != null,
                    )
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                    PixelButton(
                        text = "＋ 预约未来计划",
                        onClick = onNewPlan,
                        primary = false,
                    )
                }
            }
        }
    }
    // 像素滚动条：仅在内容真的溢出时出现（一屏可见优化后典型态不溢出 → 不再画无意义的空轨道）
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

    // 备餐编辑弹窗：tile 上备餐文字可点 → 打开；±5 分钟（0–180），保存后重排闹钟
    editingPrep?.let { type ->
        val meal = state.meals.firstOrNull { it.type == type }
            ?: run { editingPrep = null; return@let }
        PixelDialog(
            onDismiss = { editingPrep = null },
            title = "${meal.name} · 备餐",
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(
                    text = if (meal.prepMinutes > 0) {
                        "到点前 ${meal.prepMinutes} 分钟提醒备餐"
                    } else {
                        "无备餐 · 到点直接开吃"
                    },
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelStepper(
                    value = "${meal.prepMinutes}",
                    onDecrease = {
                        if (meal.prepMinutes > 0) {
                            onEditPrep(type, (meal.prepMinutes - 5).coerceAtLeast(0))
                        }
                    },
                    onIncrease = { onEditPrep(type, (meal.prepMinutes + 5).coerceAtMost(180)) },
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelButton(
                    text = "完成",
                    onClick = { editingPrep = null },
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.sm),
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

/** Preview 假数据（有计划的典型态） */
private fun previewContentState() = HomeUiState.Content(
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
)

@PreviewPixel
@Composable
private fun HomeScreenPreview() {
    PixelTheme {
        HomeContent(
            state = previewContentState(),
            onNewPlan = {},
            onCheckIn = {},
            onUncheckIn = {},
            onCheckInNow = {},
            onEditPrep = { _, _ -> },
            onOpenRecord = {},
            onEditPlan = {},
        )
    }
}

/**
 * 一屏可见验证用 Preview 框架：复刻 Fast16NavHost 的 Scaffold 结构，
 * 让可视高度与真机一致（扣掉底栏），用来目视确认内容是否一屏放下。
 * 由带不同 device spec 的 @Preview 入口调用（Android Studio 按注解渲染各尺寸）。
 */
@Composable
private fun HomeContentFramePreview() {
    PixelTheme {
        Scaffold(
            containerColor = PixelColors.bg,
            bottomBar = { Box(modifier = Modifier.height(62.dp)) },
        ) { innerPadding ->
            HomeContent(
                state = previewContentState(),
                onNewPlan = {},
                onCheckIn = {},
                onUncheckIn = {},
                onCheckInNow = {},
                onEditPrep = { _, _ -> },
                onOpenRecord = {},
                onEditPlan = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** 主流屏：360×800dp（可视 ≈662dp），目标是一屏放下、无滚动 */
@Preview(
    name = "Home · 主流屏 360x800",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun HomeScreenMainstreamPreview() {
    HomeContentFramePreview()
}

/** 矮屏：360×640dp（可视 ≈506dp），目标是不裁切、可滚动阅读 */
@Preview(
    name = "Home · 矮屏 360x640",
    device = "spec:width=360dp,height=640dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun HomeScreenSmallDevicePreview() {
    HomeContentFramePreview()
}

/** 无计划空态假数据（detected 可指定当前时段；checked = 今日已打卡餐次） */
private fun emptyPreviewState(detected: MealType?, checked: Set<MealType>) = HomeUiState.Content(
    hasPlanToday = false,
    characterState = CharacterState.IDLE,
    bubbleText = "来记录早餐，开启今天第一餐吧！",
    fastingSeconds = null,
    meals = emptyList(),
    subtitle = "今天 · 还没有计划",
    streak = 0,
    windowProgress = 0f,
    planId = -1L,
    detectedMealType = detected,
    checkedInToday = checked,
)

/** 空态 · 未打卡：显示时段引导 + 可点打卡按钮 */
@Preview(
    name = "Home · 空态未打卡",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun HomeEmptyPreview() {
    PixelTheme {
        HomeContent(
            state = emptyPreviewState(detected = MealType.DINNER, checked = emptySet()),
            onNewPlan = {},
            onCheckIn = {},
            onUncheckIn = {},
            onCheckInNow = {},
            onEditPrep = { _, _ -> },
            onOpenRecord = {},
            onEditPlan = {},
        )
    }
}

/** 空态 · 已打卡：显示「今日已记录」反馈 + 按钮禁用（打卡结果可见） */
@Preview(
    name = "Home · 空态已打卡",
    device = "spec:width=360dp,height=800dp,dpi=480",
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
)
@Composable
private fun HomeEmptyCheckedPreview() {
    PixelTheme {
        HomeContent(
            state = emptyPreviewState(detected = MealType.DINNER, checked = setOf(MealType.DINNER)),
            onNewPlan = {},
            onCheckIn = {},
            onUncheckIn = {},
            onCheckInNow = {},
            onEditPrep = { _, _ -> },
            onOpenRecord = {},
            onEditPlan = {},
        )
    }
}
