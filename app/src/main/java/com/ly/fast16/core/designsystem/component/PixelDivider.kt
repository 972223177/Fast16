package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.token.PixelColors

/** 像素分隔线（原型 hr：2px 面板深色）；页面标题栏下方使用 */
@Composable
fun PixelDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(PixelColors.panel.copy(alpha = 0.6f)),
    )
}
