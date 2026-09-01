package com.ly.fast16.core.system

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 通知权限封装（compat-api24-37 §0/§5 红线，版本差异收口 core/system）。
 *
 * `POST_NOTIFICATIONS` 仅 API 33+ 为运行时权限：<33 视为已授权；
 * 33+ 未授权时通知被静默丢弃（含 FGS 通知），需引导用户授权。
 */
class NotificationPermission(private val context: Context) {

    /** 是否已授权（<33 恒 true） */
    val isGranted: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** 33+ 发起运行时申请（<33 直接成功，无需调用） */
    fun launchRequest(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            requestCode,
        )
    }

    /** 是否应展示「为什么需要通知权限」说明（33+，曾被拒绝一次） */
    fun shouldShowRationale(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )

    /** 是否已「永不再询问」（33+ 拒绝过且系统不再展示 rationale → 只能跳系统设置页） */
    fun isPermanentlyDenied(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )

    /** 跳系统「应用通知」设置页（永不再询问后的引导路径） */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
}
