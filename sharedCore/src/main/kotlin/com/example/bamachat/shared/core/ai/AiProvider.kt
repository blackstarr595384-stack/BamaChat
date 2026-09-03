package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface AiProvider {
    fun id(): AiProviderId

    suspend fun chat(request: AiChatRequest): AiChatResponse

    fun stream(request: AiChatRequest): Flow<AiChatResponse>

    fun streamEvents(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        emit(AiStreamStarted(provider = request.provider, model = request.model))
        emitAll(stream(request).map { response -> AiStreamCompleted(response) })
        emit(AiStreamFinished(provider = request.provider, model = request.model))
    }

    fun supportsStreaming(): Boolean
}
