package com.dovimu

import android.app.Application
import com.dovimu.platform.AndroidApp
import com.dovimu.platform.CrashReporter

class DoVimuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidApp.context = applicationContext
        CrashReporter.install(this)
    }
}
