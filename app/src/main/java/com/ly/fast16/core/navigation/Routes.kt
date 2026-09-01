package com.ly.fast16.core.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import androidx.navigation.toRoute
import com.ly.fast16.core.designsystem.component.ICON_BOWL
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

/** 新建 / 编辑计划：全屏（隐藏底栏）；planId ≥ 0 为编辑模式（预填 + 更新重排） */
@Serializable
data class CreateRoute(val planId: Long = -1L)

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
                    onNewPlan = { navController.navigate(CreateRoute()) },
                    onOpenRecord = { navController.navigateTab(RecordRoute) },
                    onEditPlan = { planId -> navController.navigate(CreateRoute(planId)) },
                )
            }
            composable<CreateRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<CreateRoute>()
                CreateScreen(
                    planId = route.planId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<RecordRoute> {
                RecordScreen(
                    onEditPlan = { planId -> navController.navigate(CreateRoute(planId)) },
                )
            }
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

/** 像素风底部导航栏：像素图标+文字，动态滚动地形背景，选中项主黄描边+黄底（原型 .app-tabs / .tab） */
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .height(80.dp),
    ) {
        // 动态像素背景：主题元素（时钟/饭碗/星星）无限横滚，铺满整条底栏作为 tab 背景
        PixelScrollBackdrop(
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { (label, route, icon) ->
                val selected = currentDestination?.hierarchy?.any { it.hasRoute(route::class) } == true
                val borderColor = if (selected) PixelColors.yellow else Color.Transparent
                val fg = if (selected) PixelColors.yellow else PixelColors.gray
                Row(
                    modifier = Modifier
                        .clickable { onSelect(route) }
                        .then(
                            if (selected) {
                                Modifier.background(PixelColors.yellow.copy(alpha = 0.20f))
                            } else {
                                Modifier
                            },
                        )
                        .border(BorderStroke(PixelShape.borderWidth, borderColor))
                        .padding(
                            horizontal = PixelShape.Spacing.lg,
                            vertical = PixelShape.Spacing.md,
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelSprite(
                        rows = icon,
                        palette = mapOf('K' to fg),
                        modifier = Modifier.size(20.dp),
                        contentDescription = "$label 标签",
                    )
                    Spacer(Modifier.size(PixelShape.Spacing.sm))
                    PixelText(
                        text = label,
                        color = fg,
                        fontSize = PixelType.Size.sm,
                    )
                }
            }
        }
    }
}

/**
 * 轮播像素背景：主题元素（时钟/饭碗/星星）铺满整条底栏，无限横向滚动（无缝循环）。
 * 动画位移一个 tile 宽后回绕，绘制 2 个 tile 保证全程覆盖。
 */
@Composable
private fun PixelScrollBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        // tile 宽 = 整个底栏宽：一组元素从右侧进入，完整横穿底栏，左侧出去
        val density = LocalDensity.current
        val tileWidthPx = with(density) { maxWidth.toPx() }
        val infinite = rememberInfiniteTransition(label = "tabBackdrop")
        val scroll by infinite.animateFloat(
            initialValue = 0f,
            targetValue = -tileWidthPx,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
            ),
            label = "tabBackdropScroll",
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = scroll },
        ) {
            // Canvas 尺寸即底栏宽（fillMaxSize）；画 2 组 tile，平移一个 bar 宽后第二组顶上来无缝回绕
            val tileW = size.width
            repeat(2) { i ->
                val ox = i * tileW
                BACKDROP_SPRITES.forEach { sprite ->
                    drawSpriteGrid(
                        rows = sprite.rows,
                        origin = Offset(ox + sprite.xRatio * tileW, sprite.y.toPx()),
                        cellPx = sprite.cell.toPx(),
                        color = sprite.color,
                    )
                }
            }
        }
    }
}

/** 背景元素：字符网格 + 相对 tile 宽度的横向比例位置 + 纵向 dp 与单格尺寸 */
private data class BackdropSprite(
    val rows: List<String>,
    val xRatio: Float,
    val y: Dp,
    val cell: Dp,
    val color: Color,
)

/** 将字符网格逐格绘制为像素方块（'.' 为透明） */
private fun DrawScope.drawSpriteGrid(
    rows: List<String>,
    origin: Offset,
    cellPx: Float,
    color: Color,
) {
    rows.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch ->
            if (ch != '.') {
                drawRect(
                    color = color,
                    topLeft = Offset(origin.x + c * cellPx, origin.y + r * cellPx),
                    size = Size(cellPx, cellPx),
                )
            }
        }
    }
}

/** 像素时钟（8×8，3 点指针）——断食倒计时主题 */
private val BACKDROP_CLOCK = listOf(
    "..KKKK..",
    ".K....K.",
    "K..K...K",
    "K..K...K",
    "K..K...K",
    "K..K...K",
    ".K....K.",
    "..KKKK..",
)

/** 像素星星（5×4）——完成/激励 */
private val BACKDROP_SPARK = listOf(
    "..Y..",
    ".Y.Y.",
    "..Y..",
    ".....",
)

/** 背景元素布局（tile 宽 = 底栏全宽，cell 4dp；低透明度贴边分布，tab 无底衬也不抢文字） */
private val BACKDROP_SPRITES = listOf(
    // 时钟：左侧贴顶
    BackdropSprite(BACKDROP_CLOCK, 0.04f, 8.dp, 4.dp, PixelColors.blue.copy(alpha = 0.18f)),
    // 星星：中上
    BackdropSprite(BACKDROP_SPARK, 0.50f, 4.dp, 4.dp, PixelColors.yellow.copy(alpha = 0.20f)),
    // 饭碗：右侧偏下（复用 PixelCharacter 餐次碗图标）
    BackdropSprite(ICON_BOWL, 0.82f, 48.dp, 4.dp, PixelColors.white.copy(alpha = 0.14f)),
)

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
