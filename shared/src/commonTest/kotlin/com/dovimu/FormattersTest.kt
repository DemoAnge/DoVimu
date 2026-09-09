package com.dovimu

import com.dovimu.data.extractUrl
import com.dovimu.data.isDownloadablePage
import com.dovimu.data.isPlaylistUrl
import com.dovimu.data.looksLikeUrl
import com.dovimu.data.resolveAddress
import com.dovimu.data.statusLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormattersTest {
    @Test
    fun extractsUrlFromShareText() {
        val raw = "Mira esto https://youtu.be/dQw4w9WgXcQ?si=abc extra"
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc", extractUrl(raw))
        assertTrue(looksLikeUrl(raw))
        assertFalse(looksLikeUrl("hola"))
    }

    @Test
    fun detectsPlaylist() {
        assertTrue(isPlaylistUrl("https://www.youtube.com/playlist?list=PLtest"))
        assertTrue(isPlaylistUrl("https://www.youtube.com/watch?v=x&list=PLtest"))
        assertFalse(isPlaylistUrl("https://www.youtube.com/watch?v=x"))
    }

    @Test
    fun statusInSpanish() {
        assertEquals("Guardado", statusLabel("saved"))
        assertEquals("Descargando", statusLabel("downloading"))
    }

    @Test
    fun youtubeAliasOpensOfficialSite() {
        assertEquals("https://m.youtube.com", resolveAddress("youtube"))
        assertEquals("https://m.youtube.com", resolveAddress("YouTube"))
        assertEquals("https://m.youtube.com", resolveAddress("yt"))
        assertEquals("https://www.tiktok.com", resolveAddress("tiktok"))
    }

    @Test
    fun searchGoesToOfficialYoutube() {
        val url = resolveAddress("bad bunny dtmf")
        assertTrue(url.startsWith("https://m.youtube.com/results?search_query="))
        assertTrue("bad" in url)
    }

    @Test
    fun downloadableWatchUrl() {
        assertTrue(isDownloadablePage("https://m.youtube.com/watch?v=abc"))
        assertFalse(isDownloadablePage("https://m.youtube.com/"))
    }
}
