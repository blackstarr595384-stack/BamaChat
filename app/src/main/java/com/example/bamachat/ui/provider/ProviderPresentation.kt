package com.example.bamachat.ui.provider

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderRepositoryError
import com.example.bamachat.data.provider.ProviderRepositoryException

internal fun ProviderConnectionType.displayName(): String = when (this) {
    ProviderConnectionType.OPENAI_COMPATIBLE -> "OpenAI-kompatibel"
    ProviderConnectionType.OLLAMA_LOCAL -> "Ollama lokal"
}

internal fun ProviderAuthenticationType.displayName(): String = when (this) {
    ProviderAuthenticationType.BEARER -> "Bearer-Token"
    ProviderAuthenticationType.NONE_LOCAL_ONLY -> "Keine Authentifizierung"
}

internal fun ProviderDefinition.secretSummary(): String = when {
    builtIn -> "Schlüssel in bisherigen KI-Einstellungen"
    hasSecret -> "API-Key gespeichert"
    authenticationType == ProviderAuthenticationType.NONE_LOCAL_ONLY -> "Kein API-Key erforderlich"
    else -> "Kein API-Key gespeichert"
}

internal fun Throwable.toProviderUserMessage(): String = when (this) {
    is ProviderRepositoryException -> when (error) {
        ProviderRepositoryError.CLEANUP_REQUIRED -> "Die sichere Bereinigung ist noch nicht abgeschlossen. Bitte versuche das Löschen erneut."
        ProviderRepositoryError.BUILT_IN_DELETE_FORBIDDEN -> "Integrierte Anbieter können nicht gelöscht werden."
        ProviderRepositoryError.MODEL_DISABLED -> "Das Standardmodell muss aktiviert sein."
        ProviderRepositoryError.MODEL_NOT_FOUND -> "Das ausgewählte Modell wurde nicht gefunden."
        else -> message ?: "Die Anbieteränderung konnte nicht gespeichert werden."
    }
    else -> "Die Anbieteränderung konnte nicht sicher verarbeitet werden."
}
