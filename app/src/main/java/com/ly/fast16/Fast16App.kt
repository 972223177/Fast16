package com.ly.fast16

import android.app.Application
import com.ly.fast16.core.di.appModule
import com.ly.fast16.core.di.dataModule
import com.ly.fast16.core.di.schedulingModule
import com.ly.fast16.core.notification.NotificationFactory
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin

class Fast16App : Application(), KoinComponent {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@Fast16App)
            modules(appModule, dataModule, schedulingModule)
        }
        // 26+ 通知渠道：App 启动即建（compat-api24-37 §0 红线，避免首次提醒静默丢弃）
        get<NotificationFactory>().createChannel()
    }
}
