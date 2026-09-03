package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.QuickActionSuggestion

internal fun aiTestRequest(providerId: AiProviderId): AiChatRequest {
    return AiChatRequest(
        provider = providerId,
        model = "test-model",
        messages = listOf(AiChatMessage(AiChatRole.USER, "hello")),
        quickAction = QuickActionSuggestion.AUTO
    )
}
