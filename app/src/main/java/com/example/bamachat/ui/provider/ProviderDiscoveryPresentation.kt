package com.example.bamachat.ui.provider

import com.example.bamachat.data.provider.discovery.ProviderDiscoveryError

object ProviderDiscoveryPresentation {
    fun errorMessage(error: ProviderDiscoveryError): String = when (error) {
        ProviderDiscoveryError.PROVIDER_NOT_SAVED -> "Speichere den Anbieter zuerst."
        ProviderDiscoveryError.PROVIDER_MISSING -> "Der Anbieter wurde gelöscht."
        ProviderDiscoveryError.PROVIDER_DISABLED -> "Der Anbieter ist deaktiviert."
        ProviderDiscoveryError.BUILT_IN_NOT_SUPPORTED -> "Verbindungstests für integrierte Anbieter folgen später."
        ProviderDiscoveryError.UNSAFE_URL -> "Die Anbieteradresse ist aus Sicherheitsgründen nicht erlaubt."
        ProviderDiscoveryError.LOCAL_HTTP_CONFIRMATION_REQUIRED -> "Die lokale HTTP-Verbindung muss zuerst bestätigt werden."
        ProviderDiscoveryError.SECRET_MISSING -> "Für diesen Anbieter fehlt ein API-Schlüssel."
        ProviderDiscoveryError.BAD_REQUEST -> "Der Anbieter hat die Anfrage nicht akzeptiert."
        ProviderDiscoveryError.AUTHENTICATION_FAILED -> "Der Anbieter hat den API-Schlüssel abgelehnt."
        ProviderDiscoveryError.NOT_FOUND -> "Der Modell-Endpunkt wurde nicht gefunden."
        ProviderDiscoveryError.TIMEOUT -> "Der Anbieter hat nicht rechtzeitig geantwortet."
        ProviderDiscoveryError.RATE_LIMITED -> "Der Anbieter begrenzt derzeit die Anfragen. Versuche es später erneut."
        ProviderDiscoveryError.SERVER_ERROR -> "Beim Anbieter ist ein Serverfehler aufgetreten."
        ProviderDiscoveryError.CONNECTION_FAILED -> "Die Verbindung konnte nicht geprüft werden."
        ProviderDiscoveryError.TLS_FAILURE -> "Die sichere Verbindung zum Anbieter konnte nicht hergestellt werden."
        ProviderDiscoveryError.REDIRECT_BLOCKED -> "Die Weiterleitung wurde aus Sicherheitsgründen blockiert."
        ProviderDiscoveryError.EMPTY_RESPONSE -> "Der Anbieter hat keine Antwort geliefert."
        ProviderDiscoveryError.INVALID_JSON,
        ProviderDiscoveryError.UNEXPECTED_FORMAT -> "Die Antwort des Anbieters konnte nicht verarbeitet werden."
        ProviderDiscoveryError.RESPONSE_TOO_LARGE -> "Die Modellantwort war zu groß."
        ProviderDiscoveryError.CANCELLED -> "Vorgang abgebrochen."
    }
}
