package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.QuickActionSuggestion
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamDelta
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamEvent
import com.example.bamachat.shared.core.ai.AiStreamFinished
import com.example.bamachat.shared.core.ai.AiStreamStarted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAiOrchestratorTest {
    @Test
    fun sharedAiPilotGateIsOffWhenBothFlagsAreFalse() {
        assertEquals(
            false,
            AndroidAiOrchestrator.isSharedAiPilotEnabled(
                sharedAiExperimental = false,
                developerModeEnabled = false
            )
        )
    }

    @Test
    fun sharedAiPilotGateIsOffWhenOnlyExperimentalFlagIsTrue() {
        assertEquals(
            false,
            AndroidAiOrchestrator.isSharedAiPilotEnabled(
                sharedAiExperimental = true,
                developerModeEnabled = false
            )
        )
    }

    @Test
    fun sharedAiPilotGateIsOffWhenOnlyDeveloperModeIsTrue() {
        assertEquals(
            false,
            AndroidAiOrchestrator.isSharedAiPilotEnabled(
                sharedAiExperimental = false,
                developerModeEnabled = true
            )
        )
    }

    @Test
    fun sharedAiPilotGateIsOnWhenBothFlagsAreTrue() {
        assertEquals(
            true,
            AndroidAiOrchestrator.isSharedAiPilotEnabled(
                sharedAiExperimental = true,
                developerModeEnabled = true
            )
        )
    }

    @Test
    fun streamingPilotGateRequiresAllFlags() {
        assertEquals(false, AndroidAiOrchestrator.isStreamingPilotEnabled(false, false, false))
        assertEquals(false, AndroidAiOrchestrator.isStreamingPilotEnabled(true, false, true))
        assertEquals(false, AndroidAiOrchestrator.isStreamingPilotEnabled(false, true, true))
        assertEquals(false, AndroidAiOrchestrator.isStreamingPilotEnabled(true, true, false))
        assertEquals(true, AndroidAiOrchestrator.isStreamingPilotEnabled(true, true, true))
    }

    @Test
    fun flagOffKeepsLegacyPathByReturningNullWithoutCallingCompletion() = runBlocking {
        var completionCalls = 0
        val orchestrator = AndroidAiOrchestrator(
            isExperimentalEnabled = { false },
            chatCompletion = {
                completionCalls++
                OpenRouterChatResponse(choices = emptyList())
            },
            logEvent = ::ignoreEvent,
            logError = ::ignoreError
        )

        val response = orchestrator.chatOrNull(sampleRequest())

        assertNull(response)
        assertEquals(0, completionCalls)
    }

    @Test
    fun flagOnUsesAiEngineAndOpenRouterProvider() = runBlocking {
        var captured: OpenRouterChatRequest? = null
        val orchestrator = AndroidAiOrchestrator(
            isExperimentalEnabled = { true },
            chatCompletion = { request ->
                captured = request
                OpenRouterChatResponse(
                    choices = listOf(
                        OpenRouterChoice(OpenRouterMessage(role = "assistant", content = "Pilot antwortet."))
                    )
                )
            },
            logEvent = ::ignoreEvent,
            logError = ::ignoreError
        )

        val response = orchestrator.chatOrNull(sampleRequest())

        assertEquals("openrouter/pilot", captured?.model)
        assertEquals(listOf("system", "user"), captured?.messages?.map { it.role })
        assertEquals(listOf("System prompt", "Hallo Pilot"), captured?.messages?.map { it.content })
        assertEquals(512, captured?.maxTokens)
        assertEquals(0.25f, captured?.temperature)
        assertEquals(AiProviderId.OPENROUTER, response?.provider)
        assertEquals("openrouter/pilot", response?.model)
        assertEquals(AiChatRole.ASSISTANT, response?.message?.role)
        assertEquals("Pilot antwortet.", response?.message?.text)
    }

    @Test
    fun flagOnFallsBackToLegacyPathWhenCompletionFails() = runBlocking {
        val orchestrator = AndroidAiOrchestrator(
            isExperimentalEnabled = { true },
            chatCompletion = { throw IllegalStateException("provider down") },
            logEvent = ::ignoreEvent,
            logError = ::ignoreError
        )

        val response = orchestrator.chatOrNull(sampleRequest())

        assertNull(response)
    }

    @Test
    fun flagOnFallsBackToLegacyPathWhenCompletionReturnsEmptyResponse() = runBlocking {
        val orchestrator = AndroidAiOrchestrator(
            isExperimentalEnabled = { true },
            chatCompletion = {
                OpenRouterChatResponse(
                    choices = listOf(OpenRouterChoice(OpenRouterMessage(role = "assistant", content = "")))
                )
            },
            logEvent = ::ignoreEvent,
            logError = ::ignoreError
        )

        val response = orchestrator.chatOrNull(sampleRequest())

        assertNull(response)
    }

    @Test
    fun flagOnDoesNotSwallowCancellationException() {
        val orchestrator = AndroidAiOrchestrator(
            isExperimentalEnabled = { true },
            chatCompletion = { throw CancellationException("cancelled") },
            logEvent = ::ignoreEvent,
            logError = ::ignoreError
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { orchestrator.chatOrNull(sampleRequest()) }
        }
    }

    @Test
    fun streamFlagOffUsesLegacyFallback() = runBlocking {
        var legacyCalls = 0
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = false,
            legacyStreamEvents = {
                legacyCalls++
                flowOf(completedEvent("legacy"))
            }
        )

        val events = orchestrator.streamEvents(sampleRequest()).toList()

        assertEquals(1, legacyCalls)
        assertEquals("legacy", events.completedText())
    }

    @Test
    fun streamFlagOnEmitsSharedAiStreamEvents() = runBlocking {
        val telemetry = mutableListOf<Pair<String, Map<String, Any?>>>()
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = true,
            streamTextChunks = { flowOf("Hel", "lo") },
            legacyStreamEvents = { flowOf(completedEvent("legacy")) },
            logEvent = { name, params -> telemetry.add(name to params) }
        )

        val events = orchestrator.streamEvents(sampleRequest()).toList()

        assertTrue(events.first() is AiStreamStarted)
        assertEquals(listOf("Hel", "lo"), events.filterIsInstance<AiStreamDelta>().map { it.text })
        assertEquals("Hello", events.completedText())
        assertTrue(events.last() is AiStreamFinished)

        val success = telemetry.single { it.first == "stream_pilot_success" }.second
        assertEquals(AiProviderId.OPENROUTER.name, success["provider"])
        assertEquals(2, success["delta_count"])
        assertEquals(5, success["final_length"])
        assertEquals(5, success["final_text_length"])
        assertTrue((success["duration_ms"] as Long) >= 0L)
        assertTrue((success["stream_duration_ms"] as Long) >= 0L)
    }

    @Test
    fun streamSingleDeltaCanSucceed() = runBlocking {
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = true,
            streamTextChunks = { flowOf("single") },
            legacyStreamEvents = { flowOf(completedEvent("legacy")) }
        )

        val events = orchestrator.streamEvents(sampleRequest()).toList()

        assertEquals(listOf("single"), events.filterIsInstance<AiStreamDelta>().map { it.text })
        assertEquals("single", events.completedText())
    }

    @Test
    fun streamProviderErrorFallsBackToLegacyStream() = runBlocking {
        val telemetry = mutableListOf<Pair<String, Map<String, Any?>>>()
        var legacyCalls = 0
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = true,
            streamTextChunks = {
                flow {
                    emit("partial")
                    throw IllegalStateException("provider down")
                }
            },
            legacyStreamEvents = {
                legacyCalls++
                flowOf(completedEvent("legacy after error"))
            },
            logEvent = { name, params -> telemetry.add(name to params) }
        )

        val events = orchestrator.streamEvents(sampleRequest()).toList()

        assertEquals(1, legacyCalls)
        assertEquals("legacy after error", events.completedText())
        assertEquals(emptyList<AiStreamError>(), events.filterIsInstance<AiStreamError>())
        assertEquals("provider_error", telemetry.lastFallbackReason())
    }

    @Test
    fun streamEmptyPilotFallsBackToLegacyStream() = runBlocking {
        val telemetry = mutableListOf<Pair<String, Map<String, Any?>>>()
        var legacyCalls = 0
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = true,
            streamTextChunks = { flowOf() },
            legacyStreamEvents = {
                legacyCalls++
                flowOf(completedEvent("legacy after empty"))
            },
            logEvent = { name, params -> telemetry.add(name to params) }
        )

        val events = orchestrator.streamEvents(sampleRequest()).toList()

        assertEquals(1, legacyCalls)
        assertEquals("legacy after empty", events.completedText())
        assertEquals("empty_response", telemetry.lastFallbackReason())
    }

    @Test
    fun streamCompletedWithoutTextFallsBackToLegacyStream() = runBlocking {
        val telemetry = mutableListOf<Pair<String, Map<String, Any?>>>()
        var legacyCalls = 0
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = true,
            streamTextChunks = { flowOf("   ") },
            legacyStreamEvents = {
                legacyCalls++
                flowOf(completedEvent("legacy after blank"))
            },
            logEvent = { name, params -> telemetry.add(name to params) }
        )

        val events = orchestrator.streamEvents(sampleRequest()).toList()

        assertEquals(1, legacyCalls)
        assertEquals("legacy after blank", events.completedText())
        assertEquals("empty_response", telemetry.lastFallbackReason())
    }

    @Test
    fun streamCancellationIsNotSwallowed() {
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = true,
            streamTextChunks = { flow { throw CancellationException("cancel stream") } },
            legacyStreamEvents = { flowOf(completedEvent("legacy")) }
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { orchestrator.streamEvents(sampleRequest()).toList() }
        }
    }

    @Test
    fun streamMissingLegacyFallbackFailsClearly() {
        val orchestrator = AndroidAiOrchestrator(
            isExperimentalEnabled = { false },
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) },
            isStreamingExperimentalEnabled = { false },
            logEvent = ::ignoreEvent,
            logError = ::ignoreError
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { orchestrator.streamEvents(sampleRequest()).toList() }
        }

        assertTrue(error.message.orEmpty().contains("Legacy stream fallback is not configured"))
    }

    @Test
    fun streamLegacyFallbackExceptionPropagates() {
        val telemetry = mutableListOf<Pair<String, Map<String, Any?>>>()
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = false,
            legacyStreamEvents = {
                flow {
                    throw IllegalStateException("legacy stream failed")
                }
            },
            logEvent = { name, params -> telemetry.add(name to params) }
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { orchestrator.streamEvents(sampleRequest()).toList() }
        }

        assertEquals("legacy stream failed", error.message)
        assertEquals("flag_disabled", telemetry.firstFallbackReason())
        assertEquals("legacy_exception", telemetry.lastFallbackReason())
    }

    @Test
    fun streamFallbackTelemetryIsEmitted() = runBlocking {
        val eventsLog = mutableListOf<String>()
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = true,
            streamTextChunks = {
                flow {
                    throw IllegalStateException("provider down")
                }
            },
            legacyStreamEvents = { flowOf(completedEvent("legacy")) },
            logEvent = { name, _ -> eventsLog.add(name) }
        )

        orchestrator.streamEvents(sampleRequest()).toList()

        assertTrue(eventsLog.contains("stream_pilot_attempt"))
        assertTrue(eventsLog.contains("stream_pilot_error"))
        assertTrue(eventsLog.contains("stream_pilot_fallback"))
    }

    @Test
    fun streamManyDeltasSucceedWithTelemetryCounts() = runBlocking {
        val telemetry = mutableListOf<Pair<String, Map<String, Any?>>>()
        val chunks = (1..100).map { "x" }
        val orchestrator = streamOrchestrator(
            isStreamingEnabled = true,
            streamTextChunks = { flowOf(*chunks.toTypedArray()) },
            legacyStreamEvents = { flowOf(completedEvent("legacy")) },
            logEvent = { name, params -> telemetry.add(name to params) }
        )

        val events = orchestrator.streamEvents(sampleRequest()).toList()

        assertEquals(100, events.filterIsInstance<AiStreamDelta>().size)
        assertEquals("x".repeat(100), events.completedText())
        val success = telemetry.single { it.first == "stream_pilot_success" }.second
        assertEquals(100, success["delta_count"])
        assertEquals(100, success["final_length"])
    }

    private fun sampleRequest(): AiChatRequest {
        return AiChatRequest(
            provider = AiProviderId.OPENROUTER,
            model = "openrouter/pilot",
            messages = listOf(
                AiChatMessage(AiChatRole.SYSTEM, "System prompt"),
                AiChatMessage(AiChatRole.USER, "Hallo Pilot")
            ),
            quickAction = QuickActionSuggestion.AUTO,
            maxTokens = 512,
            temperature = 0.25,
            stream = false
        )
    }

    private fun streamOrchestrator(
        isStreamingEnabled: Boolean,
        streamTextChunks: ((OpenRouterChatRequest) -> kotlinx.coroutines.flow.Flow<String>)? = { flowOf("pilot") },
        legacyStreamEvents: (AiChatRequest) -> kotlinx.coroutines.flow.Flow<AiStreamEvent> = {
            flowOf(completedEvent("legacy"))
        },
        logEvent: (String, Map<String, Any?>) -> Unit = ::ignoreEvent
    ): AndroidAiOrchestrator {
        return AndroidAiOrchestrator(
            isExperimentalEnabled = { false },
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) },
            isStreamingExperimentalEnabled = { isStreamingEnabled },
            streamTextChunks = streamTextChunks,
            legacyStreamEvents = legacyStreamEvents,
            logEvent = logEvent,
            logError = ::ignoreError
        )
    }

    private fun completedEvent(text: String): AiStreamCompleted {
        return AiStreamCompleted(
            AiChatResponse(
                provider = AiProviderId.OPENROUTER,
                model = "openrouter/pilot",
                message = AiChatMessage(AiChatRole.ASSISTANT, text)
            )
        )
    }

    private fun List<AiStreamEvent>.completedText(): String? {
        return filterIsInstance<AiStreamCompleted>().single().response.message.text
    }

    private fun List<Pair<String, Map<String, Any?>>>.firstFallbackReason(): String? {
        return first { it.first == "stream_pilot_fallback" }.second["fallback_reason"] as? String
    }

    private fun List<Pair<String, Map<String, Any?>>>.lastFallbackReason(): String? {
        return last { it.first == "stream_pilot_fallback" }.second["fallback_reason"] as? String
    }

    private fun ignoreEvent(name: String, params: Map<String, Any?>) {
        Unit
    }

    private fun ignoreError(name: String, error: Throwable?) {
        Unit
    }
}
