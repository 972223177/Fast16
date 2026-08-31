package com.ly.fast16.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ly.fast16.core.scheduling.ReminderChannel

/**
 * 精确闹钟触发入口：读取 alarm 携带的 mealId + phase，
 * 校验计划有效后交 [ReminderChannel] 触发提醒。
 *
 * M0 占位：空实现，M1 填充（见 设计方案 §5.3 / 流程图 §6）。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO(M1): 解析 mealId + MealPhase → 查 Meal → ReminderChannel.trigger(meal, phase) → 标记去重 → 刷新 Widget
    }
}
