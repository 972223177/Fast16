package com.ly.fast16.core.scheduling

import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPlan

/**
 * 就餐计划调度（基建文档 §1.3 契约 / 设计方案 §5.3）：
 * 对每个 Meal 的 PREP/EATING 时刻经 AlarmManager 注册精确闹钟。
 *
 * 接口先行，M1 实现 [AlarmPlanScheduler] 时须满足 compat-api24-37 §5 红线：
 * - 排程前 `canScheduleExactAlarms()` 检查，未授权走设置引导 + `setInexactRepeating` 降级
 * - `PendingIntent` 一律 `FLAG_IMMUTABLE`
 * - 不用后台 Service / FGS
 */
interface PlanScheduler {
    /** 排程 PREP/EATING 闹钟 */
    fun schedule(plan: MealPlan, meals: List<Meal>)

    /** 撤销该计划全部闹钟 */
    fun cancel(planId: Long)

    /** 重启 / 时区自愈：重排全部有效计划的闹钟（BootReceiver / TimeChangedReceiver 调用） */
    fun rescheduleAll()
}

/** M0 占位实现：M1 填充 AlarmManager 逻辑 */
class AlarmPlanScheduler : PlanScheduler {
    override fun schedule(plan: MealPlan, meals: List<Meal>) {
        // TODO(M1): setExactAndAllowWhileIdle + canScheduleExactAlarms 检查 + PendingIntent(FLAG_IMMUTABLE)
    }

    override fun cancel(planId: Long) {
        // TODO(M1): 遍历该计划 mealId 取消对应 PendingIntent
    }

    override fun rescheduleAll() {
        // TODO(M1): 遍历今日计划所有 Meal 重排
    }
}
