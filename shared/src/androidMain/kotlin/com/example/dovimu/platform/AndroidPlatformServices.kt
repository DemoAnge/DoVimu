package com.example.dovimu.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import io.ktor.client.engine.okhttp.OkHttp

private val BRAVE_PACKAGES = listOf(
    "com.brave.browser",
    "com.brave.browser_beta",
    "com.brave.browser_nightly",
)

actual fun createPlatformServices(): PlatformServices {
    val context = AndroidApp.context
    return object : PlatformServices {
        override val settings: SettingsStore = AndroidSettingsStore(context)
        override val fileSaver: FileSaver = AndroidFileSaver(context)
        override val httpEngine = OkHttp.create()

        override fun openFile(uri: String, mime: String?): String? {
            return try {
                val parsed = Uri.parse(uri)
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(parsed, mime ?: context.contentResolver.getType(parsed) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(view, "Abrir con").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
                null
            } catch (error: ActivityNotFoundException) {
                CrashReporter.recordNonFatal(error)
                "No hay una app para abrir este archivo."
            } catch (error: Exception) {
                CrashReporter.recordNonFatal(error)
                error.toUserMessage()
            }
        }

        override fun notifyDownload(
            jobId: String,
            title: String,
            status: String,
            progress: Int,
            speedLabel: String?,
            localUri: String?,
            mime: String?,
            error: String?,
        ) {
            DownloadNotificationController.sync(
                jobId = jobId,
                title = title,
                status = status,
                progress = progress,
                speedLabel = speedLabel,
                localUri = localUri,
                mime = mime,
                error = error,
            )
        }

        override fun cancelDownloadNotification(jobId: String) {
            DownloadNotificationController.cancel(jobId)
        }

        override fun hasBrave(): Boolean = BRAVE_PACKAGES.any { pkg ->
            runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
        }

        override fun openInBrave(url: String): Boolean {
            val parsed = Uri.parse(url.ifBlank { "https://m.youtube.com" })
            for (pkg in BRAVE_PACKAGES) {
                if (runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull() == null) continue
                val opened = runCatching {
                    val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                    tabs.intent.`package` = pkg
                    tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    tabs.launchUrl(context, parsed)
                    true
                }.recoverCatching {
                    val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
                        `package` = pkg
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                if (opened.isSuccess) return true
                opened.exceptionOrNull()?.let(CrashReporter::recordNonFatal)
            }
            val store = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.brave.browser"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            return try {
                context.startActivity(store)
                false
            } catch (error: ActivityNotFoundException) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=com.brave.browser"),
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                    )
                }.onFailure(CrashReporter::recordNonFatal)
                false
            } catch (error: Exception) {
                CrashReporter.recordNonFatal(error)
                false
            }
        }
    }
}
