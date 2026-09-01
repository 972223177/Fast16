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
 * 时间/时区变更自愈（流程图 §7）：改时间或时区后重排全部提醒闹钟。
 *
 * 依赖经 KoinComponent + inject 惰性解析；goAsync + try/finally 兜底 finish。
 * lint 安全要求：受保护广播（TIME_SET / TIMEZONE_CHANGED）必须校验 action 防止伪造 Intent。
 */
class TimeChangedReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduler: PlanScheduler by inject()
    private val dispatcher: CoroutineDispatcher by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_TIME_CHANGED && action != Intent.ACTION_TIMEZONE_CHANGED) return
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
