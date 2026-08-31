package com.ly.fast16.domain.schedule

import com.ly.fast16.core.time.Time
import java.time.Duration
import java.time.Instant

/**
 * 候选方案（设计方案 §7 / 完整定义 §2.1）
 *
 * @param label 语义标签：均衡 / 午餐偏早 / 晚餐偏晚
 */
data class Candidate(val label: String, val lunch: Instant, val dinner: Instant)

/**
 * 时间推理算法：由早餐时间（= 进食窗口起点，决策 #1 语义 A）推理午/晚 3 套候选。
 *
 * 约束（§7.1）：
 *  1. lunch ≥ B + minGap
 *  2. dinner ≥ lunch + minGap
 *  3. dinner ≤ B + W − bufferEnd（窗口硬尾）
 *
 * 输出全部经整分钟向下对齐（像素产品时刻精度 = 分钟），且**三条约束恒成立**：
 * 晚餐偏晚方案的午餐会被回拉到「硬尾 − minGap」，保证晚餐贴硬尾时间隔仍满足
 * （文档 §7.2 示例表中该方案午餐 12:45 与其自身约束 2 冲突，此处以约束为准）。
 * 退化参数（硬尾早于最早午餐，即约束互斥）时以窗口硬尾为上限，由 Validator 在
 * 微调阶段兜底提示，生成器不崩溃。
 */
object CandidateGenerator {

    fun generateCandidates(
        breakfast: Instant,
        window: Duration,
        minGap: Duration,
        bufferEnd: Duration,
    ): List<Candidate> {
        val hardEnd = breakfast.plus(window).minus(bufferEnd)   // 约束3：晚餐最晚时刻
        val earliest = breakfast.plus(minGap)                  // 约束1：午餐最早时刻
        val flex = window.minus(bufferEnd).minus(minGap)       // L：午/晚排布总弹性
        // 午餐上限：让「晚餐 = 硬尾」时间隔约束仍成立
        val lunchUpper = maxOf(earliest, hardEnd.minus(minGap))

        // 方案1 · 均衡：午餐=窗口中点，晚餐向硬尾靠 0.5·L
        val lunch1 = lunch(earliest, lunchUpper, breakfast.plus(window.dividedBy(2)))
        val dinner1 = dinner(lunch1.plus(minGap), hardEnd, lunch1.plus(minGap).plus(flex.dividedBy(2)))

        // 方案2 · 午餐偏早：午餐位于弹性区间 1/4 处，晚餐=午餐+间隔
        val lunch2 = lunch(earliest, lunchUpper, earliest.plus(flex.dividedBy(4)))
        val dinner2 = dinner(lunch2.plus(minGap), hardEnd, lunch2.plus(minGap))

        // 方案3 · 晚餐偏晚：晚餐贴硬尾，午餐回拉保证间隔
        val lunch3 = lunch(earliest, lunchUpper, earliest.plus(flex.multipliedBy(3).dividedBy(4)))
        val dinner3 = dinner(lunch3.plus(minGap), hardEnd, hardEnd)

        return listOf(
            Candidate("均衡", lunch1, dinner1),
            Candidate("午餐偏早", lunch2, dinner2),
            Candidate("晚餐偏晚", lunch3, dinner3),
        )
    }

    /** 午餐 = 公式值向下取整分钟，clamp 进 [earliest, lunchUpper] */
    private fun lunch(earliest: Instant, lunchUpper: Instant, formula: Instant): Instant {
        val aligned = Time.alignToMinute(formula)
        return when {
            aligned < earliest -> earliest
            aligned > lunchUpper -> lunchUpper
            else -> aligned
        }
    }

    /** 晚餐 = 公式值向下取整分钟后与硬尾取小；间隔下限仅在不超过硬尾时回拉 */
    private fun dinner(lowerBound: Instant, hardEnd: Instant, formula: Instant): Instant {
        val aligned = minOf(Time.alignToMinute(formula), hardEnd)
        return if (aligned < lowerBound) minOf(lowerBound, hardEnd) else aligned
    }
}
