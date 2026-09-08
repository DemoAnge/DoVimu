package com.example.dovimu.data

data class QuickSite(
    val name: String,
    val url: String,
    val aliases: List<String>,
    val color: Long,
)

val QUICK_SITES = listOf(
    QuickSite("YouTube", "https://m.youtube.com", listOf("youtube", "yt", "youtu", "you tube"), 0xFFFF0000),
    QuickSite("TikTok", "https://www.tiktok.com", listOf("tiktok", "tik tok"), 0xFF25F4EE),
    QuickSite("Instagram", "https://www.instagram.com", listOf("instagram", "ig", "insta"), 0xFFE1306C),
    QuickSite("Facebook", "https://m.facebook.com", listOf("facebook", "fb"), 0xFF1877F2),
    QuickSite("X", "https://x.com", listOf("x", "twitter"), 0xFFEEEEEE),
    QuickSite("SoundCloud", "https://m.soundcloud.com", listOf("soundcloud"), 0xFFFF5500),
    QuickSite("Vimeo", "https://vimeo.com", listOf("vimeo"), 0xFF1AB7EA),
    QuickSite("Dailymotion", "https://www.dailymotion.com", listOf("dailymotion"), 0xFF0066DC),
)

private val domainRegex = Regex(
    """^(https?://)?(www\.)?([a-z0-9-]+\.)+[a-z]{2,}(:\d+)?(/.*)?$""",
    RegexOption.IGNORE_CASE,
)

private val downloadableHints = listOf(
    "youtube.com/watch",
    "youtube.com/shorts",
    "youtube.com/clip",
    "youtu.be/",
    "music.youtube.com/",
    "tiktok.com/",
    "vm.tiktok.com/",
    "instagram.com/p/",
    "instagram.com/reel/",
    "instagram.com/tv/",
    "facebook.com/",
    "fb.watch/",
    "x.com/",
    "twitter.com/",
    "soundcloud.com/",
    "vimeo.com/",
    "dailymotion.com/video",
)

fun resolveAddress(raw: String): String {
    val input = raw.trim()
    if (input.isEmpty()) return QUICK_SITES.first().url

    val aliasKey = input.lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('/')
        .trim()

    QUICK_SITES.firstOrNull { site ->
        site.aliases.any { alias -> aliasKey == alias || aliasKey == alias.replace(" ", "") }
    }?.let { return it.url }

    if (looksLikeUrl(input)) return extractUrl(input)

    if (' ' !in input && domainRegex.matches(input)) {
        return if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
    }

    return "https://m.youtube.com/results?search_query=${encodeQuery(input)}"
}

fun isDownloadablePage(url: String): Boolean {
    val lower = url.lowercase()
    if ("youtube.com" in lower || "youtu.be" in lower || "youtube-nocookie.com" in lower) {
        return listOf("watch", "shorts", "clip", "live", "embed", "v=", "music.youtube").any { it in lower }
    }
    return downloadableHints.any { it in lower }
}

fun encodeQuery(value: String): String = buildString {
    for (ch in value) {
        when {
            ch.isLetterOrDigit() || ch in "-._~" -> append(ch)
            ch == ' ' -> append('+')
            else -> append('%').append(ch.code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
