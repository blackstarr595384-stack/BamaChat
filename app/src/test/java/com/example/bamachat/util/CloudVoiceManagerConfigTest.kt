package com.example.bamachat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudVoiceManagerConfigTest {

    @Test
    fun resolveElevenLabsConfigNormalizesCommonPasteFormats() {
        val config = CloudVoiceManager.resolveElevenLabsConfig(
            apiKey = " xi-api-key: \u201Csk_test_123\u201D ",
            voiceId = " voice_id = ' JBFqnCBsd6RMkjVDRZzb ' ",
            modelId = " model_id = ` eleven_flash_v2_5 ` "
        )

        requireNotNull(config)
        assertEquals(CloudVoiceManager.Provider.ELEVENLABS, config.provider)
        assertEquals("sk_test_123", config.apiKey)
        assertEquals("JBFqnCBsd6RMkjVDRZzb", config.voiceId)
        assertEquals("eleven_flash_v2_5", config.modelId)
    }

    @Test
    fun resolveElevenLabsConfigUsesDefaultsWhenVoiceAndModelAreBlank() {
        val config = CloudVoiceManager.resolveElevenLabsConfig(
            apiKey = "sk_live_abc",
            voiceId = "   ",
            modelId = ""
        )

        requireNotNull(config)
        assertEquals(CloudVoiceManager.Provider.ELEVENLABS, config.provider)
        assertEquals("JBFqnCBsd6RMkjVDRZzb", config.voiceId)
        assertEquals("eleven_flash_v2_5", config.modelId)
    }

    @Test
    fun resolveElevenLabsConfigRejectsMissingApiKey() {
        val config = CloudVoiceManager.resolveElevenLabsConfig(
            apiKey = "Bearer:   ",
            voiceId = "JBFqnCBsd6RMkjVDRZzb",
            modelId = "eleven_multilingual_v2"
        )

        assertNull(config)
    }

    @Test
    fun resolvePiperConfigNormalizesEndpointAndOptionalVoice() {
        val config = CloudVoiceManager.resolvePiperConfig(
            endpoint = " endpoint = ' 192.168.178.162:5000/ ' ",
            voiceName = " voice_name = \"de_DE-thorsten-high\" "
        )

        requireNotNull(config)
        assertEquals(CloudVoiceManager.Provider.PIPER, config.provider)
        assertEquals("http://192.168.178.162:5000", config.piperEndpoint)
        assertEquals("de_DE-thorsten-high", config.piperVoiceName)
    }

    @Test
    fun resolveCloudVoiceConfigSelectsPiperProvider() {
        val config = CloudVoiceManager.resolveCloudVoiceConfig(
            providerValue = "piper",
            elevenLabsApiKey = "",
            elevenLabsVoiceId = "",
            elevenLabsModelId = "",
            piperEndpoint = "localhost:5000",
            piperVoiceName = ""
        )

        requireNotNull(config)
        assertEquals(CloudVoiceManager.Provider.PIPER, config.provider)
        assertEquals("http://localhost:5000", config.piperEndpoint)
        assertEquals("", config.piperVoiceName)
    }
}
