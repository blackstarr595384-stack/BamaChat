package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakeAiProvider(
    private val providerId: AiProviderId,
    private val streaming: Boolean = true
) : AiProvider {
    override fun id(): AiProviderId = providerId

    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        return AiChatResponse(
            provider = request.provider,
            model = request.model,
            message = AiChatMessage(
                role = AiChatRole.ASSISTANT,
                text = "reply from ${request.provider}"
            )
        )
    }

    override fun stream(request: AiChatRequest): Flow<AiChatResponse> = flowOf(
        AiChatResponse(
            provider = request.provider,
            model = request.model,
            message = AiChatMessage(AiChatRole.ASSISTANT, "stream reply")
        )
    )

    override fun supportsStreaming(): Boolean = streaming
}
