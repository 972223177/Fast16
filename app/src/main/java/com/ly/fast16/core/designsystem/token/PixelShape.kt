package com.ly.fast16.core.designsystem.token

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 像素形状令牌（基建文档 §2.1）：
 * 阶梯圆角 = 3 个 2px 台阶（cornerSteps=3）+ 2px 黑描边。
 *
 * [stairShape] 生成「像素阶梯圆角」Shape：角部按整数像素台阶内切，
 * 不做平滑圆弧（整数像素网格铁律）。
 */
object PixelShape {

    /** 每台阶像素（px，按 density 换算） */
    const val STEP_PX = 2

    /** 台阶数 */
    const val CORNER_STEPS = 3

    /** 描边宽度（业务组件绘制 2px 黑描边统一用此值） */
    val borderWidth: Dp = 2.dp

    /** 像素阶梯圆角 Shape（可复用给 Card / Dialog / Button 背景） */
    val stair: Shape = PixelStairShape()

    /**
     * 紧凑间距体系（一屏可见优化）：4dp 网格，全 App 统一留白。
     *
     * 由 8dp 网格（6/10/16/20/28/40）整体下调约 25%，用于把 Home 等内容页压进
     * 一屏可见高度——配合 [PixelType.LineHeight] 压行高，避免用户靠滚动发现内容。
     */
    object Spacing {
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
    }

    /** 大屏内容最大宽度（平板/横屏不拉伸，保持阅读线宽；底栏仍全宽） */
    val contentMaxWidth: Dp = 560.dp
}

/** 像素阶梯圆角实现（四角对称，台阶按整数 px 交替水平/垂直内切） */
class PixelStairShape(
    private val stepPx: Int = PixelShape.STEP_PX,
    private val cornerSteps: Int = PixelShape.CORNER_STEPS,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val s = stepPx.toFloat()
        val corner = s * cornerSteps
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(0f, corner)
            stepIn(0f, corner, corner, 0f)
            lineTo(w - corner, 0f)
            stepIn(w - corner, 0f, w, corner)
            lineTo(w, h - corner)
            stepIn(w, h - corner, w - corner, h)
            lineTo(corner, h)
            stepIn(corner, h, 0f, h - corner)
            close()
        }
        return Outline.Generic(path)
    }

    /** 从 start 到 end 的阶梯路径：每步先水平再垂直，各走 (dx/steps, dy/steps) */
    private fun Path.stepIn(startX: Float, startY: Float, endX: Float, endY: Float) {
        val dx = (endX - startX) / cornerSteps
        val dy = (endY - startY) / cornerSteps
        var px = startX
        var py = startY
        repeat(cornerSteps) {
            px += dx
            lineTo(px, py)   // 水平段
            py += dy
            lineTo(px, py)   // 垂直段
        }
    }
}
