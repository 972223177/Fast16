package com.ly.fast16.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.character.SpeechCatalog
import com.ly.fast16.core.designsystem.component.PixelConfettiBurst
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.plan.AutoPlanGenerator
import com.ly.fast16.core.plan.AutoPlanResult
import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.SettingsStore
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
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import kotlin.time.Duration.Companion.milliseconds

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

    /** 每秒实时状态（倒计时/角色/气泡）：高频订阅，避免整页每秒重组 */
    private val _liveState = MutableStateFlow(HomeLiveState(null, CharacterState.IDLE, ""))
    val liveState: StateFlow<HomeLiveState> = _liveState.asStateFlow()

    // 高频 ticker 复用快照（低频流每次更新时写入）
    private var cachedPlan: MealPlan? = null
    private var cachedMeals: List<Meal> = emptyList()
    private var cachedChecked: Set<MealType> = emptySet()

    /** 打卡成功轻提示 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 成功礼花（打卡：小礼花 / 三餐全打卡：大礼花） */
    private val _confetti = MutableStateFlow<PixelConfettiBurst?>(null)
    val confetti: StateFlow<PixelConfettiBurst?> = _confetti.asStateFlow()

    init {
        // 低频流：数据变化驱动（打卡/计划/统计变更才重算），不含 ticker → uiState 不每秒换新
        viewModelScope.launch {
            val today = SystemTimeProvider.today()
            combine(
                planRepository.watchPlanByDate(today),
                planRepository.watchMealsByDate(today),
                checkInRepository.watchMonth(YearMonth.from(today)),
                // 跨月滚动窗口（今起 366 天）：连续打卡不受月末截断
                checkInRepository.watchCompletedDays(today.minusDays(366), today),
            ) { planPair, meals, monthChecked, completedDays ->
                val now = clock.instant()
                val checkedSet = monthChecked[today].orEmpty()
                // 缓存快照供高频 ticker 复用（原始 meals，ticker 侧自行 reconcile）
                cachedPlan = planPair?.first
                cachedMeals = meals
                cachedChecked = checkedSet
                val streak = StreakCalculator.computeStreak(completedDays, today)
                derive(now, planPair?.first, meals, checkedSet, streak)
            }.collect { _uiState.value = it }
        }
        // 高频流：每秒刷新实时状态（倒计时/角色/气泡），只驱动 liveState 订阅方重组
        viewModelScope.launch {
            while (true) {
                val now = clock.instant()
                val reconciled = cachedMeals.map { meal ->
                    meal.copy(status = MealStateMachine.advance(meal, now, meal.type in cachedChecked))
                }
                val character = CharacterStateDeriver.derive(now, cachedPlan, reconciled)
                val fasting = FastingTimer.fastingRemaining(now, reconciled)?.seconds
                val bubble = bubbleText(now, character, reconciled)
                _liveState.value = HomeLiveState(fasting, character, bubble)
                delay(1000.milliseconds)
            }
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
        val bubble = bubbleText(now, character, reconciled)
        val mealUis = reconciled.map { meal ->
            val zone = SystemTimeProvider.zone
            MealUi(
                type = meal.type,
                name = SpeechCatalog.mealName(meal.type),
                timeLabel = Time.hhmm(meal.mealTime, zone),
                prepMinutes = meal.prepMinutes,
                checkedIn = meal.type in checkedSet,
                hasMeal = true,
            )
        }
        // 顶部副标题：「今天 · 早餐 08:00 ｜ 窗口结束 16:00」（原型 home-date）
        val subtitle = if (hasPlan) {
            val bf = reconciled.firstOrNull { it.type == MealType.BREAKFAST }?.let {
                Time.hhmm(it.mealTime, SystemTimeProvider.zone)
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

    private fun bubbleText(now: Instant, character: CharacterState, meals: List<Meal>): String = when (character) {
        // IDLE = 无计划（derive 仅在 plan==null || meals.isEmpty 时为 IDLE）：按时段给提示，
        // 避免深夜还喊「记录早餐」；非用餐时段 → 断食/休息文案
        CharacterState.IDLE -> when (MealWindow.detect(Time.timeOf(now, SystemTimeProvider.zone))) {
            MealType.BREAKFAST -> "来记录早餐，开启今天第一餐吧！"
            MealType.LUNCH -> "来记录午餐，保持节奏！"
            MealType.DINNER -> "来记录晚餐，吃得开心！"
            null -> "现在是断食时段，好好休息吧！"
        }
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
        // 中性化：避免深夜刚进断食时「再坚持一下就开饭」误导（距下次开饭可能 10+ 小时）
        CharacterState.FASTING -> "断食中，坚持就是胜利！"
    }
}
