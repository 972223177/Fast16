package com.ly.fast16.feature.onboarding.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelDialog
import com.ly.fast16.core.designsystem.component.PixelProgressBar
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PixelTypewriterText
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.system.NotificationPermission
import com.ly.fast16.data.local.SettingsStore
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val REQ_NOTIFICATION_PERMISSION = 1001

/**
 * 首次启动引导（设计方案 §8.8 / 流程图 §2）：
 * 根层 overlay（非路由）：onboardingSeen==false 时显示 8:16 说明弹窗；
 * 关闭（知道/跳过）均置 seen=true；**关闭后**触发通知权限申请（33+ 运行时，compat §0）。
 */
@Composable
fun OnboardingRoot(content: @Composable () -> Unit) {
    val settingsStore = koinInject<SettingsStore>()
    // IN-02：复用 Koin 单例（避免每次 new），申请前判 isGranted（已授权不再弹系统框）
    val notificationPermission = koinInject<NotificationPermission>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by settingsStore.settings.collectAsState(initial = null)
    val onboardingSeen = settings?.onboardingSeen ?: true

    if (!onboardingSeen) {
        OnboardingDialog(
            onDismiss = {
                scope.launch { settingsStore.setOnboardingSeen(true) }
                // 通知权限申请时机：首启引导关闭后（33+，仅未授权时申请）
                (context as? ComponentActivity)?.let { activity ->
                    if (!notificationPermission.isGranted) {
                        notificationPermission.launchRequest(activity, REQ_NOTIFICATION_PERMISSION)
                    }
                }
            },
        )
    }
    content()
}

/** 8:16 说明弹窗（可被设置页「查看 8:16 说明」重看） */
@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    PixelDialog(onDismiss = onDismiss, title = "🍙 什么是 8:16 饮食法？") {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(text = "一天 24 小时", color = PixelColors.gray, fontSize = PixelType.Size.xs)
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            // 8h 进食 + 16h 断食 双 seg（原型 onboarding 图示；弹窗内用 4px 小格防溢出）
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelProgressBar(progress = 1f, segments = 8, litColor = PixelColors.yellow, segmentWidth = 4.dp)
                Spacer(modifier = Modifier.width(PixelShape.Spacing.xs))
                PixelText(text = "8h 进食", color = PixelColors.yellow, fontSize = PixelType.Size.xs)
                Spacer(modifier = Modifier.width(PixelShape.Spacing.sm))
                PixelProgressBar(progress = 1f, segments = 16, litColor = PixelColors.red, segmentWidth = 4.dp)
                Spacer(modifier = Modifier.width(PixelShape.Spacing.xs))
                PixelText(text = "16h 断食", color = PixelColors.red, fontSize = PixelType.Size.xs)
            }
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            // 像素对话框灵魂：主流程文案打字机逐字
            PixelTypewriterText(
                text = "记录早餐 → 自动排午/晚 → 到点提醒 → 吃完打卡",
                color = PixelColors.white,
                fontSize = PixelType.Size.xs,
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            PixelText(
                text = "「8:16 是一种进食时间安排，请结合自身情况」",
                color = PixelColors.gray,
                fontSize = PixelType.Size.xs,
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            PixelButton(text = "知道了，开始使用", onClick = onDismiss, primary = true)
        }
    }
}

@PreviewPixel
@Composable
private fun OnboardingDialogPreview() {
    PixelTheme {
        OnboardingDialog(onDismiss = {})
    }
}
