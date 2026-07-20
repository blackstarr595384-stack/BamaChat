package com.example.bamachat.data.provider

import java.net.URI
import java.util.Locale

enum class ProviderUrlError {
    EMPTY,
    UNSUPPORTED_SCHEME,
    INVALID_HOST,
    USER_INFO_NOT_ALLOWED,
    FRAGMENT_NOT_ALLOWED,
    QUERY_NOT_ALLOWED,
    INVALID_PORT,
    PUBLIC_HTTP_NOT_ALLOWED
}

sealed interface ProviderUrlValidationResult {
    data class Valid(val normalizedUrl: String, val localTarget: Boolean) : ProviderUrlValidationResult
    data class RequiresLocalHttpConfirmation(
        val normalizedUrl: String,
        val message: String = "Lokale HTTP-Verbindungen müssen ausdrücklich bestätigt werden."
    ) : ProviderUrlValidationResult
    data class Invalid(val error: ProviderUrlError, val message: String) : ProviderUrlValidationResult
}

object ProviderUrlPolicy {
    fun validate(rawUrl: String, localHttpConfirmed: Boolean): ProviderUrlValidationResult {
        val clean = rawUrl.trim()
        if (clean.isEmpty()) {
            return ProviderUrlValidationResult.Invalid(ProviderUrlError.EMPTY, "Die Basis-URL darf nicht leer sein.")
        }
        val uri = runCatching { URI(clean) }.getOrNull()
            ?: return ProviderUrlValidationResult.Invalid(ProviderUrlError.INVALID_HOST, "Die Basis-URL ist ungültig.")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
            ?: return ProviderUrlValidationResult.Invalid(ProviderUrlError.UNSUPPORTED_SCHEME, "Die Basis-URL benötigt HTTPS oder lokales HTTP.")
        if (scheme != "https" && scheme != "http") {
            return ProviderUrlValidationResult.Invalid(ProviderUrlError.UNSUPPORTED_SCHEME, "Nur HTTPS und bestätigtes lokales HTTP sind erlaubt.")
        }
        if (uri.rawUserInfo != null) {
            return ProviderUrlValidationResult.Invalid(ProviderUrlError.USER_INFO_NOT_ALLOWED, "Zugangsdaten dürfen nicht in der Basis-URL stehen.")
        }
        if (uri.rawFragment != null) {
            return ProviderUrlValidationResult.Invalid(ProviderUrlError.FRAGMENT_NOT_ALLOWED, "Fragmente sind in der Basis-URL nicht erlaubt.")
        }
        if (uri.rawQuery != null) {
            return ProviderUrlValidationResult.Invalid(ProviderUrlError.QUERY_NOT_ALLOWED, "Abfrageparameter sind in der Basis-URL nicht erlaubt.")
        }
        val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.lowercase(Locale.ROOT).orEmpty()
        if (host.isBlank()) {
            return ProviderUrlValidationResult.Invalid(ProviderUrlError.INVALID_HOST, "Die Basis-URL benötigt einen gültigen Host.")
        }
        if (uri.port !in -1..65535 || uri.port == 0) {
            return ProviderUrlValidationResult.Invalid(ProviderUrlError.INVALID_PORT, "Der Port der Basis-URL ist ungültig.")
        }
        val localTarget = isLocalHost(host)
        val normalized = normalize(uri, scheme, host)
        if (scheme == "http" && !localTarget) {
            return ProviderUrlValidationResult.Invalid(ProviderUrlError.PUBLIC_HTTP_NOT_ALLOWED, "Öffentliche Anbieter müssen HTTPS verwenden.")
        }
        if (scheme == "http" && !localHttpConfirmed) {
            return ProviderUrlValidationResult.RequiresLocalHttpConfirmation(normalized)
        }
        return ProviderUrlValidationResult.Valid(normalized, localTarget)
    }

    private fun normalize(uri: URI, scheme: String, host: String): String {
        val path = uri.normalize().rawPath.orEmpty().ifBlank { "/" }.let {
            if (it.endsWith('/')) it else "$it/"
        }
        return URI(scheme, null, host, uri.port, path, null, null).toASCIIString()
    }

    private fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return true
        if (host.startsWith("fe8", ignoreCase = true) || host.startsWith("fe9", ignoreCase = true) ||
            host.startsWith("fea", ignoreCase = true) || host.startsWith("feb", ignoreCase = true)
        ) return true
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 127 ||
            octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 169 && octets[1] == 254)
    }
}
