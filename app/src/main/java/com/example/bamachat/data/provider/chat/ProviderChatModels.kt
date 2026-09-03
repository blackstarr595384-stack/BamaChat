package com.example.bamachat.data.provider.chat

data class ProviderChatMessage(val role: String, val content: String)

data class ProviderChatRequest(
    val selection: ActiveChatProviderSelection.Custom,
    val messages: List<ProviderChatMessage>
)

data class ProviderChatChunk(val text: String)

data class ProviderChatResult(val text: String)

enum class ProviderChatError {
    INVALID_SELECTION,
    SECRET_MISSING,
    UNSAFE_URL,
    CONNECTION_FAILED,
    TIMEOUT,
    TLS_FAILURE,
    REDIRECT_BLOCKED,
    AUTHENTICATION_FAILED,
    RATE_LIMITED,
    BAD_REQUEST,
    NOT_FOUND,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
    RESPONSE_TOO_LARGE,
    CANCELLED,
    UNSUPPORTED_FEATURE
}

class ProviderChatException(
    val error: ProviderChatError,
    val statusCode: Int? = null,
    message: String
) : IllegalStateException(message)

object ProviderChatErrorMessages {
    fun message(error: ProviderChatError): String = when (error) {
        ProviderChatError.INVALID_SELECTION -> "Die ausgewählte Chat-Konfiguration ist nicht mehr gültig."
        ProviderChatError.SECRET_MISSING -> "Für den ausgewählten Anbieter fehlt ein API-Key."
        ProviderChatError.UNSAFE_URL -> "Die Anbieteradresse wurde aus Sicherheitsgründen blockiert."
        ProviderChatError.CONNECTION_FAILED -> "Der ausgewählte Anbieter ist derzeit nicht erreichbar."
        ProviderChatError.TIMEOUT -> "Der ausgewählte Anbieter hat nicht rechtzeitig geantwortet."
        ProviderChatError.TLS_FAILURE -> "Die sichere Verbindung zum Anbieter konnte nicht hergestellt werden."
        ProviderChatError.REDIRECT_BLOCKED -> "Eine Weiterleitung wurde aus Sicherheitsgründen blockiert."
        ProviderChatError.AUTHENTICATION_FAILED -> "Der API-Key wurde vom ausgewählten Anbieter abgelehnt."
        ProviderChatError.RATE_LIMITED -> "Der ausgewählte Anbieter hat sein Nutzungslimit erreicht."
        ProviderChatError.BAD_REQUEST -> "Der ausgewählte Anbieter hat die Anfrage nicht akzeptiert."
        ProviderChatError.NOT_FOUND -> "Der Chat-Endpunkt des ausgewählten Anbieters wurde nicht gefunden."
        ProviderChatError.HTTP_CLIENT_ERROR -> "Der ausgewählte Anbieter hat die Anfrage abgelehnt."
        ProviderChatError.HTTP_SERVER_ERROR -> "Der ausgewählte Anbieter ist vorübergehend nicht verfügbar."
        ProviderChatError.EMPTY_RESPONSE -> "Der ausgewählte Anbieter hat keine Antwort geliefert."
        ProviderChatError.INVALID_RESPONSE -> "Die Antwort des ausgewählten Anbieters konnte nicht verarbeitet werden."
        ProviderChatError.RESPONSE_TOO_LARGE -> "Die Antwort des ausgewählten Anbieters war zu groß."
        ProviderChatError.CANCELLED -> "Die Anfrage wurde abgebrochen."
        ProviderChatError.UNSUPPORTED_FEATURE -> "Diese Funktion wird mit dem ausgewählten Anbieter noch nicht unterstützt."
    } + " Es wurde kein anderer Anbieter verwendet."
}
