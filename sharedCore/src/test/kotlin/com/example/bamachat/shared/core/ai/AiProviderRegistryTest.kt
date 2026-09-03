package com.example.bamachat.shared.core.ai

import com.example.bamachat.shared.core.AiProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRegistryTest {
    @Test
    fun registerStoresProviderById() {
        val provider = FakeAiProvider(AiProviderId.OPENROUTER)
        val registry = AiProviderRegistry().register(provider)

        assertTrue(registry.contains(AiProviderId.OPENROUTER))
        assertSame(provider, registry.provider(AiProviderId.OPENROUTER))
        assertEquals(listOf(provider), registry.providers())
    }

    @Test
    fun providerThrowsForUnknownProvider() {
        val registry = AiProviderRegistry()

        assertThrows(IllegalStateException::class.java) {
            registry.provider(AiProviderId.OPENROUTER)
        }
    }

    @Test
    fun unregisterRemovesProvider() {
        val provider = FakeAiProvider(AiProviderId.OPENROUTER)
        val registry = AiProviderRegistry(listOf(provider))

        assertSame(provider, registry.unregister(AiProviderId.OPENROUTER))
        assertFalse(registry.contains(AiProviderId.OPENROUTER))
    }

    @Test
    fun defaultProviderUsesExplicitDefault() {
        val openRouter = FakeAiProvider(AiProviderId.OPENROUTER)
        val ollama = FakeAiProvider(AiProviderId.OLLAMA)
        val registry = AiProviderRegistry(
            providers = listOf(openRouter, ollama),
            defaultProviderId = AiProviderId.OLLAMA
        )

        assertSame(ollama, registry.defaultProvider())
    }

    @Test
    fun defaultProviderFallsBackToFirstRegisteredProvider() {
        val openRouter = FakeAiProvider(AiProviderId.OPENROUTER)
        val ollama = FakeAiProvider(AiProviderId.OLLAMA)
        val registry = AiProviderRegistry(listOf(openRouter, ollama))

        assertSame(openRouter, registry.defaultProvider())
    }

    @Test
    fun unregisterDefaultSwitchesToNextRegisteredProvider() {
        val openRouter = FakeAiProvider(AiProviderId.OPENROUTER)
        val ollama = FakeAiProvider(AiProviderId.OLLAMA)
        val registry = AiProviderRegistry(listOf(openRouter, ollama))

        registry.unregister(AiProviderId.OPENROUTER)

        assertSame(ollama, registry.defaultProvider())
    }
}
