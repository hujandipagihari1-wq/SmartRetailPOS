package com.industri.smartpos

import android.app.Application
import timber.log.Timber

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Pasang Debug Tree HANYA saat aplikasi dijalankan dalam mode DEBUG
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}