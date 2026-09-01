package com.ly.fast16.core.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import com.ly.fast16.Fast16App
import com.ly.fast16.MainActivity
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.widget.WidgetDataProvider.WidgetData
import com.ly.fast16.domain.model.MealType

/**
 * 桌面像素小组件（design-spec §1 .widget / §5.2）：
 * 标题「816 轻断食」+ 断食状态（SettingsStore.widgetShowFasting 控制）+
 * 三餐状态点（已打卡绿 / 未打灰）+「打卡午餐」与「打开 App」按钮。
 *
 * - 数据：provideGlance 内 runBlocking 快照（WidgetDataProvider，低频、当日 ≤3 餐）
 * - 打卡：Glance ActionCallback（不启动 Activity，31+ trampoline 合规）→ CheckInUseCase
 *   （@Upsert 幂等 + Meal 联动）→ updateAll 刷新
 * - 打开 App：actionStartActivity 直达 MainActivity（禁 trampoline）
 */
class Fast16Widget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataProvider.load(context)
        provideContent {
            Fast16WidgetContent(data)
        }
    }
}

/** Widget 打卡 ActionCallback：打卡今日指定餐 → 刷新 widget（不启动 Activity） */
class WidgetCheckInCallback : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val typeRaw = parameters[KEY_TYPE] ?: return
        val type = runCatching { MealType.valueOf(typeRaw) }.getOrNull() ?: return
        val app = context.applicationContext as Fast16App
        app.checkInUseCase.checkIn(SystemTimeProvider.today(), type, SystemTimeProvider.now())
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
    val context = LocalContext.current
    // 外层 bg + 2px 黑描边（Glance 无 stroke，用黑底 + 内层面板模拟）
    Column(modifier = GlanceModifier.fillMaxSize().background(C_BG).padding(4.dp)) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(C_PANEL)
                .cornerRadius(4.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // 标题 + 断食状态（widgetShowFasting 控制显示）
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "816 轻断食",
                    style = TextStyle(color = C_WHITE, fontSize = 11.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (data.showFasting) {
                    Text(
                        text = data.fastingLabel ?: "休息中",
                        style = TextStyle(color = C_YELLOW, fontSize = 9.sp),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // 三餐状态点（已打卡绿 / 未打卡灰；IN-01：无计划显示空态）
            if (data.hasPlan) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    MealType.entries.forEach { type ->
                        val checked = type in data.checked
                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(10.dp)
                                    .background(if (checked) C_GREEN else C_GRAY),
                            ) { }
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = mealLabel(type),
                                style = TextStyle(color = C_WHITE, fontSize = 9.sp),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "今日无计划",
                    style = TextStyle(color = C_WHITE, fontSize = 9.sp),
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // 操作：打卡午餐 + 打开 App
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = GlanceModifier
                        .background(C_GREEN)
                        .cornerRadius(4.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(
                            onClick = actionRunCallback<WidgetCheckInCallback>(
                                actionParametersOf(WidgetCheckInCallback.KEY_TYPE to MealType.LUNCH.name),
                            ),
                        ),
                ) {
                    Text(
                        text = "打卡午餐",
                        style = TextStyle(color = C_BG, fontSize = 10.sp),
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .background(C_BG)
                        .cornerRadius(4.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(onClick = actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        text = "打开 App",
                        style = TextStyle(color = C_WHITE, fontSize = 10.sp),
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
