package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiProviderId
import kotlinx.coroutines.flow.Flow

class AiEngine(
    providers: Iterable<AiProvider> = emptyList()
) {
    private val providersById = linkedMapOf<AiProviderId, AiProvider>()

    init {
        providers.forEach { register(it) }
    }

    fun register(provider: AiProvider): AiEngine {
        providersById[provider.id()] = provider
        return this
    }

    fun select(providerId: AiProviderId): AiProvider {
        return providersById[providerId]
            ?: throw IllegalStateException("AI provider is not registered: $providerId")
    }

    suspend fun chat(request: AiChatRequest): AiChatResponse {
        return select(request.provider).chat(request)
    }

    fun stream(request: AiChatRequest): Flow<AiChatResponse> {
        val provider = select(request.provider)
        if (!provider.supportsStreaming()) {
            throw IllegalStateException("AI provider does not support streaming: ${request.provider}")
        }
        return provider.stream(request)
    }

    fun supportsStreaming(providerId: AiProviderId): Boolean {
        return providersById[providerId]?.supportsStreaming() ?: false
    }
}
