package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ly.fast16.core.designsystem.token.PixelMotion
import kotlinx.coroutines.delay

/**
 * 精灵帧循环（基建 §2.4 M1 / 设计方案 §8.5）：
 * 按 [frameMs]（默认 120ms/帧，PixelMotion）循环切换帧内容（Composable slot），
 * 阶跃切换无平滑；用于角色待机/气泡弹出等占位帧动画（正式美术 M2 替换帧内容）。
 *
 * @param frames 帧渲染列表（>=1）；单帧时不循环（静态占位）。
 */
@Composable
fun PixelFrameAnimation(
    frames: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    frameMs: Long = PixelMotion.FRAME_MS,
) {
    if (frames.isEmpty()) return
    if (frames.size == 1) {
        frames[0]()
        return
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(frames.size) {
        while (true) {
            delay(frameMs)
            index = (index + 1) % frames.size
        }
    }
    Box(modifier = modifier) {
        frames[index]()
    }
}
