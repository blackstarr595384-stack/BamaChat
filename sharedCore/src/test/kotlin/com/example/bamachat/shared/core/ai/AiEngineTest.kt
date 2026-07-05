package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiProviderId
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEngineTest {
    @Test
    fun chatDelegatesThroughRegistry() = runTest {
        val provider = FakeAiProvider(AiProviderId.OLLAMA)
        val engine = AiEngine(AiProviderRegistry(listOf(provider)))
        val response = engine.chat(aiTestRequest(AiProviderId.OLLAMA))

        assertEquals(AiProviderId.OLLAMA, response.provider)
        assertEquals("reply from OLLAMA", response.message.text)
    }

    @Test
    fun streamRejectsProviderWithoutStreamingSupport() {
        val engine = AiEngine(
            AiProviderRegistry(listOf(FakeAiProvider(AiProviderId.OPENROUTER, streaming = false)))
        )

        assertThrows(IllegalStateException::class.java) {
            engine.stream(aiTestRequest(AiProviderId.OPENROUTER))
        }
    }

    @Test
    fun streamDelegatesToStreamingProvider() = runTest {
        val engine = AiEngine(
            AiProviderRegistry(listOf(FakeAiProvider(AiProviderId.OPENROUTER, streaming = true)))
        )

        val response = engine.stream(aiTestRequest(AiProviderId.OPENROUTER)).single()

        assertEquals(AiProviderId.OPENROUTER, response.provider)
        assertEquals("stream reply", response.message.text)
    }

    @Test
    fun supportsStreamingReflectsProviderCapability() {
        val engine = AiEngine(
            AiProviderRegistry(listOf(
                FakeAiProvider(AiProviderId.OPENROUTER, streaming = true),
                FakeAiProvider(AiProviderId.OLLAMA, streaming = false)
            ))
        )

        assertTrue(engine.supportsStreaming(AiProviderId.OPENROUTER))
        assertFalse(engine.supportsStreaming(AiProviderId.OLLAMA))
    }
}
