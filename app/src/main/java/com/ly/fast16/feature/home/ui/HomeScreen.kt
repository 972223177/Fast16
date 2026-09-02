package com.ly.fast16.feature.home.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.ly.fast16.core.character.SpeechCatalog
import com.ly.fast16.core.designsystem.component.PixelAmbientBackdrop
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelCard
import com.ly.fast16.core.designsystem.component.PixelConfetti
import com.ly.fast16.core.designsystem.component.PixelConfettiBurst
import com.ly.fast16.core.designsystem.component.PixelDialog
import com.ly.fast16.core.designsystem.component.PixelDivider
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.designsystem.component.PixelMealRow
import com.ly.fast16.core.designsystem.component.PixelPageScaffold
import com.ly.fast16.core.designsystem.component.PixelPageTitle
import com.ly.fast16.core.designsystem.component.PixelSectionTitle
import com.ly.fast16.core.designsystem.component.PixelStepper
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PixelToast
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.widget.Fast16Widget
import com.ly.fast16.domain.model.MealType
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 首页 Screen（今日计划流 + 打卡 + 断食计时） */
@Composable
fun HomeScreen(
    onNewPlan: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecord: () -> Unit = {},
    onEditPlan: (Long) -> Unit = {},
) {
    val vm: HomeViewModel = koinViewModel()
    val state by vm.uiState.collectAsState()
    val toastText by vm.toast.collectAsState()
    val confetti by vm.confetti.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showToast by remember { mutableStateOf(false) }

    LaunchedEffect(toastText) {
        if (toastText != null) {
            showToast = true
        }
    }

    when (val s = state) {
        HomeUiState.Loading -> PixelLoading()
        is HomeUiState.Content -> HomeContent(
            state = s,
            liveState = vm.liveState,
            onNewPlan = { vm.onIntent(HomeIntent.NewPlan); onNewPlan() },
            onCheckIn = {
                vm.onIntent(HomeIntent.CheckIn(it))
                scope.launch { Fast16Widget().updateAll(context) } // 打卡 → 桌面小组件同步
            },
            onUncheckIn = {
                vm.onIntent(HomeIntent.UncheckIn(it))
                scope.launch { Fast16Widget().updateAll(context) } // 撤销 → 桌面小组件同步
            },
            onCheckInNow = {
                vm.onIntent(HomeIntent.CheckInNow)
                scope.launch { Fast16Widget().updateAll(context) } // 打卡/自动生成 → 小组件同步
            },
            onEditPrep = { type, minutes ->
                vm.onIntent(HomeIntent.EditPrep(type, minutes))
                scope.launch { Fast16Widget().updateAll(context) } // 备餐变化 → 小组件同步
            },
            onOpenRecord = onOpenRecord,
            onEditPlan = onEditPlan,
            modifier = modifier,
        )
    }

    // 打卡成功轻提示
    if (toastText != null) {
        PixelToast(
            text = toastText.orEmpty(),
            show = showToast,
            onDismiss = { showToast = false; vm.consumeToast() },
        )
    }

    // 打卡成功礼花：全屏居中 overlay，播完移除（日常小礼花 / 三餐全打卡大礼花）
    confetti?.let { burst ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            PixelConfetti(
                burst = burst,
                onFinished = vm::consumeConfetti,
                modifier = Modifier.size(160.dp),
            )
        }
    }
}

@Composable
internal fun HomeContent(
    state: HomeUiState.Content,
    liveState: StateFlow<HomeLiveState>,
    onNewPlan: () -> Unit,
    onCheckIn: (MealType) -> Unit,
    onUncheckIn: (MealType) -> Unit,
    onCheckInNow: () -> Unit,
    onEditPrep: (MealType, Int) -> Unit,
    onOpenRecord: () -> Unit,
    onEditPlan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 备餐编辑弹窗当前目标餐次（null = 未打开）
    var editingPrep by remember { mutableStateOf<MealType?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        // 氛围背景（云 + 星光，垂直漂浮）：常驻淡背景，有内容时降透明度
        PixelAmbientBackdrop(
            dimmed = state.hasPlanToday,
            modifier = Modifier.fillMaxSize(),
        )
        PixelPageScaffold(
            modifier = Modifier.fillMaxSize(),
            headerAlignment = Alignment.CenterHorizontally,
            contentAlignment = Alignment.CenterHorizontally,
            header = {
                // 头部：TODAY + 副标题 + streak chip（原型 home-head / home-streak）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 页面标题规范：打字机大标题 + 副标题（PixelPageTitle）
                    PixelPageTitle(title = "TODAY", subtitle = state.subtitle)
                    StreakChip(streak = state.streak, onClick = onOpenRecord)
                }

                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelDivider()
            },
        ) {
            Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

            // 角色「小健」+ 气泡（文案同源 SpeechCatalog）——高频 live 驱动，每秒仅本行重组
            CharacterRow(
                liveState = liveState,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

            // 断食面板：标签 + 大计时（时:分:秒）+ 16 格 seg（原型 fast-panel）——高频 live 驱动
            FastingPanel(
                state = state,
                liveState = liveState,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

            // 分区标题（信息层级 + 留白）；一屏可见优化：进度小字与编辑入口并入标题行右侧，
            // 省下「小字 14 + 上间距 8」与「全宽按钮 38 + 上间距 12」共 ~72dp
            PixelSectionTitle(
                text = "今日三餐",
                trailing = if (state.hasPlanToday) {
                    {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PixelShape.Spacing.sm),
                        ) {
                            PixelText(
                                text = "已打卡 ${state.meals.count { it.checkedIn }}/${state.meals.size}",
                                color = PixelColors.gray,
                                fontSize = PixelType.Size.xs,
                            )
                            // 今日计划编辑入口（复用 Create 编辑模式）
                            Box(
                                modifier = Modifier
                                    .clickable(onClick = { onEditPlan(state.planId) })
                                    .padding(all = PixelShape.Spacing.xs),
                            ) {
                                PixelText(
                                    text = "编辑 ›",
                                    color = PixelColors.blue,
                                    fontSize = PixelType.Size.xs,
                                )
                            }
                        }
                    }
                } else {
                    null
                },
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

            // 三餐时间轴（原型 meal-row ×3）或空态
            if (state.hasPlanToday) {
                state.meals.forEach { meal ->
                    PixelMealRow(
                        name = meal.name,
                        timeLabel = meal.timeLabel,
                        prepMinutes = meal.prepMinutes,
                        checkedIn = meal.checkedIn,
                        hasMeal = meal.hasMeal,
                        // 已打卡 → 二次点击撤销；未打卡 → 打卡
                        onCheckIn = {
                            if (meal.checkedIn) onUncheckIn(meal.type) else onCheckIn(meal.type)
                        },
                        // 备餐可点编辑（弹 PixelDialog stepper；prepMinutes 是 per-meal 字段，非全局写死）
                        onEditPrep = { editingPrep = meal.type },
                        modifier = Modifier.padding(vertical = PixelShape.Spacing.xs),
                    )
                }
            } else {
                val detected = state.detectedMealType
                // 当前时段餐是否已打卡：已打 → 按钮禁用并显示「已记录」，避免重复礼花
                val alreadyChecked = detected != null && detected in state.checkedInToday
                PixelCard(backgroundColor = PixelColors.panel.copy(alpha = 0.4f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.checkedInToday.isEmpty()) {
                            PixelText(text = "今天还没有就餐计划", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                            // 按时段判定当前餐次：用户不选餐次，系统按本地时间推断（MealWindow）
                            PixelText(
                                text = when (detected) {
                                    MealType.BREAKFAST -> "当前是早餐时段 · 打卡自动安排午/晚"
                                    MealType.LUNCH -> "当前是午餐时段 · 将只记录这一餐"
                                    MealType.DINNER -> "当前是晚餐时段 · 将只记录这一餐"
                                    null -> "现在不是用餐时段"
                                },
                                color = PixelColors.gray,
                                fontSize = PixelType.Size.xs,
                            )
                        } else {
                            // 单餐打卡反馈：无计划但今日已记录，展示已记录餐次（打卡结果可见）
                            PixelText(text = "今天还没有就餐计划", color = PixelColors.gray, fontSize = PixelType.Size.xs)
                            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                            PixelText(
                                text = "今日已记录：" + MealType.entries
                                    .filter { it in state.checkedInToday }
                                    .joinToString(" ") { SpeechCatalog.mealName(it) } + " ✓",
                                color = PixelColors.yellow,
                                fontSize = PixelType.Size.xs,
                            )
                            if (detected != null && !alreadyChecked) {
                                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                                PixelText(
                                    text = "还可记录 ${SpeechCatalog.mealName(detected)}",
                                    color = PixelColors.gray,
                                    fontSize = PixelType.Size.xs,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                        PixelButton(
                            text = when {
                                alreadyChecked -> "${SpeechCatalog.mealName(detected)}已记录 ✓"
                                detected == MealType.BREAKFAST -> "打卡早餐 · 自动安排午/晚"
                                detected == MealType.LUNCH -> "打卡午餐"
                                detected == MealType.DINNER -> "打卡晚餐"
                                else -> "现在不是用餐时段"
                            },
                            onClick = onCheckInNow,
                            primary = true,
                            enabled = !alreadyChecked && detected != null,
                        )
                        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                        PixelButton(
                            text = "＋ 预约未来计划",
                            onClick = onNewPlan,
                            primary = false,
                        )
                    }
                }
            }
        }
    }

    // 备餐编辑弹窗：tile 上备餐文字可点 → 打开；±5 分钟（0–180），保存后重排闹钟
    editingPrep?.let { type ->
        val meal = state.meals.firstOrNull { it.type == type }
            ?: run { editingPrep = null; return@let }
        PixelDialog(
            onDismiss = { editingPrep = null },
            title = "${meal.name} · 备餐",
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(
                    text = if (meal.prepMinutes > 0) {
                        "到点前 ${meal.prepMinutes} 分钟提醒备餐"
                    } else {
                        "无备餐 · 到点直接开吃"
                    },
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelStepper(
                    value = "${meal.prepMinutes}",
                    onDecrease = {
                        if (meal.prepMinutes > 0) {
                            onEditPrep(type, (meal.prepMinutes - 5).coerceAtLeast(0))
                        }
                    },
                    onIncrease = { onEditPrep(type, (meal.prepMinutes + 5).coerceAtMost(180)) },
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
                PixelButton(
                    text = "完成",
                    onClick = { editingPrep = null },
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
