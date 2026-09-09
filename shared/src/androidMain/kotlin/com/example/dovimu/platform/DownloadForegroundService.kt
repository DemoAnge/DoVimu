package com.example.dovimu.platform

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Keeps the process alive while a download is running so the status-bar
 * notification (and the transfer itself) survive leaving the app.
 */
class DownloadForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { DownloadNotificationController.onServiceStart(this) }
            .onFailure { error ->
                CrashReporter.recordNonFatal(error)
                runCatching { stopSelf() }
            }
        return START_STICKY
    }

    override fun onDestroy() {
        DownloadNotificationController.onServiceDestroyed(this)
        super.onDestroy()
    }
}
