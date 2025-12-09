package com.danpun9.memocore

import android.app.Application
import com.danpun9.memocore.data.ObjectBoxStore
import com.danpun9.memocore.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

class MemocoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MemocoreApplication)
            modules(AppModule().module)
        }
        ObjectBoxStore.init(this)
    }
}
