package com.ly.fast16.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自愈：重启后重排全部提醒闹钟。
 *
 * M0 占位：空实现，M1 填充（见 流程图 §7）。
 * lint 安全要求：受保护广播（BOOT_COMPLETED）必须校验 action 防止伪造 Intent。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // TODO(M1): PlanScheduler.rescheduleAll()
    }
}
