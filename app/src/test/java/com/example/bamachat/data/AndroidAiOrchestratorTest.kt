package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.QuickActionSuggestion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAiOrchestratorTest {
    @Test
    fun flagOffKeepsLegacyPathByReturningNullWithoutCallingCompletion() = runBlocking {
        var completionCalls = 0
        val orchestrator = AndroidAiOrchestrator(
            isExperimentalEnabled = { false },
            chatCompletion = {
                completionCalls++
                OpenRouterChatResponse(choices = emptyList())
            }
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
            }
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
            chatCompletion = { throw IllegalStateException("provider down") }
        )

        val response = orchestrator.chatOrNull(sampleRequest())

        assertNull(response)
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
}
