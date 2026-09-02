package com.ly.fast16.feature.create.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.core.time.Time
import com.ly.fast16.data.local.SettingsStore
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.model.PlanStatus
import com.ly.fast16.domain.repository.PlanRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

/** 新建计划 ViewModel（FR-1~FR-5）：候选→微调→生成→落库→排程 */
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
