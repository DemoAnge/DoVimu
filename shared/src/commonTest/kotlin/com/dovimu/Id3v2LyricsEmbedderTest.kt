package com.dovimu

import com.dovimu.platform.Id3v2LyricsEmbedder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Id3v2LyricsEmbedderTest {
    @Test
    fun embedsLyricsInTaglessMp3() {
        val audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00, 1, 2, 3, 4)
        val tagged = Id3v2LyricsEmbedder.embed(audio, "Hola mundo\nsegunda línea", "Tema", "Artista", "Disco")
        assertEquals("Hola mundo\nsegunda línea", Id3v2LyricsEmbedder.extractLyrics(tagged))
        assertTrue(tagged.size > audio.size)
        assertTrue(tagged.copyOfRange(tagged.size - audio.size, tagged.size).contentEquals(audio))
    }

    @Test
    fun keepsSpanishCharacters() {
        val audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x00)
        val lyrics = "¿Cómo estás, niño? Ñandú — corazón"
        val tagged = Id3v2LyricsEmbedder.embed(audio, lyrics)
        assertEquals(lyrics, Id3v2LyricsEmbedder.extractLyrics(tagged))
    }

    @Test
    fun replacesPreviousLyrics() {
        val first = Id3v2LyricsEmbedder.embed(byteArrayOf(9, 8, 7), "letra vieja")
        val second = Id3v2LyricsEmbedder.embed(first, "letra nueva")
        assertEquals("letra nueva", Id3v2LyricsEmbedder.extractLyrics(second))
    }

    @Test
    fun emptyLyricsLeavesFileAlone() {
        val audio = byteArrayOf(1, 2, 3)
        assertTrue(Id3v2LyricsEmbedder.embed(audio, "  ").contentEquals(audio))
        assertNull(Id3v2LyricsEmbedder.extractLyrics(audio))
    }
}
