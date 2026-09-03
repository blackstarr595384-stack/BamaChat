package com.example.bamachat.shared.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPilotFlagUtilsTest {
    @Test
    fun sharedPilotRequiresExperimentalAndDeveloperMode() {
        assertFalse(AiPilotFlagUtils.isSharedAiPilotEnabled(false, false))
        assertFalse(AiPilotFlagUtils.isSharedAiPilotEnabled(true, false))
        assertFalse(AiPilotFlagUtils.isSharedAiPilotEnabled(false, true))
        assertTrue(AiPilotFlagUtils.isSharedAiPilotEnabled(true, true))
    }

    @Test
    fun streamingPilotRequiresAllThreeFlags() {
        assertFalse(AiPilotFlagUtils.isStreamingPilotEnabled(false, false, false))
        assertFalse(AiPilotFlagUtils.isStreamingPilotEnabled(true, false, true))
        assertFalse(AiPilotFlagUtils.isStreamingPilotEnabled(false, true, true))
        assertFalse(AiPilotFlagUtils.isStreamingPilotEnabled(true, true, false))
        assertTrue(AiPilotFlagUtils.isStreamingPilotEnabled(true, true, true))
    }
}
