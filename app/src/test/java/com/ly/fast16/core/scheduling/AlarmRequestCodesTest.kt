package com.ly.fast16.core.scheduling

import com.ly.fast16.domain.model.MealPhase
import com.ly.fast16.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRequestCodesTest {

    @Test
    fun requestCode_contract_planIdTimes10PlusSlot() {
        // LUNCH.ordinal=1 → slot(PREP)=2, slot(EATING)=3
        assertEquals(12, AlarmRequestCodes.of(1, MealType.LUNCH, MealPhase.PREP))
        assertEquals(13, AlarmRequestCodes.of(1, MealType.LUNCH, MealPhase.EATING))
        // DINNER.ordinal=2 → slot=4/5
        assertEquals(25, AlarmRequestCodes.of(2, MealType.DINNER, MealPhase.EATING))
        // BREAKFAST.ordinal=0 → slot(PREP)=0, slot(EATING)=1
        assertEquals(10, AlarmRequestCodes.of(1, MealType.BREAKFAST, MealPhase.PREP))
        assertEquals(11, AlarmRequestCodes.of(1, MealType.BREAKFAST, MealPhase.EATING))
    }

    @Test
    fun requestCode_allSlotsInRange0To5() {
        MealType.entries.forEach { type ->
            MealPhase.entries.forEach { phase ->
                val slot = AlarmRequestCodes.of(0, type, phase) // planId=0 → 即 slot
                assertTrue("slot 越界: $slot", slot in 0..5)
            }
        }
    }

    @Test
    fun notificationId_usesMealIdBase() {
        // DINNER.ordinal=2 → slot(PREP)=4；mealId=0 → 0*10+4=4
        assertEquals(4, AlarmRequestCodes.notificationOf(0, MealType.DINNER, MealPhase.PREP))
        // mealId=5, DINNER/EATING(slot=5) → 5*10+5=55
        assertEquals(55, AlarmRequestCodes.notificationOf(5, MealType.DINNER, MealPhase.EATING))
    }

    @Test
    fun distinctPhasesHaveDistinctCodes() {
        val codePrep = AlarmRequestCodes.of(3, MealType.LUNCH, MealPhase.PREP)
        val codeEat = AlarmRequestCodes.of(3, MealType.LUNCH, MealPhase.EATING)
        assertTrue(codePrep != codeEat)
    }
}
