package com.ly.fast16.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * 桌面小组件「主动添加」（design-spec §1 widget）——Android 8+（API 26）。
 *
 * 判定不依赖品牌名单（厂商桌面行为多变，名单必漏且易误伤），全走行为层：
 * 1. [isPinned]：桌面已有实例 → 不重复弹添加面板；
 * 2. [isRequestPinSupported]（官方行为判定）：默认桌面是否支持 requestPin 流程，
 *    false 如实提示不支持（国行 ColorOS 15/16 卡片商店桌面即属此类）；
 * 3. [requestPin] 返回值：false 不支持；true 仅代表请求已送达——是否真弹面板 /
 *    能否落格由桌面决定，调用方以中性提示兜底，不撒谎也不硬判。
 *
 * 已知平台差异（不做任何厂商分支，交由上述行为判定）：
 * - 原生 / 旧版 ColorOS：弹系统确认框，可正常落格；
 * - 国行 ColorOS 15/16：添加流程被「卡片商店」体系接管，第三方标准 widget 无入口，
 *   requestPin 可能恒 true 却不弹面板；
 * - 小米 MIUI 13 以下：需 Manifest 声明 INSTALL_SHORTCUT 权限，否则静默无效；
 * - vivo：未接入原子组件平台则无效。
 */
object WidgetPinner {

    fun component(context: Context): ComponentName =
        ComponentName(context, Fast16WidgetReceiver::class.java)

    /** 桌面是否已有本 app 的小组件实例 */
    fun isPinned(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    /** 官方行为判定：默认桌面是否支持 requestPin 固定流程（API 26+，系统实现决定）。 */
    fun isRequestPinSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    /**
     * 发起添加到桌面。
     * @return false = 设备/桌面不支持自动添加（< API 26 或桌面明确拒绝）；
     * true = 请求已送达，不保证面板弹出或落格成功。
     */
    fun requestPin(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return AppWidgetManager.getInstance(context)
            .requestPinAppWidget(component(context), null, null)
    }
}
