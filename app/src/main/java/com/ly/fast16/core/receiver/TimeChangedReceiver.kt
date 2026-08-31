package com.ly.fast16.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 时间/时区变更自愈：改时间或时区后重排全部提醒闹钟。
 *
 * M0 占位：空实现，M1 填充（见 流程图 §7）。
 * lint 安全要求：受保护广播（TIME_SET / TIMEZONE_CHANGED）必须校验 action 防止伪造 Intent。
 */
class TimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_TIME_CHANGED && action != Intent.ACTION_TIMEZONE_CHANGED) return
        // TODO(M1): PlanScheduler.rescheduleAll()
    }
}
