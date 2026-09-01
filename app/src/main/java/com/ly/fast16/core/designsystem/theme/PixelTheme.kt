package com.ly.fast16.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.data.local.FontMode
import com.ly.fast16.data.local.SettingsStore

/**
 * 像素风深色主题（基建文档 §2.5：V1 深色 only）。
 * M3 ColorScheme 由 PixelColors 覆写；业务组件只引用 tokens，不引用 M3 默认值。
 *
 * 系统栏：Android 15+（API 35）edge-to-edge 强制，`statusBarColor` 已弃用
 * （compat-api24-37 §4/§5）——本主题不设置系统栏颜色，页面背景（PixelColors.bg）
 * 自然延伸至系统栏；MainActivity 走 enableEdgeToEdge + insets。
 *
 * 字体全量覆盖：Typography **15 个样式全部显式映射**（设计方案 §8.3）——
 * 任何未显式传 style 的 M3 Text / 组件内部文字都会命中以下映射：
 * - 标题/强调（display/headline/title/label）→ 中文像素字体 `cjk`（Fusion Pixel）
 * - 大数字标题（headlineLarge）→ 数字像素字体 `digital`（Press Start 2P）
 * - 正文（body*）→ 系统黑体 `bodyFamily`（可读性优先）
 * 注：Compose 不走 Android 系统 `android:fontFamily`，主题 Typography 全覆盖才是全局生效路径。
 *
 * @param settingsStore 字体模式来源（MainActivity 注入；null = 像素风——Preview/测试环境）。
 *        Preview 无法走 Koin 注入，null 保证 @PreviewPixel 可独立渲染。
 */
@Composable
fun PixelTheme(
    settingsStore: SettingsStore? = null,
    content: @Composable () -> Unit,
) {
    // 字体模式：订阅 SettingsStore（SSOT）→ 提供对应字体组；null（Preview/测试）保持像素风
    val fonts by produceState(initialValue = PixelFonts.pixel) {
        if (settingsStore == null) {
            value = PixelFonts.pixel
        } else {
            settingsStore.settings.collect { s ->
                value = if (s.fontMode == FontMode.SYSTEM) PixelFonts.system else PixelFonts.pixel
            }
        }
    }
    val base = Typography()
    CompositionLocalProvider(LocalPixelFonts provides fonts) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = PixelColors.green,
                onPrimary = PixelColors.bg,
                secondary = PixelColors.yellow,
                onSecondary = PixelColors.bg,
                tertiary = PixelColors.blue,
                onTertiary = PixelColors.bg,
                error = PixelColors.red,
                onError = PixelColors.white,
                background = PixelColors.bg,
                onBackground = PixelColors.white,
                surface = PixelColors.panel,
                onSurface = PixelColors.white,
                surfaceVariant = PixelColors.panel,
                onSurfaceVariant = PixelColors.gray,
                outline = PixelColors.gray,
                // spec 3.11 遮罩：rgba(5,5,10,.86)（PixelDialog 等全屏遮罩）
                scrim = Color(0xDB05050A),
            ),
            typography = Typography(
                // 超大标题：中文（按字体模式）
                displayLarge = base.displayLarge.pixelTitle(PixelType.Size.lg, fonts),
                displayMedium = base.displayMedium.pixelTitle(PixelType.Size.md, fonts),
                displaySmall = base.displaySmall.pixelTitle(PixelType.Size.md, fonts),
                // 大数字标题（计时）：数字（按字体模式）
                headlineLarge = base.headlineLarge.pixelDigital(PixelType.Size.lg, fonts),
                headlineMedium = base.headlineMedium.pixelTitle(PixelType.Size.md, fonts),
                headlineSmall = base.headlineSmall.pixelTitle(PixelType.Size.sm, fonts),
                titleLarge = base.titleLarge.pixelTitle(PixelType.Size.md, fonts),
                titleMedium = base.titleMedium.pixelTitle(PixelType.Size.sm, fonts),
                titleSmall = base.titleSmall.pixelTitle(PixelType.Size.xs, fonts),
                // 正文：系统默认字体（可读性，两模式一致）
                bodyLarge = base.bodyLarge.pixelBody(PixelType.Size.sm),
                bodyMedium = base.bodyMedium.pixelBody(PixelType.Size.sm),
                bodySmall = base.bodySmall.pixelBody(PixelType.Size.xs),
                // 标签/按钮：中文（按字体模式）
                labelLarge = base.labelLarge.pixelTitle(PixelType.Size.sm, fonts),
                labelMedium = base.labelMedium.pixelTitle(PixelType.Size.xs, fonts),
                labelSmall = base.labelSmall.pixelTitle(PixelType.Size.xs, fonts),
            ),
            content = content,
        )
    }
}

/** 中文标题样式（按字体模式：像素风 = Fusion Pixel；系统 = 默认字体） */
private fun TextStyle.pixelTitle(size: TextUnit, fonts: PixelFonts) = copy(
    fontFamily = fonts.cjk,
    fontWeight = FontWeight.Normal,
    fontSize = size,
)

/** 数字标题样式（按字体模式：像素风 = Press Start 2P + 宽字距；系统 = 默认字体） */
private fun TextStyle.pixelDigital(size: TextUnit, fonts: PixelFonts) = copy(
    fontFamily = fonts.digital,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    letterSpacing = 2.sp,
)

/** 正文样式（系统默认字体，两模式一致） */
private fun TextStyle.pixelBody(size: TextUnit) = copy(
    fontFamily = PixelType.bodyFamily,
    fontWeight = FontWeight.Normal,
    fontSize = size,
)
