package com.ly.fast16.core.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ly.fast16.MainActivity
import com.ly.fast16.R
import com.ly.fast16.core.widget.WidgetDataProvider.WidgetData
import com.ly.fast16.domain.model.MealType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 桌面像素小组件（design-spec §1 .widget / §5.2）——「计划信息流」版：
 * 默认申请小尺寸（2×1 targetCell），内容随系统分配尺寸（options → LocalSize）两档自适应：
 * - 紧凑档（宽 < 250dp）：标题 + 断食/连击 + 三餐状态点行；放不下则只留核心，不裁切
 * - 完整档（≥250×150dp）：标题 + 断食/连击 + 三餐信息流行（碗图标·餐名·状态·时刻）+ 下一餐小结
 * - 无计划：占位文案（两档各自精简）
 * 整卡可点 → 打开 App（无卡内按钮；不做卡内快捷打卡——交互收敛到 App 内）。
 *
 * 尺寸来源：系统 AppWidgetOptions（onAppWidgetOptionsChanged）→ Glance Responsive 候选
 * → LocalSize，非 pin 返回值；<Android 12 回退最小候选（默认紧凑档）。
 * 布局遵守 RemoteViews 约束：无滚动/动画/自绘，超出行被系统按格子高度裁切（内容行数可控）。
 */
class Fast16Widget : GlanceAppWidget(), KoinComponent {

    // 依赖经 KoinComponent 惰性注入（系统实例化类，与 Receiver 同模式）——不穿透分层
    private val dataProvider: WidgetDataProvider by inject()

    // Responsive：桌面在 2×1 ↔ 3×2 间拉伸，系统据分配尺寸匹配合适候选重渲染
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp), // 紧凑候选（小尺寸申请默认命中）
            DpSize(250.dp, 110.dp), // 过渡（仍按紧凑档渲染）
            DpSize(250.dp, 250.dp), // 完整候选
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = dataProvider.load()
        provideContent {
            Fast16WidgetContent(data)
        }
    }
}

// 像素 8 色（与 PixelColors 一致；Glance 的 background / TextStyle.color 均需 ColorProvider）
private val C_BG = ColorProvider(Color(0xFF0F0F1A))
private val C_PANEL = ColorProvider(Color(0xFF1A1C2C))
private val C_GREEN = ColorProvider(Color(0xFF38B764))
private val C_GRAY = ColorProvider(Color(0xFF7B7B8C))
private val C_WHITE = ColorProvider(Color(0xFFF4F4F4))
private val C_YELLOW = ColorProvider(Color(0xFFFFCD75))

/** 尺寸分档边界：桌面分配 ≥ 此值走完整信息流，否则紧凑档（避免 220 魔法临界） */
private val FULL_WIDTH_MIN = 250.dp
private val FULL_HEIGHT_MIN = 150.dp

@Composable
private fun Fast16WidgetContent(data: WidgetData) {
    val size = LocalSize.current
    val isFull = size.width >= FULL_WIDTH_MIN && size.height >= FULL_HEIGHT_MIN

    // 整卡点击 → 打开 App（可点面积最大化；不做卡内按钮）
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(C_BG)
            .padding(4.dp)
            .clickable(onClick = actionStartActivity<MainActivity>()),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(C_PANEL)
                .cornerRadius(4.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (isFull) FullContent(data) else CompactContent(data)
        }
    }
}

/** 头部标题行（两档共用）：左「816 断食」+ 右状态（断食剩余优先，其次连击天数） */
@Composable
private fun HeaderRow(data: WidgetData, titleSize: androidx.compose.ui.unit.TextUnit) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "816 断食",
            style = TextStyle(color = C_WHITE, fontSize = titleSize),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        headerRightLabel(data)?.let {
            Text(
                text = it,
                style = TextStyle(color = C_YELLOW, fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}

private fun headerRightLabel(data: WidgetData): String? =
    data.fastingLabel
        ?: data.streak.takeIf { it > 0 }?.let { "$it 天" }

// ---------- 紧凑档（小尺寸：标题 + 状态行/占位，顶部标题、底部信息，防裁切） ----------

@Composable
private fun CompactContent(data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(data, titleSize = 10.sp)
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (data.hasPlan) {
            // 三餐状态点行：色点 + 首字（8sp，贴近最小可读）
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MealType.entries.forEach { type ->
                    val checked = type in data.checked
                    Row(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(5.dp)
                                .background(if (checked) C_GREEN else C_GRAY),
                        ) { }
                        Spacer(modifier = GlanceModifier.width(3.dp))
                        Text(
                            text = shortMealLabel(type),
                            style = TextStyle(
                                color = if (checked) C_GREEN else C_WHITE,
                                fontSize = 9.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        } else {
            Text(
                text = "今日无计划 · 点按开始",
                style = TextStyle(color = C_GRAY, fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}

// ---------- 完整档（大尺寸：计划信息流） ----------

@Composable
private fun FullContent(data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(data, titleSize = 11.sp)
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (data.hasPlan) {
            // 信息流：每餐一行（碗图标 · 餐名 · 状态 · 时刻），行数/状态随计划数据
            MealType.entries.forEach { type ->
                MealRow(
                    type = type,
                    time = data.mealTimes[type],
                    checked = type in data.checked,
                    isNext = type == data.nextCheckInType,
                )
                Spacer(modifier = GlanceModifier.height(5.dp))
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            // 小结行：下一餐信息（备餐提前提示）；无下一餐且全部完成 → 汇总
            val footer = data.nextMealLabel ?: if (data.checked.size >= data.mealCount) {
                "今日 ${data.mealCount} 餐已全部打卡"
            } else {
                null
            }
            footer?.let {
                Text(
                    text = it,
                    style = TextStyle(color = C_GREEN, fontSize = 9.sp),
                    maxLines = 1,
                )
            }
        } else {
            EmptyPlanPlaceholder()
        }
    }
}

@Composable
private fun MealRow(type: MealType, time: String?, checked: Boolean, isNext: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(
                if (checked) R.drawable.ic_widget_bowl_checked
                else R.drawable.ic_widget_bowl_unchecked,
            ),
            contentDescription = mealLabel(type),
            modifier = GlanceModifier.size(14.dp),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = mealLabel(type),
            style = TextStyle(color = C_WHITE, fontSize = 10.sp),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        val (statusText, statusColor) = when {
            checked -> "已打卡" to C_GREEN
            isNext -> "下一餐" to C_YELLOW
            else -> "未打卡" to C_GRAY
        }
        Text(
            text = statusText,
            style = TextStyle(color = statusColor, fontSize = 9.sp),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = time ?: "--:--",
            style = TextStyle(color = C_GRAY, fontSize = 9.sp),
            maxLines = 1,
        )
    }
}

/** 无计划占位（完整档）：垂直居中的两句提示，整卡仍可点进 App 创建 */
@Composable
private fun EmptyPlanPlaceholder() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = "今日暂无 8:16 计划",
            style = TextStyle(color = C_WHITE, fontSize = 10.sp),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = "点按卡片创建轻断食计划",
            style = TextStyle(color = C_YELLOW, fontSize = 9.sp),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

// ---------- 文案映射 ----------

private fun mealLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
}

/** 紧凑档首字（放不下全名时的最简表达） */
private fun shortMealLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早"
    MealType.LUNCH -> "午"
    MealType.DINNER -> "晚"
}

/** GlanceAppWidgetReceiver：桌面添加/更新入口 */
class Fast16WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Fast16Widget()
}
