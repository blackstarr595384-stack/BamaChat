package com.example.bamachat.data.provider

enum class ProviderModelSource {
    BUILT_IN,
    MANUAL,
    DISCOVERED
}

enum class ProviderModelValidationError {
    EMPTY_MODEL_ID,
    INVALID_DISPLAY_NAME
}

class ProviderModelValidationException(
    val error: ProviderModelValidationError,
    message: String
) : IllegalArgumentException(message)

data class ProviderModelDefinition(
    val providerId: ProviderId,
    val modelId: String,
    val displayName: String,
    val source: ProviderModelSource,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        if (modelId.isBlank() || modelId != modelId.trim()) {
            throw ProviderModelValidationException(
                ProviderModelValidationError.EMPTY_MODEL_ID,
                "Die Modell-ID darf nicht leer sein."
            )
        }
        if (displayName.isBlank() || displayName != displayName.trim()) {
            throw ProviderModelValidationException(
                ProviderModelValidationError.INVALID_DISPLAY_NAME,
                "Der Modellname ist ungültig."
            )
        }
    }

    companion object {
        fun create(
            providerId: ProviderId,
            modelId: String,
            displayName: String = modelId,
            source: ProviderModelSource,
            enabled: Boolean = true,
            createdAt: Long = System.currentTimeMillis(),
            updatedAt: Long = createdAt
        ): ProviderModelDefinition = ProviderModelDefinition(
            providerId = providerId,
            modelId = modelId.trim(),
            displayName = displayName.trim(),
            source = source,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
