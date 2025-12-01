package com.turboguys.myaibot

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyAiBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@MyAiBotApplication)
            modules(com.turboguys.myaibot.di.appModule)
        }
    }
}

