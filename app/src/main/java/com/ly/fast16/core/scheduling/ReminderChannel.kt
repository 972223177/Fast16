package com.ly.fast16.core.scheduling

import android.app.NotificationManager
import android.content.Context
import com.ly.fast16.core.character.SpeechCatalog
import com.ly.fast16.core.notification.NotificationFactory
import com.ly.fast16.core.notification.VibratorCompat
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPhase
import com.ly.fast16.domain.model.ReminderMode

/**
 * 提醒策略接口（设计方案 §5.3 扩展点）：
 * 通知文案与像素气泡同源（取 SpeechCatalog）。
 */
interface ReminderChannel {
    fun trigger(meal: Meal, phase: MealPhase)
}

/**
 * 提醒实现（FR-6 / M3 多模式）：
 * 文案 = SpeechCatalog（与 PixelBubble 同源）→ NotificationFactory 构建（点击直达 + 打卡 action）
 * → 按 `meal.reminderMode` 选渠道 notify：
 * - NOTIFY：默认渠道 + 80ms 轻震（VibratorCompat）
 * - VIBRATE：震动渠道（渠道级 pattern，无声，不额外 buzz）
 * - SOUND：声音渠道（系统提示音，渠道级）
 * - ALARM：默认高重要度渠道 + 轻震（设备侧表现 DEFERRED，语义=提醒+震动）
 */
class NotificationReminderChannel(
    private val context: Context,
    private val notificationFactory: NotificationFactory,
    private val vibratorCompat: VibratorCompat,
) : ReminderChannel {

    override fun trigger(meal: Meal, phase: MealPhase) {
        // WR-06：NONE = 不提醒，显式短路（防未来 NONE 默认值静默弹通知）
        if (meal.reminderMode == ReminderMode.NONE) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val vars = mapOf(
            "餐名" to SpeechCatalog.mealName(meal.type),
            "备餐分钟" to meal.prepMinutes.toString(),
        )
        val text = SpeechCatalog.text(phase, meal.type, vars)
        val id = AlarmRequestCodes.notificationOf(meal.id, meal.type, phase)
        val channelId = NotificationFactory.channelFor(meal.reminderMode)
        manager.notify(id, notificationFactory.buildReminder(id, meal, phase, text, channelId))
        // NOTIFY / ALARM 附轻震（VIBRATE 渠道自带 pattern；SOUND 渠道无震）
        if (meal.reminderMode == ReminderMode.NOTIFY || meal.reminderMode == ReminderMode.ALARM) {
            vibratorCompat.buzz()
        }
    }
}
