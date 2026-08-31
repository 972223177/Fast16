package com.ly.fast16.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 纯 JVM 时间工具：Instant / LocalTime / LocalDate 换算与整分钟对齐。
 *
 * **无 Android 依赖**（domain 层可安全引用）；所有时区由调用方显式传入——
 * 取「系统时区 / 系统时间」请走 [com.ly.fast16.core.device.SystemTimeProvider]
 * （Android 感知的唯一兼容豁免点，AGENTS.md §6 兼容方案下沉 core/）。
 */
object Time {

    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** 向下对齐到整分钟（截断秒与纳秒）。像素风产品时刻精度 = 分钟。 */
    fun alignToMinute(instant: Instant): Instant =
        instant.truncatedTo(ChronoUnit.MINUTES)

    /** Instant → 指定时区的 LocalDate */
    fun dateOf(instant: Instant, zone: ZoneId): LocalDate =
        instant.atZone(zone).toLocalDate()

    /** Instant → 指定时区的 LocalTime */
    fun timeOf(instant: Instant, zone: ZoneId): LocalTime =
        instant.atZone(zone).toLocalTime()

    /** LocalDate + LocalTime → Instant（指定时区） */
    fun at(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        ZonedDateTime.of(date, time, zone).toInstant()

    /** ISO yyyy-MM-dd 字符串 → LocalDate（Room 列格式） */
    fun parseDate(iso: String): LocalDate = LocalDate.parse(iso, ISO_DATE)

    /** LocalDate → ISO yyyy-MM-dd 字符串（Room 列格式） */
    fun formatDate(date: LocalDate): String = ISO_DATE.format(date)
}
