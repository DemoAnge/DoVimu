package com.dovimu.platform

/**
 * Last fatal crash captured on Android so the next launch can show why the app closed.
 */
object AppErrors {
    @Volatile
    var lastFatal: String? = null
        private set

    @Volatile
    var lastFatalDetail: String? = null
        private set

    fun setFatal(message: String, detail: String) {
        lastFatal = message
        lastFatalDetail = detail
    }

    fun consumeFatal(): Pair<String, String>? {
        val message = lastFatal ?: return null
        val detail = lastFatalDetail ?: message
        lastFatal = null
        lastFatalDetail = null
        return message to detail
    }
}

fun Throwable.toUserMessage(): String {
    val msg = message?.trim().orEmpty()
    val name = this::class.simpleName.orEmpty()
    return when {
        "UnknownHost" in name || "ConnectException" in name ->
            "Sin conexión. Revisa Internet y la URL de la API."
        "SocketTimeout" in name || "Timeout" in name ->
            "Tiempo de espera agotado. Inténtalo de nuevo."
        "SSL" in name || "Certificate" in name ->
            "Error de certificado HTTPS."
        "ActivityNotFound" in name ->
            "No hay una app para abrir este contenido."
        "SecurityException" in name ->
            "No hay permiso para abrir o guardar el archivo."
        "MissingWebView" in name || "WebView" in name && "Exception" in name ->
            "El navegador interno no está disponible. Actualiza Android System WebView."
        msg.contains("FLAG_ACTIVITY_NEW_TASK") ->
            "No se pudo abrir el archivo."
        msg.isNotBlank() -> msg.take(280)
        else -> "Error inesperado ($name)."
    }
}
