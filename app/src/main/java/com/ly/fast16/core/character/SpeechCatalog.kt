package com.ly.fast16.core.character

import com.ly.fast16.domain.model.MealPhase
import com.ly.fast16.domain.model.MealType
import kotlin.random.Random

/**
 * 角色气泡文案目录（设计方案 §8.6 / FR-12）：
 * - 按 (phase, mealType) 键控模板，同一键多条**随机轮换**
 * - 模板变量：{餐名} {剩余时间} {备餐分钟}，填充后**≤20 字**
 * - **通知文案与气泡同源**（通知触发与 PixelBubble 都调本目录，保证一致）
 *
 * 新增文案后须同步重跑字体子集化（docs/font-licenses.md）。
 */
object SpeechCatalog {

    private val MEAL_NAMES: Map<MealType, String> = mapOf(
        MealType.BREAKFAST to "早餐",
        MealType.LUNCH to "午餐",
        MealType.DINNER to "晚餐",
    )

    /** 餐前准备文案（prepMinutes > 0 才有 PREP 阶段） */
    private val PREP_TEMPLATES: Map<MealType, List<String>> = mapOf(
        MealType.LUNCH to listOf(
            "备餐时间！{餐名}做起来",
            "{备餐分钟} 分钟备餐，主食和菜安排上！",
        ),
        MealType.DINNER to listOf(
            "备餐时间！{餐名}做起来",
            "{备餐分钟} 分钟备餐，主食和菜安排上！",
        ),
    )

    /** 开始吃文案 */
    private val EATING_TEMPLATES: Map<MealType, List<String>> = mapOf(
        MealType.BREAKFAST to listOf(
            "开饭啦！早餐慢慢吃",
            "早餐到点，好好吃！",
        ),
        MealType.LUNCH to listOf(
            "开饭啦！午餐慢慢吃",
            "{餐名}到点，好好吃饭",
        ),
        MealType.DINNER to listOf(
            "开饭啦！晚餐慢慢吃",
            "{餐名}到点，好好吃饭",
        ),
    )

    /** 取 (phase, mealType) 文案（变量填充 + 随机轮换）；PREP 无模板时回退 EATING */
    fun text(phase: MealPhase, mealType: MealType, vars: Map<String, String> = emptyMap()): String {
        val pool = when (phase) {
            MealPhase.PREP -> PREP_TEMPLATES[mealType] ?: EATING_TEMPLATES[mealType].orEmpty()
            MealPhase.EATING -> EATING_TEMPLATES[mealType].orEmpty()
        }
        if (pool.isEmpty()) return "到点啦！记得打卡"
        val template = pool[Random.nextInt(pool.size)]
        return fill(template, vars)
    }

    /** 餐名（供 UI/通知使用） */
    fun mealName(mealType: MealType): String = MEAL_NAMES.getValue(mealType)

    /** 模板变量填充：{餐名}{剩余时间}{备餐分钟} */
    private fun fill(template: String, vars: Map<String, String>): String = vars
        .entries
        .fold(template) { acc, (key, value) -> acc.replace("{$key}", value) }
}
