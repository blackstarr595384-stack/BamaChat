package com.example.bamachat.data.provider.chat

import com.example.bamachat.data.provider.ProviderDefinition

interface ProviderChatAdapter {
    suspend fun execute(
        provider: ProviderDefinition,
        normalizedBaseUrl: String,
        modelId: String,
        secret: String?,
        messages: List<ProviderChatMessage>,
        onChunk: suspend (ProviderChatChunk) -> Unit
    ): ProviderChatResult
}
