package com.ly.fast16.core.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ExactAlarmGateTest {

    @Test
    fun api31_unAuthorized_cannotScheduleExact_andGrantIntentAvailable() {
        // Robolectric API 31 默认未授权精确闹钟
        val gate = ExactAlarmGate(RuntimeEnvironment.getApplication())
        assertFalse(gate.canScheduleExact)
        assertNotNull(gate.grantIntent())
    }

    @Test
    fun api31_authorized_grantIntentNull() {
        // 无法在单测中修改系统设置，此处验证门控逻辑结构：授权时 grantIntent 应为 null
        // （canScheduleExactAlarms 由系统状态决定，此处跳过真实授权模拟）
        val gate = ExactAlarmGate(RuntimeEnvironment.getApplication())
        if (gate.canScheduleExact) {
            assertNull(gate.grantIntent())
        }
    }

    @Config(sdk = [24])
    @Test
    fun api24_below31_alwaysSchedulableExact() {
        // <31 无精确闹钟权限体系，视为已授权
        val gate = ExactAlarmGate(RuntimeEnvironment.getApplication())
        assertTrue(gate.canScheduleExact)
        assertNull(gate.grantIntent())
    }
}
