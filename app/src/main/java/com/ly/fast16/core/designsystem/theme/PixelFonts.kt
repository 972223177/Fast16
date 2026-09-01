package com.ly.fast16.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.data.local.FontMode

/**
 * 字体组（cjk = 中文/正文像素字；digital = 数字/英文像素字）。
 *
 * 由 [PixelTheme] 按 [FontMode] 提供（读 SettingsStore），经 [LocalPixelFonts]
 * 下发到全部 Pixel 组件：
 * - [PixelFonts.pixel]：Fusion Pixel（cjk）+ Press Start 2P（digital）
 * - [PixelFonts.system]：全部系统默认字体（Settings 默认，可读性优先）
 *
 * **注意**：Glance Widget 不在 Compose 组合内，不消费本 CompositionLocal，
 * 字体保持像素风（独立实现）。
 */
data class PixelFonts(
    val cjk: FontFamily,
    val digital: FontFamily,
) {
    companion object {
        /** 像素风：Fusion Pixel + Press Start 2P */
        val pixel = PixelFonts(cjk = PixelType.cjk, digital = PixelType.digital)

        /** 系统默认字体（Settings 默认模式） */
        val system = PixelFonts(cjk = FontFamily.Default, digital = FontFamily.Default)
    }
}

/** 当前字体组（PixelTheme 注入；未注入时回退像素风，保证 Preview / 测试可渲染） */
val LocalPixelFonts = staticCompositionLocalOf { PixelFonts.pixel }
