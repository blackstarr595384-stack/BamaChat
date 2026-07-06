package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamDelta
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamEvent
import com.example.bamachat.shared.core.ai.AiStreamFinished
import com.example.bamachat.shared.core.ai.AiStreamStarted
import com.example.bamachat.shared.core.ai.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Android OpenRouter adapter for the shared AI architecture.
 *
 * This skeleton is intentionally not registered or used by the production chat flow yet.
 * It reuses the existing OpenRouter DTOs and delegates transport to the provided completion function.
 */
class AndroidOpenRouterAiProvider(
    private val chatCompletion: suspend (OpenRouterChatRequest) -> OpenRouterChatResponse,
    private val streamTextChunks: ((OpenRouterChatRequest) -> Flow<String>)? = null
) : AiProvider {
    constructor(service: OpenAICompatibleService) : this(chatCompletion = service::chatCompletion)

    override fun id(): AiProviderId = AiProviderId.OPENROUTER

    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        require(request.provider == AiProviderId.OPENROUTER) {
            "AndroidOpenRouterAiProvider only supports OPENROUTER requests."
        }

        val response = chatCompletion(request.toOpenRouterChatRequest(stream = false))
        response.error?.message?.takeIf { it.isNotBlank() }?.let { error ->
            throw IllegalStateException(error)
        }

        val message = response.choices
            ?.firstOrNull()
            ?.message
            ?.toAiChatMessageOrNull()
            ?: AiChatMessage(role = AiChatRole.ASSISTANT, text = "")

        return AiChatResponse(
            provider = AiProviderId.OPENROUTER,
            model = request.model,
            message = message
        )
    }

    override fun stream(request: AiChatRequest): Flow<AiChatResponse> {
        throw UnsupportedOperationException("Android OpenRouter streaming is not wired through AiProvider yet.")
    }

    override fun streamEvents(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        require(request.provider == AiProviderId.OPENROUTER) {
            "AndroidOpenRouterAiProvider only supports OPENROUTER requests."
        }

        val stream = streamTextChunks
            ?: throw UnsupportedOperationException("Android OpenRouter stream events are not wired to transport yet.")
        val streamRequest = request.toOpenRouterChatRequest(stream = true)
        val accumulated = StringBuilder()

        emit(AiStreamStarted(provider = AiProviderId.OPENROUTER, model = request.model))
        try {
            stream(streamRequest).collect { chunk ->
                if (chunk.isNotEmpty()) {
                    accumulated.append(chunk)
                    emit(
                        AiStreamDelta(
                            text = chunk,
                            provider = AiProviderId.OPENROUTER,
                            model = request.model
                        )
                    )
                }
            }
            emit(
                AiStreamCompleted(
                    AiChatResponse(
                        provider = AiProviderId.OPENROUTER,
                        model = request.model,
                        message = AiChatMessage(
                            role = AiChatRole.ASSISTANT,
                            text = accumulated.toString()
                        )
                    )
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emit(
                AiStreamError(
                    message = error.message ?: "OpenRouter stream failed",
                    exceptionClass = error::class.java.simpleName,
                    provider = AiProviderId.OPENROUTER,
                    model = request.model
                )
            )
        } finally {
            emit(AiStreamFinished(provider = AiProviderId.OPENROUTER, model = request.model))
        }
    }

    override fun supportsStreaming(): Boolean = streamTextChunks != null
}

internal fun AiChatRequest.toOpenRouterChatRequest(stream: Boolean = this.stream): OpenRouterChatRequest {
    return OpenRouterChatRequest(
        model = model,
        messages = messages.map { it.toOpenRouterMessage() },
        maxTokens = maxTokens,
        temperature = temperature.toFloat(),
        stream = stream
    )
}

private fun AiChatMessage.toOpenRouterMessage(): OpenRouterMessage {
    val roleName = when (role) {
        AiChatRole.SYSTEM -> "system"
        AiChatRole.USER -> "user"
        AiChatRole.ASSISTANT -> "assistant"
    }
    return OpenRouterMessage(role = roleName, content = text)
}
