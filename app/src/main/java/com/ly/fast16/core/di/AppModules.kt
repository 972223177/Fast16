package com.ly.fast16.core.di

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.scheduling.AlarmPlanScheduler
import com.ly.fast16.core.scheduling.NotificationReminderChannel
import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.core.scheduling.ReminderChannel
import com.ly.fast16.data.local.AppDatabase
import com.ly.fast16.data.local.SettingsStore
import com.ly.fast16.data.repository.CheckInRepository
import com.ly.fast16.data.repository.LocalCheckInRepository
import com.ly.fast16.data.repository.LocalPlanRepository
import com.ly.fast16.data.repository.PlanRepository
import com.ly.fast16.feature.create.ui.CreateViewModel
import com.ly.fast16.feature.home.ui.HomeViewModel
import com.ly.fast16.feature.record.ui.RecordViewModel
import com.ly.fast16.feature.settings.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.time.Clock

/**
 * app 级装配：ViewModel（feature MVI 壳）。
 */
val appModule: Module = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::CreateViewModel)
    viewModelOf(::RecordViewModel)
    viewModelOf(::SettingsViewModel)
}

/**
 * 数据层装配：Room / DataStore / Repository / Clock。
 * Clock 注入（testing-best-practices：时间源可替换可测）。
 */
val dataModule: Module = module {
    // 系统时钟走 core/device 统一兼容封装（API 24–37 全区间，AGENTS.md §6）
    single<Clock> { SystemTimeProvider.clock }
    single {
        // Room 3.0：SQLiteDriver 体系 + 协程上下文取代 Executor（官方迁移文档 §四）
        Room.databaseBuilder<AppDatabase>(androidContext(), AppDatabase.NAME)
            .setQueryCoroutineContext(Dispatchers.IO)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }
    single { SettingsStore(androidContext()) }
    single<PlanRepository> { LocalPlanRepository(get(), get()) }
    single<CheckInRepository> { LocalCheckInRepository(get()) }
}

/**
 * 调度层装配：PlanScheduler / ReminderChannel（M1 填充实现）。
 */
val schedulingModule: Module = module {
    single<PlanScheduler> { AlarmPlanScheduler() }
    single<ReminderChannel> { NotificationReminderChannel() }
}
