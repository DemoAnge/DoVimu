package com.example.dovimu.platform

/**
 * Embeds unsynchronized lyrics (USLT) into an MP3 ID3v2 tag.
 * Preserves existing frames; replaces any previous USLT/ULT.
 */
object Id3v2LyricsEmbedder {
    private val lyricsIds = setOf("USLT", "ULT")
    private const val maxLyricsChars = 200_000

    fun embed(
        mp3: ByteArray,
        lyrics: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ): ByteArray {
        val text = lyrics.trim().take(maxLyricsChars)
        if (text.isEmpty() || mp3.isEmpty()) return mp3
        val parsed = parse(mp3)
        val audio = if (parsed != null) mp3.copyOfRange(parsed.audioStart, mp3.size) else mp3
        val version = when (parsed?.version) {
            2, 3 -> 3
            else -> 4
        }
        val frames = parsed?.frames.orEmpty()
            .map { remapFrame(it, version) }
            .filter { it.id.length == 4 }
            .filterNot { it.id in lyricsIds }
            .toMutableList()
        frames.add(0, usltFrame(text, version))
        if (frames.none { it.id == "TIT2" }) {
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { frames.add(textFrame("TIT2", it, version)) }
        }
        if (frames.none { it.id == "TPE1" }) {
            artist?.trim()?.takeIf { it.isNotEmpty() }?.let { frames.add(textFrame("TPE1", it, version)) }
        }
        if (frames.none { it.id == "TALB" }) {
            album?.trim()?.takeIf { it.isNotEmpty() }?.let { frames.add(textFrame("TALB", it, version)) }
        }
        return writeTag(version, frames) + audio
    }

    fun extractLyrics(mp3: ByteArray): String? {
        val parsed = parse(mp3) ?: return null
        val frame = parsed.frames.firstOrNull { it.id in lyricsIds } ?: return null
        return decodeUslt(frame.data)
    }

    private fun remapFrame(frame: Id3Frame, version: Int): Id3Frame {
        if (version != 3 && version != 4) return frame
        val mapped = when (frame.id) {
            "TT2" -> "TIT2"
            "TP1" -> "TPE1"
            "TAL" -> "TALB"
            "ULT" -> "USLT"
            else -> frame.id
        }
        return if (mapped == frame.id) frame else Id3Frame(mapped, frame.data)
    }

    private fun parse(mp3: ByteArray): ParsedTag? {
        if (mp3.size < 10) return null
        if (mp3[0] != 'I'.code.toByte() || mp3[1] != 'D'.code.toByte() || mp3[2] != '3'.code.toByte()) return null
        val version = mp3[3].toInt() and 0xFF
        if (version !in 2..4) return null
        val flags = mp3[5].toInt() and 0xFF
        val tagSize = readSynchsafe(mp3, 6)
        if (tagSize <= 0 || 10 + tagSize > mp3.size) return null
        var body = mp3.copyOfRange(10, 10 + tagSize)
        val unsync = flags and 0x80 != 0
        if (unsync) body = deunsync(body)
        var offset = 0
        if (flags and 0x40 != 0) {
            if (body.size < 4) return null
            val extSize = if (version == 4) readSynchsafe(body, 0) else readBe(body, 0)
            offset = if (version == 4) extSize else 4 + extSize
            if (offset < 4 || offset > body.size) return null
        }
        val frames = mutableListOf<Id3Frame>()
        while (offset + 6 <= body.size) {
            if (body[offset] == 0.toByte()) break
            val frame = when (version) {
                2 -> readFrameV2(body, offset) ?: break
                3 -> readFrameV3(body, offset) ?: break
                else -> readFrameV4(body, offset) ?: break
            }
            frames.add(frame)
            offset += frame.headerSize
        }
        val footer = if (version == 4 && flags and 0x10 != 0) 10 else 0
        return ParsedTag(version, 10 + tagSize + footer, frames)
    }

    private fun readFrameV2(body: ByteArray, offset: Int): Id3Frame? {
        if (offset + 6 > body.size) return null
        val id = body.decodeLatin1(offset, offset + 3)
        if (id.any { it !in 'A'..'Z' && it !in '0'..'9' }) return null
        val size = readBe3(body, offset + 3)
        if (size < 0 || offset + 6 + size > body.size) return null
        return Id3Frame(id, body.copyOfRange(offset + 6, offset + 6 + size), headerSize = 6 + size)
    }

    private fun readFrameV3(body: ByteArray, offset: Int): Id3Frame? {
        if (offset + 10 > body.size) return null
        val id = body.decodeLatin1(offset, offset + 4)
        if (id.any { it !in 'A'..'Z' && it !in '0'..'9' }) return null
        val size = readBe(body, offset + 4)
        if (size < 0 || offset + 10 + size > body.size) return null
        return Id3Frame(id, body.copyOfRange(offset + 10, offset + 10 + size), headerSize = 10 + size)
    }

    private fun readFrameV4(body: ByteArray, offset: Int): Id3Frame? {
        if (offset + 10 > body.size) return null
        val id = body.decodeLatin1(offset, offset + 4)
        if (id.any { it !in 'A'..'Z' && it !in '0'..'9' }) return null
        val size = readSynchsafe(body, offset + 4)
        if (size < 0 || offset + 10 + size > body.size) return null
        return Id3Frame(id, body.copyOfRange(offset + 10, offset + 10 + size), headerSize = 10 + size)
    }

    private fun usltFrame(lyrics: String, version: Int): Id3Frame {
        val data = if (version >= 4) {
            byteArrayOf(3) + "und".encodeToByteArray() + byteArrayOf(0) + lyrics.encodeToByteArray()
        } else {
            byteArrayOf(1) + "und".encodeToByteArray() + utf16Le("") + byteArrayOf(0, 0) + utf16Le(lyrics)
        }
        return Id3Frame("USLT", data)
    }

    private fun textFrame(id: String, value: String, version: Int): Id3Frame {
        val data = if (version >= 4) {
            byteArrayOf(3) + value.encodeToByteArray()
        } else {
            byteArrayOf(1) + utf16Le(value)
        }
        return Id3Frame(id, data)
    }

    private fun writeTag(version: Int, frames: List<Id3Frame>): ByteArray {
        val body = join(frames.map { encodeFrame(version, it) })
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = version.toByte()
        header[4] = 0
        header[5] = 0
        writeSynchsafe(header, 6, body.size)
        return header + body
    }

    private fun encodeFrame(version: Int, frame: Id3Frame): ByteArray {
        val id = frame.id.encodeToByteArray()
        val size = ByteArray(4)
        if (version >= 4) writeSynchsafe(size, 0, frame.data.size) else writeBe(size, 0, frame.data.size)
        return id + size + byteArrayOf(0, 0) + frame.data
    }

    private fun decodeUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt() and 0xFF
        val rest = data.copyOfRange(4, data.size)
        val text = when (encoding) {
            0 -> splitLatin1(rest)
            1, 2 -> splitUtf16(rest, littleEndian = encoding == 1)
            3 -> {
                val zero = rest.indexOf(0)
                if (zero < 0) rest.decodeToString() else rest.copyOfRange(zero + 1, rest.size).decodeToString()
            }
            else -> rest.decodeToString()
        }
        return text.trim().takeIf { it.isNotEmpty() }
    }

    private fun splitLatin1(data: ByteArray): String {
        val zero = data.indexOf(0)
        return if (zero < 0) data.decodeLatin1(0, data.size) else data.decodeLatin1(zero + 1, data.size)
    }

    private fun splitUtf16(data: ByteArray, littleEndian: Boolean): String {
        var i = 0
        if (data.size >= 2 && (
                (data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) ||
                    (data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte())
                )
        ) {
            i = 2
        }
        while (i + 1 < data.size && !(data[i] == 0.toByte() && data[i + 1] == 0.toByte())) i += 2
        i += 2
        if (i >= data.size) return ""
        return decodeUtf16(data, i, littleEndian)
    }

    private fun decodeUtf16(data: ByteArray, start: Int, littleEndianHint: Boolean): String {
        var little = littleEndianHint
        var offset = start
        if (offset + 1 < data.size) {
            if (data[offset] == 0xFF.toByte() && data[offset + 1] == 0xFE.toByte()) {
                little = true
                offset += 2
            } else if (data[offset] == 0xFE.toByte() && data[offset + 1] == 0xFF.toByte()) {
                little = false
                offset += 2
            }
        }
        return buildString {
            var i = offset
            while (i + 1 < data.size) {
                val code = if (little) {
                    (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)
                } else {
                    ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                }
                if (code == 0) break
                append(code.toChar())
                i += 2
            }
        }
    }

    private fun utf16Le(text: String): ByteArray {
        val out = ByteArray(2 + text.length * 2)
        out[0] = 0xFF.toByte()
        out[1] = 0xFE.toByte()
        for (i in text.indices) {
            val c = text[i].code
            out[2 + i * 2] = (c and 0xFF).toByte()
            out[2 + i * 2 + 1] = (c shr 8).toByte()
        }
        return out
    }

    private fun deunsync(data: ByteArray): ByteArray {
        val out = ArrayList<Byte>(data.size)
        var i = 0
        while (i < data.size) {
            out.add(data[i])
            if (data[i] == 0xFF.toByte() && i + 1 < data.size && data[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    private fun join(parts: List<ByteArray>): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var o = 0
        for (part in parts) {
            part.copyInto(out, o)
            o += part.size
        }
        return out
    }

    private fun readSynchsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or
            ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or
            (b[o + 3].toInt() and 0x7F)

    private fun readBe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)

    private fun readBe3(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 16) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            (b[o + 2].toInt() and 0xFF)

    private fun writeSynchsafe(dest: ByteArray, o: Int, value: Int) {
        dest[o] = ((value shr 21) and 0x7F).toByte()
        dest[o + 1] = ((value shr 14) and 0x7F).toByte()
        dest[o + 2] = ((value shr 7) and 0x7F).toByte()
        dest[o + 3] = (value and 0x7F).toByte()
    }

    private fun writeBe(dest: ByteArray, o: Int, value: Int) {
        dest[o] = ((value ushr 24) and 0xFF).toByte()
        dest[o + 1] = ((value ushr 16) and 0xFF).toByte()
        dest[o + 2] = ((value ushr 8) and 0xFF).toByte()
        dest[o + 3] = (value and 0xFF).toByte()
    }

    private fun ByteArray.decodeLatin1(from: Int, to: Int): String = buildString(to - from) {
        for (i in from until to) append((this@decodeLatin1[i].toInt() and 0xFF).toChar())
    }

    private data class Id3Frame(val id: String, val data: ByteArray, val headerSize: Int = 10 + data.size)

    private data class ParsedTag(val version: Int, val audioStart: Int, val frames: List<Id3Frame>)
}
