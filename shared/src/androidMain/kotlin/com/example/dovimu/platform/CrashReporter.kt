package com.example.dovimu.platform

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val TAG = "DoVimu"
    private const val PREFS = "dovimu_crash"
    private const val KEY_MSG = "last_crash_message"
    private const val KEY_DETAIL = "last_crash_detail"
    private const val LOG_FILE = "dovimu-errors.log"

    fun install(context: Context) {
        val app = context.applicationContext
        restore(app)
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is DovimuUncaughtHandler) return
        Thread.setDefaultUncaughtExceptionHandler(DovimuUncaughtHandler(app, current))
    }

    fun recordNonFatal(error: Throwable) {
        Log.e(TAG, "error no fatal: ${error.toUserMessage()}", error)
        val ctx = runCatching { AndroidApp.context }.getOrNull() ?: return
        appendLog(ctx, error, fatal = false)
    }

    fun recordFatal(context: Context, error: Throwable) {
        val message = error.toUserMessage()
        val detail = error.stackTraceToString()
        Log.e(TAG, "crash fatal: $message", error)
        AppErrors.setFatal(message, detail)
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MSG, message)
                .putString(KEY_DETAIL, detail.take(8_000))
                .commit()
        }
        appendLog(context, error, fatal = true)
    }

    private fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val message = prefs.getString(KEY_MSG, null) ?: return
        val detail = prefs.getString(KEY_DETAIL, message) ?: message
        AppErrors.setFatal(message, detail)
        prefs.edit().remove(KEY_MSG).remove(KEY_DETAIL).apply()
    }

    private fun appendLog(context: Context, error: Throwable, fatal: Boolean) {
        runCatching {
            val file = File(context.filesDir, LOG_FILE)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val block = buildString {
                appendLine("----- $stamp ${if (fatal) "FATAL" else "ERROR"} -----")
                appendLine(error.stackTraceToString())
                appendLine()
            }
            file.appendText(block)
            if (file.length() > 64 * 1024) {
                file.writeText(file.readText().takeLast(32 * 1024))
            }
        }
    }

    private class DovimuUncaughtHandler(
        private val app: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            recordFatal(app, error)
            previous?.uncaughtException(thread, error)
        }
    }
}
