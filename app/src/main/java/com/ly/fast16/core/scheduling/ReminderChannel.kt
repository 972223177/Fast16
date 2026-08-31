package com.ly.fast16.core.scheduling

import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPhase

/**
 * 提醒策略接口（设计方案 §5.3 扩展点）：
 * 通知文案与像素气泡同源（取 SpeechCatalog，M1 落地）。
 *
 * V1 只实现 [NotificationReminderChannel]（通知 + 固定轻短震动 ~80ms）；
 * VIBRATE / SOUND / ALARM 为 M3 扩展实现，不推翻现有模型。
 */
interface ReminderChannel {
    fun trigger(meal: Meal, phase: MealPhase)
}

/** M0 占位实现：M1 填充通知渠道（26+ 必须建 Channel，33+ 运行时权限，见 compat-api24-37 §0/§5） */
class NotificationReminderChannel : ReminderChannel {
    override fun trigger(meal: Meal, phase: MealPhase) {
        // TODO(M1): NotificationManager + 轻短震动（单次 ~80ms）+ 通知点击直达 Activity（无 trampoline）
    }
}
