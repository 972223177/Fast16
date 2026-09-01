package com.ly.fast16.domain.schedule

import com.ly.fast16.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class MealWindowTest {

    @Test
    fun breakfastWindow() {
        assertEquals(MealType.BREAKFAST, MealWindow.detect(LocalTime.of(4, 0)))
        assertEquals(MealType.BREAKFAST, LocalTime.of(8, 0).let { MealWindow.detect(it) })
        assertEquals(MealType.BREAKFAST, LocalTime.of(10, 59).let { MealWindow.detect(it) })
    }

    @Test
    fun lunchWindow() {
        assertEquals(MealType.LUNCH, MealWindow.detect(LocalTime.of(11, 0)))
        assertEquals(MealType.LUNCH, LocalTime.of(12, 30).let { MealWindow.detect(it) })
        assertEquals(MealType.LUNCH, LocalTime.of(14, 59).let { MealWindow.detect(it) })
    }

    @Test
    fun dinnerWindow() {
        assertEquals(MealType.DINNER, MealWindow.detect(LocalTime.of(15, 0)))
        assertEquals(MealType.DINNER, LocalTime.of(18, 0).let { MealWindow.detect(it) })
        assertEquals(MealType.DINNER, LocalTime.of(21, 59).let { MealWindow.detect(it) })
    }

    @Test
    fun lateNight_noMeal() {
        assertNull(MealWindow.detect(LocalTime.of(22, 0)))
        assertNull(MealWindow.detect(LocalTime.of(0, 30)))
        assertNull(MealWindow.detect(LocalTime.of(3, 59)))
    }

    @Test
    fun boundaries_areExclusive() {
        // 11:00 起算午餐，10:59 仍是早餐；15:00 起算晚餐，14:59 仍是午餐
        assertEquals(MealType.BREAKFAST, MealWindow.detect(LocalTime.of(10, 59)))
        assertEquals(MealType.LUNCH, MealWindow.detect(LocalTime.of(11, 0)))
        assertEquals(MealType.LUNCH, MealWindow.detect(LocalTime.of(14, 59)))
        assertEquals(MealType.DINNER, MealWindow.detect(LocalTime.of(15, 0)))
    }
}
