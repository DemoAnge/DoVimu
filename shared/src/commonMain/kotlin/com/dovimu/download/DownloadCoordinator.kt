package com.dovimu.download

import com.dovimu.data.DownloadItem
import com.dovimu.data.DoVimuApi
import com.dovimu.data.InspectResponse
import com.dovimu.data.QualityOption
import com.dovimu.data.TrackMetadata
import com.dovimu.data.createHttpClient
import com.dovimu.data.prettySpeed
import com.dovimu.platform.FileMeta
import com.dovimu.platform.PlatformServices
import com.dovimu.platform.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-scoped download runner. Survives leaving the UI so the transfer
 * and its status-bar notification keep going in the background.
 */
class DownloadCoordinator private constructor(
    private val platform: PlatformServices,
) {
    private val client = createHttpClient(platform.httpEngine)
    private val api = DoVimuApi(client) { platform.settings.getBaseUrl() }
    private val savingIds = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _jobs = MutableStateFlow<List<DownloadItem>>(emptyList())
    val jobs: StateFlow<List<DownloadItem>> = _jobs

    init {
        scope.launch {
            while (isActive) {
                val hasActive = _jobs.value.any { it.status in ACTIVE_STATUSES }
                if (hasActive) {
                    runCatching { pollActiveJobs() }
                    delay(900)
                } else {
                    delay(1_500)
                }
            }
        }
    }

    suspend fun start(url: String, quality: QualityOption, media: InspectResponse) {
        val created = api.startDownload(url, quality.id)
        val meta = media.metadata.let { current ->
            when {
                current == null -> TrackMetadata(
                    title = media.title,
                    durationSeconds = media.duration,
                    thumbnail = media.thumbnail,
                )
                current.durationSeconds == null && media.duration != null ->
                    current.copy(durationSeconds = media.duration)
                else -> current
            }
        }
        publish(
            DownloadItem(
                jobId = created.jobId,
                title = meta.title ?: media.title,
                qualityLabel = "${quality.label} · ${quality.ext.uppercase()}",
                kind = quality.kind,
                thumbnail = meta.thumbnail ?: media.thumbnail,
                status = created.status,
                progress = 0f,
                downloadUrl = created.fileUrl,
                artist = meta.artist,
                album = meta.album,
                metadata = meta,
            ),
        )
    }

    fun retrySave(item: DownloadItem) {
        scope.launch { saveToDevice(item) }
    }

    fun cancel(item: DownloadItem): Job = scope.launch {
        runCatching { api.cancel(item.jobId) }
        _jobs.update { current -> current.filterNot { it.jobId == item.jobId } }
        platform.cancelDownloadNotification(item.jobId)
    }

    private suspend fun pollActiveJobs() {
        val active = _jobs.value.filter { it.status in ACTIVE_STATUSES }
        if (active.isEmpty()) return
        for (item in active) {
            if (item.status == "saving") continue
            try {
                val remote = api.job(item.jobId)
                val meta = remote.metadata ?: item.metadata
                val merged = item.copy(
                    status = remote.status,
                    progress = remote.progress.toFloat(),
                    speedBytes = remote.speedBytes,
                    etaSeconds = remote.etaSeconds,
                    error = remote.error,
                    filename = remote.filename ?: item.filename,
                    mime = remote.mime ?: item.mime,
                    downloadUrl = remote.downloadUrl ?: item.downloadUrl,
                    title = meta?.title ?: remote.title ?: item.title,
                    artist = meta?.artist ?: item.artist,
                    album = meta?.album ?: item.album,
                    metadata = meta,
                    thumbnail = meta?.thumbnail ?: item.thumbnail,
                )
                publish(merged)
                if (remote.status == "ready" && merged.localUri == null) {
                    saveToDevice(merged)
                }
            } catch (_: Exception) {
                // keep last known status; next tick retries
            }
        }
    }

    private suspend fun saveToDevice(item: DownloadItem) {
        if (!beginSave(item.jobId)) return
        publish(item.copy(status = "saving", progress = 0f, error = null))
        try {
            val relative = item.downloadUrl ?: "/api/v1/jobs/${item.jobId}/file"
            val url = platform.settings.getBaseUrl().trimEnd('/') + relative
            val filename = displayFilename(item)
            val uri = platform.fileSaver.save(
                client,
                url,
                filename,
                item.mime,
                { progress ->
                    val current = _jobs.value.firstOrNull { it.jobId == item.jobId }
                    if (current != null) {
                        publish(current.copy(status = "saving", progress = progress))
                    }
                },
                FileMeta(
                    title = humanizeName(item.metadata?.title ?: item.title).ifBlank { item.title },
                    artist = humanizeName(item.metadata?.artist ?: item.artist.orEmpty()).ifBlank { null },
                    album = humanizeName(item.metadata?.album ?: item.album.orEmpty()).ifBlank { null },
                    albumArtist = humanizeName(item.metadata?.albumArtist.orEmpty()).ifBlank { null },
                    year = item.metadata?.year,
                    track = item.metadata?.track,
                    durationMs = item.metadata?.durationSeconds?.let { it * 1000L },
                    width = item.metadata?.width,
                    height = item.metadata?.height,
                    lyrics = item.metadata?.lyrics?.trim()?.ifBlank { null },
                ),
            )
            publish(
                item.copy(status = "saved", progress = 100f, localUri = uri, filename = filename, error = null),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            publish(
                item.copy(
                    status = "save_failed",
                    error = e.toUserMessage().ifBlank { "No se pudo guardar el archivo en el teléfono." },
                ),
            )
        } finally {
            endSave(item.jobId)
        }
    }

    private fun publish(item: DownloadItem) {
        _jobs.update { current ->
            if (current.any { it.jobId == item.jobId }) {
                current.map { if (it.jobId == item.jobId) item else it }
            } else {
                listOf(item) + current
            }
        }
        runCatching {
            platform.notifyDownload(
                jobId = item.jobId,
                title = item.title,
                status = item.status,
                progress = item.progress.toInt().coerceIn(0, 100),
                speedLabel = prettySpeed(item.speedBytes),
                localUri = item.localUri,
                mime = item.mime,
                error = item.error,
            )
        }
    }

    private fun beginSave(jobId: String): Boolean = synchronized(savingIds) { savingIds.add(jobId) }

    private fun endSave(jobId: String) {
        synchronized(savingIds) { savingIds.remove(jobId) }
    }

    private fun displayFilename(item: DownloadItem): String {
        val ext = item.filename
            ?.substringAfterLast('.')
            ?.takeIf { it.isNotBlank() && it.length <= 5 }
            ?: if (item.kind == "audio") "mp3" else "mp4"
        val artist = humanizeName(item.metadata?.artist.orEmpty())
        val title = humanizeName((item.metadata?.title ?: item.title).ifBlank { "DoVimu" })
        val stem = if (artist.isNotEmpty() && !title.contains(artist, ignoreCase = true)) {
            "$artist - $title"
        } else {
            title
        }
        val cleaned = stem.replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "DoVimu" }
        return "$cleaned.$ext"
    }

    private fun humanizeName(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val text = if ("_-_" in trimmed || (trimmed.count { it == '_' } >= 2 && ' ' !in trimmed)) {
            trimmed.replace('_', ' ')
        } else {
            trimmed
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        private val ACTIVE_STATUSES = setOf(
            "queued",
            "downloading",
            "processing",
            "ready",
            "saving",
        )

        @Volatile
        private var instance: DownloadCoordinator? = null

        fun get(platform: PlatformServices): DownloadCoordinator {
            return instance ?: synchronized(this) {
                instance ?: DownloadCoordinator(platform).also { instance = it }
            }
        }
    }
}
