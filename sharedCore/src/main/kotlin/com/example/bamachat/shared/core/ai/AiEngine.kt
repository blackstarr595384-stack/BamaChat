package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiProviderId
import kotlinx.coroutines.flow.Flow

class AiEngine(
    private val registry: AiProviderRegistry
) {
    suspend fun chat(request: AiChatRequest): AiChatResponse {
        return registry.provider(request.provider).chat(request)
    }

    fun stream(request: AiChatRequest): Flow<AiStreamEvent> {
        val provider = registry.provider(request.provider)
        if (!provider.supportsStreaming()) {
            throw IllegalStateException("AI provider does not support streaming: ${request.provider}")
        }
        return provider.streamEvents(request)
    }

    fun supportsStreaming(providerId: AiProviderId): Boolean {
        return if (registry.contains(providerId)) {
            registry.provider(providerId).supportsStreaming()
        } else {
            false
        }
    }
}
