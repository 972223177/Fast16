package com.ly.fast16.domain.stats

import java.time.LocalDate
import java.time.YearMonth

/**
 * 打卡统计（完整定义 §2.6–2.7）。
 *
 * 「完成日」= 当日三餐均存在 CheckIn 记录，由 CheckInDao 聚合后传入；
 * 本对象只做纯函数计算（gap 检测 / 比例），不落地，避免双写漂移。
 */
object StreakCalculator {

    /** 连续打卡天数：从今天（若今天尚未完成则从昨天）向前连续「完成日」数 */
    fun computeStreak(completedDays: Set<LocalDate>, today: LocalDate): Int {
        var cursor = if (completedDays.contains(today)) today else today.minusDays(1)
        var streak = 0
        while (completedDays.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** 本月完成率 = 完成日数 / 当月天数 */
    fun monthCompletion(completedDays: Set<LocalDate>, month: YearMonth): Float {
        val total = month.lengthOfMonth()
        val done = completedDays.count { it.year == month.year && it.month == month.month }
        return if (total == 0) 0f else done.toFloat() / total
    }

    /** 本周完成率 = 完成日数 / 本周天数（week 由调用方按周一起始等口径生成） */
    fun weekCompletion(completedDays: Set<LocalDate>, week: List<LocalDate>): Float =
        if (week.isEmpty()) 0f
        else week.count { it in completedDays }.toFloat() / week.size
}
