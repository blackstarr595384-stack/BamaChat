package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.ai.AiEngine
import com.example.bamachat.shared.core.ai.AiProviderRegistry

/**
 * Small Android pilot bridge into sharedCore AiEngine.
 *
 * The production caller keeps the legacy path as fallback. This class returns null whenever
 * the pilot is disabled or fails, so callers can preserve existing behavior.
 */
class AndroidAiOrchestrator(
    private val isExperimentalEnabled: () -> Boolean,
    private val chatCompletion: suspend (OpenRouterChatRequest) -> OpenRouterChatResponse
) {
    suspend fun chatOrNull(request: AiChatRequest): AiChatResponse? {
        if (!isExperimentalEnabled()) return null

        return runCatching {
            val provider = AndroidOpenRouterAiProvider(chatCompletion)
            val engine = AiEngine(AiProviderRegistry(listOf(provider)))
            engine.chat(request)
        }.getOrNull()
    }

    companion object {
        const val KEY_SHARED_AI_EXPERIMENTAL = "shared.ai.experimental"
    }
}
