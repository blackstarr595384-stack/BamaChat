package com.example.bamachat.service

import com.example.bamachat.data.ApiClient
import com.example.bamachat.shared.core.AiProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFacingAiErrorMapperTest {
    @Test
    fun legacyEmptyStreamMapsToGermanOpenRouterMessage() {
        val result = UserFacingAiErrorMapper.terminal(
            AiProviderId.OPENROUTER,
            "Legacy stream returned an empty response"
        )

        assertEquals(AiFailureCategory.EMPTY_RESPONSE, result.category)
        assertEquals(
            "OpenRouter hat keine Antwort geliefert. Bitte versuche es erneut oder wähle einen anderen Anbieter.",
            result.message
        )
        assertFalse(result.message.contains("Legacy", ignoreCase = true))
    }

    @Test
    fun fallbackProgressIsInformationalAndContentFree() {
        val message = UserFacingAiErrorMapper.fallbackInProgress(ApiClient.Provider.OPENROUTER)

        assertEquals(
            "OpenRouter antwortet gerade nicht. BamaFlow versucht einen anderen Anbieter.",
            message
        )
    }

    @Test
    fun internalExceptionTextIsNeverReturned() {
        val internal = "IllegalStateException at endpoint with raw body"
        val result = UserFacingAiErrorMapper.terminal(AiProviderId.OPENROUTER, internal)

        assertFalse(result.message.contains("IllegalStateException"))
        assertFalse(result.message.contains("endpoint", ignoreCase = true))
        assertFalse(result.message.contains("body", ignoreCase = true))
    }
}
