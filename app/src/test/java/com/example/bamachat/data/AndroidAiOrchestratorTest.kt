package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.QuickActionSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    private fun ignoreEvent(name: String, params: Map<String, Any?>) {
        Unit
    }

    private fun ignoreError(name: String, error: Throwable?) {
        Unit
    }
}
