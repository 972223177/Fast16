package com.ly.fast16.core.designsystem.component

import androidx.compose.ui.tooling.preview.Preview

/** 像素组件统一 Preview 注解（深色背景展示，AGENTS.md §6：Preview 必写） */
@Preview(
    showBackground = true,
    backgroundColor = 0xFF0F0F1A,
    widthDp = 320,
)
annotation class PreviewPixel
