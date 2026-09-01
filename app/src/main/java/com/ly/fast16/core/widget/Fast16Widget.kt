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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
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
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.widget.WidgetDataProvider.WidgetData
import com.ly.fast16.domain.model.MealType
import com.ly.fast16.domain.usecase.CheckInUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 桌面像素小组件（design-spec §1 .widget / §5.2）：
 * 标题「816 轻断食」+ 断食状态（SettingsStore.widgetShowFasting 控制）+
 * 三餐状态点（已打卡绿 / 未打灰）+「打卡午餐」与「打开 App」按钮。
 *
 * - 数据：provideGlance 协程内 await 快照（WidgetDataProvider 注入 domain repository，低频）
 * - 打卡：Glance ActionCallback（不启动 Activity，31+ trampoline 合规）→ CheckInUseCase
 *   （@Upsert 幂等 + Meal 联动）→ updateAll 刷新
 * - 打开 App：actionStartActivity 直达 MainActivity（禁 trampoline）
 */
class Fast16Widget : GlanceAppWidget(), KoinComponent {

    // 依赖经 KoinComponent 惰性注入（系统实例化类，与 Receiver 同模式）——不穿透分层
    private val dataProvider: WidgetDataProvider by inject()

    // 响应式：支持桌面拉伸/缩放到多个尺寸（2×1 紧凑 / 3×1 宽 / 3×2 大）
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 250.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = dataProvider.load()
        provideContent {
            Fast16WidgetContent(data)
        }
    }
}

/** Widget 打卡 ActionCallback：打卡今日指定餐 → 刷新 widget（不启动 Activity） */
class WidgetCheckInCallback : ActionCallback, KoinComponent {
    // 依赖经 KoinComponent 惰性注入（系统实例化类，与 Receiver 同模式）——避免强转 Application 穿透分层
    private val checkInUseCase: CheckInUseCase by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val typeRaw = parameters[KEY_TYPE] ?: return
        val type = runCatching { MealType.valueOf(typeRaw) }.getOrNull() ?: return
        checkInUseCase.checkIn(SystemTimeProvider.today(), type, SystemTimeProvider.now())
        Fast16Widget().updateAll(context)
    }

    companion object {
        val KEY_TYPE = ActionParameters.Key<String>("meal_type")
    }
}

// 像素 8 色（与 PixelColors 一致；Glance 的 background / TextStyle.color 均需 ColorProvider）
private val C_BG = ColorProvider(Color(0xFF0F0F1A))
private val C_PANEL = ColorProvider(Color(0xFF1A1C2C))
private val C_GREEN = ColorProvider(Color(0xFF38B764))
private val C_GRAY = ColorProvider(Color(0xFF7B7B8C))
private val C_WHITE = ColorProvider(Color(0xFFF4F4F4))
private val C_YELLOW = ColorProvider(Color(0xFFFFCD75))

@Composable
private fun Fast16WidgetContent(data: WidgetData) {
    val size = LocalSize.current
    // 外层 bg + 内层面板（Glance 无 stroke，用黑底 + 内层模拟描边）
    Column(modifier = GlanceModifier.fillMaxSize().background(C_BG).padding(4.dp)) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(C_PANEL)
                .cornerRadius(4.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // 头部：816 轻断食 + 断食倒计时（右侧；widgetShowFasting 控制）
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "816 轻断食",
                    style = TextStyle(color = C_WHITE, fontSize = 11.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (data.showFasting) {
                    Text(
                        text = data.fastingLabel ?: "断食 --:--",
                        style = TextStyle(color = C_YELLOW, fontSize = 9.sp),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (data.hasPlan) {
                // 三餐打卡信息流（主体）：像素碗（绿=已打卡/灰=未打卡）+ 餐名+状态点
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    MealType.entries.forEach { type ->
                        val checked = type in data.checked
                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                provider = ImageProvider(
                                    if (checked) R.drawable.ic_widget_bowl_checked
                                    else R.drawable.ic_widget_bowl_unchecked
                                ),
                                contentDescription = mealLabel(type),
                                modifier = GlanceModifier.size(28.dp).padding(2.dp),
                            )
                            Spacer(modifier = GlanceModifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = GlanceModifier
                                        .size(4.dp)
                                        .background(if (checked) C_GREEN else C_GRAY),
                                ) { }
                                Spacer(modifier = GlanceModifier.width(3.dp))
                                Text(
                                    text = mealLabel(type),
                                    style = TextStyle(color = if (checked) C_GREEN else C_WHITE, fontSize = 9.sp),
                                )
                            }
                        }
                    }
                }

                // 下一餐信息行（仅宽尺寸 ≥220dp 显示，紧凑尺寸省略避免挤压）
                if (size.width >= 220.dp) {
                    data.nextMealLabel?.let { label ->
                        Spacer(modifier = GlanceModifier.height(8.dp))
                        Text(
                            text = label,
                            style = TextStyle(color = C_GRAY, fontSize = 9.sp),
                        )
                    }
                }
            } else {
                Text(
                    text = "今日无计划，快去记录早餐吧",
                    style = TextStyle(color = C_WHITE, fontSize = 10.sp),
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 低调入口：打开 App（panel 底小按钮，居右；不再用醒目绿底）
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Box(
                    modifier = GlanceModifier
                        .background(C_BG)
                        .cornerRadius(4.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .clickable(onClick = actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        text = "打开 App ↗",
                        style = TextStyle(color = C_WHITE, fontSize = 9.sp),
                    )
                }
            }
        }
    }
}

private fun mealLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
}

/** GlanceAppWidgetReceiver：桌面添加/更新入口 */
class Fast16WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Fast16Widget()
}
