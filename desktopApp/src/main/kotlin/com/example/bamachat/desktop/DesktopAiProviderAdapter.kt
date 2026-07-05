package com.example.bamachat.desktop

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.ai.AiProvider
import kotlinx.coroutines.flow.Flow

class DesktopAiProviderAdapter(
    private val settings: DesktopUserSettings,
    private val gateway: DesktopChatGateway
) : AiProvider {
    override fun id(): AiProviderId = settings.provider.toAiProviderId()

    override suspend fun chat(request: AiChatRequest): AiChatResponse = gateway.chat(settings, request)

    override fun stream(request: AiChatRequest): Flow<AiChatResponse> {
        throw UnsupportedOperationException("Desktop streaming is not implemented yet.")
    }

    override fun supportsStreaming(): Boolean = false
}

fun DesktopProvider.toAiProviderId(): AiProviderId = when (this) {
    DesktopProvider.OPENROUTER -> AiProviderId.OPENROUTER
    DesktopProvider.OLLAMA -> AiProviderId.OLLAMA
}
