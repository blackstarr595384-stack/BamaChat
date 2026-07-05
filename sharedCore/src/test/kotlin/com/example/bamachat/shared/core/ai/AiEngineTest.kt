package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.QuickActionSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEngineTest {
    @Test
    fun registryRegistersProvider() {
        val provider = FakeProvider(AiProviderId.OPENROUTER)
        val registry = AiProviderRegistry().register(provider)

        assertTrue(registry.contains(AiProviderId.OPENROUTER))
        assertSame(provider, registry.provider(AiProviderId.OPENROUTER))
        assertEquals(listOf(provider), registry.providers())
    }

    @Test
    fun registryRemovesProvider() {
        val provider = FakeProvider(AiProviderId.OPENROUTER)
        val registry = AiProviderRegistry(listOf(provider))

        assertSame(provider, registry.unregister(AiProviderId.OPENROUTER))
        assertFalse(registry.contains(AiProviderId.OPENROUTER))
    }

    @Test
    fun registryReturnsDefaultProvider() {
        val openRouter = FakeProvider(AiProviderId.OPENROUTER)
        val ollama = FakeProvider(AiProviderId.OLLAMA)
        val registry = AiProviderRegistry(listOf(openRouter, ollama))

        assertSame(openRouter, registry.defaultProvider())
    }

    @Test
    fun chatDelegatesThroughRegistry() = runTest {
        val provider = FakeProvider(AiProviderId.OLLAMA)
        val engine = AiEngine(AiProviderRegistry(listOf(provider)))
        val response = engine.chat(request(AiProviderId.OLLAMA))

        assertEquals(AiProviderId.OLLAMA, response.provider)
        assertEquals("reply from OLLAMA", response.message.text)
    }

    @Test
    fun streamRejectsProviderWithoutStreamingSupport() {
        val engine = AiEngine(
            AiProviderRegistry(listOf(FakeProvider(AiProviderId.OPENROUTER, streaming = false)))
        )

        assertThrows(IllegalStateException::class.java) {
            engine.stream(request(AiProviderId.OPENROUTER))
        }
    }

    @Test
    fun supportsStreamingReflectsProviderCapability() {
        val engine = AiEngine(
            AiProviderRegistry(listOf(
                FakeProvider(AiProviderId.OPENROUTER, streaming = true),
                FakeProvider(AiProviderId.OLLAMA, streaming = false)
            ))
        )

        assertTrue(engine.supportsStreaming(AiProviderId.OPENROUTER))
        assertFalse(engine.supportsStreaming(AiProviderId.OLLAMA))
    }

    private fun request(providerId: AiProviderId): AiChatRequest {
        return AiChatRequest(
            provider = providerId,
            model = "test-model",
            messages = listOf(AiChatMessage(AiChatRole.USER, "hello")),
            quickAction = QuickActionSuggestion.AUTO
        )
    }

    private class FakeProvider(
        private val providerId: AiProviderId,
        private val streaming: Boolean = true
    ) : AiProvider {
        override fun id(): AiProviderId = providerId

        override suspend fun chat(request: AiChatRequest): AiChatResponse {
            return AiChatResponse(
                provider = request.provider,
                model = request.model,
                message = AiChatMessage(
                    role = AiChatRole.ASSISTANT,
                    text = "reply from ${request.provider}"
                )
            )
        }

        override fun stream(request: AiChatRequest): Flow<AiChatResponse> = flowOf(
            AiChatResponse(
                provider = request.provider,
                model = request.model,
                message = AiChatMessage(AiChatRole.ASSISTANT, "stream reply")
            )
        )

        override fun supportsStreaming(): Boolean = streaming
    }
}
