package com.example.bamachat.data.provider.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderEndpointBuilderTest {
    @Test
    fun openAiEndpointPreservesVersionAndDoesNotDuplicateSuffix() {
        assertEquals(
            "https://provider.example/v1/chat/completions",
            ProviderEndpointBuilder.openAiChatCompletions("https://provider.example/v1/").toString()
        )
        assertEquals(
            "https://provider.example/v1/chat/completions",
            ProviderEndpointBuilder.openAiChatCompletions("https://provider.example/v1/chat/completions").toString()
        )
    }

    @Test
    fun ollamaEndpointIsBuiltWithoutChangingAuthority() {
        val endpoint = ProviderEndpointBuilder.ollamaChat("http://127.0.0.1:11434/")
        assertEquals("http", endpoint.scheme)
        assertEquals("127.0.0.1", endpoint.host)
        assertEquals(11434, endpoint.port)
        assertEquals("/api/chat", endpoint.encodedPath)
    }

    @Test
    fun discoveryEndpointsPreserveExistingBasePaths() {
        assertEquals(
            "https://provider.example/v1/models",
            ProviderEndpointBuilder.openAiModels("https://provider.example/v1/").toString()
        )
        assertEquals(
            "https://provider.example/openai/v1/models",
            ProviderEndpointBuilder.openAiModels("https://provider.example/openai/v1/").toString()
        )
        assertEquals(
            "http://127.0.0.1:11434/api/tags",
            ProviderEndpointBuilder.ollamaTags("http://127.0.0.1:11434/").toString()
        )
    }

    @Test
    fun discoveryEndpointsDoNotDuplicateTheirSuffix() {
        assertEquals(
            "https://provider.example/v1/models",
            ProviderEndpointBuilder.openAiModels("https://provider.example/v1/models").toString()
        )
        assertEquals(
            "http://localhost:11434/api/tags",
            ProviderEndpointBuilder.ollamaTags("http://localhost:11434/api/tags").toString()
        )
    }

    @Test
    fun queryFragmentAndUserInfoAreRejected() {
        listOf(
            "https://provider.example/v1/?token=value",
            "https://provider.example/v1/#fragment",
            "https://user:password@provider.example/v1/"
        ).forEach { url ->
            assertThrows(ProviderChatException::class.java) {
                ProviderEndpointBuilder.openAiChatCompletions(url)
            }
            assertThrows(ProviderChatException::class.java) {
                ProviderEndpointBuilder.openAiModels(url)
            }
        }
    }
}
