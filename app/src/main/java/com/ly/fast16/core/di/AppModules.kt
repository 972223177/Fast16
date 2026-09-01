package com.ly.fast16.core.di

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.ly.fast16.core.device.SystemTimeProvider
import com.ly.fast16.core.notification.NotificationFactory
import com.ly.fast16.core.notification.VibratorCompat
import com.ly.fast16.core.plan.AutoPlanGenerator
import com.ly.fast16.core.system.ExactAlarmGate
import com.ly.fast16.core.system.NotificationPermission
import com.ly.fast16.core.scheduling.AlarmPlanScheduler
import com.ly.fast16.core.scheduling.NotificationReminderChannel
import com.ly.fast16.core.scheduling.PlanScheduler
import com.ly.fast16.core.scheduling.ReminderChannel
import com.ly.fast16.core.widget.WidgetDataProvider
import com.ly.fast16.data.local.AppDatabase
import com.ly.fast16.data.local.CheckInDao
import com.ly.fast16.data.local.SettingsStore
import com.ly.fast16.data.repository.LocalCheckInRepository
import com.ly.fast16.data.repository.LocalPlanRepository
import com.ly.fast16.domain.repository.CheckInRepository
import com.ly.fast16.domain.repository.PlanRepository
import com.ly.fast16.domain.usecase.CheckInUseCase
import com.ly.fast16.domain.usecase.DefaultCheckInUseCase
import com.ly.fast16.feature.create.ui.CreateViewModel
import com.ly.fast16.feature.home.ui.HomeViewModel
import com.ly.fast16.feature.record.ui.RecordViewModel
import com.ly.fast16.feature.settings.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
    // IO 调度器注入（Receiver 等系统实例化组件经 KoinComponent.inject 取用）
    single<CoroutineDispatcher> { Dispatchers.IO }
    single {
        // Room 3.0：SQLiteDriver 体系 + 协程上下文取代 Executor（官方迁移文档 §四）
        Room.databaseBuilder<AppDatabase>(androidContext(), AppDatabase.NAME)
            .setQueryCoroutineContext(Dispatchers.IO)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }
    single { SettingsStore(androidContext()) }
    // DAO 注册（Room 3：Local*Repository 经 Koin 注入；运行时缺定义会抛 NoDefinitionFound）
    single<CheckInDao> { get<AppDatabase>().checkInDao() }
    single<PlanRepository> { LocalPlanRepository(get(), get()) }
    single<CheckInRepository> { LocalCheckInRepository(get()) }
    single<CheckInUseCase> { DefaultCheckInUseCase(get(), get()) }
    // 早餐打卡自动生成（Home「打卡现在这餐」入口；Settings 由调用方读出传入，无 data 依赖）
    single { AutoPlanGenerator(get(), get()) }
    // 桌面小组件数据源：注入 domain repository + SettingsStore（core/widget 不直连 DAO）
    single { WidgetDataProvider(get(), get(), get()) }
}

/**
 * 调度层装配：权限门 / 通知工厂 / PlanScheduler / ReminderChannel。
 */
val schedulingModule: Module = module {
    single<ExactAlarmGate> { ExactAlarmGate(androidContext()) }
    single<NotificationPermission> { NotificationPermission(androidContext()) }
    single<VibratorCompat> { VibratorCompat(androidContext()) }
    single<NotificationFactory> { NotificationFactory(androidContext()) }
    single<PlanScheduler> { AlarmPlanScheduler(androidContext(), get(), get()) }
    single<ReminderChannel> { NotificationReminderChannel(androidContext(), get(), get()) }
}
