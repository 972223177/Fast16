package com.ly.fast16.feature.settings.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.system.NotificationPermission
import com.ly.fast16.feature.onboarding.ui.OnboardingDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 设置页 Screen 入口：VM 绑定 + 通知权限申请 launcher + 前台权限刷新。
 * 权限授权回调 / 回到前台 → refreshPermissions。
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onShowOnboarding: () -> Unit = {},
) {
    val vm: SettingsViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    var showOnboarding by remember { mutableStateOf(false) }

    // 通知权限运行时申请（33+；回调后刷新状态）
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }
    val notificationPermission = koinInject<NotificationPermission>()
    val context = LocalContext.current

    // 每次回到前台刷新权限状态：覆盖「从系统设置返回」（精确闹钟授权）与外部改权限，
    // 不依赖个别授权路径的回调（launcher 回调仅作为快速响应兜底）
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refreshPermissions()
        }
    }

    if (showOnboarding) {
        OnboardingDialog(onDismiss = { showOnboarding = false })
    }

    when (val s = state) {
        SettingsUiState.Loading -> PixelLoading()
        is SettingsUiState.Content -> SettingsContent(
            state = s,
            onShowOnboarding = {
                showOnboarding = true
                onShowOnboarding()
            },
            onSetReminderMode = { vm.onIntent(SettingsIntent.SetReminderMode(it)) },
            onSetSetting = vm::onIntent,
            onSetFontMode = { vm.onIntent(SettingsIntent.SetFontMode(it)) },
            onRequestNotification = {
                // 永不再询问 → 跳系统通知设置页；否则弹系统申请框
                // （字符串字面量：POST_NOTIFICATIONS 常量 API 33+；<33 恒已授权不会走到申请）
                val activity = context as? Activity
                if (activity != null && notificationPermission.isPermanentlyDenied(activity)) {
                    activity.startActivity(notificationPermission.settingsIntent())
                } else {
                    notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
                }
            },
            modifier = modifier,
        )
    }
}
