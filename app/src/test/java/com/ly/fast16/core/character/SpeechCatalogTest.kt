package com.ly.fast16.core.character

import com.ly.fast16.domain.model.MealPhase
import com.ly.fast16.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCatalogTest {

    @Test
    fun prep_returnsNonEmptyFilledText() {
        val text = SpeechCatalog.text(
            MealPhase.PREP,
            MealType.LUNCH,
            mapOf("餐名" to "午餐", "备餐分钟" to "30"),
        )
        assertTrue(text.isNotBlank())
        // 变量已填充：无残留 {餐名}/{备餐分钟} 占位（多条模板随机轮换，第二条不含餐名）
        assertTrue("{餐名}" !in text)
        assertTrue("{备餐分钟}" !in text)
    }

    @Test
    fun eating_returnsMealNameFilledText() {
        val text = SpeechCatalog.text(MealPhase.EATING, MealType.DINNER, mapOf("餐名" to "晚餐"))
        assertTrue("晚餐" in text)
    }

    @Test
    fun text_keepsUnder20Chars() {
        repeat(20) { _ ->
            val text = SpeechCatalog.text(MealPhase.PREP, MealType.LUNCH, mapOf("餐名" to "午餐", "备餐分钟" to "30"))
            assertTrue("文案超 20 字: $text", text.length <= 20)
            val eating = SpeechCatalog.text(MealPhase.EATING, MealType.DINNER, mapOf("餐名" to "晚餐"))
            assertTrue("文案超 20 字: $eating", eating.length <= 20)
        }
    }

    @Test
    fun multipleCalls_doNotCrashOnRandomRotation() {
        // 多条模板随机轮换：连续调用不抛异常且均为有效文案
        repeat(10) { _ ->
            val text = SpeechCatalog.text(MealPhase.EATING, MealType.BREAKFAST)
            assertTrue(text.isNotBlank())
        }
    }

    @Test
    fun mealName_mapsCorrectly() {
        assertEquals("早餐", SpeechCatalog.mealName(MealType.BREAKFAST))
        assertEquals("午餐", SpeechCatalog.mealName(MealType.LUNCH))
        assertEquals("晚餐", SpeechCatalog.mealName(MealType.DINNER))
    }
}
