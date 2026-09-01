package com.ly.fast16.core.designsystem.token

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ly.fast16.R

/**
 * 像素字体令牌（基建文档 §2.1 / 设计方案 §8.3）：
 * 数字/英文/计时用 Press Start 2P；中文标题用缝合像素 Fusion Pixel（简体子集）；
 * 正文用系统黑体保证可读性。均为开源可商用字体（授权记录见 docs/font-licenses.md）。
 */
object PixelType {

    /**
     * 字号阶梯（紧凑排版）。
     *
     * **点阵红线**：中文走 Fusion Pixel 12px 点阵字，只有 **12 的整数倍**（12 / 24）
     * 才是点阵原生尺寸，非整数倍会破坏像素网格导致笔画发虚。因此：
     * - [Size.xs] = 12 为全 App 主力正文尺寸，**不可下调**（降到 10/11 会糊）；
     * - 「整体视觉变小」通过收 [PixelShape.Spacing] 间距 + 压数字字体行高实现，
     *   而不是下调 xs 破坏点阵清晰度（行高策略见下方行高 KDoc）；
     * - 数字/英文走 Press Start 2P（矢量轮廓），不受点阵倍数约束，可自由取偶数尺寸。
     */
    object Size {
        /** 12sp：辅助/小字/正文（Fusion Pixel 点阵原生尺寸，勿下调） */
        val xs: TextUnit = 12.sp

        /** 14sp：正文强调（按钮/次要标题） */
        val sm: TextUnit = 14.sp

        /** 16sp：页面大标题（TODAY/RECORD/SETTINGS；英文/数字走 Press Start 2P 偶数尺寸，不破坏网格） */
        val title: TextUnit = 16.sp

        /** 20sp：强调数字/标题 */
        val md: TextUnit = 20.sp

        /** 28sp：大计时/页面主标题 */
        val lg: TextUnit = 28.sp
    }

    /**
     * 行高策略（实测字体 metrics 定档，勿凭直觉调）：
     *
     * | 字体 | hhea | OS/2 win | 字形 bbox | Android 默认行高 @12sp | 处置 |
     * |---|---|---|---|---|---|
     * | Fusion Pixel（中文） | 1.0em | 1.0em | ymin=0 | **12.00px** | 已是 1.0em，**不要设 lineHeight**（设了只会变高） |
     * | Press Start 2P（数字） | 1.0em | **1.374em** | ymin=0 无降部 | **16.49px** | 关 `includeFontPadding` → 1.0em = 12px |
     *
     * 结论：紧凑化只对数字字体生效，由 `PixelText` 在 `digital = true` 时
     * 统一套 `PlatformTextStyle(includeFontPadding = false)`（省 ~4.5px/行）。
     * 中文是子集字体，未覆盖字符（如 🔥、›）会回落到系统字体，回落字形带降部，
     * 关 padding 有裁切风险，故中文一律不干预。
     */

    /** 数字/英文/计时：Press Start 2P（OFL 1.1 可商用，res/font/press_start_2p.ttf） */
    val digital: FontFamily = FontFamily(Font(R.font.press_start_2p))

    /** 中文标题/强调：缝合像素 Fusion Pixel 12px 简体子集（OFL 可商用，res/font/fusion_pixel_12px.ttf） */
    val cjk: FontFamily = FontFamily(Font(R.font.fusion_pixel_12px))

    /** 正文：系统黑体（可读性优先，设计方案 §8.3） */
    val bodyFamily: FontFamily = FontFamily.Default

    /** 正文样式 */
    val body: TextStyle = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Size.sm,
    )

    /** 中文标题样式（像素化） */
    val cjkText: TextStyle = TextStyle(
        fontFamily = cjk,
        fontWeight = FontWeight.Normal,
        fontSize = Size.sm,
    )

    /** 强调数字样式（像素字 + 更宽字距突出「跳字」感） */
    val digitalText: TextStyle = TextStyle(
        fontFamily = digital,
        fontWeight = FontWeight.Normal,
        fontSize = Size.sm,
        letterSpacing = 2.sp,
    )
}
