package com.ly.fast16

import android.app.Application
import com.ly.fast16.core.di.appModule
import com.ly.fast16.core.di.dataModule
import com.ly.fast16.core.di.schedulingModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class Fast16App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@Fast16App)
            modules(appModule, dataModule, schedulingModule)
        }
    }
}
