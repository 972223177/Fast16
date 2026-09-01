package com.ly.fast16.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ly.fast16.MainActivity
import com.ly.fast16.core.receiver.CheckInReceiver
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPhase
import com.ly.fast16.domain.model.ReminderMode

/**
 * 通知工厂（compat-api24-37 §0/§5 红线，版本差异收口 core/notification）：
 *
 * - [createChannel]：26+ 必建三渠道（默认 / 震动 / 声音），否则通知静默丢弃；
 *   **在 Fast16App.onCreate 调用**（App 启动即建，不依赖首次 trigger）。
 * - [buildReminder]：构建提醒通知——点击 PendingIntent.getActivity 直达 MainActivity
 *   （禁 trampoline，31+ 红线）；附加「打卡」action → CheckInReceiver（广播执行打卡，
 *   非启动 Activity）；一律 FLAG_IMMUTABLE（31+ 强制）。
 * - [channelFor]：按 `ReminderMode` 映射渠道（VIBRATE=震动渠道 / SOUND=声音渠道 / 其余=默认）。
 *   后台音频合规：声音走通知渠道（NotificationChannel.setSound），非前台音频流（compat §4）。
 */
class NotificationFactory(private val context: Context) {

    companion object {
        const val CHANNEL_MEAL_REMINDER = "meal_reminder"
        const val CHANNEL_MEAL_VIBRATE = "meal_vibrate"
        const val CHANNEL_MEAL_SOUND = "meal_sound"
        const val ACTION_CHECK_IN = "com.ly.fast16.action.CHECK_IN"
        const val EXTRA_MEAL_ID = "extra_meal_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        /** 按提醒模式映射通知渠道 id（可测纯函数；ALARM 走默认高重要度渠道） */
        fun channelFor(mode: ReminderMode): String = when (mode) {
            ReminderMode.VIBRATE -> CHANNEL_MEAL_VIBRATE
            ReminderMode.SOUND -> CHANNEL_MEAL_SOUND
            else -> CHANNEL_MEAL_REMINDER
        }
    }

    /** 26+ 创建提醒三渠道（<26 无需渠道） */
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEAL_REMINDER,
                "餐次提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "餐前准备 / 开始吃提醒（默认 + 轻震）"
            },
        )
        // 纯震动渠道：无声（setSound(null)），短震动 pattern
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEAL_VIBRATE,
                "餐次提醒（震动）",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "纯震动提醒，无声音"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 80, 60, 80)
            },
        )
        // 声音渠道：系统默认通知音（后台音频合规路径：渠道声音，非前台音频流）
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEAL_SOUND,
                "餐次提醒（声音）",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "系统提示音提醒"
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }

    /** 构建提醒通知（文案由调用方从 SpeechCatalog 取，保证与气泡同源；channelId 按模式映射） */
    fun buildReminder(
        notificationId: Int,
        meal: Meal,
        phase: MealPhase,
        text: String,
        channelId: String = channelFor(meal.reminderMode),
    ): Notification {
        val clickIntent = Intent(context, MainActivity::class.java)
        val clickPending = PendingIntent.getActivity(
            context,
            notificationId,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val checkInIntent = Intent(context, CheckInReceiver::class.java)
            .setAction(ACTION_CHECK_IN)
            .putExtra(EXTRA_MEAL_ID, meal.id)
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        val checkInPending = PendingIntent.getBroadcast(
            context,
            notificationId,
            checkInIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (phase == MealPhase.PREP) "餐前准备" else "开始吃")
            .setContentText(text)
            .setContentIntent(clickPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "打卡", checkInPending)
            .build()
    }
}
