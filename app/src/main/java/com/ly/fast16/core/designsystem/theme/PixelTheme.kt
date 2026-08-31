package com.ly.fast16.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelType

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
 */
@Composable
fun PixelTheme(content: @Composable () -> Unit) {
    val base = Typography()
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
            scrim = Color.Black.copy(alpha = 0.6f),
        ),
        typography = Typography(
            // 超大标题：中文像素
            displayLarge = base.displayLarge.pixelTitle(PixelType.Size.lg),
            displayMedium = base.displayMedium.pixelTitle(PixelType.Size.md),
            displaySmall = base.displaySmall.pixelTitle(PixelType.Size.md),
            // 大数字标题（计时）：Press Start 2P
            headlineLarge = base.headlineLarge.pixelDigital(PixelType.Size.lg),
            headlineMedium = base.headlineMedium.pixelTitle(PixelType.Size.md),
            headlineSmall = base.headlineSmall.pixelTitle(PixelType.Size.sm),
            titleLarge = base.titleLarge.pixelTitle(PixelType.Size.md),
            titleMedium = base.titleMedium.pixelTitle(PixelType.Size.sm),
            titleSmall = base.titleSmall.pixelTitle(PixelType.Size.xs),
            // 正文：系统黑体（可读性）
            bodyLarge = base.bodyLarge.pixelBody(PixelType.Size.sm),
            bodyMedium = base.bodyMedium.pixelBody(PixelType.Size.sm),
            bodySmall = base.bodySmall.pixelBody(PixelType.Size.xs),
            // 标签/按钮：中文像素
            labelLarge = base.labelLarge.pixelTitle(PixelType.Size.sm),
            labelMedium = base.labelMedium.pixelTitle(PixelType.Size.xs),
            labelSmall = base.labelSmall.pixelTitle(PixelType.Size.xs),
        ),
        content = content,
    )
}

/** 中文像素标题样式 */
private fun TextStyle.pixelTitle(size: TextUnit) = copy(
    fontFamily = PixelType.cjk,
    fontWeight = FontWeight.Normal,
    fontSize = size,
)

/** 数字像素标题样式（Press Start 2P + 宽字距突出「跳字」感） */
private fun TextStyle.pixelDigital(size: TextUnit) = copy(
    fontFamily = PixelType.digital,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    letterSpacing = 2.sp,
)

/** 正文样式（系统黑体） */
private fun TextStyle.pixelBody(size: TextUnit) = copy(
    fontFamily = PixelType.bodyFamily,
    fontWeight = FontWeight.Normal,
    fontSize = size,
)
