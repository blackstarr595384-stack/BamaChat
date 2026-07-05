package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.ExtensionRuntimeDecision
import com.example.bamachat.shared.core.QuickActionSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class AiChatRequestBuilderTest {
    private val builder = AiChatRequestBuilder()

    @Test
    fun buildsSimpleConversationRequest() {
        val messages = listOf(AiChatMessage(AiChatRole.USER, "Hallo"))

        val request = builder.build(
            provider = AiProviderId.OPENROUTER,
            model = "test-model",
            messages = messages
        )

        assertEquals(AiProviderId.OPENROUTER, request.provider)
        assertEquals("test-model", request.model)
        assertEquals(messages, request.messages)
        assertEquals(QuickActionSuggestion.AUTO, request.quickAction)
        assertEquals(1200, request.maxTokens)
        assertEquals(0.7, request.temperature, 0.0)
        assertFalse(request.stream)
    }

    @Test
    fun buildsRequestWithMultipleMessages() {
        val messages = listOf(
            AiChatMessage(AiChatRole.USER, "Hallo"),
            AiChatMessage(AiChatRole.ASSISTANT, "Hi"),
            AiChatMessage(AiChatRole.USER, "Wie geht es weiter?")
        )

        val request = builder.build(
            provider = AiProviderId.OLLAMA,
            model = "llama3",
            messages = messages
        )

        assertEquals(messages, request.messages)
    }

    @Test
    fun allowsEmptyHistory() {
        val request = builder.build(
            provider = AiProviderId.GROQ,
            model = "groq-model",
            messages = emptyList()
        )

        assertEquals(emptyList<AiChatMessage>(), request.messages)
    }

    @Test
    fun carriesSystemPromptThroughRuntimeDecision() {
        val runtimeDecision = ExtensionRuntimeDecision(
            promptContext = "Systemprompt: Antworte kurz.",
            appliedExtensionNames = listOf("System"),
            forceWebResearch = false
        )

        val request = builder.build(
            provider = AiProviderId.OPENROUTER,
            model = "test-model",
            messages = listOf(AiChatMessage(AiChatRole.USER, "Hallo")),
            quickAction = QuickActionSuggestion.PLAN,
            runtimeDecision = runtimeDecision,
            maxTokens = 512,
            temperature = 0.2,
            stream = true
        )

        assertEquals(QuickActionSuggestion.PLAN, request.quickAction)
        assertSame(runtimeDecision, request.runtimeDecision)
        assertEquals(512, request.maxTokens)
        assertEquals(0.2, request.temperature, 0.0)
        assertEquals(true, request.stream)
    }
}
