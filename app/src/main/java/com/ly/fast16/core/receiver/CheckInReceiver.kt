package com.ly.fast16.core.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.notification.NotificationFactory
import com.ly.fast16.core.widget.Fast16Widget
import com.ly.fast16.domain.repository.PlanRepository
import com.ly.fast16.domain.usecase.CheckInUseCase
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 通知「打卡」action 入口（流程图 §8 入口1 · 通知动作按钮）。
 *
 * 仅执行打卡写入 + 移除已处理通知（不启动 Activity，31+ trampoline 红线合规）。
 * 依赖经 KoinComponent + inject 惰性解析（全局 Koin 上下文）。
 */
class CheckInReceiver : BroadcastReceiver(), KoinComponent {

    private val planRepository: PlanRepository by inject()
    private val checkInUseCase: CheckInUseCase by inject()
    private val dispatcher: CoroutineDispatcher by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationFactory.ACTION_CHECK_IN) return
        val mealId = intent.getLongExtra(NotificationFactory.EXTRA_MEAL_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationFactory.EXTRA_NOTIFICATION_ID, -1)
        if (mealId < 0) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        scope.launch {
            try {
                planRepository.getMealById(mealId)?.let { meal ->
                    checkInUseCase.checkInMeal(meal, SystemTimeProvider.now())
                    Fast16Widget().updateAll(context) // 打卡 → 桌面小组件同步
                }
                if (notificationId >= 0) {
                    context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
