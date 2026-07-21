package com.example.bamachat.data.provider.chat

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderCapabilities
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
import java.util.UUID
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderChatAdaptersTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun openAiNonStreamingSendsOneAuthorizedRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"choices\":[{\"message\":{\"content\":\"Hallo\"}}]}"))
        val secret = CharArray(24) { 'x' }.concatToString()
        val chunks = mutableListOf<String>()

        val result = OpenAiCompatibleChatAdapter().execute(
            openAiProvider(streaming = false), server.url("/v1/").toString(), "model-a", secret,
            listOf(ProviderChatMessage("user", "Test"))
        ) { chunks += it.text }

        assertEquals("Hallo", result.text)
        assertEquals(listOf("Hallo"), chunks)
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer $secret", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"model\":\"model-a\""))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun openAiSseHandlesChunksAndDone() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hal\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\n" +
                "data: [DONE]\n\n"
        ))
        val chunks = mutableListOf<String>()

        val result = OpenAiCompatibleChatAdapter().execute(
            openAiProvider(streaming = true), server.url("/v1/").toString(), "model-a", generatedSecret(),
            listOf(ProviderChatMessage("user", "Test"))
        ) { chunks += it.text }

        assertEquals("Hallo", result.text)
        assertEquals(listOf("Hal", "lo"), chunks)
    }

    @Test
    fun redirectAndAuthenticationErrorsAreTypedAndDoNotRetry() {
        listOf(
            302 to ProviderChatError.REDIRECT_BLOCKED,
            400 to ProviderChatError.BAD_REQUEST,
            401 to ProviderChatError.AUTHENTICATION_FAILED,
            404 to ProviderChatError.NOT_FOUND,
            429 to ProviderChatError.RATE_LIMITED,
            500 to ProviderChatError.HTTP_SERVER_ERROR
        ).forEach { (status, expected) ->
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Location", server.url("/other")))
            val error = assertThrows(ProviderChatException::class.java) {
                runBlocking {
                    OpenAiCompatibleChatAdapter().execute(
                        openAiProvider(false), server.url("/v1/").toString(), "model-a", generatedSecret(),
                        listOf(ProviderChatMessage("user", "Test"))
                    ) { }
                }
            }
            assertEquals(expected, error.error)
        }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun emptyAndMalformedResponsesAreRejectedWithoutLeakingBody() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"choices\":[]}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        val empty = adapterFailure()
        val malformed = adapterFailure()

        assertEquals(ProviderChatError.EMPTY_RESPONSE, empty.error)
        assertEquals(ProviderChatError.INVALID_RESPONSE, malformed.error)
        assertFalse(empty.message.orEmpty().contains("choices"))
        assertFalse(malformed.message.orEmpty().contains("not-json"))
    }

    @Test
    fun oversizedResponseIsRejectedBeforeParsing() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("x".repeat(ProviderHttpSupport.MAX_RESPONSE_CHARS + 1)))

        val error = adapterFailure()

        assertEquals(ProviderChatError.RESPONSE_TOO_LARGE, error.error)
    }

    @Test
    fun cancellationStopsTheSingleInFlightRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch {
            OpenAiCompatibleChatAdapter().execute(
                openAiProvider(false), server.url("/v1/").toString(), "model-a", generatedSecret(),
                listOf(ProviderChatMessage("user", "Test"))
            ) { }
        }
        while (server.requestCount == 0) delay(10)

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun ollamaStreamingUsesNdjsonAndNeverAddsAuthorization() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            "{\"message\":{\"content\":\"Gu\"},\"done\":false}\n" +
                "{\"message\":{\"content\":\"ten Tag\"},\"done\":true}\n"
        ))
        val chunks = mutableListOf<String>()

        val result = OllamaLocalChatAdapter().execute(
            ollamaProvider(streaming = true), server.url("/").toString(), "local-model", null,
            listOf(ProviderChatMessage("user", "Test"))
        ) { chunks += it.text }

        assertEquals("Guten Tag", result.text)
        assertEquals("/api/chat", server.takeRequest().path)
        assertEquals(listOf("Gu", "ten Tag"), chunks)
        val request = server.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(null, request)
    }

    @Test
    fun ollamaRequestHasNoAuthorizationHeader() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"message\":{\"content\":\"OK\"}}"))
        OllamaLocalChatAdapter().execute(
            ollamaProvider(streaming = false), server.url("/").toString(), "local-model", null,
            listOf(ProviderChatMessage("user", "Test"))
        ) { }
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun malformedOllamaStreamIsRejectedSafely() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json\n"))

        val error = assertThrows(ProviderChatException::class.java) {
            runBlocking {
                OllamaLocalChatAdapter().execute(
                    ollamaProvider(streaming = true), server.url("/").toString(), "local-model", null,
                    listOf(ProviderChatMessage("user", "Test"))
                ) { }
            }
        }

        assertEquals(ProviderChatError.INVALID_RESPONSE, error.error)
        assertFalse(error.message.orEmpty().contains("not-json"))
    }

    private fun adapterFailure(): ProviderChatException = assertThrows(ProviderChatException::class.java) {
        runBlocking {
            OpenAiCompatibleChatAdapter().execute(
                openAiProvider(false), server.url("/v1/").toString(), "model-a", generatedSecret(),
                listOf(ProviderChatMessage("user", "Test"))
            ) { }
        }
    }

    private fun openAiProvider(streaming: Boolean) = provider(
        ProviderConnectionType.OPENAI_COMPATIBLE,
        ProviderAuthenticationType.BEARER,
        streaming
    )

    private fun ollamaProvider(streaming: Boolean) = provider(
        ProviderConnectionType.OLLAMA_LOCAL,
        ProviderAuthenticationType.NONE_LOCAL_ONLY,
        streaming
    )

    private fun provider(
        connectionType: ProviderConnectionType,
        authenticationType: ProviderAuthenticationType,
        streaming: Boolean
    ) = ProviderDefinition.create(
        id = ProviderId.newCustom(UUID.fromString("22222222-2222-2222-2222-222222222222")),
        displayName = "Testanbieter",
        connectionType = connectionType,
        baseUrl = server.url("/").toString(),
        authenticationType = authenticationType,
        capabilities = ProviderCapabilities(streaming, false, false, false),
        timeoutMs = 5_000,
        localHttpConfirmed = true
    )

    private fun generatedSecret(): String = CharArray(24) { 'y' }.concatToString()
}
