package com.ly.fast16.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.scheduling.ReminderChannel
import com.ly.fast16.domain.model.MealPhase
import com.ly.fast16.domain.repository.PlanRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 精确闹钟触发入口（设计方案 §5.3 / 流程图 §6）：
 * 解析 alarm 携带 mealId + phase → 查 Meal → 迟到忽略（>10min）→ ReminderChannel.trigger。
 *
 * BroadcastReceiver 由系统实例化：依赖经 KoinComponent + inject 惰性解析（全局 Koin 上下文）。
 */
class AlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val planRepository: PlanRepository by inject()
    private val reminder: ReminderChannel by inject()
    private val dispatcher: CoroutineDispatcher by inject()

    companion object {
        const val EXTRA_MEAL_ID = "extra_meal_id"
        const val EXTRA_PHASE = "extra_phase"

        /** 迟到容差：闹钟晚触发超过 10 分钟则忽略（防重启后补发过期提醒） */
        const val LATE_GRACE_MS = 10 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val mealId = intent.getLongExtra(EXTRA_MEAL_ID, -1L)
        val phase = intent.getStringExtra(EXTRA_PHASE)
            ?.let { raw -> runCatching { MealPhase.valueOf(raw) }.getOrNull() }
        if (mealId < 0 || phase == null) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        scope.launch {
            try {
                handle(mealId, phase)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(mealId: Long, phase: MealPhase) {
        val meal = planRepository.getMealById(mealId) ?: return // 计划已删除/不存在
        val triggerAt = if (phase == MealPhase.PREP) meal.prepTime else meal.mealTime
        val now = SystemTimeProvider.now()
        if (now.toEpochMilli() - triggerAt.toEpochMilli() > LATE_GRACE_MS) return // 迟到忽略
        reminder.trigger(meal, phase)
    }
}
