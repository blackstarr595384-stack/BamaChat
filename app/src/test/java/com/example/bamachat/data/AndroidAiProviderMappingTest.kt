package com.example.bamachat.data

import com.example.bamachat.shared.core.AiProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAiProviderMappingTest {
    @Test
    fun mapsAndroidProvidersToSharedProviderIds() {
        assertEquals(AiProviderId.OPENROUTER, ApiClient.Provider.OPENROUTER.toAiProviderId())
        assertEquals(AiProviderId.GROQ, ApiClient.Provider.GROQ.toAiProviderId())
        assertEquals(AiProviderId.CEREBRAS, ApiClient.Provider.CEREBRAS.toAiProviderId())
        assertEquals(AiProviderId.TOGETHER, ApiClient.Provider.TOGETHER.toAiProviderId())
        assertEquals(AiProviderId.OPENCODE, ApiClient.Provider.OPENCODE.toAiProviderId())
    }
}
