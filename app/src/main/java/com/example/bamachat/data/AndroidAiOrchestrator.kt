package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.ai.AiEngine
import com.example.bamachat.shared.core.ai.AiProviderRegistry
import com.example.bamachat.util.AppTelemetry
import kotlinx.coroutines.CancellationException

/**
 * Small Android pilot bridge into sharedCore AiEngine.
 *
 * The production caller keeps the legacy path as fallback. This class returns null whenever
 * the pilot is disabled or fails, so callers can preserve existing behavior.
 */
class AndroidAiOrchestrator(
    private val isExperimentalEnabled: () -> Boolean,
    private val chatCompletion: suspend (OpenRouterChatRequest) -> OpenRouterChatResponse,
    private val logEvent: (String, Map<String, Any?>) -> Unit = { name, params ->
        AppTelemetry.logEvent(name, params)
    },
    private val logError: (String, Throwable?) -> Unit = { name, error ->
        AppTelemetry.logError(name, error)
    }
) {
    suspend fun chatOrNull(request: AiChatRequest): AiChatResponse? {
        if (!isExperimentalEnabled()) return null

        logEvent("pilot_attempt", mapOf("provider" to request.provider.name, "model" to request.model))

        return try {
            val provider = AndroidOpenRouterAiProvider(chatCompletion)
            val engine = AiEngine(AiProviderRegistry(listOf(provider)))
            val response = engine.chat(request)
            if (response.message.text.isBlank()) {
                logEvent("pilot_fallback_empty_response", mapOf("provider" to request.provider.name, "model" to request.model))
                null
            } else {
                logEvent("pilot_success", mapOf("provider" to response.provider.name, "model" to response.model))
                response
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logEvent(
                "pilot_error",
                mapOf(
                    "provider" to request.provider.name,
                    "model" to request.model,
                    "exception" to error::class.java.simpleName
                )
            )
            logError("android_ai_orchestrator_pilot_failed", error)
            null
        }
    }

    companion object {
        const val KEY_SHARED_AI_EXPERIMENTAL = "shared.ai.experimental"

        fun isSharedAiPilotEnabled(
            sharedAiExperimental: Boolean,
            developerModeEnabled: Boolean
        ): Boolean = sharedAiExperimental && developerModeEnabled
    }
}
