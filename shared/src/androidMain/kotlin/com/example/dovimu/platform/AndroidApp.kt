package com.example.dovimu.platform

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

object AndroidApp {
    lateinit var context: Context

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    var activity: Activity?
        get() = activityRef?.get()
        set(value) {
            activityRef = value?.let { WeakReference(it) }
        }
}
