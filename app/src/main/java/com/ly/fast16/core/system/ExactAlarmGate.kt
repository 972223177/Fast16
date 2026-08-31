package com.ly.fast16.core.system

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * 精确闹钟权限门（compat-api24-37 §0/§5 红线，版本差异收口 core/system）。
 *
 * - API 31+ 才有精确闹钟权限概念：`canScheduleExactAlarms()` 检查；
 *   <31 视为已授权（无该权限体系）。
 * - API 14（Android 14）起对新装应用 `SCHEDULE_EXACT_ALARM` **默认拒绝**，
 *   需引导用户到系统设置授权（[grantIntent]），未授权走 setInexactRepeating 降级。
 *
 * feature 层只依赖本类，禁止裸 `Build.VERSION` / `@SuppressLint("NewApi")`。
 */
class ExactAlarmGate(private val context: Context) {

    /** 是否可精确排程（<31 恒 true；31+ 走系统检查） */
    val canScheduleExact: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /**
     * 引导授权 Intent（31+ 且未授权时给用户跳系统精确闹钟设置页）。
     * 仅在 [canScheduleExact] == false 时调用，否则返回 null。
     */
    fun grantIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            null
        }
}
