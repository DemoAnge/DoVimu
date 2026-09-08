package com.example.dovimu.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

const val DEFAULT_API_URL = "http://10.0.2.2:8000"

interface SettingsStore {
    fun getBaseUrl(): String
    fun setBaseUrl(url: String)
}

data class FileMeta(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: String? = null,
    val track: Int? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val lyrics: String? = null,
)

interface FileSaver {
    suspend fun save(
        client: HttpClient,
        url: String,
        filename: String,
        mime: String?,
        onProgress: (Float) -> Unit,
        meta: FileMeta? = null,
    ): String
}

interface PlatformServices {
    val settings: SettingsStore
    val fileSaver: FileSaver
    val httpEngine: HttpClientEngine
    /** Opens [uri]. Returns an error message if it failed, or null on success. */
    fun openFile(uri: String, mime: String?): String?
    fun hasBrave(): Boolean
    fun openInBrave(url: String): Boolean
}

expect fun createPlatformServices(): PlatformServices
