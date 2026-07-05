package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiProviderId
import kotlinx.coroutines.flow.Flow

interface AiProvider {
    fun id(): AiProviderId

    suspend fun chat(request: AiChatRequest): AiChatResponse

    fun stream(request: AiChatRequest): Flow<AiChatResponse>

    fun supportsStreaming(): Boolean
}
