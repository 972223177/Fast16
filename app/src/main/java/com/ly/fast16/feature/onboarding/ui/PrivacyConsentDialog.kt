package com.ly.fast16.feature.onboarding.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ly.fast16.core.designsystem.component.LegalAssets
import com.ly.fast16.core.designsystem.component.LegalDialog
import com.ly.fast16.core.designsystem.component.PixelBackdrop
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelDialog
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.data.local.SettingsStore
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 首次启动隐私同意 gate（国内商店上架合规，《个人信息保护法》§16）：
 * 根层 overlay（非路由）：privacyAccepted==false 时**不渲染主界面**，仅显示同意弹窗；
 * 「同意并继续」→ 置 privacyAccepted=true 进入后续引导；「不同意」→ 退出应用（「不同意即退出」是商店审核硬性要求）。
 */
@Composable
fun PrivacyConsentRoot(content: @Composable () -> Unit) {
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by settingsStore.settings.collectAsState(initial = null)
    // settings 未加载（首帧）时不渲染主界面——避免同意弹窗闪现后再进入（冷启动闪一下）
    if (settings == null) return
    val privacyAccepted = settings?.privacyAccepted ?: false

    if (!privacyAccepted) {
        // 全屏黑底 + 像素动态（星点闪烁 / 漂移 / CRT 扫描线）垫底；
        // 弹窗 scrim 置透明（PixelDialog 参数），背景动态完全可见
        Box(modifier = Modifier.fillMaxSize()) {
            PixelBackdrop(modifier = Modifier.fillMaxSize())
            PrivacyConsentDialog(
                onAccept = { scope.launch { settingsStore.setPrivacyAccepted(true) } },
                onDecline = {
                    // 不同意：退出应用
                    (context as? Activity)?.finish()
                },
            )
        }
    } else {
        content()
    }
}

/** 隐私同意弹窗：可查看隐私政策/用户协议全文；不同意退出 / 同意进入 */
@Composable
fun PrivacyConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    var showPrivacy by remember { mutableStateOf(false) }
    var showAgreement by remember { mutableStateOf(false) }
    PixelDialog(onDismiss = onDecline, title = "📋 隐私与条款") {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(
                text = "欢迎使用 Fast16 816 轻断食。使用前请阅读并同意以下条款：",
                color = PixelColors.gray,
                fontSize = PixelType.Size.xs,
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            LegalEntryRow(label = "查看隐私政策", onClick = { showPrivacy = true })
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            LegalEntryRow(label = "查看用户协议", onClick = { showAgreement = true })
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            PixelButton(
                text = "不同意并退出",
                onClick = onDecline,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelButton(
                text = "同意并继续",
                onClick = onAccept,
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showPrivacy) {
        LegalDialog(
            title = "隐私政策",
            assetPath = LegalAssets.PRIVACY_POLICY,
            onDismiss = { showPrivacy = false },
        )
    }
    if (showAgreement) {
        LegalDialog(
            title = "用户协议",
            assetPath = LegalAssets.USER_AGREEMENT,
            onDismiss = { showAgreement = false },
        )
    }
}

/** 弹窗内条款查看行（面板底黑边 + 右侧 ▶） */
@Composable
private fun LegalEntryRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .clickable(onClick = onClick)
            .padding(PixelShape.Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelText(text = label, color = PixelColors.white, fontSize = PixelType.Size.sm)
        PixelText(text = "▶", color = PixelColors.gray, fontSize = PixelType.Size.xs)
    }
}

@PreviewPixel
@Composable
private fun PrivacyConsentDialogPreview() {
    PixelTheme {
        PrivacyConsentDialog(onAccept = {}, onDecline = {})
    }
}
