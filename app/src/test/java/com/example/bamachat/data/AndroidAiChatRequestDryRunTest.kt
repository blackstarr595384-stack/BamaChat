package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAiChatRequestDryRunTest {
    @Test
    fun keepsExistingOpenRouterHistoryUnchanged() {
        val history = listOf(
            OpenRouterMessage(role = "user", content = "Hallo"),
            OpenRouterMessage(role = "assistant", content = "Hi")
        )

        history.toAiChatRequestForValidation(
            provider = AiProviderId.OPENROUTER,
            model = "test-model"
        )

        assertEquals("user", history[0].role)
        assertEquals("Hallo", history[0].content)
        assertEquals("assistant", history[1].role)
        assertEquals("Hi", history[1].content)
    }

    @Test
    fun mapsSameRolesAndContentIntoAiChatRequest() {
        val history = listOf(
            OpenRouterMessage(role = "user", content = "Hallo"),
            OpenRouterMessage(role = "assistant", content = "Hi")
        )

        val request = history.toAiChatRequestForValidation(
            provider = AiProviderId.OPENROUTER,
            model = "test-model"
        )

        assertEquals(AiProviderId.OPENROUTER, request.provider)
        assertEquals("test-model", request.model)
        assertEquals(AiChatRole.USER, request.messages[0].role)
        assertEquals("Hallo", request.messages[0].text)
        assertEquals(AiChatRole.ASSISTANT, request.messages[1].role)
        assertEquals("Hi", request.messages[1].text)
    }

    @Test
    fun mapsSystemMessageIntoAiChatRequest() {
        val history = listOf(OpenRouterMessage(role = "system", content = "Antworte kurz."))

        val request = history.toAiChatRequestForValidation(
            provider = AiProviderId.OPENROUTER,
            model = "test-model"
        )

        assertEquals(AiChatRole.SYSTEM, request.messages.single().role)
        assertEquals("Antworte kurz.", request.messages.single().text)
    }

    @Test
    fun mapsSystemUserAndAssistantHistoryInOrder() {
        val history = listOf(
            OpenRouterMessage(role = "system", content = "Du bist hilfreich."),
            OpenRouterMessage(role = "user", content = "Hallo"),
            OpenRouterMessage(role = "assistant", content = "Hi")
        )

        val request = history.toAiChatRequestForValidation(
            provider = AiProviderId.OPENROUTER,
            model = "test-model"
        )

        assertEquals(3, request.messages.size)
        assertEquals(AiChatRole.SYSTEM, request.messages[0].role)
        assertEquals("Du bist hilfreich.", request.messages[0].text)
        assertEquals(AiChatRole.USER, request.messages[1].role)
        assertEquals("Hallo", request.messages[1].text)
        assertEquals(AiChatRole.ASSISTANT, request.messages[2].role)
        assertEquals("Hi", request.messages[2].text)
    }

    @Test
    fun preservesBlankContentLikeExistingHistory() {
        val history = listOf(OpenRouterMessage(role = "user", content = ""))

        val request = history.toAiChatRequestForValidation(
            provider = AiProviderId.OPENROUTER,
            model = "test-model"
        )

        assertEquals("", history[0].content)
        assertEquals("", request.messages.single().text)
    }

    @Test
    fun skipsMessagesThatCannotBecomeSharedChatMessages() {
        val history = listOf(
            OpenRouterMessage(role = "tool", content = "tool output"),
            OpenRouterMessage(role = "assistant", content = null),
            OpenRouterMessage(role = "user", content = "valid")
        )

        val request = history.toAiChatRequestForValidation(
            provider = AiProviderId.OPENROUTER,
            model = "test-model"
        )

        assertEquals(3, history.size)
        assertEquals(1, request.messages.size)
        assertEquals(AiChatRole.USER, request.messages.single().role)
        assertEquals("valid", request.messages.single().text)
    }

    @Test
    fun deliberatelySkipsToolMessages() {
        val history = listOf(
            OpenRouterMessage(role = "system", content = "System"),
            OpenRouterMessage(role = "tool", content = "tool output"),
            OpenRouterMessage(role = "user", content = "valid")
        )

        val request = history.toAiChatRequestForValidation(
            provider = AiProviderId.OPENROUTER,
            model = "test-model",
            stream = true
        )

        assertEquals(2, request.messages.size)
        assertEquals(AiChatRole.SYSTEM, request.messages[0].role)
        assertEquals(AiChatRole.USER, request.messages[1].role)
        assertEquals(true, request.stream)
    }

    @Test
    fun normalStreamingHistoryRemainsMappable() {
        val history = listOf(
            OpenRouterMessage(role = "system", content = "Runtime context"),
            OpenRouterMessage(role = "user", content = "Frage"),
            OpenRouterMessage(role = "assistant", content = "Antwort"),
            OpenRouterMessage(role = "user", content = "Folgefrage")
        )

        val request = history.toAiChatRequestForValidation(
            provider = AiProviderId.OPENROUTER,
            model = "test-model",
            stream = true
        )

        assertEquals(AiProviderId.OPENROUTER, request.provider)
        assertEquals("test-model", request.model)
        assertEquals(true, request.stream)
        assertEquals(4, request.messages.size)
        assertEquals(listOf(AiChatRole.SYSTEM, AiChatRole.USER, AiChatRole.ASSISTANT, AiChatRole.USER), request.messages.map { it.role })
    }
}
