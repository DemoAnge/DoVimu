package com.dovimu.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dovimu.shared.R
import java.util.concurrent.ConcurrentHashMap

internal object DownloadNotificationController {
    private const val PROGRESS_CHANNEL = "dovimu_downloads_progress"
    private const val DONE_CHANNEL = "dovimu_downloads_done"
    private const val FGS_ID = 1001
    private const val THROTTLE_MS = 300L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jobs = ConcurrentHashMap<String, JobNotice>()
    private val lastPostedAt = ConcurrentHashMap<String, Long>()
    private val lastSignature = ConcurrentHashMap<String, String>()

    @Volatile
    private var service: Service? = null

    fun sync(
        jobId: String,
        title: String,
        status: String,
        progress: Int,
        speedLabel: String?,
        localUri: String?,
        mime: String?,
        error: String?,
    ) {
        val notice = JobNotice(
            jobId = jobId,
            title = title.ifBlank { "DoVimu" },
            status = status,
            progress = progress.coerceIn(0, 100),
            speedLabel = speedLabel,
            localUri = localUri,
            mime = mime,
            error = error,
        )
        if (notice.isCancelled) {
            cancel(jobId)
            return
        }
        jobs[jobId] = notice
        runCatching {
            ensureChannel()
            requestPermissionIfNeeded()
            when {
                notice.isActive -> {
                    if (!shouldPost(notice)) return@runCatching
                    ensureService()
                    postProgress()
                }
                notice.isDone -> complete(notice)
                notice.isFailed -> fail(notice)
                else -> cancel(jobId)
            }
        }.onFailure(CrashReporter::recordNonFatal)
    }

    fun cancel(jobId: String) {
        jobs.remove(jobId)
        lastPostedAt.remove(jobId)
        lastSignature.remove(jobId)
        runCatching { NotificationManagerCompat.from(AndroidApp.context).cancel(notifyId(jobId)) }
        postProgress()
        stopServiceIfIdle()
    }

    fun onServiceStart(svc: Service) {
        service = svc
        val notification = runCatching {
            ensureChannel()
            progressNotification() ?: placeholderNotification()
        }.getOrElse { placeholderNotification() }
        runCatching { startForeground(svc, notification) }
            .onFailure { error ->
                CrashReporter.recordNonFatal(error)
                svc.stopSelf()
                return
            }
        if (activeJobs().isEmpty()) {
            stopServiceIfIdle()
        }
    }

    fun onServiceDestroyed(svc: Service) {
        if (service === svc) service = null
    }

    private fun complete(notice: JobNotice) {
        jobs.remove(notice.jobId)
        lastPostedAt.remove(notice.jobId)
        lastSignature.remove(notice.jobId)
        val context = AndroidApp.context
        val notification = NotificationCompat.Builder(context, DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download_done)
            .setContentTitle(notice.title)
            .setContentText(context.getString(R.string.notification_complete))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_complete_detail)),
            )
            .setColor(ContextCompat.getColor(context, R.color.notification_complete))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentIntent(notice))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        notify(notifyId(notice.jobId), notification)
        postProgress()
        stopServiceIfIdle()
    }

    private fun fail(notice: JobNotice) {
        jobs.remove(notice.jobId)
        lastPostedAt.remove(notice.jobId)
        lastSignature.remove(notice.jobId)
        val context = AndroidApp.context
        val detail = notice.error?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notification_failed)
        val notification = NotificationCompat.Builder(context, DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(notice.title)
            .setContentText(context.getString(R.string.notification_failed))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setColor(ContextCompat.getColor(context, R.color.notification_failed))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(notice))
            .build()
        notify(notifyId(notice.jobId), notification)
        postProgress()
        stopServiceIfIdle()
    }

    private fun postProgress() {
        val active = activeJobs()
        val svc = service
        val notification = progressNotification()
        if (active.isEmpty() || notification == null) {
            stopServiceIfIdle()
            return
        }
        if (svc != null) {
            startForeground(svc, notification)
        } else {
            notify(FGS_ID, notification)
        }
    }

    private fun progressNotification(): Notification? {
        val active = activeJobs()
        if (active.isEmpty()) return null
        val context = AndroidApp.context
        val primary = active.maxBy { it.progress }
        val statusText = statusLine(primary)
        val title = if (active.size > 1) {
            context.getString(R.string.notification_multiple, active.size)
        } else {
            primary.title
        }
        val indeterminate = primary.progress <= 0 ||
            primary.status == "queued" ||
            primary.status == "processing" ||
            primary.status == "ready"
        val builder = NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(statusText)
            .setColor(ContextCompat.getColor(context, R.color.notification_download))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setProgress(100, primary.progress, indeterminate)
            .setContentIntent(contentIntent(primary))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (active.size > 1) {
            builder.setSubText(primary.title)
        }
        return builder.build()
    }

    private fun placeholderNotification(): Notification {
        val context = AndroidApp.context
        return NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.notification_placeholder))
            .setColor(ContextCompat.getColor(context, R.color.notification_download))
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, 0, true)
            .setContentIntent(contentIntent(null))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun statusLine(notice: JobNotice): String {
        val context = AndroidApp.context
        val base = when (notice.status) {
            "queued" -> context.getString(R.string.notification_queued)
            "processing" -> context.getString(R.string.notification_processing)
            "ready" -> context.getString(R.string.notification_saving_wait)
            "saving" -> if (notice.progress > 0) {
                context.getString(R.string.notification_saving, notice.progress)
            } else {
                context.getString(R.string.notification_saving_wait)
            }
            else -> if (notice.progress > 0) {
                context.getString(R.string.notification_downloading, notice.progress)
            } else {
                context.getString(R.string.notification_downloading_wait)
            }
        }
        val speed = notice.speedLabel
        return if (speed.isNullOrBlank() || notice.status != "downloading") {
            base
        } else {
            context.getString(R.string.notification_progress_speed, base, speed)
        }
    }

    private fun shouldPost(notice: JobNotice): Boolean {
        val signature = "${notice.status}:${notice.progress}:${notice.speedLabel.orEmpty()}"
        val previous = lastSignature.put(notice.jobId, signature)
        if (previous == signature) return false
        val now = System.currentTimeMillis()
        val last = lastPostedAt[notice.jobId] ?: 0L
        val statusChanged = previous == null || previous.substringBefore(':') != notice.status
        if (!statusChanged && now - last < THROTTLE_MS) return false
        lastPostedAt[notice.jobId] = now
        return true
    }

    private fun ensureService() {
        if (service != null) return
        val start = {
            if (service == null) {
                val context = AndroidApp.context
                val intent = Intent(context, DownloadForegroundService::class.java)
                runCatching {
                    ContextCompat.startForegroundService(context, intent)
                }.onFailure(CrashReporter::recordNonFatal)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            start()
        } else {
            mainHandler.post { start() }
        }
    }

    private fun stopServiceIfIdle() {
        if (activeJobs().isNotEmpty()) return
        val svc = service ?: return
        ServiceCompat.stopForeground(svc, ServiceCompat.STOP_FOREGROUND_REMOVE)
        svc.stopSelf()
        service = null
        runCatching { NotificationManagerCompat.from(AndroidApp.context).cancel(FGS_ID) }
    }

    private fun startForeground(svc: Service, notification: Notification) {
        ServiceCompat.startForeground(
            svc,
            FGS_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun notify(id: Int, notification: Notification) {
        val context = AndroidApp.context
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val context = AndroidApp.context
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val progress = NotificationChannel(
            PROGRESS_CHANNEL,
            context.getString(R.string.notification_channel_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_progress_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val done = NotificationChannel(
            DONE_CHANNEL,
            context.getString(R.string.notification_channel_done),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_done_desc)
        }
        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(done)
    }

    private fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val context = AndroidApp.context
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val activity = AndroidApp.activity ?: return
        mainHandler.post {
            val current = AndroidApp.activity ?: return@post
            if (ContextCompat.checkSelfPermission(current, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                return@post
            }
            ActivityCompat.requestPermissions(
                current,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                92,
            )
        }
    }

    private fun contentIntent(notice: JobNotice?): PendingIntent {
        val context = AndroidApp.context
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val uri = notice?.localUri
        val view = if (!uri.isNullOrBlank()) {
            runCatching {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse(uri),
                        notice.mime ?: "*/*",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }.getOrNull()
        } else {
            null
        }
        val intent = view ?: launch
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            notice?.jobId.hashCode(),
            intent,
            flags,
        )
    }

    private fun notifyId(jobId: String): Int {
        val hashed = jobId.hashCode() and 0x7FFFFFFF
        return if (hashed == 0 || hashed == FGS_ID) hashed + 11 else hashed
    }

    private fun activeJobs(): List<JobNotice> = jobs.values.filter { it.isActive }

    private data class JobNotice(
        val jobId: String,
        val title: String,
        val status: String,
        val progress: Int,
        val speedLabel: String?,
        val localUri: String?,
        val mime: String?,
        val error: String?,
    ) {
        val isActive: Boolean
            get() = status in ACTIVE
        val isDone: Boolean
            get() = status == "saved"
        val isFailed: Boolean
            get() = status == "failed" || status == "save_failed"
        val isCancelled: Boolean
            get() = status == "cancelled"
    }

    private val ACTIVE = setOf("queued", "downloading", "processing", "ready", "saving")
}
