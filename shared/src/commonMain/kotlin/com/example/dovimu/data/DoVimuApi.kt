package com.example.dovimu.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val appJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(appJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        connectTimeoutMillis = 12_000
        socketTimeoutMillis = 90_000
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}

class DoVimuApi(
    private val client: HttpClient,
    private val baseUrl: () -> String,
) {
    private fun path(suffix: String): String = baseUrl().trimEnd('/') + suffix

    suspend fun health(): HealthResponse = get("/health")

    suspend fun inspect(url: String, playlist: Boolean = false): InspectResponse =
        post("/api/v1/media/inspect", InspectRequest(url = url, playlist = playlist))

    suspend fun startDownload(url: String, qualityId: String): JobCreatedResponse =
        post("/api/v1/media/download", DownloadRequest(url = url, qualityId = qualityId))

    suspend fun job(jobId: String): JobResponse = get("/api/v1/jobs/$jobId")

    suspend fun cancel(jobId: String) {
        val response = client.delete(path("/api/v1/jobs/$jobId"))
        if (response.status.value != 204 && !response.status.isSuccess()) {
            throw response.toApiException()
        }
    }

    suspend fun search(query: String, limit: Int = 10): SearchResponse =
        get("/api/v1/search?q=${query.encodeUrl()}&limit=$limit")

    private suspend inline fun <reified T> get(suffix: String): T {
        val response = client.get(path(suffix))
        return response.bodyOrThrow()
    }

    private suspend inline fun <reified T> post(suffix: String, body: Any): T {
        val response = client.post(path(suffix)) { setBody(body) }
        return response.bodyOrThrow()
    }
}

private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    if (status.isSuccess()) return body()
    throw toApiException()
}

private suspend fun HttpResponse.toApiException(): ApiException {
    val parsed = runCatching { body<ApiErrorResponse>() }.getOrNull()
    val message = parsed?.error?.message ?: "Error del servidor (${status.value})"
    return ApiException(message, parsed?.error?.code, status.value)
}

private fun String.encodeUrl(): String = buildString {
    for (ch in this@encodeUrl) {
        when {
            ch.isLetterOrDigit() || ch in "-._~" -> append(ch)
            ch == ' ' -> append("%20")
            else -> append("%${ch.code.toString(16).uppercase().padStart(2, '0')}")
        }
    }
}
