package com.ly.fast16.feature.create.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelConfetti
import com.ly.fast16.core.designsystem.component.PixelConfettiBurst
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.designsystem.component.PixelStepper
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
import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.core.widget.Fast16Widget
import androidx.glance.appwidget.updateAll
import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.AppSettings
import com.ly.fast16.data.local.SettingsStore
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.domain.repository.PlanRepository
import com.ly.fast16.domain.schedule.Candidate
import com.ly.fast16.domain.schedule.CandidateGenerator
import com.ly.fast16.domain.schedule.PlanValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// ---------- Intent ----------

/** 新建计划用户意图（记录早餐→选方案→微调→生成；日期 = 预约未来计划） */
sealed interface CreateIntent {
    data object NextStep : CreateIntent
    data object PrevStep : CreateIntent
    /** 计划日期 ±1 天（新建模式不可早于今天；编辑模式保持原日期） */
    data class ShiftDate(val delta: Int) : CreateIntent
    data class PickBreakfast(val time: LocalTime) : CreateIntent
    data class SelectCandidate(val index: Int) : CreateIntent
    data class AdjustLunchTime(val time: LocalTime) : CreateIntent
    data class AdjustDinnerTime(val time: LocalTime) : CreateIntent
    data class AdjustPrepLunch(val minutes: Int) : CreateIntent
    data class AdjustPrepDinner(val minutes: Int) : CreateIntent
    data object Generate : CreateIntent
}

// ---------- State ----------

/** 新建计划 UI 状态：Loading / Content（step 1=早餐，2=方案微调） */
sealed interface CreateUiState {
    data object Loading : CreateUiState

    data class Content(
        val step: Int = 1,
        /** 编辑模式（CreateRoute.planId ≥ 0）：标题切 EDIT PLAN，生成走更新+重排 */
        val isEdit: Boolean = false,
        /** 计划日期（新建=今天或未来「预约」；编辑=原计划日期） */
        val date: LocalDate,
        val zone: ZoneId,
        val breakfastTime: LocalTime,
        // Settings 快照（候选生成参数 / 生成时继承 reminderMode）
        val windowHours: Int,
        val minGapMinutes: Int,
        val bufferEndMinutes: Int,
        val prepLunchDefault: Int,
        val prepDinnerDefault: Int,
        val defaultReminderMode: ReminderMode,
        // 候选与微调
        val candidates: List<Candidate> = emptyList(),
        val selectedIndex: Int = 0,
        val lunchTime: LocalTime,
        val dinnerTime: LocalTime,
        val prepLunch: Int,
        val prepDinner: Int,
        /** 午/晚餐提醒模式（编辑模式继承原值；新建=全局默认） */
        val lunchReminderMode: ReminderMode = ReminderMode.NOTIFY,
        val dinnerReminderMode: ReminderMode = ReminderMode.NOTIFY,
        val errors: List<String> = emptyList(),
    ) : CreateUiState
}

/** 新建计划一次性事件（生成成功回首页 / 失败提示） */
sealed interface CreateEvent {
    data object Generated : CreateEvent

    /** 生成失败（保存/排程异常）——Screen 层 Toast 反馈 */
    data object Failed : CreateEvent
}

// ---------- Reducer（纯函数：候选生成 / 约束校验均无 IO、无时间源） ----------

object CreateReducer {

    /**
     * 早餐时间合理范围 [04:00, 14:00]（8:16 常规窗口）。
     *
     * **跨午夜红线**：早餐过晚会生成跨午夜的候选（如 20:00 早餐 → 午餐次日 00:00），
     * 而候选经 [syncFromCandidate] 转 LocalTime 后会被 [revalidate] 按原 date 组装回当天，
     * 校验必然失败（午餐 < 早餐+minGap）→ 生成按钮禁用，流程走不通。
     * 在合理范围内候选永不跨午夜（14:00 早餐 → 最晚晚餐 21:30，当天内）。
     */
    val BREAKFAST_MIN: LocalTime = LocalTime.of(4, 0)
    val BREAKFAST_MAX: LocalTime = LocalTime.of(14, 0)

    /** 超范围时回退的合理默认早餐 */
    val DEFAULT_BREAKFAST: LocalTime = LocalTime.of(8, 0)

    /** 默认早餐：当前时间在合理范围用当前时间，否则 08:00（晚上进 Create 不默认 20:00） */
    fun defaultBreakfast(now: LocalTime): LocalTime =
        if (now in BREAKFAST_MIN..BREAKFAST_MAX) now else DEFAULT_BREAKFAST

    fun initial(
        settings: AppSettings,
        date: LocalDate,
        zone: ZoneId,
        defaultBreakfast: LocalTime,
        isEdit: Boolean = false,
    ): CreateUiState.Content = CreateUiState.Content(
        isEdit = isEdit,
        date = date,
        zone = zone,
        breakfastTime = defaultBreakfast,
        windowHours = settings.windowHours,
        minGapMinutes = settings.minMealGapMinutes,
        bufferEndMinutes = settings.dinnerBufferMinutes,
        prepLunchDefault = settings.defaultPrepLunchMinutes,
        prepDinnerDefault = settings.defaultPrepDinnerMinutes,
        defaultReminderMode = settings.defaultReminderMode,
        lunchTime = defaultBreakfast,
        dinnerTime = defaultBreakfast,
        prepLunch = settings.defaultPrepLunchMinutes,
        prepDinner = settings.defaultPrepDinnerMinutes,
        lunchReminderMode = settings.defaultReminderMode,
        dinnerReminderMode = settings.defaultReminderMode,
    ).regen()

    /** 编辑预填：从现有计划构造编辑初始状态（Record 详情「编辑计划」入口） */
    fun editInitial(
        settings: AppSettings,
        plan: MealPlan,
        meals: List<Meal>,
        zone: ZoneId,
    ): CreateUiState.Content {
        val base = initial(
            settings = settings,
            date = plan.date,
            zone = zone,
            defaultBreakfast = Time.timeOf(plan.windowStart, zone),
            isEdit = true,
        )
        val lunch = meals.firstOrNull { it.type == MealType.LUNCH }
        val dinner = meals.firstOrNull { it.type == MealType.DINNER }
        val lunchTime = lunch?.let { Time.timeOf(it.mealTime, zone) } ?: base.lunchTime
        // WR-03(复审)：候选高亮与实际预填值对齐——选中与预填午餐最接近的候选
        val lunchInstant = Time.at(plan.date, lunchTime, zone)
        val selectedIndex = base.candidates.indices.minByOrNull { idx ->
            abs(Duration.between(base.candidates[idx].lunch, lunchInstant).toMillis())
        } ?: 0
        return base.copy(
            selectedIndex = selectedIndex,
            lunchTime = lunchTime,
            dinnerTime = dinner?.let { Time.timeOf(it.mealTime, zone) } ?: base.dinnerTime,
            prepLunch = lunch?.prepMinutes ?: base.prepLunch,
            prepDinner = dinner?.prepMinutes ?: base.prepDinner,
            // WR-05：编辑继承原餐次提醒模式（不被全局默认覆盖）
            lunchReminderMode = lunch?.reminderMode ?: base.lunchReminderMode,
            dinnerReminderMode = dinner?.reminderMode ?: base.dinnerReminderMode,
        ).revalidate()
    }

    fun reduce(state: CreateUiState, intent: CreateIntent): CreateUiState {
        val s = state as? CreateUiState.Content ?: return state
        return when (intent) {
            CreateIntent.NextStep -> s.copy(step = 2)
            CreateIntent.PrevStep -> s.copy(step = 1)
            // 日期 ±1 天：新建模式不可早于今天（预约未来）；编辑模式不限制（保持原日期）
            is CreateIntent.ShiftDate -> {
                val newDate = s.date.plusDays(intent.delta.toLong())
                if (!s.isEdit && newDate.isBefore(SystemTimeProvider.today())) {
                    s // 不允许选过去日期
                } else {
                    s.copy(date = newDate).regen()
                }
            }
            // 早餐 clamp 到合理范围（UI stepper 已限，reducer 双保险防跨午夜候选）
            is CreateIntent.PickBreakfast ->
                s.copy(breakfastTime = intent.time.coerceIn(BREAKFAST_MIN, BREAKFAST_MAX)).regen()
            is CreateIntent.SelectCandidate -> s.copy(selectedIndex = intent.index).syncFromCandidate()
            is CreateIntent.AdjustLunchTime -> s.copy(lunchTime = intent.time).revalidate()
            is CreateIntent.AdjustDinnerTime -> s.copy(dinnerTime = intent.time).revalidate()
            is CreateIntent.AdjustPrepLunch -> s.copy(prepLunch = intent.minutes).revalidate()
            is CreateIntent.AdjustPrepDinner -> s.copy(prepDinner = intent.minutes).revalidate()
            CreateIntent.Generate -> s // 生成副作用由 ViewModel 处理
        }
    }

    /** 早餐变化 / 日期变化 → 重新生成 3 套候选并同步选中项 */
    private fun CreateUiState.Content.regen(): CreateUiState.Content {
        val breakfast = Time.at(date, breakfastTime, zone)
        val candidates = CandidateGenerator.generateCandidates(
            breakfast = breakfast,
            window = Duration.ofHours(windowHours.toLong()),
            minGap = Duration.ofMinutes(minGapMinutes.toLong()),
            bufferEnd = Duration.ofMinutes(bufferEndMinutes.toLong()),
        )
        val sel = selectedIndex.coerceIn(0, candidates.lastIndex)
        return copy(candidates = candidates, selectedIndex = sel)
            .syncFromCandidate()
            .revalidate()
    }

    /** 选中候选 → 同步午/晚时间。备餐分钟是用户独立偏好（initial/editInitial 已给默认），
     *  不随候选重置——避免切候选/改早餐时静默丢弃已微调值 */
    private fun CreateUiState.Content.syncFromCandidate(): CreateUiState.Content {
        val c = candidates.getOrNull(selectedIndex) ?: return this
        return copy(
            lunchTime = Time.timeOf(c.lunch, zone),
            dinnerTime = Time.timeOf(c.dinner, zone),
        ).revalidate()
    }

    /** 微调 → 实时约束校验（PlanValidator） */
    private fun CreateUiState.Content.revalidate(): CreateUiState.Content {
        val breakfast = Time.at(date, breakfastTime, zone)
        val lunch = Time.at(date, lunchTime, zone)
        val dinner = Time.at(date, dinnerTime, zone)
        return copy(
            errors = PlanValidator.validate(
                lunch = lunch,
                dinner = dinner,
                breakfast = breakfast,
                window = Duration.ofHours(windowHours.toLong()),
                minGap = Duration.ofMinutes(minGapMinutes.toLong()),
                bufferEnd = Duration.ofMinutes(bufferEndMinutes.toLong()),
            ),
        )
    }
}

// ---------- ViewModel ----------

/** 新建计划 ViewModel（FR-1~FR-5）：候选→微调→生成→落库→排程→自动打卡 */
class CreateViewModel(
    private val settingsStore: SettingsStore,
    private val planRepository: PlanRepository,
    private val scheduler: PlanScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateUiState>(CreateUiState.Loading)
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val _events = Channel<CreateEvent>(Channel.BUFFERED)
    val events: Flow<CreateEvent> = _events.receiveAsFlow()

    /** 编辑模式：当前编辑的计划 id（-1 = 新建） */
    private var editingPlanId: Long = -1L

    /** 加载请求（-1 = 新建态；≥0 = 编辑预填）：单协程串行处理，消除 init 与 loadForEdit 并发写竞态 */
    private val loadRequest = MutableStateFlow(-1L)

    /** 编辑模式：按 planId 加载现有计划预填（Record 详情「编辑计划」入口） */
    fun loadForEdit(planId: Long) {
        loadRequest.value = planId
    }

    init {
        viewModelScope.launch {
            // 统一加载入口：collect 串行 await，后值覆盖前值（StateFlow 合并），无并发覆盖
            loadRequest.collect { planId ->
                val settings = settingsStore.settings.first()
                val zone = SystemTimeProvider.zone
                if (planId >= 0) {
                    val pair = planRepository.getPlanById(planId)
                    if (pair != null) {
                        editingPlanId = planId
                        _uiState.value = CreateReducer.editInitial(
                            settings = settings,
                            plan = pair.first,
                            meals = pair.second,
                            zone = zone,
                        )
                        return@collect
                    }
                    // 计划已不存在（被删）→ 退回新建态
                    editingPlanId = -1L
                }
                // 新建模式 = 「预约未来计划」：默认日期取明天（而非今天——今天的计划由
                // Home 早餐打卡自动生成，Create 专职预约）。想预约今天可 ◀ 回退到今天
                // （今天 ≥ 今天 允许；昨天不可）。
                val now = Time.alignToMinute(clock.instant()).atZone(zone).toLocalTime()
                _uiState.value = CreateReducer.initial(
                    settings = settings,
                    date = SystemTimeProvider.today().plusDays(1),
                    zone = zone,
                    defaultBreakfast = CreateReducer.defaultBreakfast(now),
                )
            }
        }
    }

    fun onIntent(intent: CreateIntent) {
        _uiState.value = CreateReducer.reduce(_uiState.value, intent)
        if (intent is CreateIntent.Generate) {
            viewModelScope.launch { doGenerate() }
        }
    }

    private suspend fun doGenerate() {
        val s = _uiState.value as? CreateUiState.Content ?: return
        if (s.errors.isNotEmpty()) return // 约束违规阻止生成
        try {
            val zone = s.zone
            val breakfast = Time.at(s.date, s.breakfastTime, zone)
            val window = Duration.ofHours(s.windowHours.toLong())
            val plan = MealPlan(
                // 编辑模式带原 id → savePlan upsert 更新原计划（date 不变，重新生成三餐）
                id = editingPlanId,
                date = s.date,
                windowStart = breakfast,
                windowEnd = breakfast.plus(window),
                status = PlanStatus.ACTIVE,
            )
            val meals = listOf(
                Meal(type = MealType.BREAKFAST, mealTime = breakfast, prepMinutes = 0, reminderMode = s.defaultReminderMode),
                Meal(type = MealType.LUNCH, mealTime = Time.at(s.date, s.lunchTime, zone), prepMinutes = s.prepLunch, reminderMode = s.lunchReminderMode),
                Meal(type = MealType.DINNER, mealTime = Time.at(s.date, s.dinnerTime, zone), prepMinutes = s.prepDinner, reminderMode = s.dinnerReminderMode),
            )
            val planId = planRepository.savePlan(plan, meals)
            // WR-01：编辑/覆盖前先撤销旧闹钟，杜绝孤儿/幽灵提醒
            if (editingPlanId >= 0) scheduler.cancel(editingPlanId)
            // 排程必须用落库后的真实 Meal（含 id）——AlarmReceiver 依 EXTRA_MEAL_ID 查库，
            // 用内存 id=0 的 Meal 会导致到点提醒查不到餐次而被静默丢弃
            val savedMeals = planRepository.watchMealsByDate(s.date).first()
            // IN-03：仅今天及未来排程（编辑历史日期只落库，过期闹钟无意义且会被迟到忽略）
            if (!s.date.isBefore(SystemTimeProvider.today())) {
                scheduler.schedule(plan.copy(id = planId), savedMeals)
            }
            // 自动打卡早餐已移除：打卡入口统一走 Home（早餐打卡自动生成 / 单餐记录），
            // 未来日期计划更无「打卡」语义
            editingPlanId = -1L
            _events.send(CreateEvent.Generated)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 取消必须重抛（coroutines-best-practices §4）
        } catch (e: Exception) {
            // 生成失败兜底：不崩溃应用 + UI 反馈（P1）
            android.util.Log.e(TAG, "生成计划失败", e)
            _events.send(CreateEvent.Failed)
        }
    }

    private companion object {
        const val TAG = "CreateViewModel"
    }
}

// ---------- Screen ----------

/** 新建 / 编辑计划 Screen（全屏无底栏；planId ≥ 0 为编辑模式） */
@Composable
fun CreateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    planId: Long = -1L,
) {
    val vm: CreateViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    var showFailToast by remember { mutableStateOf(false) }

    // 编辑模式：planId 进入时加载现有计划预填
    LaunchedEffect(planId) {
        if (planId >= 0) vm.loadForEdit(planId)
    }

    var showConfetti by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                CreateEvent.Generated -> {
                    // IN-02：计划变更后刷新桌面小组件（updatePeriodMillis=0，事件驱动）
                    Fast16Widget().updateAll(context)
                    showConfetti = true // 生成成功礼花，播完再返回
                }

                CreateEvent.Failed -> showFailToast = true
            }
        }
    }

    when (val s = state) {
        CreateUiState.Loading -> PixelLoading()
        is CreateUiState.Content -> CreateContent(
            state = s,
            onIntent = vm::onIntent,
            onBack = onBack,
            modifier = modifier,
        )
    }

    // 生成失败轻提示
    if (showFailToast) {
        PixelToast(
            text = "生成失败，请重试",
            show = true,
            onDismiss = { showFailToast = false },
        )
    }

    // 生成成功礼花 overlay：遮罩 + 大礼花，播完自动返回首页
    if (showConfetti) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PixelColors.bg.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            PixelConfetti(
                burst = PixelConfettiBurst.PLAN_CREATED,
                onFinished = { showConfetti = false; onBack() },
                modifier = Modifier.size(180.dp),
            )
        }
    }
}

@Composable
private fun CreateContent(
    state: CreateUiState.Content,
    modifier: Modifier = Modifier,
    onIntent: (CreateIntent) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = PixelShape.contentMaxWidth)
            .verticalScroll(scrollState)
            .padding(
                start = PixelShape.Spacing.lg,
                end = PixelShape.Spacing.lg,
                top = PixelShape.Spacing.lg,
                bottom = PixelShape.Spacing.xxl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部：NEW PLAN + 步骤标签 + 取消（原型 create-head）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 页面标题规范：打字机大标题 + 副标题（PixelPageTitle）
            PixelPageTitle(
                title = if (state.isEdit) "EDIT PLAN" else "NEW PLAN",
                subtitle = if (state.isEdit) {
                    if (state.step == 1) "编辑计划 · 第 1 步" else "编辑计划 · 第 2 步"
                } else {
                    if (state.step == 1) "第 1 步 · 日期与早餐" else "第 2 步 · 选方案 + 微调"
                },
            )
            // 取消（ghost sm，回首页）
            Box(
                modifier = Modifier
                    .background(PixelColors.panel, PixelShape.stair)
                    .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
                    .clickable(onClick = onBack)
                    .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                PixelText(text = "取消", color = PixelColors.white, fontSize = PixelType.Size.xs)
            }
        }

        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 分隔线（原型 .hr，位于 NEW PLAN 头之后）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(PixelColors.panel.copy(alpha = 0.6f)),
        )

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        if (state.step == 1) {
            BreakfastStep(state = state, onIntent = onIntent)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))
            PixelButton(
                text = "下一步：选午/晚餐 ▶",
                onClick = { onIntent(CreateIntent.NextStep) },
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PlanStep(state = state, onIntent = onIntent)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))
            PixelButton(
                text = "生成就餐计划 ✔",
                onClick = { onIntent(CreateIntent.Generate) },
                primary = true,
                enabled = state.errors.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                PixelText(
                    text = state.errors.joinToString("；"),
                    color = PixelColors.red,
                    fontSize = PixelType.Size.xs,
                )
            }
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelButton(
                text = "上一步",
                onClick = { onIntent(CreateIntent.PrevStep) },
                primary = false,
            )
        }
    }
    // 像素滚动条：矮屏内容被裁时提示可滚动（右上端、贯穿高度）
    PixelVerticalScrollbar(
        scrollState = scrollState,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(vertical = PixelShape.Spacing.sm),
    )
    }
}

@Composable
private fun BreakfastStep(state: CreateUiState.Content, onIntent: (CreateIntent) -> Unit) {
    // 计划日期（预约未来计划：今天起不可过去；编辑模式固定原日期，左箭头禁用）
    // 自动打卡早餐已移除——打卡统一走 Home「打卡现在这餐」（按时段判定 + 自动生成）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val canShiftBack = state.isEdit || state.date > SystemTimeProvider.today()
        DateArrow(symbol = "◀", enabled = canShiftBack) { onIntent(CreateIntent.ShiftDate(-1)) }
        PixelText(
            text = "${state.date.monthValue}月${state.date.dayOfMonth}日" +
                if (state.date == SystemTimeProvider.today()) " · 今天" else "",
            color = PixelColors.white,
            fontSize = PixelType.Size.xs,
        )
        DateArrow(symbol = "▶", enabled = true) { onIntent(CreateIntent.ShiftDate(1)) }
    }

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    PixelText(
        text = "早餐时间是进食窗口起点，默认取当前时间。",
        color = PixelColors.gray,
        fontSize = PixelType.Size.xs,
    )

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    // 早餐时间 stepper（原型 step1：- 08:00 +，±5 分钟）
    // 范围 [BREAKFAST_MIN, BREAKFAST_MAX]：超范围候选跨午夜 → 校验失败流程走不通（CreateReducer KDoc）
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(text = "早餐时间", color = PixelColors.gray, fontSize = PixelType.Size.xs)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelStepper(
                value = Time.hhmm(state.breakfastTime),
                onDecrease = {
                    if (state.breakfastTime > CreateReducer.BREAKFAST_MIN) {
                        onIntent(CreateIntent.PickBreakfast(state.breakfastTime.minusMinutes(5)))
                    }
                },
                onIncrease = {
                    if (state.breakfastTime < CreateReducer.BREAKFAST_MAX) {
                        onIntent(CreateIntent.PickBreakfast(state.breakfastTime.plusMinutes(5)))
                    }
                },
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelText(
                text = "建议 ${Time.hhmm(CreateReducer.BREAKFAST_MIN)} – ${Time.hhmm(CreateReducer.BREAKFAST_MAX)}",
                color = PixelColors.gray,
                fontSize = PixelType.Size.xs,
            )
        }
    }
}

/** 日期切换箭头（◀ ▶；禁用态灰显不可点——新建不可选过去日期） */
@Composable
private fun DateArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(PixelColors.panel, PixelShape.stair)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(
            text = symbol,
            color = if (enabled) PixelColors.yellow else PixelColors.gray,
            fontSize = PixelType.Size.xs,
        )
    }
}

@Composable
private fun PlanStep(state: CreateUiState.Content, onIntent: (CreateIntent) -> Unit) {
    PixelText(
        text = "系统按 ${state.windowHours} 小时窗口推理了 3 套候选，选一套再微调备餐时长。",
        color = PixelColors.gray,
        fontSize = PixelType.Size.xs,
    )

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    // 候选三选一（原型 cand 卡：方案名 + 午/晚时刻，选中主黄描边）
    state.candidates.forEachIndexed { index, candidate ->
        val selected = index == state.selectedIndex
        PixelCard(
            borderColor = if (selected) PixelColors.yellow else Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PixelShape.Spacing.xs)
                .clickable { onIntent(CreateIntent.SelectCandidate(index)) },
        ) {
            Column {
                PixelText(
                    text = candidate.label,
                    color = PixelColors.white,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.xs))
                PixelText(
                    text = "午 ${Time.hhmm(candidate.lunch, state.zone)} · 晚 ${Time.hhmm(candidate.dinner, state.zone)}",
                    color = PixelColors.yellow,
                    fontSize = PixelType.Size.xs,
                    digital = true,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    // 备餐时长微调（原型 step2 stepper：午餐/晚餐备餐）
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelText(text = "午餐备餐", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                PixelStepper(
                    value = "${state.prepLunch}",
                    onDecrease = { onIntent(CreateIntent.AdjustPrepLunch((state.prepLunch - 5).coerceAtLeast(0))) },
                    onIncrease = { onIntent(CreateIntent.AdjustPrepLunch((state.prepLunch + 5).coerceAtMost(180))) },
                    valueMinWidth = 32.dp,
                )
            }
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelText(text = "晚餐备餐", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                PixelStepper(
                    value = "${state.prepDinner}",
                    onDecrease = { onIntent(CreateIntent.AdjustPrepDinner((state.prepDinner - 5).coerceAtLeast(0))) },
                    onIncrease = { onIntent(CreateIntent.AdjustPrepDinner((state.prepDinner + 5).coerceAtMost(180))) },
                    valueMinWidth = 32.dp,
                )
            }
        }
    }
}

@PreviewPixel
@Composable
private fun CreateScreenPreview() {
    PixelTheme {
        CreateContent(
            state = CreateReducer.initial(
                settings = AppSettings(
                    windowHours = 8, minMealGapMinutes = 180, dinnerBufferMinutes = 30,
                    defaultPrepLunchMinutes = 30, defaultPrepDinnerMinutes = 45,
                    defaultReminderMode = ReminderMode.NOTIFY,
                    widgetShowFasting = true, onboardingSeen = true,
                ),
                date = LocalDate.of(2026, 8, 31),
                zone = ZoneId.of("Asia/Shanghai"),
                defaultBreakfast = LocalTime.of(8, 0),
            ),
            onIntent = {},
            onBack = {},
        )
    }
}
