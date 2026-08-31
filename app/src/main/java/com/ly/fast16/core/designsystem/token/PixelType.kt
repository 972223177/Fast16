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

    object Size {
        /** 12sp：辅助/小字 */
        val xs: TextUnit = 12.sp

        /** 16sp：正文（文档字阶 16） */
        val sm: TextUnit = 16.sp

        /** 24sp：强调数字/标题（文档字阶 24） */
        val md: TextUnit = 24.sp

        /** 32sp：大计时/页面主标题（文档字阶 32） */
        val lg: TextUnit = 32.sp
    }

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
