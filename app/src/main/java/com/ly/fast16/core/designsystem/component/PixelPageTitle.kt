package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 页面标题规范组件（**所有带标题的新页面必须使用**，design-spec 页面头统一规范）：
 * - 大标题（数字/英文，Press Start 2P 像素字）**打字机逐字**（PixelTypewriterText，60ms/字 + 闪烁光标）；
 * - 可选中文副标题（静态，灰字小号）。
 *
 * 切页时 composable 重建 → 打字机自然重放（像素游戏感）；替代各屏手写的
 * `PixelText("TODAY") + 副标题` 组合，保证后续新页面标题行为一致。
 *
 * @param title 页面大标题（如 "TODAY" / "RECORD" / "SETTINGS" / "NEW PLAN"）
 * @param subtitle 可选副标题（中文说明，静态）
 */
@Composable
fun PixelPageTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier) {
        PixelTypewriterText(
            text = title,
            color = PixelColors.white,
            fontSize = PixelType.Size.xs,
            digital = true,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            PixelText(text = subtitle, color = PixelColors.gray, fontSize = PixelType.Size.xs)
        }
    }
}

@PreviewPixel
@Composable
private fun PixelPageTitlePreview() {
    PixelTheme {
        PixelPageTitle(title = "RECORD", subtitle = "统计概览 · 日历打卡")
    }
}
