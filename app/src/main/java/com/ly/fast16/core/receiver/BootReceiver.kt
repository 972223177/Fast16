package com.ly.fast16.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ly.fast16.core.scheduling.PlanScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 开机自愈（流程图 §7）：重启后重排全部提醒闹钟。
 *
 * 依赖经 KoinComponent + inject 惰性解析（BroadcastReceiver 由系统实例化）；
 * goAsync + try/finally 兜底 finish（注入失败也不泄漏 Receiver）。
 * lint 安全要求：受保护广播（BOOT_COMPLETED）必须校验 action 防止伪造 Intent。
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduler: PlanScheduler by inject()
    private val dispatcher: CoroutineDispatcher by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        rescheduleAsync()
    }

    private fun rescheduleAsync() {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        scope.launch {
            try {
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
