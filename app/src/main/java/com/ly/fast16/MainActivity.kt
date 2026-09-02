package com.ly.fast16

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.navigation.Fast16NavHost
import com.ly.fast16.feature.onboarding.ui.StartupGateRoot
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge（API 35+ 强制，compat-api24-37 §4/§5）
        // App 深色 only：强制浅色系统栏图标（SystemBarStyle.dark = 白色图标），
        // 否则系统浅色模式下状态栏/导航栏图标呈暗色，深色背景上看不清
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            // 注入 SettingsStore：PixelTheme 据此切换字体模式（像素风 / 系统默认）
            PixelTheme(settingsStore = koinInject()) {
                // 首启统一 gate（非路由）：隐私条款作为全屏向导首步（privacy_accepted），
                // 同意后继续概念引导（onboarding_seen），完成后再渲染主界面（国内商店合规）
                StartupGateRoot {
                    Fast16NavHost()
                }
            }
        }
    }
}
