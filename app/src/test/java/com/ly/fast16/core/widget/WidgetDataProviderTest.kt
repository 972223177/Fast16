package com.ly.fast16.core.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class WidgetDataProviderTest {

    @Test
    fun fmtDuration_formatsHoursMinutes() {
        assertEquals("0:00", WidgetDataProvider.fmtDuration(Duration.ofSeconds(0)))
        assertEquals("3:20", WidgetDataProvider.fmtDuration(Duration.ofMinutes(200)))
        assertEquals("16:05", WidgetDataProvider.fmtDuration(Duration.ofMinutes(965)))
        // 59 分 59 秒 → 分钟精度截断为 59 分
        assertEquals("0:59", WidgetDataProvider.fmtDuration(Duration.ofSeconds(3599)))
    }

    @Test
    fun fmtDuration_clampsNegativeToZero() {
        assertEquals("0:00", WidgetDataProvider.fmtDuration(Duration.ofMinutes(-5)))
    }
}
