package com.ly.fast16.feature.home.ui

import com.ly.fast16.domain.model.CharacterState
import com.ly.fast16.domain.model.MealType

/** 首页 UI 状态：Loading / Content */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val hasPlanToday: Boolean,
        val characterState: CharacterState,
        val bubbleText: String,
        /** 断食剩余秒（null = 无未来动作） */
        val fastingSeconds: Long?,
        val meals: List<MealUi>,
        /** 顶部副标题：「今天 · 早餐 08:00 ｜ 窗口结束 16:00」 */
        val subtitle: String,
        /** 连续打卡天数（当月口径，原型右上 🔥 chip） */
        val streak: Int,
        /** 进食窗口进度 0..1（断食面板 16 格 seg） */
        val windowProgress: Float,
        /** 今日计划 id（-1 = 无计划；编辑入口用） */
        val planId: Long = -1L,
        /** 无计划时按 MealWindow 时段判定的当前餐次（空态「打卡现在这餐」用；null = 非用餐时段） */
        val detectedMealType: MealType? = null,
        /** 今日已打卡餐次（无计划空态展示打卡反馈；有计划的餐次在 meals 行内展示） */
        val checkedInToday: Set<MealType> = emptySet(),
    ) : HomeUiState
}

/** 三餐时间轴行数据（原型 meal-row：名称 / 时刻 / 备餐 / 状态 / 打卡） */
data class MealUi(
    val type: MealType,
    val name: String,
    val timeLabel: String,
    val prepMinutes: Int,
    val checkedIn: Boolean,
    val hasMeal: Boolean,
)

/**
 * 每秒变动的实时状态（高频 ticker）：
 * 断食倒计时 / 角色 / 气泡。与低频 [HomeUiState] 分离订阅，
 * 每秒只重组「倒计时 + 角色 + 气泡」局部，避免整页每秒重组。
 */
data class HomeLiveState(
    val fastingSeconds: Long?,
    val characterState: CharacterState,
    val bubbleText: String,
)
