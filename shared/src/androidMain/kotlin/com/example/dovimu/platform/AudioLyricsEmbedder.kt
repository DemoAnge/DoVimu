package com.example.dovimu.platform

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

internal object AudioLyricsEmbedder {
    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun embed(file: File, mime: String, filename: String, meta: FileMeta?) {
        val lyrics = meta?.lyrics?.trim().orEmpty()
        if (lyrics.isEmpty() || !file.isFile || file.length() == 0L) return
        try {
            val ext = filename.substringAfterLast('.', "").lowercase()
            if (isMp3(mime, ext)) {
                val original = file.readBytes()
                val tagged = Id3v2LyricsEmbedder.embed(original, lyrics, meta?.title, meta?.artist, meta?.album)
                if (!tagged.contentEquals(original)) file.writeBytes(tagged)
                return
            }
            if (!isAudio(mime, ext)) return
            embedWithTagger(file, lyrics, meta)
        } catch (error: Throwable) {
            CrashReporter.recordNonFatal(error)
        }
    }

    private fun embedWithTagger(file: File, lyrics: String, meta: FileMeta?) {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        tag.setField(FieldKey.LYRICS, lyrics.take(200_000))
        meta?.title?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { tag.setField(FieldKey.TITLE, it) } }
        meta?.artist?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { tag.setField(FieldKey.ARTIST, it) } }
        meta?.album?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { tag.setField(FieldKey.ALBUM, it) } }
        audio.commit()
    }

    private fun isMp3(mime: String, ext: String): Boolean =
        ext == "mp3" || mime == "audio/mpeg" || mime == "audio/mp3"

    private fun isAudio(mime: String, ext: String): Boolean =
        mime.startsWith("audio/") || ext in setOf("m4a", "aac", "flac", "ogg", "opus", "wav", "wma")
}
