package com.ly.fast16.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType

/** 合规文本 assets 路径常量（唯一事实源，App 内各入口统一引用，避免路径字面量散落） */
object LegalAssets {
    const val PRIVACY_POLICY = "privacy_policy.txt"
    const val USER_AGREEMENT = "user_agreement.txt"
}

/**
 * 合规文本弹窗（隐私政策 / 用户协议，跨 feature 共享 → core/designsystem）：
 * 本地 assets 读取展示，不申请网络权限（纯本地离线基线，设计方案 §6 本地优先）；
 * 内容超长时在 70% 屏高内滚动查看，底部【关闭】退出。
 */
@Composable
fun LegalDialog(
    title: String,
    assetPath: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    // runCatching：assets 读取失败（含 Preview 无资源上下文）时兜底占位，不崩溃
    LaunchedEffect(assetPath) {
        text = runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
    PixelDialog(onDismiss = onDismiss, title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .verticalScroll(rememberScrollState()),
        ) {
            PixelText(
                text = text ?: "内容加载中…",
                color = PixelColors.white,
                fontSize = PixelType.Size.xs,
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            PixelButton(
                text = "关闭",
                onClick = onDismiss,
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewPixel
@Composable
private fun LegalDialogPreview() {
    PixelTheme {
        LegalDialog(title = "隐私政策", assetPath = LegalAssets.PRIVACY_POLICY, onDismiss = {})
    }
}
