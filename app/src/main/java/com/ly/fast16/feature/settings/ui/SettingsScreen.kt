package com.ly.fast16.feature.settings.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ly.fast16.core.designsystem.component.LegalAssets
import com.ly.fast16.core.designsystem.component.LegalDialog
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelDialog
import com.ly.fast16.core.designsystem.component.PixelDivider
import com.ly.fast16.core.designsystem.component.PixelLoading
import com.ly.fast16.core.designsystem.component.PixelPageScaffold
import com.ly.fast16.core.designsystem.component.PixelPageTitle
import com.ly.fast16.core.designsystem.component.PixelSectionTitle
import com.ly.fast16.core.designsystem.component.PixelStepper
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType
import com.ly.fast16.core.system.ExactAlarmGate
import com.ly.fast16.core.system.NotificationPermission
import com.ly.fast16.data.local.AppSettings
import com.ly.fast16.data.local.FontMode
import com.ly.fast16.data.local.SettingsStore
import com.ly.fast16.domain.model.ReminderMode
import com.ly.fast16.feature.onboarding.ui.OnboardingDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// ---------- Intent ----------

/** 设置页用户意图（改偏好 / 重看说明；偏好均可配，写 SettingsStore） */
sealed interface SettingsIntent {
    data object ShowOnboarding : SettingsIntent

    /** 修改默认提醒模式（写 SettingsStore，副作用在 ViewModel） */
    data class SetReminderMode(val mode: ReminderMode) : SettingsIntent

    data class SetWindowHours(val hours: Int) : SettingsIntent
    data class SetMinMealGap(val minutes: Int) : SettingsIntent
    data class SetDinnerBuffer(val minutes: Int) : SettingsIntent
    data class SetPrepLunch(val minutes: Int) : SettingsIntent
    data class SetPrepDinner(val minutes: Int) : SettingsIntent

    /** 切换字体模式（像素风 / 系统默认；写 SettingsStore，PixelTheme 实时响应） */
    data class SetFontMode(val mode: FontMode) : SettingsIntent
}

// ---------- State ----------

/** 设置页 UI 状态：设置项当前值列表 */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(
        val windowHours: Int,
        val minMealGapMinutes: Int,
        val dinnerBufferMinutes: Int,
        val defaultPrepLunchMinutes: Int,
        val defaultPrepDinnerMinutes: Int,
        val defaultReminderMode: String,
        /** 通知权限是否已授权（33+ 运行时；<33 恒 true） */
        val notificationGranted: Boolean = true,
        /** 精确闹钟是否可精确排程（31+；false = 提醒可能被 Doze 延迟） */
        val exactAlarmEnabled: Boolean = true,
        /** 精确闹钟授权引导 Intent（canScheduleExact 时 null） */
        val exactAlarmGrantIntent: Intent? = null,
        /** 字体模式（像素风 / 系统默认；默认系统） */
        val fontMode: FontMode = FontMode.SYSTEM,
    ) : SettingsUiState
}

// ---------- Reducer（纯函数；M1 无状态变更，偏好可配归 M3） ----------

object SettingsReducer {
    fun reduce(state: SettingsUiState, intent: SettingsIntent): SettingsUiState =
        when (intent) {
            SettingsIntent.ShowOnboarding -> state // 重看弹窗由 Screen 层控制
            // 其余写 SettingsStore，settings flow 驱动 UI 自动更新（SSOT）
            is SettingsIntent.SetReminderMode,
            is SettingsIntent.SetWindowHours,
            is SettingsIntent.SetMinMealGap,
            is SettingsIntent.SetDinnerBuffer,
            is SettingsIntent.SetPrepLunch,
            is SettingsIntent.SetPrepDinner,
            is SettingsIntent.SetFontMode,
            -> state
        }
}

// ---------- ViewModel ----------

/** 设置页 ViewModel：订阅 SettingsStore（SSOT）+ 权限状态（ExactAlarmGate / NotificationPermission） */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val exactAlarmGate: ExactAlarmGate,
    private val notificationPermission: NotificationPermission,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s -> _uiState.value = buildContent(s) }
        }
    }

    /** 权限变化后手动刷新（通知授权回调），SSOT 不变（设置本身未改） */
    fun refreshPermissions() {
        viewModelScope.launch {
            val s = settingsStore.settings.first()
            _uiState.value = buildContent(s)
        }
    }

    private fun buildContent(s: AppSettings): SettingsUiState.Content = SettingsUiState.Content(
        windowHours = s.windowHours,
        minMealGapMinutes = s.minMealGapMinutes,
        dinnerBufferMinutes = s.dinnerBufferMinutes,
        defaultPrepLunchMinutes = s.defaultPrepLunchMinutes,
        defaultPrepDinnerMinutes = s.defaultPrepDinnerMinutes,
        defaultReminderMode = s.defaultReminderMode.name,
        notificationGranted = notificationPermission.isGranted,
        exactAlarmEnabled = exactAlarmGate.canScheduleExact,
        exactAlarmGrantIntent = exactAlarmGate.grantIntent(),
        fontMode = s.fontMode,
    )

    fun onIntent(intent: SettingsIntent) {
        _uiState.value = SettingsReducer.reduce(_uiState.value, intent)
        viewModelScope.launch {
            when (intent) {
                is SettingsIntent.SetReminderMode -> settingsStore.setDefaultReminderMode(intent.mode)
                is SettingsIntent.SetWindowHours -> settingsStore.setWindowHours(intent.hours)
                is SettingsIntent.SetMinMealGap -> settingsStore.setMinMealGapMinutes(intent.minutes)
                is SettingsIntent.SetDinnerBuffer -> settingsStore.setDinnerBufferMinutes(intent.minutes)
                is SettingsIntent.SetPrepLunch -> settingsStore.setDefaultPrepLunchMinutes(intent.minutes)
                is SettingsIntent.SetPrepDinner -> settingsStore.setDefaultPrepDinnerMinutes(intent.minutes)
                is SettingsIntent.SetFontMode -> settingsStore.setFontMode(intent.mode)
                SettingsIntent.ShowOnboarding -> Unit
            }
        }
    }
}

// ---------- Screen ----------

/** 设置页 Screen（设置项列表 + 权限引导 + 重看 8:16 说明） */
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

/** 可编辑数值设置项（stepper 弹窗用） */
private enum class EditableSetting { WINDOW, GAP, BUFFER, PREP_LUNCH, PREP_DINNER }

@Composable
private fun SettingsContent(
    state: SettingsUiState.Content,
    onShowOnboarding: () -> Unit,
    onSetReminderMode: (ReminderMode) -> Unit,
    onSetSetting: (SettingsIntent) -> Unit,
    onSetFontMode: (FontMode) -> Unit,
    onRequestNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showReminderDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditableSetting?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAgreement by remember { mutableStateOf(false) }
    val context = LocalContext.current

    PixelPageScaffold(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = PixelShape.Spacing.lg,
            end = PixelShape.Spacing.lg,
            top = PixelShape.Spacing.lg,
            bottom = PixelShape.Spacing.xxl,
        ),
        header = {
            // 页面标题规范：打字机大标题 + 副标题（PixelPageTitle）
            PixelPageTitle(title = "SETTINGS", subtitle = "偏好与关于")
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            PixelDivider()
        },
    ) {
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 设置项（原型 set-row：label 左 / value 右；均可配 → stepper 弹窗）
        SettingRow(label = "进食窗口", value = "${state.windowHours} 小时", onClick = { editing = EditableSetting.WINDOW })
        SettingRow(label = "餐间隔下限", value = "${state.minMealGapMinutes} 分钟", onClick = { editing = EditableSetting.GAP })
        SettingRow(label = "晚餐距窗口结束缓冲", value = "${state.dinnerBufferMinutes} 分钟", onClick = { editing = EditableSetting.BUFFER })
        SettingRow(label = "午餐默认备餐", value = "${state.defaultPrepLunchMinutes} 分钟", onClick = { editing = EditableSetting.PREP_LUNCH })
        SettingRow(label = "晚餐默认备餐", value = "${state.defaultPrepDinnerMinutes} 分钟", onClick = { editing = EditableSetting.PREP_DINNER })

        // 字体模式（像素风 / 系统默认；默认系统，可切换像素风）
        SettingRow(
            label = "字体",
            value = if (state.fontMode == FontMode.PIXEL) "像素风" else "默认",
            onClick = { showFontDialog = true },
        )

        // 分区：提醒与权限
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))
        PixelSectionTitle(text = "提醒与权限")
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 提醒模式（可配：点击弹窗 4 选，写 SettingsStore）
        SettingRow(
            label = "提醒模式",
            value = reminderLabel(state.defaultReminderMode),
            onClick = { showReminderDialog = true },
        )

        // 精确闹钟未授权警示（P1）：点击前往系统授权，避免 Doze 延迟提醒
        if (!state.exactAlarmEnabled) {
            Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PixelColors.panel)
                    .border(BorderStroke(PixelShape.borderWidth, PixelColors.red))
                    .clickable { state.exactAlarmGrantIntent?.let { context.startActivity(it) } }
                    .padding(PixelShape.Spacing.md),
            ) {
                PixelText(
                    text = "⚠ 精确提醒未授权 · 点击前往系统授权",
                    color = PixelColors.red,
                    fontSize = PixelType.Size.xs,
                )
            }
        }

        // 通知权限行（P1）：未授权时点击申请（33+）
        SettingRow(
            label = "通知权限",
            value = if (state.notificationGranted) "已授权" else "未授权",
            onClick = { if (!state.notificationGranted) onRequestNotification() },
        )

        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

        // 分区：关于
        PixelSectionTitle(text = "关于")
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        // 关于区入口行（可点，带 ▶）
        SettingsActionRow(label = "查看 8:16 说明", onClick = onShowOnboarding)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
        SettingsActionRow(label = "隐私政策", onClick = { showPrivacy = true })
        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
        SettingsActionRow(label = "用户协议", onClick = { showAgreement = true })
        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))

        // 关于
        SettingRow(label = "关于 Fast16", value = "v0.1.0")
    }

    // 提醒模式选择弹窗（像素 chip，选中黄底黑字）
    if (showReminderDialog) {
        PixelDialog(onDismiss = { showReminderDialog = false }, title = "提醒模式") {
            Column {
                PixelText(
                    text = "到点提醒的呈现方式：",
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                ReminderMode.entries.forEach { mode ->
                    val selected = mode.name == state.defaultReminderMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = PixelShape.Spacing.xs)
                            .background(if (selected) PixelColors.yellow else PixelColors.panel)
                            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
                            .clickable {
                                onSetReminderMode(mode)
                                showReminderDialog = false
                            }
                            .padding(PixelShape.Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelText(
                            text = reminderLabel(mode.name),
                            color = if (selected) PixelColors.bg else PixelColors.white,
                            fontSize = PixelType.Size.sm,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (selected) {
                            PixelText(text = "●", color = PixelColors.bg, fontSize = PixelType.Size.xs)
                        }
                    }
                }
            }
        }
    }

    // 字体模式选择弹窗（像素风 / 系统默认；选中黄底黑字；切换实时生效——
    // PixelTheme 订阅同一 settings 流，全 App 字体即时切换）
    if (showFontDialog) {
        PixelDialog(onDismiss = { showFontDialog = false }, title = "字体") {
            Column {
                PixelText(
                    text = "像素风字体 / 系统默认字体：",
                    color = PixelColors.gray,
                    fontSize = PixelType.Size.xs,
                )
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                FontMode.entries.forEach { mode ->
                    val selected = mode == state.fontMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = PixelShape.Spacing.xs)
                            .background(if (selected) PixelColors.yellow else PixelColors.panel)
                            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
                            .clickable {
                                onSetFontMode(mode)
                                showFontDialog = false
                            }
                            .padding(PixelShape.Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelText(
                            text = if (mode == FontMode.PIXEL) "像素风" else "默认字体",
                            color = if (selected) PixelColors.bg else PixelColors.white,
                            fontSize = PixelType.Size.sm,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (selected) {
                            PixelText(text = "●", color = PixelColors.bg, fontSize = PixelType.Size.xs)
                        }
                    }
                }
            }
        }
    }

    // 数值设置 stepper 弹窗（偏好可配，写 SettingsStore；+/- 只调数值，【完成】才保存关闭）
    editing?.let { e ->
        NumericSettingDialog(
            title = numericTitle(e),
            value = numericValue(state, e),
            valueSuffix = numericSuffix(e),
            min = numericRange(e).first,
            max = numericRange(e).last,
            step = numericStep(e),
            onConfirm = { v ->
                onSetSetting(numericIntent(e, v))
                editing = null
            },
            onClose = { editing = null },
        )
    }

    // 合规文本弹窗（隐私政策 / 用户协议，本地 assets 展示，无网络权限）
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

/**
 * 数值设置弹窗：像素 stepper ± 步进（clamp 到范围）+ 完成。
 * 局部 state：+/- 仅调整预览值（不落库、不关闭）；【完成】才回调 [onConfirm] 保存并关闭。
 */
@Composable
private fun NumericSettingDialog(
    title: String,
    value: Int,
    valueSuffix: String,
    min: Int,
    max: Int,
    step: Int,
    onConfirm: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var current by remember { mutableIntStateOf(value) }
    PixelDialog(onDismiss = onClose, title = title) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelStepper(
                value = "$current",
                unit = valueSuffix,
                onDecrease = { current = (current - step).coerceAtLeast(min) },
                onIncrease = { current = (current + step).coerceAtMost(max) },
            )
            Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
            PixelButton(
                text = "完成",
                onClick = { onConfirm(current) },
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------- 数值设置项映射（标题/当前值/范围/步长/Intent） ----------

private fun numericTitle(e: EditableSetting): String = when (e) {
    EditableSetting.WINDOW -> "进食窗口（小时）"
    EditableSetting.GAP -> "餐间隔下限（分钟）"
    EditableSetting.BUFFER -> "晚餐距窗口结束（分钟）"
    EditableSetting.PREP_LUNCH -> "午餐默认备餐（分钟）"
    EditableSetting.PREP_DINNER -> "晚餐默认备餐（分钟）"
}

private fun numericValue(state: SettingsUiState.Content, e: EditableSetting): Int = when (e) {
    EditableSetting.WINDOW -> state.windowHours
    EditableSetting.GAP -> state.minMealGapMinutes
    EditableSetting.BUFFER -> state.dinnerBufferMinutes
    EditableSetting.PREP_LUNCH -> state.defaultPrepLunchMinutes
    EditableSetting.PREP_DINNER -> state.defaultPrepDinnerMinutes
}

private fun numericSuffix(e: EditableSetting): String =
    if (e == EditableSetting.WINDOW) " 小时" else " 分钟"

private fun numericRange(e: EditableSetting): IntRange = when (e) {
    EditableSetting.WINDOW -> 4..16
    EditableSetting.GAP -> 60..240
    EditableSetting.BUFFER -> 0..60
    EditableSetting.PREP_LUNCH,
    EditableSetting.PREP_DINNER,
    -> 0..120
}

private fun numericStep(e: EditableSetting): Int = when (e) {
    EditableSetting.WINDOW -> 1
    EditableSetting.GAP -> 10
    else -> 5
}

private fun numericIntent(e: EditableSetting, v: Int): SettingsIntent = when (e) {
    EditableSetting.WINDOW -> SettingsIntent.SetWindowHours(v)
    EditableSetting.GAP -> SettingsIntent.SetMinMealGap(v)
    EditableSetting.BUFFER -> SettingsIntent.SetDinnerBuffer(v)
    EditableSetting.PREP_LUNCH -> SettingsIntent.SetPrepLunch(v)
    EditableSetting.PREP_DINNER -> SettingsIntent.SetPrepDinner(v)
}

/** 设置行（原型 set-row：面板底黑边，label 左 / 像素 value 右；可选点击） */
@Composable
private fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(PixelShape.Spacing.md)
            // 行间留白：连续多行设置不贴边（原无底部 padding，行贴行）
            .padding(bottom = PixelShape.Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelText(text = label, color = PixelColors.white, fontSize = PixelType.Size.sm)
        PixelText(text = value, color = PixelColors.gray, fontSize = PixelType.Size.xs, digital = true)
    }
}

/** 关于区入口行（面板底黑边 + 右侧 ▶；与设置行同构的导航型行） */
@Composable
private fun SettingsActionRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelColors.panel)
            .border(BorderStroke(PixelShape.borderWidth, Color.Black))
            .clickable(onClick = onClick)
            .padding(PixelShape.Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelText(text = label, color = PixelColors.white, fontSize = PixelType.Size.sm)
            PixelText(text = "▶", color = PixelColors.gray, fontSize = PixelType.Size.xs)
        }
    }
}

private fun reminderLabel(mode: String): String = when (mode) {
    "NOTIFY" -> "通知"
    "VIBRATE" -> "震动"
    "SOUND" -> "铃声"
    "ALARM" -> "闹钟"
    else -> "无"
}

@PreviewPixel
@Composable
private fun SettingsScreenPreview() {
    PixelTheme {
        SettingsContent(
            state = SettingsUiState.Content(
                windowHours = 8,
                minMealGapMinutes = 180,
                dinnerBufferMinutes = 30,
                defaultPrepLunchMinutes = 30,
                defaultPrepDinnerMinutes = 45,
                defaultReminderMode = "NOTIFY",
            ),
            onShowOnboarding = {},
            onSetReminderMode = {},
            onSetSetting = {},
            onSetFontMode = {},
            onRequestNotification = {},
        )
    }
}
