package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ly.fast16.core.designsystem.token.PixelShape

/**
 * 像素页骨架：固定顶部（标题栏）+ 底部可滚动内容区 + 条件像素滚动条。
 *
 * 统一 Home / Record / Settings 三 tab 页的「Box(TopCenter) + Column(widthIn max) +
 * 固定标题 + Box(weight){ verticalScroll + scrollbar }」结构，避免三处手写重复；
 * 滚动条仅在内容真正溢出时出现（一屏可见典型态不画空轨道）。
 *
 * @param header 固定标题区（不随内容滚动出屏，含 PixelPageTitle 与分隔线）
 * @param content 滚动区内容（垂直滚动，可溢出）
 * @param contentPadding 滚动区 padding（Settings 等页可传 bottom = xxl）
 */
@Composable
fun PixelPageScaffold(
    modifier: Modifier = Modifier,
    headerAlignment: Alignment.Horizontal = Alignment.Start,
    contentAlignment: Alignment.Horizontal = Alignment.Start,
    contentPadding: PaddingValues = PaddingValues(
        start = PixelShape.Spacing.lg,
        end = PixelShape.Spacing.lg,
        top = PixelShape.Spacing.lg,
        bottom = PixelShape.Spacing.lg,
    ),
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = PixelShape.contentMaxWidth),
        ) {
            // 固定标题栏：不随内容滚动出屏
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PixelShape.Spacing.lg,
                        end = PixelShape.Spacing.lg,
                        top = PixelShape.Spacing.lg,
                    ),
                horizontalAlignment = headerAlignment,
            ) {
                header()
            }
            // 滚动区（标题栏以下，weight 占满剩余高度）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // overscrollEffect = null 关掉边缘拉伸：① 内容本就一屏可见时，滑动不再产生
                        // 「还能往下滚」的错觉，消除滚动交互负担；② 边缘拉伸属 M3 平滑动效，与像素风
                        // 阶跃规范冲突。矮屏真需要滚动时滚动本身照常工作，只是没有回弹。
                        .verticalScroll(
                            state = scrollState,
                            overscrollEffect = null,
                        )
                        .padding(contentPadding),
                    horizontalAlignment = contentAlignment,
                ) {
                    content()
                }
                // 像素滚动条：仅在内容真的溢出时出现（一屏可见典型态不溢出 → 不画空轨道）
                if (scrollState.maxValue > 0) {
                    PixelVerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = PixelShape.Spacing.sm),
                    )
                }
            }
        }
    }
}
