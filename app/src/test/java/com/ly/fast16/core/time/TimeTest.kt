package com.ly.fast16.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TimeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 8, 31)

    @Test
    fun alignToMinute_truncatesSecondsAndNanos() {
        val t = Time.at(date, LocalTime.of(12, 7), zone).plusSeconds(30).plusNanos(1)
        assertEquals(Time.at(date, LocalTime.of(12, 7), zone), Time.alignToMinute(t))
    }

    @Test
    fun alignToMinute_keepsWholeMinute() {
        val t = Time.at(date, LocalTime.of(12, 7), zone)
        assertEquals(t, Time.alignToMinute(t))
    }

    @Test
    fun dateAndTimeConversion_roundTrips() {
        val instant = Time.at(date, LocalTime.of(8, 16), zone)
        assertEquals(date, Time.dateOf(instant, zone))
        assertEquals(LocalTime.of(8, 16), Time.timeOf(instant, zone))
    }

    @Test
    fun sameInstant_differsAcrossZones() {
        val instant = Time.at(date, LocalTime.of(8, 16), zone)
        val utc = Time.timeOf(instant, ZoneId.of("UTC"))
        assertEquals(LocalTime.of(0, 16), utc)
    }

    @Test
    fun isoDate_parseAndFormatRoundTrip() {
        val iso = "2026-08-31"
        assertEquals(iso, Time.formatDate(Time.parseDate(iso)))
    }

    @Test
    fun at_buildsInstantOfWallClockTime() {
        val instant: Instant = Time.at(date, LocalTime.of(0, 16), zone)
        // 0:16 = 当日起点后 16 分钟
        assertEquals(16 * 60L, instant.epochSecond - date.atStartOfDay(zone).toEpochSecond())
    }
}
