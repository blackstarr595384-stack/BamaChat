package com.example.bamachat.ui.provider

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderRepositoryError
import com.example.bamachat.data.provider.ProviderRepositoryException
import com.example.bamachat.data.provider.customDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPresentationTest {
    @Test
    fun technicalEnumsMapToGermanLabels() {
        assertEquals("OpenAI-kompatibel", ProviderConnectionType.OPENAI_COMPATIBLE.displayName())
        assertEquals("Ollama lokal", ProviderConnectionType.OLLAMA_LOCAL.displayName())
        assertEquals("Bearer-Token", ProviderAuthenticationType.BEARER.displayName())
        assertEquals("Keine Authentifizierung", ProviderAuthenticationType.NONE_LOCAL_ONLY.displayName())
    }

    @Test
    fun secretSummaryNeverContainsASecretValue() {
        val summary = customDefinition(hasSecret = true).secretSummary()

        assertEquals("API-Key gespeichert", summary)
        assertFalse(summary.contains("Bearer", ignoreCase = true))
    }

    @Test
    fun cleanupFailureGetsSafeRecoverableText() {
        val message = ProviderRepositoryException(
            ProviderRepositoryError.CLEANUP_REQUIRED,
            "internal storage details"
        ).toProviderUserMessage()

        assertTrue(message.contains("Bereinigung"))
        assertFalse(message.contains("internal"))
    }
}
