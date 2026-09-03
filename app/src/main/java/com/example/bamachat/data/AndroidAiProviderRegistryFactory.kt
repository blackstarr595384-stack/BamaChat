package com.example.bamachat.data

import com.example.bamachat.shared.core.ai.AiProviderRegistry

/**
 * Prepares Android AI providers for the shared AiEngine architecture.
 *
 * This factory is not wired into ChatViewModel yet; it only centralizes future provider
 * registration so the current production chat flow remains unchanged.
 */
object AndroidAiProviderRegistryFactory {
    fun create(openRouterService: OpenAICompatibleService): AiProviderRegistry {
        return create(openRouterProvider = AndroidOpenRouterAiProvider(openRouterService))
    }

    internal fun create(openRouterProvider: AndroidOpenRouterAiProvider): AiProviderRegistry {
        return AiProviderRegistry()
            .register(openRouterProvider)
    }
}
