package com.example.bamachat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProviderPolicyTest {
    @Test
    fun localModeAlwaysRequiresOnDeviceInput() {
        assertTrue(
            VoiceProviderPolicy.requiresOnDeviceInput(
                VoiceMode.LOCAL,
                VoiceInputProvider.OPENAI_TRANSCRIPTION
            )
        )
    }

    @Test
    fun localModeRejectsPublicPiperEndpoint() {
        assertEquals(
            VoiceOutputProvider.ANDROID,
            VoiceProviderPolicy.resolveLocalOutputProvider(
                VoiceOutputProvider.PIPER,
                "https://voice.example.com"
            )
        )
    }

    @Test
    fun localModeAcceptsPrivatePiperEndpoint() {
        assertEquals(
            VoiceOutputProvider.PIPER,
            VoiceProviderPolicy.resolveLocalOutputProvider(
                VoiceOutputProvider.PIPER,
                "http://192.168.178.162:5000"
            )
        )
        assertTrue(VoiceProviderPolicy.isPrivateNetworkEndpoint("http://localhost:5000"))
        assertTrue(VoiceProviderPolicy.isPrivateNetworkEndpoint("http://10.0.0.4:5000"))
        assertFalse(VoiceProviderPolicy.isPrivateNetworkEndpoint("https://8.8.8.8"))
    }
}
