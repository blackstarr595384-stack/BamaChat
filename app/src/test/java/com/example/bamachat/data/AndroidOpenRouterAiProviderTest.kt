package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.QuickActionSuggestion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidOpenRouterAiProviderTest {
    @Test
    fun exposesOpenRouterProviderIdAndNoStreamingSupportYet() {
        val provider = AndroidOpenRouterAiProvider { OpenRouterChatResponse(choices = emptyList()) }

        assertEquals(AiProviderId.OPENROUTER, provider.id())
        assertFalse(provider.supportsStreaming())
    }

    @Test
    fun buildsExistingOpenRouterRequestDtoForChat() = runBlocking {
        var captured: OpenRouterChatRequest? = null
        val provider = AndroidOpenRouterAiProvider { request ->
            captured = request
            OpenRouterChatResponse(
                choices = listOf(OpenRouterChoice(OpenRouterMessage(role = "assistant", content = "Hallo")))
            )
        }

        val response = provider.chat(sampleRequest())

        assertEquals("test-model", captured?.model)
        assertEquals(false, captured?.stream)
        assertEquals(321, captured?.maxTokens)
        assertEquals(0.2f, captured?.temperature)
        assertEquals(listOf("system", "user"), captured?.messages?.map { it.role })
        assertEquals(listOf("Kurz antworten.", "Hallo"), captured?.messages?.map { it.content })
        assertEquals(AiProviderId.OPENROUTER, response.provider)
        assertEquals("test-model", response.model)
        assertEquals(AiChatRole.ASSISTANT, response.message.role)
        assertEquals("Hallo", response.message.text)
    }

    @Test
    fun rejectsNonOpenRouterRequests() {
        val provider = AndroidOpenRouterAiProvider { OpenRouterChatResponse(choices = emptyList()) }
        val request = sampleRequest(provider = AiProviderId.GROQ)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { provider.chat(request) }
        }
    }

    @Test
    fun streamIsExplicitlyUnsupportedForSkeleton() {
        val provider = AndroidOpenRouterAiProvider { OpenRouterChatResponse(choices = emptyList()) }

        assertThrows(UnsupportedOperationException::class.java) {
            provider.stream(sampleRequest())
        }
    }

    private fun sampleRequest(provider: AiProviderId = AiProviderId.OPENROUTER): AiChatRequest {
        return AiChatRequest(
            provider = provider,
            model = "test-model",
            messages = listOf(
                AiChatMessage(AiChatRole.SYSTEM, "Kurz antworten."),
                AiChatMessage(AiChatRole.USER, "Hallo")
            ),
            quickAction = QuickActionSuggestion.AUTO,
            maxTokens = 321,
            temperature = 0.2,
            stream = false
        )
    }
}
