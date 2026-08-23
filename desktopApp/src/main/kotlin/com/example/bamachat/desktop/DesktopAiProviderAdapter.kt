package com.example.bamachat.desktop

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.ai.AiProvider
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

class DesktopAiProviderAdapter(
    private val settings: DesktopUserSettings,
    private val gateway: DesktopChatGateway
) : AiProvider {
    override fun id(): AiProviderId = settings.provider.toAiProviderId()

    override suspend fun chat(request: AiChatRequest): AiChatResponse = gateway.chat(settings, request)

    override fun stream(request: AiChatRequest): Flow<AiChatResponse> =
        gateway.stream(settings, request).transform { event ->
            when (event) {
                is AiStreamCompleted -> emit(event.response)
                is AiStreamError -> throw DesktopUnknownProviderException(event.message)
                else -> Unit
            }
        }

    override fun streamEvents(request: AiChatRequest): Flow<AiStreamEvent> =
        gateway.stream(settings, request)

    override fun supportsStreaming(): Boolean = true
}

fun DesktopProvider.toAiProviderId(): AiProviderId = when (this) {
    DesktopProvider.OPENROUTER -> AiProviderId.OPENROUTER
    DesktopProvider.OLLAMA -> AiProviderId.OLLAMA
}
