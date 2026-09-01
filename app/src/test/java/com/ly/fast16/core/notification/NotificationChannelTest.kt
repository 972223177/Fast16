package com.ly.fast16.core.notification

import com.ly.fast16.domain.model.ReminderMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationChannelTest {

    @Test
    fun channelFor_mapsReminderModesToChannels() {
        assertEquals(NotificationFactory.CHANNEL_MEAL_REMINDER, NotificationFactory.channelFor(ReminderMode.NOTIFY))
        assertEquals(NotificationFactory.CHANNEL_MEAL_VIBRATE, NotificationFactory.channelFor(ReminderMode.VIBRATE))
        assertEquals(NotificationFactory.CHANNEL_MEAL_SOUND, NotificationFactory.channelFor(ReminderMode.SOUND))
        // ALARM 走默认高重要度渠道（设备侧表现 DEFERRED）
        assertEquals(NotificationFactory.CHANNEL_MEAL_REMINDER, NotificationFactory.channelFor(ReminderMode.ALARM))
    }
}
