package com.example.bamachat.data.provider.discovery

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderModelDiscoveryAdaptersTest {
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
    fun openAiDiscoveryUsesGetModelsAndBearerOnlyWhenRequired() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\" model-a \"},{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}"))
        val secret = generatedSecret()

        val result = OpenAiModelDiscoveryAdapter().discover(
            provider(ProviderConnectionType.OPENAI_COMPATIBLE, ProviderAuthenticationType.BEARER),
            server.url("/v1/").toString(),
            secret
        )

        assertEquals(listOf("model-a", "model-b"), result.models.map { it.modelId })
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer $secret", request.getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun openAiDiscoveryOmitsAuthorizationForLocalNoAuth() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"data\":[]}"))

        OpenAiModelDiscoveryAdapter().discover(
            provider(ProviderConnectionType.OPENAI_COMPATIBLE, ProviderAuthenticationType.NONE_LOCAL_ONLY),
            server.url("/v1/").toString(),
            null
        )

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun ollamaDiscoveryReadsNameThenModelAndNeverAuthorizes() = runBlocking {
        server.enqueue(MockResponse().setBody(
            "{\"models\":[{\"name\":\"llama3.2:latest\"},{\"model\":\"gemma:latest\"},{\"name\":\"\"}]}"
        ))

        val result = OllamaModelDiscoveryAdapter().discover(
            provider(ProviderConnectionType.OLLAMA_LOCAL, ProviderAuthenticationType.NONE_LOCAL_ONLY),
            server.url("/").toString(),
            null
        )

        assertEquals(listOf("llama3.2:latest", "gemma:latest"), result.models.map { it.modelId })
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/tags", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun discoveryLimitsModelsAndRejectsLongIds() = runBlocking {
        val entries = (0..ProviderDiscoveryHttp.MAX_MODELS).joinToString(",") { "{\"id\":\"model-$it\"}" }
        server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"${"x".repeat(ProviderDiscoveryHttp.MAX_MODEL_ID_LENGTH + 1)}\"},$entries]}"))

        val result = OpenAiModelDiscoveryAdapter().discover(
            provider(ProviderConnectionType.OPENAI_COMPATIBLE, ProviderAuthenticationType.NONE_LOCAL_ONLY),
            server.url("/v1/").toString(),
            null
        )

        assertEquals(ProviderDiscoveryHttp.MAX_MODELS, result.models.size)
        assertTrue(result.truncated)
        assertFalse(result.models.any { it.modelId.length > ProviderDiscoveryHttp.MAX_MODEL_ID_LENGTH })
    }

    @Test
    fun malformedEmptyAndUnexpectedResponsesAreTypedWithoutBodyLeak() {
        server.enqueue(MockResponse().setBody("{"))
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody("{\"items\":[]}"))

        assertDiscoveryError(ProviderDiscoveryError.INVALID_JSON)
        assertDiscoveryError(ProviderDiscoveryError.EMPTY_RESPONSE)
        assertDiscoveryError(ProviderDiscoveryError.UNEXPECTED_FORMAT)
    }

    @Test
    fun httpErrorsAndRedirectAreTypedAndNeverRetried() {
        listOf(
            400 to ProviderDiscoveryError.BAD_REQUEST,
            401 to ProviderDiscoveryError.AUTHENTICATION_FAILED,
            403 to ProviderDiscoveryError.AUTHENTICATION_FAILED,
            404 to ProviderDiscoveryError.NOT_FOUND,
            408 to ProviderDiscoveryError.TIMEOUT,
            429 to ProviderDiscoveryError.RATE_LIMITED,
            500 to ProviderDiscoveryError.SERVER_ERROR,
            302 to ProviderDiscoveryError.REDIRECT_BLOCKED
        ).forEach { (status, expected) ->
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Location", server.url("/other")))
            assertDiscoveryError(expected)
        }

        assertEquals(8, server.requestCount)
    }

    @Test
    fun cancellationStopsTheSingleDiscoveryRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch {
            OpenAiModelDiscoveryAdapter().discover(
                provider(ProviderConnectionType.OPENAI_COMPATIBLE, ProviderAuthenticationType.NONE_LOCAL_ONLY),
                server.url("/v1/").toString(),
                null
            )
        }
        while (server.requestCount == 0) delay(10)

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, server.requestCount)
    }

    private fun assertDiscoveryError(expected: ProviderDiscoveryError) {
        val error = assertThrows(ProviderDiscoveryException::class.java) {
            runBlocking {
                OpenAiModelDiscoveryAdapter().discover(
                    provider(ProviderConnectionType.OPENAI_COMPATIBLE, ProviderAuthenticationType.NONE_LOCAL_ONLY),
                    server.url("/v1/").toString(),
                    null
                )
            }
        }
        assertEquals(expected, error.error)
        assertFalse(error.message.orEmpty().contains("not-json"))
    }

    private fun provider(
        connectionType: ProviderConnectionType,
        authenticationType: ProviderAuthenticationType
    ) = ProviderDefinition.create(
        id = ProviderId.newCustom(UUID.fromString("33333333-3333-3333-3333-333333333333")),
        displayName = "Discovery-Test",
        connectionType = connectionType,
        baseUrl = localUrl("/"),
        authenticationType = authenticationType,
        capabilities = ProviderCapabilities(streaming = true, modelDiscovery = true, tools = false, vision = false),
        timeoutMs = 5_000,
        enabled = true,
        builtIn = false,
        localHttpConfirmed = true
    )

    private fun generatedSecret(): String = CharArray(24) { 'k' }.concatToString()

    private fun localUrl(path: String): String = server.url(path).newBuilder().host("127.0.0.1").build().toString()
}
