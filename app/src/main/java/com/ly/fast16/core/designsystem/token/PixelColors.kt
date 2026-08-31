package com.ly.fast16.core.designsystem.token

import androidx.compose.ui.graphics.Color

/**
 * 像素调色板（基建文档 §2.1 定值，PICO-8/NES 观感）。
 *
 * 业务组件**只引用本令牌**，不直接引用 M3 默认值（AGENTS.md §6 像素风规范）。
 */
object PixelColors {
    /** 全局背景（暗色优先） */
    val bg = Color(0xFF0F0F1A)

    /** 卡片/面板底 */
    val panel = Color(0xFF1A1C2C)

    /** 「开始吃」、进行中、成功、已打卡 */
    val green = Color(0xFF38B764)

    /** 「餐前准备」、待处理、待打卡、选中描边 */
    val yellow = Color(0xFFFFCD75)

    /** 断食中、警示、错误 */
    val red = Color(0xFFB13E53)

    /** 信息、链接、选中 */
    val blue = Color(0xFF41A6F6)

    /** 正文、图标高亮 */
    val white = Color(0xFFF4F4F4)

    /** 次要文字、禁用、无计划 */
    val gray = Color(0xFF7B7B8C)
}
