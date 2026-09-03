package com.example.bamachat.data.provider.discovery

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.data.provider.ProviderSecretStorage
import com.example.bamachat.data.provider.ProviderUrlPolicy
import com.example.bamachat.data.provider.ProviderUrlValidationResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderDiscoveryService @Inject constructor(
    private val repository: ProviderRepository,
    private val secretStorage: ProviderSecretStorage,
    private val openAiAdapter: OpenAiModelDiscoveryAdapter,
    private val ollamaAdapter: OllamaModelDiscoveryAdapter
) {
    suspend fun discover(providerId: ProviderId): ProviderDiscoveryResult {
        val provider = repository.getProvider(providerId)
            ?: throw ProviderDiscoveryException(ProviderDiscoveryError.PROVIDER_MISSING, "Provider missing")
        if (provider.builtIn || !provider.id.isCustom) {
            throw ProviderDiscoveryException(ProviderDiscoveryError.BUILT_IN_NOT_SUPPORTED, "Built-in provider unsupported")
        }
        if (!provider.enabled) {
            throw ProviderDiscoveryException(ProviderDiscoveryError.PROVIDER_DISABLED, "Provider disabled")
        }
        val validated = when (val result = ProviderUrlPolicy.validate(provider.baseUrl, provider.localHttpConfirmed)) {
            is ProviderUrlValidationResult.Valid -> result
            is ProviderUrlValidationResult.RequiresLocalHttpConfirmation -> throw ProviderDiscoveryException(
                ProviderDiscoveryError.LOCAL_HTTP_CONFIRMATION_REQUIRED,
                "Local HTTP confirmation required"
            )
            is ProviderUrlValidationResult.Invalid -> throw ProviderDiscoveryException(
                ProviderDiscoveryError.UNSAFE_URL,
                "Provider URL unsafe"
            )
        }
        if (provider.connectionType == ProviderConnectionType.OLLAMA_LOCAL && !validated.localTarget) {
            throw ProviderDiscoveryException(ProviderDiscoveryError.UNSAFE_URL, "Ollama target is not local")
        }
        val secret = if (provider.authenticationType == ProviderAuthenticationType.BEARER) {
            secretStorage.get(provider.id)?.takeIf(String::isNotBlank)
                ?: throw ProviderDiscoveryException(ProviderDiscoveryError.SECRET_MISSING, "Provider secret unavailable")
        } else null
        val adapter = when (provider.connectionType) {
            ProviderConnectionType.OPENAI_COMPATIBLE -> openAiAdapter
            ProviderConnectionType.OLLAMA_LOCAL -> ollamaAdapter
        }
        return adapter.discover(provider, validated.normalizedUrl, secret)
    }
}
