package com.example.dovimu.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dovimu.data.ApiException
import com.example.dovimu.data.DoVimuApi
import com.example.dovimu.data.DownloadItem
import com.example.dovimu.data.InspectResponse
import com.example.dovimu.data.MainTab
import com.example.dovimu.data.QualityKind
import com.example.dovimu.data.QualityOption
import com.example.dovimu.data.SearchResult
import com.example.dovimu.data.createHttpClient
import com.example.dovimu.data.extractUrl
import com.example.dovimu.data.isPlaylistUrl
import com.example.dovimu.data.looksLikeUrl
import com.example.dovimu.data.resolveAddress
import com.example.dovimu.platform.FileMeta
import com.example.dovimu.platform.PlatformServices
import com.example.dovimu.platform.WebNavAction
import com.example.dovimu.platform.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val tab: MainTab = MainTab.Home,
    val url: String = "",
    val media: InspectResponse? = null,
    val inspecting: Boolean = false,
    val error: String? = null,
    val qualityKind: QualityKind = QualityKind.Video,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
    val jobs: List<DownloadItem> = emptyList(),
    val baseUrl: String = com.example.dovimu.platform.DEFAULT_API_URL,
    val apiOnline: Boolean? = null,
    val ffmpeg: Boolean = false,
    val apiVersion: String? = null,
    val settingsOpen: Boolean = false,
    val pinging: Boolean = false,
    val browserHome: Boolean = true,
    val browserInput: String = "",
    val browserCurrentUrl: String = "",
    val browserTitle: String = "",
    val browserProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loadUrl: String = "",
    val loadId: Long = 0,
    val webNav: WebNavAction? = null,
    val qualitySheetOpen: Boolean = false,
    val choosingKind: Boolean = true,
    val extractId: Long = 0,
    val braveInstalled: Boolean = false,
)

class DoVimuViewModel(
    private val platform: PlatformServices,
) : ViewModel() {
    private val client = createHttpClient(platform.httpEngine)
    private val api = DoVimuApi(client) { _state.value.baseUrl }
    private val savingIds = mutableSetOf<String>()
    private var inspectJob: Job? = null
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            baseUrl = platform.settings.getBaseUrl(),
            braveInstalled = platform.hasBrave(),
        ),
    )
    val state: StateFlow<UiState> = _state

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        _state.update { it.copy(error = error.toUserMessage(), pinging = false, inspecting = false, searching = false) }
    }

    private fun safeLaunch(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(errorHandler, block = block)

    init {
        ping()
        safeLaunch {
            while (isActive) {
                try {
                    pollActiveJobs()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
                delay(900)
            }
        }
    }

    fun selectTab(tab: MainTab) = _state.update { it.copy(tab = tab, error = null) }

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }

    fun pasteUrl(raw: String) {
        val url = extractUrl(raw)
        _state.update { it.copy(url = url, tab = MainTab.Home, error = null) }
        navigateTo(url)
    }

    fun onBrowserInput(value: String) = _state.update { it.copy(browserInput = value) }

    fun submitAddress() {
        val typed = _state.value.browserInput.ifBlank { _state.value.browserCurrentUrl }
        navigateTo(typed)
    }

    fun navigateTo(raw: String) {
        val url = resolveAddress(raw)
        _state.update {
            it.copy(
                tab = MainTab.Home,
                browserHome = false,
                browserInput = url,
                browserCurrentUrl = url,
                loadUrl = url,
                loadId = it.loadId + 1,
                error = null,
                url = url,
            )
        }
    }

    fun goBrowserHome() = _state.update {
        it.copy(browserHome = true, browserTitle = "", browserProgress = 0)
    }

    fun browserBack() {
        val state = _state.value
        if (!state.browserHome && state.canGoBack) {
            _state.update { it.copy(webNav = WebNavAction.Back) }
            return
        }
        if (!state.browserHome) goBrowserHome()
    }

    fun browserForward() = _state.update { it.copy(webNav = WebNavAction.Forward) }

    fun reloadPage() = _state.update { it.copy(webNav = WebNavAction.Reload) }

    fun onWebNavHandled() = _state.update { it.copy(webNav = null) }

    fun onBrowserUrlChange(url: String, updateInput: Boolean = true) {
        _state.update {
            it.copy(
                browserCurrentUrl = url,
                browserInput = if (updateInput) url else it.browserInput,
                url = url,
            )
        }
    }

    fun onBrowserTitle(title: String) = _state.update { it.copy(browserTitle = title) }

    fun onBrowserProgress(progress: Int) = _state.update { it.copy(browserProgress = progress) }

    fun onCanGoBack(value: Boolean) = _state.update { it.copy(canGoBack = value) }

    fun onCanGoForward(value: Boolean) = _state.update { it.copy(canGoForward = value) }

    fun downloadCurrentPage() {
        _state.update {
            it.copy(
                extractId = it.extractId + 1,
                qualitySheetOpen = true,
                inspecting = true,
                media = null,
                choosingKind = true,
                error = null,
            )
        }
    }

    fun onExtractedUrl(url: String) {
        val target = url.ifBlank { _state.value.browserCurrentUrl }
        if (target.isBlank()) {
            _state.update {
                it.copy(
                    inspecting = false,
                    error = "No se detectó un video en reproducción. Abre un video y pulsa Descargar.",
                )
            }
            return
        }
        inspect(target)
    }

    fun closeQualitySheet() = _state.update {
        it.copy(qualitySheetOpen = false, choosingKind = true)
    }

    fun backDownloadStep() {
        val state = _state.value
        if (!state.choosingKind) {
            _state.update { it.copy(choosingKind = true) }
        } else {
            closeQualitySheet()
        }
    }

    fun openInBrave() {
        val url = _state.value.browserCurrentUrl.ifBlank { resolveAddress("youtube") }
        val opened = runCatching { platform.openInBrave(url) }.getOrElse { error ->
            _state.update { it.copy(error = error.toUserMessage()) }
            return
        }
        if (!opened) {
            _state.update { it.copy(error = "Brave no está instalado. Se abrió la Play Store.") }
        }
    }

    fun onSearchQueryChange(value: String) = _state.update { it.copy(searchQuery = value) }

    fun openSettings(open: Boolean) = _state.update { it.copy(settingsOpen = open) }

    fun onBaseUrlChange(value: String) = _state.update { it.copy(baseUrl = value.trim()) }

    fun saveBaseUrl() {
        val url = _state.value.baseUrl.trim().trimEnd('/')
        val normalized = if (url.startsWith("http")) url else "http://$url"
        platform.settings.setBaseUrl(normalized)
        _state.update { it.copy(baseUrl = normalized) }
        ping()
    }

    fun useEmulatorUrl() {
        _state.update { it.copy(baseUrl = com.example.dovimu.platform.DEFAULT_API_URL) }
        saveBaseUrl()
    }

    fun ping() {
        safeLaunch {
            _state.update { it.copy(pinging = true) }
            try {
                val health = api.health()
                _state.update {
                    it.copy(
                        pinging = false,
                        apiOnline = health.status == "ok",
                        ffmpeg = health.ffmpeg,
                        apiVersion = health.version,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update {
                    it.copy(pinging = false, apiOnline = false, ffmpeg = false)
                }
            }
        }
    }

    fun inspect(fromUrl: String? = null) {
        val raw = fromUrl ?: _state.value.url
        val url = extractUrl(raw)
        if (!looksLikeUrl(url)) {
            _state.update { it.copy(error = "Pega un enlace http o https válido.") }
            return
        }
        inspectJob?.cancel()
        inspectJob = safeLaunch {
            _state.update {
                it.copy(
                    url = url,
                    inspecting = true,
                    error = null,
                    tab = MainTab.Home,
                    media = null,
                    qualitySheetOpen = true,
                    choosingKind = true,
                )
            }
            try {
                val result = api.inspect(url, playlist = isPlaylistUrl(url))
                _state.update {
                    it.copy(
                        inspecting = false,
                        media = result,
                        choosingKind = true,
                        ffmpeg = result.ffmpegAvailable || it.ffmpeg,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update { it.copy(inspecting = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        inspecting = false,
                        error = e.toUserMessage().ifBlank { "No se pudo conectar con la API. Revisa la URL en ajustes." },
                    )
                }
            }
        }
    }

    fun inspectEntry(url: String?) {
        if (url.isNullOrBlank()) return
        inspect(url)
    }

    fun selectQualityKind(kind: QualityKind) = _state.update {
        it.copy(qualityKind = kind, choosingKind = false)
    }

    fun download(quality: QualityOption) {
        val media = _state.value.media ?: return
        val url = media.webpageUrl.ifBlank { extractUrl(_state.value.url) }
        safeLaunch {
            try {
                val created = api.startDownload(url, quality.id)
                val item = DownloadItem(
                    jobId = created.jobId,
                    title = media.metadata?.title ?: media.title,
                    qualityLabel = "${quality.label} · ${quality.ext.uppercase()}",
                    kind = quality.kind,
                    thumbnail = media.metadata?.thumbnail ?: media.thumbnail,
                    status = created.status,
                    progress = 0f,
                    downloadUrl = created.fileUrl,
                    artist = media.metadata?.artist,
                    album = media.metadata?.album,
                    metadata = media.metadata,
                )
                _state.update {
                    it.copy(
                        jobs = listOf(item) + it.jobs,
                        tab = MainTab.Downloads,
                        qualitySheetOpen = false,
                        choosingKind = true,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage().ifBlank { "No se pudo iniciar la descarga." }) }
            }
        }
    }

    fun search() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = safeLaunch {
            _state.update { it.copy(searching = true, error = null) }
            try {
                val result = api.search(query)
                _state.update {
                    it.copy(searching = false, searchResults = result.results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update { it.copy(searching = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        searching = false,
                        error = e.toUserMessage().ifBlank { "No se pudo buscar. ¿Está encendida la API?" },
                    )
                }
            }
        }
    }

    fun openSearchResult(result: SearchResult) {
        val url = result.url ?: return
        _state.update { it.copy(searchQuery = result.title, tab = MainTab.Home) }
        navigateTo(url)
        inspect(url)
    }

    fun openLocalFile(item: DownloadItem) {
        val uri = item.localUri ?: return
        val failure = runCatching { platform.openFile(uri, item.mime) }
            .getOrElse { it.toUserMessage() }
        if (!failure.isNullOrBlank()) {
            _state.update { it.copy(error = failure) }
        }
    }

    fun retrySave(item: DownloadItem) {
        safeLaunch { saveToDevice(item) }
    }

    fun cancelJob(item: DownloadItem) {
        safeLaunch {
            runCatching { api.cancel(item.jobId) }
            _state.update { state ->
                state.copy(jobs = state.jobs.filterNot { it.jobId == item.jobId })
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private suspend fun pollActiveJobs() {
        val active = _state.value.jobs.filter { it.status in ACTIVE_STATUSES }
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
                replaceJob(merged)
                if (remote.status == "ready" && merged.localUri == null) {
                    saveToDevice(merged)
                }
            } catch (_: Exception) {
                // keep last known status; next tick retries
            }
        }
    }

    private suspend fun saveToDevice(item: DownloadItem) {
        if (!savingIds.add(item.jobId)) return
        replaceJob(item.copy(status = "saving", progress = 0f, error = null))
        try {
            val relative = item.downloadUrl ?: "/api/v1/jobs/${item.jobId}/file"
            val url = _state.value.baseUrl.trimEnd('/') + relative
            val filename = displayFilename(item)
            val uri = platform.fileSaver.save(
                client,
                url,
                filename,
                item.mime,
                { progress ->
                    val current = _state.value.jobs.firstOrNull { it.jobId == item.jobId }
                    if (current != null) {
                        replaceJob(current.copy(status = "saving", progress = progress))
                    }
                },
                FileMeta(
                    title = humanizeName(item.metadata?.title ?: item.title).ifBlank { item.title },
                    artist = humanizeName(item.metadata?.artist ?: item.artist.orEmpty()).ifBlank { null },
                    album = humanizeName(item.metadata?.album ?: item.album.orEmpty()).ifBlank { null },
                    albumArtist = humanizeName(item.metadata?.albumArtist.orEmpty()).ifBlank { null },
                    year = item.metadata?.year,
                    track = item.metadata?.track,
                    durationMs = item.metadata?.durationSeconds?.let { it * 1000L }
                        ?: _state.value.media?.duration?.let { it * 1000L },
                    width = item.metadata?.width,
                    height = item.metadata?.height,
                    lyrics = item.metadata?.lyrics?.trim()?.ifBlank { null },
                ),
            )
            replaceJob(
                item.copy(status = "saved", progress = 100f, localUri = uri, filename = filename, error = null),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            replaceJob(
                item.copy(
                    status = "save_failed",
                    error = e.toUserMessage().ifBlank { "No se pudo guardar el archivo en el teléfono." },
                ),
            )
        } finally {
            savingIds.remove(item.jobId)
        }
    }

    private fun replaceJob(item: DownloadItem) {
        _state.update { state ->
            state.copy(jobs = state.jobs.map { if (it.jobId == item.jobId) item else it })
        }
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

    override fun onCleared() {
        client.close()
        super.onCleared()
    }

    companion object {
        private val ACTIVE_STATUSES = setOf(
            "queued",
            "downloading",
            "processing",
            "ready",
            "saving",
        )
    }
}
