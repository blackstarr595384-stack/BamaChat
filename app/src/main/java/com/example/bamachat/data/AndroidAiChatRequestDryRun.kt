package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.ExtensionRuntimeDecision
import com.example.bamachat.shared.core.QuickActionSuggestion
import com.example.bamachat.shared.core.ai.AiChatRequestBuilder

/**
 * Dry-run adapter for validating shared AI request construction beside the existing Android flow.
 * It must not be used for production network routing yet.
 */
fun List<OpenRouterMessage>.toAiChatRequestForValidation(
    provider: AiProviderId,
    model: String,
    quickAction: QuickActionSuggestion = QuickActionSuggestion.AUTO,
    runtimeDecision: ExtensionRuntimeDecision? = null,
    maxTokens: Int = 4096,
    temperature: Double = 0.7,
    stream: Boolean = false,
    builder: AiChatRequestBuilder = AiChatRequestBuilder()
): AiChatRequest {
    return builder.build(
        provider = provider,
        model = model,
        messages = mapNotNull { it.toAiChatMessageOrNull() },
        quickAction = quickAction,
        runtimeDecision = runtimeDecision,
        maxTokens = maxTokens,
        temperature = temperature,
        stream = stream
    )
}

fun OpenRouterMessage.toAiChatMessageOrNull(): AiChatMessage? {
    val content = content ?: return null
    val mappedRole = when (role.trim().lowercase()) {
        "system" -> AiChatRole.SYSTEM
        "user" -> AiChatRole.USER
        "assistant" -> AiChatRole.ASSISTANT
        // Developer messages are not part of the current shared role model yet.
        // Tool messages carry call results and stay out of this production dry-run mapping.
        else -> return null
    }
    return AiChatMessage(role = mappedRole, text = content)
}
