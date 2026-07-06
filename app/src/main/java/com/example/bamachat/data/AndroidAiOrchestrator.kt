package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.ai.AiEngine
import com.example.bamachat.shared.core.ai.AiPilotFlagUtils
import com.example.bamachat.shared.core.ai.AiProviderRegistry
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamDelta
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamEvent
import com.example.bamachat.util.AppTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Small Android pilot bridge into sharedCore AiEngine.
 *
 * The production caller keeps the legacy path as fallback. This class returns null whenever
 * the pilot is disabled or fails, so callers can preserve existing behavior.
 */
class AndroidAiOrchestrator(
    private val isExperimentalEnabled: () -> Boolean,
    private val chatCompletion: suspend (OpenRouterChatRequest) -> OpenRouterChatResponse,
    private val isStreamingExperimentalEnabled: () -> Boolean = { false },
    private val streamTextChunks: ((OpenRouterChatRequest) -> Flow<String>)? = null,
    private val legacyStreamEvents: (AiChatRequest) -> Flow<AiStreamEvent> = { request ->
        flow {
            throw IllegalStateException(
                "Legacy stream fallback is not configured for ${request.provider.name}/${request.model}."
            )
        }
    },
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

    fun streamEvents(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        if (!isStreamingExperimentalEnabled()) {
            emitAll(fallbackStream(request, "flag_off"))
            return@flow
        }

        logEvent("stream_pilot_attempt", mapOf("provider" to request.provider.name, "model" to request.model))

        try {
            val provider = AndroidOpenRouterAiProvider(
                chatCompletion = chatCompletion,
                streamTextChunks = streamTextChunks
            )
            val engine = AiEngine(AiProviderRegistry(listOf(provider)))
            val events = mutableListOf<AiStreamEvent>()
            engine.stream(request).collect { event ->
                events.add(event)
            }

            val error = events.filterIsInstance<AiStreamError>().firstOrNull()
            val hasTextDelta = events.filterIsInstance<AiStreamDelta>().any { it.text.isNotBlank() }
            val completedText = events
                .filterIsInstance<AiStreamCompleted>()
                .lastOrNull()
                ?.response
                ?.message
                ?.text
                .orEmpty()
            when {
                error != null -> {
                    logEvent(
                        "stream_pilot_error",
                        mapOf(
                            "provider" to request.provider.name,
                            "model" to request.model,
                            "exception" to (error.exceptionClass ?: "AiStreamError")
                        )
                    )
                    emitAll(fallbackStream(request, "provider_error"))
                }
                events.isEmpty() -> emitAll(fallbackStream(request, "empty_stream"))
                !hasTextDelta && completedText.isBlank() -> emitAll(fallbackStream(request, "empty_stream"))
                else -> {
                    logEvent("stream_pilot_success", mapOf("provider" to request.provider.name, "model" to request.model))
                    events.forEach { emit(it) }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logEvent(
                "stream_pilot_error",
                mapOf(
                    "provider" to request.provider.name,
                    "model" to request.model,
                    "exception" to error::class.java.simpleName
                )
            )
            logError("android_ai_orchestrator_stream_pilot_failed", error)
            emitAll(fallbackStream(request, "exception"))
        }
    }

    private fun fallbackStream(request: AiChatRequest, reason: String): Flow<AiStreamEvent> {
        logEvent(
            "stream_pilot_fallback",
            mapOf(
                "provider" to request.provider.name,
                "model" to request.model,
                "reason" to reason
            )
        )
        return legacyStreamEvents(request)
    }

    companion object {
        const val KEY_SHARED_AI_EXPERIMENTAL = AiPilotFlagUtils.KEY_SHARED_AI_EXPERIMENTAL
        const val KEY_SHARED_AI_STREAMING_PILOT = AiPilotFlagUtils.KEY_SHARED_AI_STREAMING_PILOT

        fun isSharedAiPilotEnabled(
            sharedAiExperimental: Boolean,
            developerModeEnabled: Boolean
        ): Boolean = AiPilotFlagUtils.isSharedAiPilotEnabled(
            sharedAiExperimental = sharedAiExperimental,
            developerModeEnabled = developerModeEnabled
        )

        fun isStreamingPilotEnabled(
            sharedAiExperimental: Boolean,
            developerModeEnabled: Boolean,
            sharedAiStreamingPilot: Boolean
        ): Boolean = AiPilotFlagUtils.isStreamingPilotEnabled(
            sharedAiExperimental = sharedAiExperimental,
            developerModeEnabled = developerModeEnabled,
            sharedAiStreamingPilot = sharedAiStreamingPilot
        )
    }
}
