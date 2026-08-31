package com.ly.fast16.domain.model

import java.time.Instant
import java.time.LocalDate

// ---------- 枚举（完整定义 §1.0，Int 值对齐 Room 列存储） ----------

/** 餐次类型 */
enum class MealType(val value: Int) {
    BREAKFAST(0), LUNCH(1), DINNER(2);

    companion object {
        fun from(value: Int): MealType = entries.first { it.value == value }
    }
}

/** 计划状态 */
enum class PlanStatus(val value: Int) {
    ACTIVE(0), ARCHIVED(1);

    companion object {
        fun from(value: Int): PlanStatus = entries.first { it.value == value }
    }
}

/** 餐次状态机：SCHEDULED → PREPARING → EATING → COMPLETED（设计方案 §1） */
enum class MealStatus(val value: Int) {
    SCHEDULED(0), PREPARING(1), EATING(2), COMPLETED(3);

    companion object {
        fun from(value: Int): MealStatus = entries.first { it.value == value }
    }
}

/** 提醒模式（V1 = NOTIFY + 固定轻短震动；VIBRATE/SOUND/ALARM 为 M3 扩展） */
enum class ReminderMode(val value: Int) {
    NONE(0), NOTIFY(1), VIBRATE(2), SOUND(3), ALARM(4);

    companion object {
        fun from(value: Int): ReminderMode = entries.first { it.value == value }
    }
}

/** 提醒触发阶段（PREP = 餐前准备时刻，EATING = 开始吃时刻；设计方案 §5.3） */
enum class MealPhase { PREP, EATING }

/** 健身像素小人状态（由「当前时间 × 今日计划」实时派生，不落库；设计方案 §8.6） */
enum class CharacterState { IDLE, FASTING, PREP, EATING, REST }

// ---------- 领域模型（设计方案 §6） ----------

/**
 * 就餐计划：某一天的一整天安排。
 *
 * @param date ISO yyyy-MM-dd（一天最多一个计划，Room 唯一索引约束）
 */
data class MealPlan(
    val id: Long = 0L,
    val date: LocalDate,
    val windowStart: Instant,
    val windowEnd: Instant,
    val status: PlanStatus = PlanStatus.ACTIVE,
)

/**
 * 一餐：含「开始吃时刻」与「餐前准备时长」两个核心字段。
 *
 * [prepTime] 由 mealTime − prepMinutes **派生**（不落库，单一事实源）。
 */
data class Meal(
    val id: Long = 0L,
    val planId: Long = 0L,
    val type: MealType,
    val mealTime: Instant,
    val prepMinutes: Int,
    val status: MealStatus = MealStatus.SCHEDULED,
    val reminderMode: ReminderMode = ReminderMode.NOTIFY,
) {
    /** 「餐前准备」时刻 = 开始吃时刻 − 备餐分钟（派生字段，永不持久化） */
    val prepTime: Instant
        get() = mealTime.minusSeconds(prepMinutes * 60L)
}

/**
 * 打卡记录：对某日某餐「已完成进食」的确认。
 * 与计划解耦——无计划也能补打卡。
 */
data class CheckIn(
    val date: LocalDate,
    val mealType: MealType,
    val checkedAt: Instant,
)
