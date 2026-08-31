package com.ly.fast16

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.navigation.Fast16NavHost
import com.ly.fast16.feature.onboarding.ui.OnboardingRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge（API 35+ 强制，compat-api24-37 §4/§5）；系统栏透明由像素背景延伸
        enableEdgeToEdge()
        setContent {
            PixelTheme {
                // 首启引导 overlay（非路由，onboarding_seen 控制；关闭后申请通知权限）
                OnboardingRoot {
                    Fast16NavHost()
                }
            }
        }
    }
}
