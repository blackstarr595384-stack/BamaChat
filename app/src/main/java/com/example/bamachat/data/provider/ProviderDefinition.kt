package com.example.bamachat.data.provider

enum class ProviderDefinitionValidationError {
    INVALID_DISPLAY_NAME,
    INVALID_TIMEOUT,
    BUILT_IN_MISMATCH
}

class ProviderDefinitionValidationException(
    val error: ProviderDefinitionValidationError,
    message: String
) : IllegalArgumentException(message)

data class ProviderDefinition(
    val id: ProviderId,
    val displayName: String,
    val connectionType: ProviderConnectionType,
    val baseUrl: String,
    val authenticationType: ProviderAuthenticationType,
    val defaultModelId: String?,
    val capabilities: ProviderCapabilities,
    val timeoutMs: Long,
    val enabled: Boolean,
    val builtIn: Boolean,
    val localHttpConfirmed: Boolean,
    val hasSecret: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        if (displayName.isBlank() || displayName != displayName.trim() || displayName.length > MAX_DISPLAY_NAME_LENGTH) {
            throw ProviderDefinitionValidationException(
                ProviderDefinitionValidationError.INVALID_DISPLAY_NAME,
                "Der Anbietername ist ungültig."
            )
        }
        if (timeoutMs !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            throw ProviderDefinitionValidationException(
                ProviderDefinitionValidationError.INVALID_TIMEOUT,
                "Das Zeitlimit muss zwischen 5 und 120 Sekunden liegen."
            )
        }
        if (builtIn != id.isBuiltIn) {
            throw ProviderDefinitionValidationException(
                ProviderDefinitionValidationError.BUILT_IN_MISMATCH,
                "Die technische Anbieterart darf nicht verändert werden."
            )
        }
    }

    companion object {
        const val MIN_TIMEOUT_MS = 5_000L
        const val MAX_TIMEOUT_MS = 120_000L
        const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_DISPLAY_NAME_LENGTH = 80

        fun create(
            id: ProviderId,
            displayName: String,
            connectionType: ProviderConnectionType,
            baseUrl: String,
            authenticationType: ProviderAuthenticationType,
            defaultModelId: String? = null,
            capabilities: ProviderCapabilities,
            timeoutMs: Long = DEFAULT_TIMEOUT_MS,
            enabled: Boolean = true,
            builtIn: Boolean = id.isBuiltIn,
            localHttpConfirmed: Boolean = false,
            hasSecret: Boolean = false,
            createdAt: Long = System.currentTimeMillis(),
            updatedAt: Long = createdAt
        ): ProviderDefinition = ProviderDefinition(
            id = id,
            displayName = displayName.trim(),
            connectionType = connectionType,
            baseUrl = baseUrl.trim(),
            authenticationType = authenticationType,
            defaultModelId = defaultModelId?.trim()?.takeIf { it.isNotEmpty() },
            capabilities = capabilities,
            timeoutMs = timeoutMs,
            enabled = enabled,
            builtIn = builtIn,
            localHttpConfirmed = localHttpConfirmed,
            hasSecret = hasSecret,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
