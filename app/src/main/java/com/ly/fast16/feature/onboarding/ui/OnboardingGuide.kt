package com.ly.fast16.feature.onboarding.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ly.fast16.core.designsystem.component.LegalAssets
import com.ly.fast16.core.designsystem.component.LegalDialog
import com.ly.fast16.core.designsystem.component.PixelBackdrop
import com.ly.fast16.core.designsystem.component.PixelButton
import com.ly.fast16.core.designsystem.component.PixelProgressBar
import com.ly.fast16.core.designsystem.component.PixelText
import com.ly.fast16.core.designsystem.component.PixelTypewriterText
import com.ly.fast16.core.designsystem.component.PreviewPixel
import com.ly.fast16.core.designsystem.theme.PixelTheme
import com.ly.fast16.core.designsystem.token.PixelColors
import com.ly.fast16.core.designsystem.token.PixelShape
import com.ly.fast16.core.designsystem.token.PixelType

/**
 * 像素风系统式首启向导（设计方案 §8.8 / 合规 §0）：
 * - [consentMode] = true：第 0 步为「隐私与条款」**硬 gate**——意图说明 + 条款全文入口，
 *   点「同意并继续」→ [onConsent]（上层写 privacyAccepted），不同意 → [onExit]（退出应用）；
 *   同意前无跳过、不可推进后续页。之后为 4 页概念引导。
 * - [consentMode] = false：直接从概念页开始（已同意过隐私的旧引导路径）。
 * 完成（末页「开始使用」或中途「跳过」）→ [onFinish]。
 *
 * 视觉：黑底 + 星点/CRT 动态（[PixelBackdrop]），像素卡 + 黑描边，打字机正文；
 * 顶栏「返回 / 跳过」，底部像素点指示器 + 主按钮。
 */
@Composable
fun OnboardingGuide(
    consentMode: Boolean = false,
    onConsent: () -> Unit = {},
    onExit: () -> Unit = {},
    onFinish: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableIntStateOf(0) }
    val isConsentPage = consentMode && page == 0
    val bodyPage = page - if (consentMode) 1 else 0
    val pageCount = if (consentMode) 5 else 4
    val isLast = page == pageCount - 1

    Box(modifier = modifier.fillMaxSize()) {
        PixelBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                // edge-to-edge 下避开状态栏/导航栏（打孔/摄像头与手势条区域）
                .systemBarsPadding()
                .padding(horizontal = PixelShape.Spacing.lg, vertical = PixelShape.Spacing.md),
        ) {
            // 顶栏：左返回（非首页）· 右跳过（非条款页且非末页）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page > 0) {
                    GuideTextAction(text = "◀ 返回") { page-- }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (!isConsentPage && !isLast) {
                    GuideTextAction(text = "跳过") { onFinish() }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 中央像素面板
            Column(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .background(PixelColors.panel)
                    .border(BorderStroke(PixelShape.borderWidth, Color.Black), PixelShape.stair)
                    .padding(horizontal = PixelShape.Spacing.lg, vertical = PixelShape.Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    isConsentPage -> ConsentPage(onAccept = { onConsent() }, onDecline = onExit)
                    bodyPage == 0 -> WelcomePage()
                    bodyPage == 1 -> RecordPage()
                    bodyPage == 2 -> RemindPage()
                    else -> CompletePage()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部：进度跑者（每「下一步」推进 1/总步数，落在指示点上方跑道）+ 指示器 + 主按钮
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pageCount) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == page) 12.dp else 8.dp)
                                .background(if (i == page) PixelColors.yellow else PixelColors.gray.copy(alpha = 0.6f)),
                        )
                    }
                }
                if (!isConsentPage) {
                    Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))
                    PixelButton(
                        text = if (isLast) "开始使用" else "下一步 ▸",
                        onClick = { if (isLast) onFinish() else page++ },
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------- 条款同意页（硬 gate） ----------

/**
 * ① 隐私与条款：意图说明（做什么 / 数据存哪 / 权限）→ 全文入口 → 双按钮。
 * 同意（[onAccept]）后由上层写 privacyAccepted；不同意（[onDecline]）退出应用。
 */
@Composable
private fun ConsentPage(onAccept: () -> Unit, onDecline: () -> Unit) {
    var showPrivacy by remember { mutableStateOf(false) }
    var showAgreement by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText(text = "📋", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
        PixelText(text = "隐私与条款", color = PixelColors.white, fontSize = PixelType.Size.md)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
        // 意图说明：产品定位 + 数据去向 + 权限用途（打字机首句 + 静态要点）
        PixelTypewriterText(
            text = "Fast16 是一款记录轻断食的本地应用。同意前，请了解：",
            color = PixelColors.gray,
            fontSize = PixelType.Size.xs,
        )
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
        ConsentBullet(text = "饮食打卡数据仅保存在本机")
        ConsentBullet(text = "无需账号 · 不上传任何数据")
        ConsentBullet(text = "到点提醒需通知权限，可随时关闭")
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))

        GuideLegalRow(label = "查看隐私政策", onClick = { showPrivacy = true })
        Spacer(modifier = Modifier.height(PixelShape.Spacing.sm))
        GuideLegalRow(label = "查看用户协议", onClick = { showAgreement = true })
        Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))

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

/** 意图说明要点行（黄点 + 白字，左对齐） */
@Composable
private fun ConsentBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).background(PixelColors.yellow))
        Spacer(modifier = Modifier.width(PixelShape.Spacing.sm))
        PixelText(text = text, color = PixelColors.white, fontSize = PixelType.Size.xs)
    }
    Spacer(modifier = Modifier.height(6.dp))
}

/** 条款查看入口行（面板底黑边 + ▶） */
@Composable
private fun GuideLegalRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelColors.bg)
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

// ---------- 概念引导页 ----------

/** ② 概念：8h 进食 + 16h 断食（双 seg 图示） */
@Composable
private fun WelcomePage() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelProgressBar(
            progress = 1f,
            segments = 8,
            litColor = PixelColors.yellow,
            segmentWidth = 5.dp,
            segmentHeight = 10.dp,
        )
        Spacer(modifier = Modifier.width(PixelShape.Spacing.xs))
        PixelText(text = "8h 进食", color = PixelColors.yellow, fontSize = PixelType.Size.xs)
        Spacer(modifier = Modifier.width(PixelShape.Spacing.md))
        PixelProgressBar(
            progress = 1f,
            segments = 16,
            litColor = PixelColors.red,
            segmentWidth = 5.dp,
            segmentHeight = 10.dp,
        )
        Spacer(modifier = Modifier.width(PixelShape.Spacing.xs))
        PixelText(text = "16h 断食", color = PixelColors.red, fontSize = PixelType.Size.xs)
    }
    Spacer(modifier = Modifier.height(PixelShape.Spacing.lg))
    GuideStep(
        title = "欢迎使用 816 轻断食",
        body = "一天 24 小时：8 小时进食，16 小时断食。",
    )
}

/** ③ 记录早餐 → 自动排餐 */
@Composable
private fun RecordPage() {
    GuideStep(
        icon = "🍙",
        title = "记一餐，排三餐",
        body = "记录早餐时间，午餐、晚餐和备餐提醒自动排好，不再手动算。",
    )
}

/** ④ 到点提醒（预告通知权限） */
@Composable
private fun RemindPage() {
    GuideStep(
        icon = "🔔",
        title = "到点提醒",
        body = "到点提醒你吃下一餐。稍后系统会请求通知权限，请选择允许。",
    )
}

/** ⑤ 完成 */
@Composable
private fun CompletePage() {
    GuideStep(
        icon = "✅",
        title = "吃完打卡，闭环养成",
        body = "吃完打卡，断食计时与桌面小组件同步更新。准备好了吗？",
    )
}

/** 概念页统一排版：图标 + 标题 + 打字机正文 */
@Composable
private fun GuideStep(icon: String? = null, title: String, body: String) {
    if (icon != null) {
        PixelText(text = icon, fontSize = 40.sp)
        Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
    }
    PixelText(text = title, color = PixelColors.white, fontSize = PixelType.Size.md)
    Spacer(modifier = Modifier.height(PixelShape.Spacing.md))
    PixelTypewriterText(
        text = body,
        color = PixelColors.gray,
        fontSize = PixelType.Size.sm,
    )
}

/** 顶栏文字动作（返回 / 跳过） */
@Composable
private fun GuideTextAction(text: String, onClick: () -> Unit) {
    PixelText(
        text = text,
        color = PixelColors.gray,
        fontSize = PixelType.Size.xs,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(PixelShape.Spacing.sm),
    )
}



@PreviewPixel
@Composable
private fun OnboardingGuidePreview() {
    PixelTheme {
        OnboardingGuide(onFinish = {})
    }
}

@PreviewPixel
@Composable
private fun OnboardingGuideConsentPreview() {
    PixelTheme {
        OnboardingGuide(consentMode = true)
    }
}
