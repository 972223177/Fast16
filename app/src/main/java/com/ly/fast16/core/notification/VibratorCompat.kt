package com.ly.fast16.core.notification

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * 轻短震动兼容封装（compat-api24-37 红线，版本差异收口 core/notification）。
 *
 * V1 规格：固定单次轻震 ~80ms（设计方案 §11 决策 #2）。
 * - API 26+：VibrationEffect.createOneShot
 * - <26：vibrator.vibrate(ms)
 * - API 31+ 可用 VibratorManager（24–30 用 Vibrator），统一按版本门控。
 *
 * 需 `VIBRATE` 权限（Manifest 已声明）。
 */
class VibratorCompat(context: Context) {

    private val vibrator: Vibrator? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            @Suppress("DEPRECATION")
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        }

        else -> @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** 单次轻震（默认 80ms；不可用时静默忽略） */
    fun buzz(ms: Long = DEFAULT_BUZZ_MS) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(createOneShot(ms))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createOneShot(ms: Long): VibrationEffect =
        VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)

    private companion object {
        const val DEFAULT_BUZZ_MS = 80L
    }
}
