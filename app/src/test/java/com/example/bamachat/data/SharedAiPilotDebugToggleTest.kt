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
}
