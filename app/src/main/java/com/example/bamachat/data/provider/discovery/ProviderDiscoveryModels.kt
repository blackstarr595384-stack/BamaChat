package com.example.bamachat.data.provider.discovery

import com.example.bamachat.data.provider.ProviderId

data class DiscoveredProviderModel(val modelId: String)

data class ProviderDiscoveryResult(
    val providerId: ProviderId,
    val models: List<DiscoveredProviderModel>,
    val truncated: Boolean
)

enum class ProviderDiscoveryError {
    PROVIDER_NOT_SAVED,
    PROVIDER_MISSING,
    PROVIDER_DISABLED,
    BUILT_IN_NOT_SUPPORTED,
    UNSAFE_URL,
    LOCAL_HTTP_CONFIRMATION_REQUIRED,
    SECRET_MISSING,
    BAD_REQUEST,
    AUTHENTICATION_FAILED,
    NOT_FOUND,
    TIMEOUT,
    RATE_LIMITED,
    SERVER_ERROR,
    CONNECTION_FAILED,
    TLS_FAILURE,
    REDIRECT_BLOCKED,
    EMPTY_RESPONSE,
    INVALID_JSON,
    UNEXPECTED_FORMAT,
    RESPONSE_TOO_LARGE,
    CANCELLED
}

class ProviderDiscoveryException(
    val error: ProviderDiscoveryError,
    message: String
) : IllegalStateException(message)

object ProviderDiscoveryMessages {
    fun forError(error: ProviderDiscoveryError): String = when (error) {
        ProviderDiscoveryError.PROVIDER_NOT_SAVED -> "Speichere den Anbieter zuerst."
        ProviderDiscoveryError.PROVIDER_MISSING -> "Der Anbieter wurde gelöscht."
        ProviderDiscoveryError.PROVIDER_DISABLED -> "Der Anbieter ist deaktiviert."
        ProviderDiscoveryError.BUILT_IN_NOT_SUPPORTED -> "Verbindungstests für integrierte Anbieter folgen später."
        ProviderDiscoveryError.UNSAFE_URL -> "Die Anbieteradresse ist nicht sicher konfiguriert."
        ProviderDiscoveryError.LOCAL_HTTP_CONFIRMATION_REQUIRED -> "Die lokale HTTP-Verbindung muss zuerst bestätigt werden."
        ProviderDiscoveryError.SECRET_MISSING -> "Für diesen Anbieter fehlt ein API-Schlüssel."
        ProviderDiscoveryError.BAD_REQUEST -> "Der Anbieter hat die Anfrage nicht akzeptiert."
        ProviderDiscoveryError.AUTHENTICATION_FAILED -> "Der Anbieter hat den API-Schlüssel abgelehnt."
        ProviderDiscoveryError.NOT_FOUND -> "Der Modell-Endpunkt wurde nicht gefunden."
        ProviderDiscoveryError.TIMEOUT -> "Der Anbieter hat nicht rechtzeitig geantwortet."
        ProviderDiscoveryError.RATE_LIMITED -> "Der Anbieter begrenzt derzeit die Anfragen. Versuche es später erneut."
        ProviderDiscoveryError.SERVER_ERROR -> "Der Anbieter ist vorübergehend nicht verfügbar."
        ProviderDiscoveryError.CONNECTION_FAILED -> "Die Verbindung konnte nicht geprüft werden."
        ProviderDiscoveryError.TLS_FAILURE -> "Die sichere Verbindung zum Anbieter konnte nicht hergestellt werden."
        ProviderDiscoveryError.REDIRECT_BLOCKED -> "Die Weiterleitung wurde aus Sicherheitsgründen blockiert."
        ProviderDiscoveryError.EMPTY_RESPONSE -> "Der Anbieter hat keine Antwort geliefert."
        ProviderDiscoveryError.INVALID_JSON,
        ProviderDiscoveryError.UNEXPECTED_FORMAT -> "Das Antwortformat des Anbieters konnte nicht verarbeitet werden."
        ProviderDiscoveryError.RESPONSE_TOO_LARGE -> "Die Modellantwort war zu groß."
        ProviderDiscoveryError.CANCELLED -> "Die Prüfung wurde abgebrochen."
    }
}
