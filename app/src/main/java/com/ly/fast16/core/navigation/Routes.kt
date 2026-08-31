package com.ly.fast16.core.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ly.fast16.core.designsystem.component.PixelSprite
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.feature.create.ui.CreateScreen
import com.ly.fast16.feature.home.ui.HomeScreen
import com.ly.fast16.feature.record.ui.RecordScreen
import com.ly.fast16.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

// ---------- 类型安全路由（基建文档 §2.2 / kotlinx.serialization） ----------

/** 首页（起始） */
@Serializable
data object HomeRoute

/** 新建计划：全屏（隐藏底栏） */
@Serializable
data object CreateRoute

/** 记录 = 统计概览 + 日历（合并页） */
@Serializable
data object RecordRoute

@Serializable
data object SettingsRoute

/**
 * 底部导航 3 Tab：首页 / 记录 / 设置（像素图标 + 选中黄描边）。
 * Create 全屏（无底栏）：currentDestination 不在 3 Tab 内时隐藏 bottomBar。
 */
@Composable
fun Fast16NavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = currentDestination?.isTabRoute() == true

    Scaffold(
        modifier = modifier,
        containerColor = PixelColors.bg,
        bottomBar = {
            if (showBottomBar) {
                PixelBottomBar(
                    currentDestination = currentDestination,
                    onSelect = { route -> navController.navigateTab(route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onNewPlan = { navController.navigate(CreateRoute) },
                    onOpenRecord = { navController.navigateTab(RecordRoute) },
                )
            }
            composable<CreateRoute> {
                CreateScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<RecordRoute> { RecordScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

/** 是否属于底部 3 Tab 页面（Create 全屏无底栏） */
private fun NavDestination?.isTabRoute(): Boolean =
    this?.hierarchy?.any {
        it.hasRoute(HomeRoute::class) ||
            it.hasRoute(RecordRoute::class) ||
            it.hasRoute(SettingsRoute::class)
    } == true

private fun NavHostController.navigateTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 像素风底部导航栏：像素图标 + 文字，选中项主黄描边（原型 .app-tabs / .tab） */
@Composable
private fun PixelBottomBar(
    currentDestination: NavDestination?,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        Triple("首页", HomeRoute, TAB_ICON_HOME),
        Triple("记录", RecordRoute, TAB_ICON_RECORD),
        Triple("设置", SettingsRoute, TAB_ICON_SETTINGS),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .padding(PixelShape.Spacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (label, route, icon) ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(route::class) } == true
            val borderColor = if (selected) PixelColors.yellow else Color.Transparent
            val fg = if (selected) PixelColors.yellow else PixelColors.gray
            Column(
                modifier = Modifier
                    .clickable { onSelect(route) }
                    .border(BorderStroke(PixelShape.borderWidth, borderColor))
                    .padding(
                        horizontal = PixelShape.Spacing.md,
                        vertical = PixelShape.Spacing.xs,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PixelSprite(
                    rows = icon,
                    palette = mapOf('K' to fg),
                    modifier = Modifier.size(18.dp),
                )
                PixelText(
                    text = label,
                    color = fg,
                    fontSize = PixelType.Size.xs,
                )
            }
        }
    }
}

/** 底部 Tab 像素图标（16×16 点阵，K=前景色；原型 .tab .t-ico 18×18） */
private val TAB_ICON_HOME = listOf(
    "................",
    "................",
    "......KKKK......",
    ".....KKKKKK.....",
    "....KKKKKKKK....",
    "...KKKKKKKKKK...",
    "..K..KKKKKK..K..",
    "..K..K....K..K..",
    "..K..K....K..K..",
    "..K..K....K..K..",
    "..K..K....K..K..",
    "..K..K....K..K..",
    "..K..K....K..K..",
    "..K..KKKKKK..K..",
    "..KKKKKKKKKKKK..",
    "................",
)

private val TAB_ICON_RECORD = listOf(
    "................",
    "..KKKKKKKKKKKK..",
    "..K..........K..",
    "..K.KKKKKKKK.K..",
    "..K.K......K.K..",
    "..K.K..KK..K.K..",
    "..K.K..KK..K.K..",
    "..K.K..KK..K.K..",
    "..K.K..KK..K.K..",
    "..K.K..KK..K.K..",
    "..K.K..KK..K.K..",
    "..K.K......K.K..",
    "..K.KKKKKKKK.K..",
    "..K..........K..",
    "..KKKKKKKKKKKK..",
    "................",
)

private val TAB_ICON_SETTINGS = listOf(
    "................",
    "................",
    "......KKKK......",
    ".....KKKKKK.....",
    "....KKKKKKKK....",
    "...KKKKKKKKKK...",
    "..KKKKKKKKKKKK..",
    "..KK.KKKKKK.KK..",
    "..KK.KKKKKK.KK..",
    "..KKKKKKKKKKKK..",
    "..KKKKKKKKKKKK..",
    "...KKKKKKKKKK...",
    "....KKKKKKKK....",
    ".....KKKKKK.....",
    "................",
    "................",
)
