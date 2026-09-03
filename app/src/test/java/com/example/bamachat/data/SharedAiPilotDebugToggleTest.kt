package com.example.bamachat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedAiPilotDebugToggleTest {
    @Test
    fun missingEnabledExtraDoesNotChangePreference() {
        assertNull(
            SharedAiPilotDebugToggle.resolveRequestedEnabled(
                hasEnabledExtra = false,
                enabled = true
            )
        )
    }

    @Test
    fun enabledExtraCanActivatePilotFlag() {
        assertEquals(
            true,
            SharedAiPilotDebugToggle.resolveRequestedEnabled(
                hasEnabledExtra = true,
                enabled = true
            )
        )
    }

    @Test
    fun enabledExtraCanDeactivatePilotFlagImmediately() {
        assertEquals(
            false,
            SharedAiPilotDebugToggle.resolveRequestedEnabled(
                hasEnabledExtra = true,
                enabled = false
            )
        )
    }

    @Test
    fun missingStreamingExtraDoesNotChangeStreamingPreference() {
        assertNull(
            SharedAiPilotDebugToggle.resolveRequestedStreamingEnabled(
                hasStreamingEnabledExtra = false,
                streamingEnabled = true
            )
        )
    }

    @Test
    fun enabledExtraCanDriveStreamingPilotWhenStreamingExtraIsMissing() {
        assertEquals(
            true,
            SharedAiPilotDebugToggle.resolveRequestedStreamingEnabled(
                hasStreamingEnabledExtra = false,
                streamingEnabled = false,
                fallbackEnabled = true
            )
        )
    }

    @Test
    fun streamingExtraCanActivateStreamingPilotFlag() {
        assertEquals(
            true,
            SharedAiPilotDebugToggle.resolveRequestedStreamingEnabled(
                hasStreamingEnabledExtra = true,
                streamingEnabled = true
            )
        )
    }

    @Test
    fun streamingExtraCanDeactivateStreamingPilotFlagImmediately() {
        assertEquals(
            false,
            SharedAiPilotDebugToggle.resolveRequestedStreamingEnabled(
                hasStreamingEnabledExtra = true,
                streamingEnabled = false
            )
        )
    }
}
