package com.dovimu.data

private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

fun extractUrl(raw: String): String {
    val trimmed = raw.trim()
    return urlRegex.find(trimmed)?.value?.trimEnd(')', ']', '.', ',', '"', '\'') ?: trimmed
}

fun looksLikeUrl(raw: String): Boolean = urlRegex.containsMatchIn(raw)

fun isPlaylistUrl(url: String): Boolean {
    val lower = url.lowercase()
    return "list=" in lower || "/playlist" in lower
}

fun prettySpeed(bytesPerSecond: Double?): String? {
    if (bytesPerSecond == null || bytesPerSecond <= 0) return null
    var size = bytesPerSecond
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var index = 0
    while (size >= 1024 && index < units.lastIndex) {
        size /= 1024
        index++
    }
    val label = if (index == 0) "${size.toInt()}" else ((size * 10).toInt() / 10.0).toString()
    return "$label ${units[index]}"
}

fun statusLabel(status: String): String = when (status) {
    "queued" -> "En cola"
    "downloading" -> "Descargando"
    "processing" -> "Procesando"
    "ready" -> "Listo en el servidor"
    "saving" -> "Guardando en el teléfono"
    "saved" -> "Guardado"
    "failed" -> "Error"
    "save_failed" -> "No se pudo guardar"
    "cancelled" -> "Cancelado"
    else -> status
}

fun sourceLabel(source: String): String = source.replaceFirstChar { it.uppercase() }
