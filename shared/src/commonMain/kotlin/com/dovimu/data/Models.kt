package com.dovimu.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InspectRequest(
    val url: String,
    val playlist: Boolean = false,
)

@Serializable
data class QualityOption(
    val id: String,
    val kind: String,
    val label: String,
    val ext: String,
    val height: Int? = null,
    val width: Int? = null,
    val fps: Double? = null,
    val abr: Int? = null,
    @SerialName("filesize_bytes") val filesizeBytes: Long? = null,
    @SerialName("filesize_label") val filesizeLabel: String? = null,
    val note: String? = null,
    @SerialName("needs_ffmpeg") val needsFfmpeg: Boolean = false,
    val recommended: Boolean = false,
)

@Serializable
data class PlaylistEntry(
    @SerialName("media_id") val mediaId: String,
    val title: String,
    val url: String? = null,
    val duration: Int? = null,
    @SerialName("duration_label") val durationLabel: String? = null,
    val thumbnail: String? = null,
    val uploader: String? = null,
)

@Serializable
data class InspectResponse(
    val source: String,
    val extractor: String,
    @SerialName("media_id") val mediaId: String,
    val title: String,
    val uploader: String? = null,
    val duration: Int? = null,
    @SerialName("duration_label") val durationLabel: String? = null,
    val thumbnail: String? = null,
    @SerialName("webpage_url") val webpageUrl: String,
    val description: String? = null,
    @SerialName("is_playlist") val isPlaylist: Boolean = false,
    val entries: List<PlaylistEntry> = emptyList(),
    val video: List<QualityOption> = emptyList(),
    val audio: List<QualityOption> = emptyList(),
    @SerialName("ffmpeg_available") val ffmpegAvailable: Boolean = false,
    val metadata: TrackMetadata? = null,
)

@Serializable
data class DownloadRequest(
    val url: String,
    @SerialName("quality_id") val qualityId: String,
)

@Serializable
data class JobCreatedResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,
    @SerialName("poll_url") val pollUrl: String,
    @SerialName("events_url") val eventsUrl: String,
    @SerialName("file_url") val fileUrl: String,
)

@Serializable
data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val track: Int? = null,
    val genre: String? = null,
    val year: String? = null,
    val lyrics: String? = null,
    val comment: String? = null,
    val thumbnail: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val embedded: Boolean = false,
)

@Serializable
data class JobResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,
    val progress: Double = 0.0,
    @SerialName("speed_bytes") val speedBytes: Double? = null,
    @SerialName("eta_seconds") val etaSeconds: Int? = null,
    val title: String? = null,
    val filename: String? = null,
    val mime: String? = null,
    @SerialName("filesize_bytes") val filesizeBytes: Long? = null,
    @SerialName("quality_id") val qualityId: String,
    @SerialName("download_url") val downloadUrl: String? = null,
    val error: String? = null,
    val metadata: TrackMetadata? = null,
)

@Serializable
data class SearchResult(
    @SerialName("media_id") val mediaId: String,
    val title: String,
    val url: String? = null,
    val duration: Int? = null,
    @SerialName("duration_label") val durationLabel: String? = null,
    val thumbnail: String? = null,
    val uploader: String? = null,
    val source: String = "youtube",
)

@Serializable
data class SearchResponse(
    val query: String,
    val results: List<SearchResult> = emptyList(),
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val ffmpeg: Boolean = false,
    @SerialName("max_concurrent_downloads") val maxConcurrentDownloads: Int = 1,
)

@Serializable
data class ApiErrorResponse(
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
)

class ApiException(
    message: String,
    val code: String? = null,
    val httpStatus: Int = 0,
) : Exception(message)

data class DownloadItem(
    val jobId: String,
    val title: String,
    val qualityLabel: String,
    val kind: String,
    val thumbnail: String?,
    val status: String,
    val progress: Float,
    val speedBytes: Double? = null,
    val etaSeconds: Int? = null,
    val error: String? = null,
    val filename: String? = null,
    val mime: String? = null,
    val localUri: String? = null,
    val downloadUrl: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val metadata: TrackMetadata? = null,
)

enum class MainTab { Home, Search, Downloads }

enum class QualityKind { Video, Audio }
