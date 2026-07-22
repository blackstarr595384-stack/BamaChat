package com.example.bamachat.ui.provider

import com.example.bamachat.data.provider.discovery.ProviderDiscoveryError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDiscoveryPresentationTest {
    @Test
    fun securityRelevantErrorsUseSafeGermanMessages() {
        val messages = ProviderDiscoveryError.entries.map(ProviderDiscoveryPresentation::errorMessage)

        assertTrue(messages.contains("Für diesen Anbieter fehlt ein API-Schlüssel."))
        assertTrue(messages.contains("Der Anbieter hat den API-Schlüssel abgelehnt."))
        assertTrue(messages.contains("Die Weiterleitung wurde aus Sicherheitsgründen blockiert."))
        assertTrue(messages.contains("Die Anbieteradresse ist aus Sicherheitsgründen nicht erlaubt."))
        assertTrue(messages.contains("Vorgang abgebrochen."))
    }

    @Test
    fun messagesContainNoTechnicalOrSensitiveDetails() {
        val combined = ProviderDiscoveryError.entries
            .joinToString(" ", transform = ProviderDiscoveryPresentation::errorMessage)
            .lowercase()

        listOf("https://", "http://", "bearer ", "exception", "stacktrace", "response body")
            .forEach { forbidden -> assertFalse(combined.contains(forbidden)) }
    }
}
