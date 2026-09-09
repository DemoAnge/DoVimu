package com.dovimu.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dovimu.data.ApiException
import com.dovimu.data.DoVimuApi
import com.dovimu.data.DownloadItem
import com.dovimu.data.InspectResponse
import com.dovimu.data.MainTab
import com.dovimu.data.QualityKind
import com.dovimu.data.QualityOption
import com.dovimu.data.SearchResult
import com.dovimu.data.createHttpClient
import com.dovimu.data.extractUrl
import com.dovimu.data.isPlaylistUrl
import com.dovimu.data.looksLikeUrl
import com.dovimu.data.resolveAddress
import com.dovimu.download.DownloadCoordinator
import com.dovimu.platform.PlatformServices
import com.dovimu.platform.WebNavAction
import com.dovimu.platform.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    val baseUrl: String = com.dovimu.platform.DEFAULT_API_URL,
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
    private val downloads = DownloadCoordinator.get(platform)
    private var inspectJob: Job? = null
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            baseUrl = platform.settings.getBaseUrl(),
            braveInstalled = platform.hasBrave(),
            jobs = downloads.jobs.value,
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
        viewModelScope.launch {
            downloads.jobs.collect { jobs ->
                _state.update { it.copy(jobs = jobs) }
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
        _state.update { it.copy(baseUrl = com.dovimu.platform.DEFAULT_API_URL) }
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
        val media = _state.value.media
        if (media == null) {
            _state.update { it.copy(error = "Todavía no hay un video para descargar.") }
            return
        }
        val url = media.webpageUrl
            .ifBlank { extractUrl(_state.value.url) }
            .ifBlank { extractUrl(_state.value.browserCurrentUrl) }
        if (!looksLikeUrl(url)) {
            _state.update { it.copy(error = "No hay un enlace válido para descargar.") }
            return
        }
        safeLaunch {
            try {
                downloads.start(url, quality, media)
                _state.update {
                    it.copy(
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
        downloads.retrySave(item)
    }

    fun cancelJob(item: DownloadItem) {
        downloads.cancel(item)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }
}
