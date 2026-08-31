package com.ly.fast16.core.device

import android.annotation.SuppressLint
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 系统时间统一入口（AGENTS.md §6「兼容方案下沉 core/」：原生 API 版本差异一律封装进 core/device）。
 *
 * **API 24–37 全区间兼容说明**：
 * `java.time`（Clock / ZoneId / LocalDate / Instant…）的 SDK 元数据要求 **API 26**；
 * 本项目已启用核心库脱糖（app/build.gradle.kts `isCoreLibraryDesugaringEnabled = true`
 * + desugar_jdk_libs 2.1.5），**API 24/25 上由脱糖库提供等价实现，运行时全区间可用**。
 * lint/IDE 对脱糖 API 的 NewApi 误报仅在**本文件集中豁免**；feature/data 层零裸系统调用、
 * 零 `Build.VERSION` 分支、零散落 `@SuppressLint`（红线要求）。
 *
 * **职责边界**：
 * - 取「当前系统时间 / 系统时区」一律经本对象（Android 感知，唯一豁免点）。
 * - 纯日期换算（不依赖系统时间的确定性计算）走 `core/time/Time`（纯 JVM，
 *   domain 可安全引用，无 Android 依赖）；二者不重复造轮子。
 */
@SuppressLint("NewApi")
object SystemTimeProvider {

    /** 系统时钟（供数据层注入 [Clock] 依赖） */
    val clock: Clock = Clock.systemDefaultZone()

    /** 系统默认时区 */
    val zone: ZoneId = ZoneId.systemDefault()

    /** 当前时刻 */
    fun now(): Instant = clock.instant()

    /** 当前日期（系统时区） */
    fun today(): LocalDate = now().atZone(zone).toLocalDate()

    /** 当前时刻（系统时区） */
    fun nowTime(): LocalTime = now().atZone(zone).toLocalTime()

    /** 指定日期 + 时刻 → Instant（系统时区） */
    fun at(date: LocalDate, time: LocalTime): Instant =
        ZonedDateTime.of(date, time, zone).toInstant()

    /** Instant → 日期（系统时区） */
    fun dateOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** Instant → 时刻（系统时区） */
    fun timeOf(instant: Instant): LocalTime = instant.atZone(zone).toLocalTime()
}
