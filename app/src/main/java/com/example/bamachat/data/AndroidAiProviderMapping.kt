package com.example.bamachat.data

import com.example.bamachat.shared.core.AiProviderId

/**
 * Preparation layer for the shared AI architecture.
 * This maps Android provider identifiers to sharedCore ids without changing request routing.
 */
fun ApiClient.Provider.toAiProviderId(): AiProviderId = when (this) {
    ApiClient.Provider.OPENROUTER -> AiProviderId.OPENROUTER
    ApiClient.Provider.GROQ -> AiProviderId.GROQ
    ApiClient.Provider.CEREBRAS -> AiProviderId.CEREBRAS
    ApiClient.Provider.TOGETHER -> AiProviderId.TOGETHER
    ApiClient.Provider.OPENCODE -> AiProviderId.OPENCODE
}
