package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.ExtensionRuntimeDecision
import com.example.bamachat.shared.core.QuickActionSuggestion

class AiChatRequestBuilder {
    fun build(
        provider: AiProviderId,
        model: String,
        messages: List<AiChatMessage>,
        quickAction: QuickActionSuggestion = QuickActionSuggestion.AUTO,
        runtimeDecision: ExtensionRuntimeDecision? = null,
        maxTokens: Int = 1200,
        temperature: Double = 0.7,
        stream: Boolean = false
    ): AiChatRequest {
        return AiChatRequest(
            provider = provider,
            model = model,
            messages = messages,
            quickAction = quickAction,
            runtimeDecision = runtimeDecision,
            maxTokens = maxTokens,
            temperature = temperature,
            stream = stream
        )
    }
}
