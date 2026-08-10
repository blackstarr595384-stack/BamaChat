package com.example.bamachat.service

import com.example.bamachat.util.UserErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatErrorRecoveryPolicyTest {
    @Test
    fun buildErrorDisplayText_formatsMessageAsExpected() {
        val message = UserErrorMessage(
            code = "NETWORK_ERROR",
            title = "Netzwerkfehler",
            description = "Keine Verbindung",
            suggestion = "Bitte WLAN prüfen",
            actionLabel = "Erneut versuchen",
            isRetryable = true
        )

        val formatted = ChatErrorRecoveryPolicy.buildErrorDisplayText(message)

        assertEquals("Netzwerkfehler: Keine Verbindung\n\n💡 Bitte WLAN prüfen", formatted)
    }

    @Test
    fun shouldEnableRetry_returnsTrueOnlyForRetryableNonBlankPendingMessage() {
        assertTrue(ChatErrorRecoveryPolicy.shouldEnableRetry(true, "Hallo"))
        assertFalse(ChatErrorRecoveryPolicy.shouldEnableRetry(true, "   "))
        assertFalse(ChatErrorRecoveryPolicy.shouldEnableRetry(true, null))
        assertFalse(ChatErrorRecoveryPolicy.shouldEnableRetry(false, "Hallo"))
    }

    @Test
    fun isValidRetryCandidate_validatesNullBlankAndNormalMessage() {
        assertFalse(ChatErrorRecoveryPolicy.isValidRetryCandidate(null))
        assertFalse(ChatErrorRecoveryPolicy.isValidRetryCandidate(""))
        assertFalse(ChatErrorRecoveryPolicy.isValidRetryCandidate("   "))
        assertTrue(ChatErrorRecoveryPolicy.isValidRetryCandidate("Neue Anfrage"))
    }
}
