package com.dovimu.platform

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

class AndroidFileSaver(private val context: Context) : FileSaver {
    override suspend fun save(
        client: HttpClient,
        url: String,
        filename: String,
        mime: String?,
        onProgress: (Float) -> Unit,
        meta: FileMeta?,
    ): String = withContext(Dispatchers.IO) {
        val safeName = sanitize(filename)
        val resolvedMime = mime ?: mimeFromName(safeName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                resolvedMime.startsWith("audio/") ->
                    saveToCollection(
                        client, url, safeName, resolvedMime, meta, onProgress,
                        collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        relativePath = Environment.DIRECTORY_MUSIC + "/DoVimu",
                        audio = true,
                    )
                resolvedMime.startsWith("video/") ->
                    saveToCollection(
                        client, url, safeName, resolvedMime, meta, onProgress,
                        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        relativePath = Environment.DIRECTORY_MOVIES + "/DoVimu",
                        audio = false,
                    )
                else -> saveToDownloads(client, url, safeName, resolvedMime, onProgress, meta)
            }
        } else {
            saveToAppFiles(client, url, safeName, resolvedMime, onProgress, meta)
        }
    }

    private suspend fun saveToCollection(
        client: HttpClient,
        url: String,
        filename: String,
        mime: String,
        meta: FileMeta?,
        onProgress: (Float) -> Unit,
        collection: Uri,
        relativePath: String,
        audio: Boolean,
    ): String {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.TITLE, meta?.title ?: filename.substringBeforeLast('.'))
            meta?.artist?.let { put(if (audio) MediaStore.Audio.Media.ARTIST else MediaStore.Video.Media.ARTIST, it) }
            meta?.album?.let { put(if (audio) MediaStore.Audio.Media.ALBUM else MediaStore.Video.Media.ALBUM, it) }
            meta?.durationMs?.takeIf { it > 0 }?.let {
                put(if (audio) MediaStore.Audio.Media.DURATION else MediaStore.Video.Media.DURATION, it)
            }
            if (audio) {
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                meta?.year?.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
                meta?.track?.let { put(MediaStore.Audio.Media.TRACK, it) }
                if (Build.VERSION.SDK_INT >= 30) {
                    meta?.albumArtist?.let { put(MediaStore.Audio.Media.ALBUM_ARTIST, it) }
                }
            } else {
                meta?.width?.takeIf { it > 0 }?.let { put(MediaStore.Video.Media.WIDTH, it) }
                meta?.height?.takeIf { it > 0 }?.let { put(MediaStore.Video.Media.HEIGHT, it) }
                put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: return saveToDownloads(client, url, filename, mime, onProgress, meta)
        try {
            if (!copyTaggedDownload(client, url, filename, mime, meta, onProgress, uri)) {
                error("No se pudo escribir el archivo.")
            }
            val scanned = fillFromFile(uri, meta)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            scanned.title?.let { values.put(MediaStore.MediaColumns.TITLE, it) }
            scanned.artist?.let {
                values.put(if (audio) MediaStore.Audio.Media.ARTIST else MediaStore.Video.Media.ARTIST, it)
            }
            scanned.album?.let {
                values.put(if (audio) MediaStore.Audio.Media.ALBUM else MediaStore.Video.Media.ALBUM, it)
            }
            scanned.durationMs?.takeIf { it > 0 }?.let {
                values.put(if (audio) MediaStore.Audio.Media.DURATION else MediaStore.Video.Media.DURATION, it)
            }
            scanned.width?.takeIf { it > 0 }?.let { values.put(MediaStore.MediaColumns.WIDTH, it) }
            scanned.height?.takeIf { it > 0 }?.let { values.put(MediaStore.MediaColumns.HEIGHT, it) }
            resolver.update(uri, values, null, null)
            resolver.notifyChange(uri, null)
            MediaScannerConnection.scanFile(context, arrayOf(uri.toString()), arrayOf(mime), null)
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun fillFromFile(uri: Uri, meta: FileMeta?): ScannedMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            ScannedMeta(
                title = meta?.title ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = meta?.artist
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = meta?.album ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = meta?.durationMs?.takeIf { it > 0 }
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                width = meta?.width
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = meta?.height
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
            )
        } catch (_: Exception) {
            ScannedMeta(meta?.title, meta?.artist, meta?.album, meta?.durationMs, meta?.width, meta?.height)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun saveToDownloads(
        client: HttpClient,
        url: String,
        filename: String,
        mime: String,
        onProgress: (Float) -> Unit,
        meta: FileMeta? = null,
    ): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear el archivo en Descargas.")
        try {
            if (!copyTaggedDownload(client, url, filename, mime, meta, onProgress, uri)) {
                error("No se pudo escribir el archivo.")
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private suspend fun saveToAppFiles(
        client: HttpClient,
        url: String,
        filename: String,
        mime: String,
        onProgress: (Float) -> Unit,
        meta: FileMeta? = null,
    ): String {
        val dirName = if (mime.startsWith("video/")) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val dir = context.getExternalFilesDir(dirName) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        file.outputStream().use { out -> downloadTo(client, url, out, onProgress) }
        runCatching { AudioLyricsEmbedder.embed(file, mime, filename, meta) }
            .onFailure(CrashReporter::recordNonFatal)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        ).toString()
    }

    private suspend fun copyTaggedDownload(
        client: HttpClient,
        url: String,
        filename: String,
        mime: String,
        meta: FileMeta?,
        onProgress: (Float) -> Unit,
        uri: Uri,
    ): Boolean {
        val tmp = File(context.cacheDir, "dovimu-${System.nanoTime()}.tmp")
        try {
            tmp.outputStream().buffered().use { downloadTo(client, url, it, onProgress) }
            runCatching { AudioLyricsEmbedder.embed(tmp, mime, filename, meta) }
                .onFailure(CrashReporter::recordNonFatal)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tmp.inputStream().buffered().use { input -> input.copyTo(out) }
            } ?: return false
            return true
        } finally {
            tmp.delete()
        }
    }

    private suspend fun downloadTo(
        client: HttpClient,
        url: String,
        out: OutputStream,
        onProgress: (Float) -> Unit,
    ) {
        client.prepareGet(url) {
            timeout {
                requestTimeoutMillis = 15 * 60 * 1000
                socketTimeoutMillis = 15 * 60 * 1000
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("No se pudo descargar el archivo (${response.status.value}).")
            }
            val length = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) continue
                out.write(buffer, 0, read)
                total += read
                if (length > 0) {
                    onProgress((total.toFloat() / length.toFloat()) * 100f)
                }
            }
            out.flush()
            onProgress(100f)
        }
    }

    private fun sanitize(name: String): String {
        val ext = name.substringAfterLast('.', "")
        val hasExt = name.contains('.') && ext.isNotBlank() && ext.length <= 5
        val stem = if (hasExt) name.substringBeforeLast('.') else name
        val human = if ("_-_" in stem || (stem.count { it == '_' } >= 2 && ' ' !in stem)) {
            stem.replace('_', ' ')
        } else {
            stem
        }
        val cleaned = human.replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "dovimu" }
        return if (hasExt) "$cleaned.${ext.lowercase()}" else cleaned
    }

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "opus" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    private data class ScannedMeta(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
    )
}
