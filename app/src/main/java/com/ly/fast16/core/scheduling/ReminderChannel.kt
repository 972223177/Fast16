package com.ly.fast16.core.scheduling

import android.app.NotificationManager
import android.content.Context
import com.ly.fast16.core.character.SpeechCatalog
import com.ly.fast16.core.notification.NotificationFactory
import com.ly.fast16.core.notification.VibratorCompat
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPhase

/**
 * 提醒策略接口（设计方案 §5.3 扩展点）：
 * 通知文案与像素气泡同源（取 SpeechCatalog）；V1 只实现 [NotificationReminderChannel]
 * （通知 + 固定轻短震动 ~80ms）；VIBRATE / SOUND / ALARM 为 M3 扩展实现。
 */
interface ReminderChannel {
    fun trigger(meal: Meal, phase: MealPhase)
}

/**
 * V1 提醒实现（FR-6）：
 * 文案 = SpeechCatalog（与 PixelBubble 同源）→ NotificationFactory 构建（点击直达 + 打卡 action）
 * → notify → 80ms 轻震（VibratorCompat）。
 */
class NotificationReminderChannel(
    private val context: Context,
    private val notificationFactory: NotificationFactory,
    private val vibratorCompat: VibratorCompat,
) : ReminderChannel {

    override fun trigger(meal: Meal, phase: MealPhase) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val vars = mapOf(
            "餐名" to SpeechCatalog.mealName(meal.type),
            "备餐分钟" to meal.prepMinutes.toString(),
        )
        val text = SpeechCatalog.text(phase, meal.type, vars)
        val id = AlarmRequestCodes.notificationOf(meal.id, meal.type, phase)
        manager.notify(id, notificationFactory.buildReminder(id, meal, phase, text))
        vibratorCompat.buzz()
    }
}
