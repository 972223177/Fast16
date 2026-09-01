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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelStepper
import com.ly.fast16.core.designsystem.component.PixelText
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
import com.ly.fast16.domain.usecase.CheckInUseCase
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

/** 新建计划用户意图（记录早餐→选方案→微调→生成） */
sealed interface CreateIntent {
    data object NextStep : CreateIntent
    data object PrevStep : CreateIntent
    data class PickBreakfast(val time: LocalTime) : CreateIntent
    data object ToggleAutoCheckIn : CreateIntent
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
        val today: LocalDate,
        val zone: ZoneId,
        val breakfastTime: LocalTime,
        val autoCheckIn: Boolean = true,
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

/** 新建计划一次性事件（生成成功回首页） */
sealed interface CreateEvent {
    data object Generated : CreateEvent
}

// ---------- Reducer（纯函数：候选生成 / 约束校验均无 IO、无时间源） ----------

object CreateReducer {

    fun initial(
        settings: AppSettings,
        today: LocalDate,
        zone: ZoneId,
        defaultBreakfast: LocalTime,
        isEdit: Boolean = false,
    ): CreateUiState.Content = CreateUiState.Content(
        isEdit = isEdit,
        today = today,
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
            today = plan.date,
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
            // 编辑不自动打卡（已有计划的打卡状态独立保留）
            autoCheckIn = false,
        ).revalidate()
    }

    fun reduce(state: CreateUiState, intent: CreateIntent): CreateUiState {
        val s = state as? CreateUiState.Content ?: return state
        return when (intent) {
            CreateIntent.NextStep -> s.copy(step = 2)
            CreateIntent.PrevStep -> s.copy(step = 1)
            is CreateIntent.PickBreakfast -> s.copy(breakfastTime = intent.time).regen()
            CreateIntent.ToggleAutoCheckIn -> s.copy(autoCheckIn = !s.autoCheckIn)
            is CreateIntent.SelectCandidate -> s.copy(selectedIndex = intent.index).syncFromCandidate()
            is CreateIntent.AdjustLunchTime -> s.copy(lunchTime = intent.time).revalidate()
            is CreateIntent.AdjustDinnerTime -> s.copy(dinnerTime = intent.time).revalidate()
            is CreateIntent.AdjustPrepLunch -> s.copy(prepLunch = intent.minutes).revalidate()
            is CreateIntent.AdjustPrepDinner -> s.copy(prepDinner = intent.minutes).revalidate()
            CreateIntent.Generate -> s // 生成副作用由 ViewModel 处理
        }
    }

    /** 早餐变化 → 重新生成 3 套候选并同步选中项 */
    private fun CreateUiState.Content.regen(): CreateUiState.Content {
        val breakfast = Time.at(today, breakfastTime, zone)
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

    /** 选中候选 → 同步午/晚时间与备餐默认值 */
    private fun CreateUiState.Content.syncFromCandidate(): CreateUiState.Content {
        val c = candidates.getOrNull(selectedIndex) ?: return this
        return copy(
            lunchTime = Time.timeOf(c.lunch, zone),
            dinnerTime = Time.timeOf(c.dinner, zone),
            prepLunch = prepLunchDefault,
            prepDinner = prepDinnerDefault,
        ).revalidate()
    }

    /** 微调 → 实时约束校验（PlanValidator） */
    private fun CreateUiState.Content.revalidate(): CreateUiState.Content {
        val breakfast = Time.at(today, breakfastTime, zone)
        val lunch = Time.at(today, lunchTime, zone)
        val dinner = Time.at(today, dinnerTime, zone)
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
    private val checkInUseCase: CheckInUseCase,
    private val scheduler: PlanScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateUiState>(CreateUiState.Loading)
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val _events = Channel<CreateEvent>(Channel.BUFFERED)
    val events: Flow<CreateEvent> = _events.receiveAsFlow()

    /** 编辑模式：当前编辑的计划 id（-1 = 新建） */
    private var editingPlanId: Long = -1L

    /** 编辑模式：按 planId 加载现有计划预填（Record 详情「编辑计划」入口） */
    fun loadForEdit(planId: Long) {
        viewModelScope.launch {
            val pair = planRepository.getPlanById(planId) ?: return@launch
            val settings = settingsStore.settings.first()
            editingPlanId = planId
            _uiState.value = CreateReducer.editInitial(
                settings = settings,
                plan = pair.first,
                meals = pair.second,
                zone = SystemTimeProvider.zone,
            )
        }
    }

    init {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val zone = SystemTimeProvider.zone
            val breakfast = Time.alignToMinute(clock.instant()).atZone(zone).toLocalTime()
            _uiState.value = CreateReducer.initial(
                settings = settings,
                today = SystemTimeProvider.today(),
                zone = zone,
                defaultBreakfast = breakfast,
            )
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
            val breakfast = Time.at(s.today, s.breakfastTime, zone)
            val window = Duration.ofHours(s.windowHours.toLong())
            val plan = MealPlan(
                // 编辑模式带原 id → savePlan upsert 更新原计划（date 不变，重新生成三餐）
                id = editingPlanId,
                date = s.today,
                windowStart = breakfast,
                windowEnd = breakfast.plus(window),
                status = PlanStatus.ACTIVE,
            )
            val meals = listOf(
                Meal(type = MealType.BREAKFAST, mealTime = breakfast, prepMinutes = 0, reminderMode = s.defaultReminderMode),
                Meal(type = MealType.LUNCH, mealTime = Time.at(s.today, s.lunchTime, zone), prepMinutes = s.prepLunch, reminderMode = s.lunchReminderMode),
                Meal(type = MealType.DINNER, mealTime = Time.at(s.today, s.dinnerTime, zone), prepMinutes = s.prepDinner, reminderMode = s.dinnerReminderMode),
            )
            val planId = planRepository.savePlan(plan, meals)
            // WR-01：编辑/覆盖前先撤销旧闹钟，杜绝孤儿/幽灵提醒
            if (editingPlanId >= 0) scheduler.cancel(editingPlanId)
            // 排程必须用落库后的真实 Meal（含 id）——AlarmReceiver 依 EXTRA_MEAL_ID 查库，
            // 用内存 id=0 的 Meal 会导致到点提醒查不到餐次而被静默丢弃
            val savedMeals = planRepository.watchMealsByDate(s.today).first()
            // IN-03：仅今天及未来排程（编辑历史日期只落库，过期闹钟无意义且会被迟到忽略）
            if (!s.today.isBefore(SystemTimeProvider.today())) {
                scheduler.schedule(plan.copy(id = planId), savedMeals)
            }
            // FR-1 自动打卡早餐（记录即已吃，默认开，可关）；仅新建生效，编辑不重复打卡
            if (editingPlanId < 0 && s.autoCheckIn) {
                checkInUseCase.checkIn(s.today, MealType.BREAKFAST, clock.instant())
            }
            editingPlanId = -1L
            _events.send(CreateEvent.Generated)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 取消必须重抛（coroutines-best-practices §4）
        } catch (e: Exception) {
            // 生成失败兜底：不崩溃应用（M1 记录日志，M2 补 Toast 引导）
            android.util.Log.e(TAG, "生成计划失败", e)
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

    // 编辑模式：planId 进入时加载现有计划预填
    LaunchedEffect(planId) {
        if (planId >= 0) vm.loadForEdit(planId)
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                CreateEvent.Generated -> {
                    // IN-02：计划变更后刷新桌面小组件（updatePeriodMillis=0，事件驱动）
                    Fast16Widget().updateAll(context)
                    onBack()
                }
            }
        }
    }

    when (val s = state) {
        CreateUiState.Loading -> Unit
        is CreateUiState.Content -> CreateContent(
            state = s,
            onIntent = vm::onIntent,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun CreateContent(
    state: CreateUiState.Content,
    modifier: Modifier = Modifier,
    onIntent: (CreateIntent) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部：NEW PLAN + 步骤标签 + 取消（原型 create-head）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                PixelText(
                    text = if (state.isEdit) "EDIT PLAN" else "NEW PLAN",
                    color = PixelColors.white,
                    fontSize = PixelType.Size.xs,
                    digital = true,
                )
                Spacer(modifier = Modifier.height(2.dp))
                PixelText(
                    text = if (state.isEdit) {
                        if (state.step == 1) "编辑今日计划 · 第 1 步" else "编辑今日计划 · 第 2 步"
                    } else {
                        if (state.step == 1) "第 1 步 · 早餐时间" else "第 2 步 · 选方案 + 微调"
                    },
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
            }
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
}

@Composable
private fun BreakfastStep(state: CreateUiState.Content, onIntent: (CreateIntent) -> Unit) {
    PixelText(
        text = "早餐时间是进食窗口起点，默认取当前时间。",
        color = PixelColors.gray,
        fontSize = PixelType.Size.xs,
    )

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    // 早餐时间 stepper（原型 step1：- 08:00 +，±5 分钟）
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(text = "早餐时间", color = PixelColors.gray, fontSize = PixelType.Size.xs)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelStepper(
                value = fmtLocalTime(state.breakfastTime),
                // spec 5.3：早餐范围 00:00–23:55（防 LocalTime 模运算 wrap）
                onDecrease = {
                    if (state.breakfastTime > LocalTime.MIN) {
                        onIntent(CreateIntent.PickBreakfast(state.breakfastTime.minusMinutes(5)))
                    }
                },
                onIncrease = {
                    if (state.breakfastTime <= LocalTime.of(23, 55)) {
                        onIntent(CreateIntent.PickBreakfast(state.breakfastTime.plusMinutes(5)))
                    }
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

    // FR-1 自动打卡早餐开关（原型 step1 auto chip：开/关）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelText(text = "自动打卡早餐（记录即已吃）", color = PixelColors.gray, fontSize = PixelType.Size.xs)
        Box(
            modifier = Modifier
                .background(if (state.autoCheckIn) PixelColors.yellow else PixelColors.panel)
                .border(BorderStroke(PixelShape.borderWidth, Color.Black))
                .clickable { onIntent(CreateIntent.ToggleAutoCheckIn) }
                .padding(horizontal = PixelShape.Spacing.md, vertical = PixelShape.Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            PixelText(
                text = if (state.autoCheckIn) "开" else "关",
                color = if (state.autoCheckIn) PixelColors.bg else PixelColors.white,
                fontSize = PixelType.Size.xs,
            )
        }
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
                    text = "午 ${fmt(candidate.lunch, state.zone)} · 晚 ${fmt(candidate.dinner, state.zone)}",
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

/** LocalTime → HH:mm（Create stepper 显示） */
private fun fmtLocalTime(t: java.time.LocalTime): String =
    "%02d:%02d".format(t.hour, t.minute)

/** Instant → HH:mm（指定时区，避免裸 systemDefault） */
private fun fmt(instant: Instant, zone: ZoneId): String =
    "%02d:%02d".format(Time.timeOf(instant, zone).hour, Time.timeOf(instant, zone).minute)

@PreviewPixel
@Composable
private fun CreateScreenPreview() {
    PixelTheme {
        CreateContent(
            state = CreateReducer.initial(
                settings = AppSettings(
                    windowHours = 8, minMealGapMinutes = 180, dinnerBufferMinutes = 30,
                    defaultPrepLunchMinutes = 30, defaultPrepDinnerMinutes = 45, defaultPrepBreakfastMinutes = 0,
                    defaultReminderMode = ReminderMode.NOTIFY,
                    widgetShowFasting = true, onboardingSeen = true,
                ),
                today = LocalDate.of(2026, 8, 31),
                zone = ZoneId.of("Asia/Shanghai"),
                defaultBreakfast = LocalTime.of(8, 0),
            ),
            onIntent = {},
            onBack = {},
        )
    }
}
