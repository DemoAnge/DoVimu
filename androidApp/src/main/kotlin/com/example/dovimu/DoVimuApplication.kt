package com.example.dovimu

import android.app.Application
import com.example.dovimu.platform.AndroidApp
import com.example.dovimu.platform.CrashReporter

class DoVimuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidApp.context = applicationContext
        CrashReporter.install(this)
    }
}
