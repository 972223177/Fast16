package com.ly.fast16.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ly.fast16.core.receiver.AlarmReceiver
import com.ly.fast16.core.system.ExactAlarmGate
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPhase
import com.ly.fast16.domain.model.MealPlan
import com.ly.fast16.domain.model.MealType

/**
 * 闹钟 requestCode 契约（可测、cancel 可遍历撤销）：
 * `planId * 10 + slot`，slot = type.ordinal * 2 + (PREP=0 / EATING=1)，范围 0..5。
 * 通知 id 同基复用（mealId * 10 + slot），保证打卡 action 能反查取消通知。
 */
/**
 * 就餐计划调度（基建文档 §1.3 契约 / 设计方案 §5.3）：
 * 对每个 Meal 的 PREP/EATING 时刻经 AlarmManager 注册精确闹钟。
 */
interface PlanScheduler {
    /** 排程 PREP/EATING 闹钟 */
    fun schedule(plan: MealPlan, meals: List<Meal>)

    /** 撤销该计划全部闹钟 */
    fun cancel(planId: Long)

    /** 重启 / 时区自愈：重排全部有效计划的闹钟（BootReceiver / TimeChangedReceiver 调用） */
    fun rescheduleAll()
}

/**
 * 闹钟 requestCode 契约（可测、cancel 可遍历撤销）：
 * `planId * 10 + slot`，slot = type.ordinal * 2 + (PREP=0 / EATING=1)，范围 0..5。
 * 通知 id 同基复用（mealId * 10 + slot），保证打卡 action 能反查取消通知。
 */
object AlarmRequestCodes {
    private fun slot(mealType: MealType, phase: MealPhase): Int =
        mealType.ordinal * 2 + if (phase == MealPhase.PREP) 0 else 1

    fun of(planId: Long, mealType: MealType, phase: MealPhase): Int =
        (planId * 10L + slot(mealType, phase)).toInt()

    fun notificationOf(mealId: Long, mealType: MealType, phase: MealPhase): Int =
        (mealId * 10L + slot(mealType, phase)).toInt()
}

/**
 * 就餐计划调度（设计方案 §5.3 / 流程图 §6）。
 *
 * - 对每 Meal 的 PREP/EATING 时刻注册闹钟：`setExactAndAllowWhileIdle`（NFR-1 精确到分钟）
 * - PendingIntent 一律 FLAG_IMMUTABLE（compat-api24-37 §5 红线）
 * - 精确闹钟未授权（Android 14+ 默认拒绝）→ 降级 `set(RTC_WAKEUP)` 非精确（可能被 Doze 延迟，
 *   属可接受降级；引导授权见 ExactAlarmGate）
 * - cancel(planId) 按 requestCode 契约遍历撤销
 * - rescheduleAll：M2 完整自愈（Boot/TimeChanged 调用），本期骨架
 */
class AlarmPlanScheduler(
    private val context: Context,
    private val exactAlarmGate: ExactAlarmGate,
) : PlanScheduler {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    override fun schedule(plan: MealPlan, meals: List<Meal>) {
        meals.forEach { meal -> scheduleMeal(plan.id, meal) }
    }

    private fun scheduleMeal(planId: Long, meal: Meal) {
        // PREP：prepMinutes > 0 才有备餐阶段（早餐默认 0）
        if (meal.prepMinutes > 0) {
            scheduleOne(planId, meal, MealPhase.PREP, meal.prepTime)
        }
        scheduleOne(planId, meal, MealPhase.EATING, meal.mealTime)
    }

    private fun scheduleOne(planId: Long, meal: Meal, phase: MealPhase, triggerAt: java.time.Instant) {
        val alarm = alarmManager ?: return
        val code = AlarmRequestCodes.of(planId, meal.type, phase)
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_MEAL_ID, meal.id)
            .putExtra(AlarmReceiver.EXTRA_PHASE, phase.name)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerMillis = triggerAt.toEpochMilli()
        if (exactAlarmGate.canScheduleExact) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            // 降级：非精确（Doze 下可能延迟；用户可经 ExactAlarmGate 引导授权后升级为精确）
            alarm.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    override fun cancel(planId: Long) {
        val alarm = alarmManager ?: return
        // 契约遍历：planId*10 .. planId*10+5（6 个 slot）
        for (slot in 0..5) {
            val code = (planId * 10L + slot).toInt()
            val existing = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: continue
            alarm.cancel(existing)
            existing.cancel()
        }
    }

    override fun rescheduleAll() {
        // TODO(M2): 遍历今日计划所有 Meal 重排（Boot/TimeChanged 自愈）
    }
}
