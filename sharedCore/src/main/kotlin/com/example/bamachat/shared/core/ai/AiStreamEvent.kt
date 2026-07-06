package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiProviderId

sealed interface AiStreamEvent {
    val provider: AiProviderId?
    val model: String?
}

data class AiStreamStarted(
    override val provider: AiProviderId,
    override val model: String
) : AiStreamEvent

data class AiStreamDelta(
    val text: String,
    override val provider: AiProviderId,
    override val model: String
) : AiStreamEvent

data class AiStreamCompleted(
    val response: AiChatResponse
) : AiStreamEvent {
    override val provider: AiProviderId = response.provider
    override val model: String = response.model
}

data class AiStreamError(
    val message: String,
    val exceptionClass: String? = null,
    override val provider: AiProviderId? = null,
    override val model: String? = null
) : AiStreamEvent

data class AiStreamFinished(
    override val provider: AiProviderId? = null,
    override val model: String? = null
) : AiStreamEvent
