package com.ly.fast16.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ly.fast16.MainActivity
import com.ly.fast16.core.receiver.CheckInReceiver
import com.ly.fast16.domain.model.Meal
import com.ly.fast16.domain.model.MealPhase

/**
 * 通知工厂（compat-api24-37 §0/§5 红线，版本差异收口 core/notification）：
 *
 * - [createChannel]：26+ 必建渠道（meal_reminder，IMPORTANCE_HIGH），否则通知静默丢弃；
 *   **在 Fast16App.onCreate 调用**（App 启动即建，不依赖首次 trigger）。
 * - [buildReminder]：构建提醒通知——点击 PendingIntent.getActivity 直达 MainActivity
 *   （禁 trampoline，31+ 红线）；附加「打卡」action → CheckInReceiver（广播执行打卡，
 *   非启动 Activity）；一律 FLAG_IMMUTABLE（31+ 强制）。
 */
class NotificationFactory(private val context: Context) {

    companion object {
        const val CHANNEL_MEAL_REMINDER = "meal_reminder"
        const val ACTION_CHECK_IN = "com.ly.fast16.action.CHECK_IN"
        const val EXTRA_MEAL_ID = "extra_meal_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    /** 26+ 创建提醒渠道（<26 无需渠道） */
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_MEAL_REMINDER,
            "餐次提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "餐前准备 / 开始吃提醒"
        }
        manager.createNotificationChannel(channel)
    }

    /** 构建提醒通知（文案由调用方从 SpeechCatalog 取，保证与气泡同源） */
    fun buildReminder(
        notificationId: Int,
        meal: Meal,
        phase: MealPhase,
        text: String,
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

        return NotificationCompat.Builder(context, CHANNEL_MEAL_REMINDER)
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
