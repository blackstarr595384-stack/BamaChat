package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiProviderId

class AiProviderRegistry(
    providers: Iterable<AiProvider> = emptyList(),
    private var defaultProviderId: AiProviderId? = null
) {
    private val providersById = linkedMapOf<AiProviderId, AiProvider>()

    init {
        providers.forEach { register(it) }
    }

    fun register(provider: AiProvider): AiProviderRegistry {
        providersById[provider.id()] = provider
        if (defaultProviderId == null) {
            defaultProviderId = provider.id()
        }
        return this
    }

    fun unregister(providerId: AiProviderId): AiProvider? {
        val removed = providersById.remove(providerId)
        if (defaultProviderId == providerId) {
            defaultProviderId = providersById.keys.firstOrNull()
        }
        return removed
    }

    fun provider(providerId: AiProviderId): AiProvider {
        return providersById[providerId]
            ?: throw IllegalStateException("AI provider is not registered: $providerId")
    }

    fun providers(): List<AiProvider> = providersById.values.toList()

    fun defaultProvider(): AiProvider {
        val providerId = defaultProviderId
            ?: throw IllegalStateException("No default AI provider is registered.")
        return provider(providerId)
    }

    fun contains(providerId: AiProviderId): Boolean = providersById.containsKey(providerId)
}
