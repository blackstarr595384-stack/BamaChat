package com.example.bamachat.data

import com.example.bamachat.shared.core.AiProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAiProviderRegistryFactoryTest {
    @Test
    fun createsRegistryWithOpenRouterProviderRegistered() {
        var chatWasCalled = false
        val openRouterProvider = AndroidOpenRouterAiProvider {
            chatWasCalled = true
            OpenRouterChatResponse(choices = emptyList())
        }

        val registry = AndroidAiProviderRegistryFactory.create(openRouterProvider)

        assertTrue(registry.contains(AiProviderId.OPENROUTER))
        assertSame(openRouterProvider, registry.provider(AiProviderId.OPENROUTER))
        assertEquals(AiProviderId.OPENROUTER, registry.defaultProvider().id())
        assertFalse("Factory registration must not invoke chat.", chatWasCalled)
    }
}
