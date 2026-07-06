package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiStreamEventTest {
    @Test
    fun startedEventCarriesProviderAndModel() {
        val event = AiStreamStarted(
            provider = AiProviderId.OPENROUTER,
            model = "openrouter/free"
        )

        assertEquals(AiProviderId.OPENROUTER, event.provider)
        assertEquals("openrouter/free", event.model)
    }

    @Test
    fun deltaEventCarriesTextProviderAndModel() {
        val event = AiStreamDelta(
            text = "hello",
            provider = AiProviderId.OLLAMA,
            model = "llama3"
        )

        assertEquals("hello", event.text)
        assertEquals(AiProviderId.OLLAMA, event.provider)
        assertEquals("llama3", event.model)
    }

    @Test
    fun completedEventExposesFinalResponseMetadata() {
        val response = AiChatResponse(
            provider = AiProviderId.GROQ,
            model = "groq-model",
            message = AiChatMessage(AiChatRole.ASSISTANT, "done")
        )
        val event = AiStreamCompleted(response)

        assertEquals(response, event.response)
        assertEquals(AiProviderId.GROQ, event.provider)
        assertEquals("groq-model", event.model)
    }

    @Test
    fun errorEventCarriesMessageAndOptionalExceptionClass() {
        val event = AiStreamError(
            message = "failed",
            exceptionClass = "IOException",
            provider = AiProviderId.OPENCODE,
            model = "code-model"
        )

        assertEquals("failed", event.message)
        assertEquals("IOException", event.exceptionClass)
        assertEquals(AiProviderId.OPENCODE, event.provider)
        assertEquals("code-model", event.model)
    }

    @Test
    fun finishedEventCanBeProviderAgnostic() {
        val event = AiStreamFinished()

        assertNull(event.provider)
        assertNull(event.model)
    }
}
