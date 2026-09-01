package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.domain.model.CharacterState

/**
 * 像素加载占位（P2）：待机角色帧动画 + 「加载中」小字。
 * 四屏 Loading 态统一使用，替代空白首帧。
 */
@Composable
fun PixelLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PixelShape.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelCharacter(state = CharacterState.IDLE)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
        PixelText(text = "加载中…", color = PixelColors.gray, fontSize = PixelType.Size.xs)
    }
}

@PreviewPixel
@Composable
private fun PixelLoadingPreview() {
    PixelTheme {
        PixelLoading()
    }
}
