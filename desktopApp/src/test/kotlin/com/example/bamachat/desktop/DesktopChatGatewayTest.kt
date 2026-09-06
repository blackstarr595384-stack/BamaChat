package com.example.bamachat.desktop

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.QuickActionSuggestion
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamDelta
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamFinished
import com.example.bamachat.shared.core.ai.AiStreamStarted
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopChatGatewayTest {
    @Test
    fun openRouterSseEmitsOrderedDeltasAndOneCompletedResponse() = runBlocking {
        val transport = QueueDesktopChatTransport(
            responsePlan(
                body = """

                    : keepalive
                    data: {"choices":[{"delta":{"role":"assistant"}}]}

                    data: {"choices":[{"delta":{"content":"Hallo "}}]}

                    data: {"choices":[{"delta":{"content":"Welt"}}]}

                    data: [DONE]

                """.trimIndent()
            )
        )
        val adapter = openRouterAdapter(transport)

        val events = adapter.streamEvents(request(AiProviderId.OPENROUTER)).toList()

        assertIs<AiStreamStarted>(events.first())
        assertIs<AiStreamFinished>(events.last())
        assertEquals(
            listOf("Hallo ", "Welt"),
            events.filterIsInstance<AiStreamDelta>().map { it.text }
        )
        val completed = events.filterIsInstance<AiStreamCompleted>().single()
        assertEquals("Hallo Welt", completed.response.message.text)
        assertEquals(1, transport.requests.size)
        assertBamaFlowSystemMessage(transport.requests.single())
        assertOpenRouterHeaders(transport.requests.single())
        assertTrue(adapter.supportsStreaming())
    }

    @Test
    fun adapterStreamReturnsExactlyOneComposedResponse() = runBlocking {
        val transport = QueueDesktopChatTransport(
            responsePlan(
                body = """
                    data: {"choices":[{"delta":{"content":"Ein "}}]}

                    data: {"choices":[{"delta":{"content":"Ergebnis"}}]}

                    data: [DONE]

                """.trimIndent()
            )
        )
        val responses = openRouterAdapter(transport)
            .stream(request(AiProviderId.OPENROUTER))
            .toList()

        assertEquals(1, responses.size)
        assertEquals("Ein Ergebnis", responses.single().message.text)
    }

    @Test
    fun ollamaNdjsonEmitsOrderedDeltasAndCompletes() = runBlocking {
        val transport = QueueDesktopChatTransport(
            responsePlan(
                body = """

                    {"message":{"role":"assistant","content":"Lokale "},"done":false}
                    {"message":{"role":"assistant","content":"Antwort"},"done":false}

                    {"message":{"role":"assistant","content":""},"done":true}
                """.trimIndent()
            )
        )
        val adapter = ollamaAdapter(transport)

        val events = adapter.streamEvents(request(AiProviderId.OLLAMA)).toList()

        assertEquals(
            listOf("Lokale ", "Antwort"),
            events.filterIsInstance<AiStreamDelta>().map { it.text }
        )
        assertEquals(
            "Lokale Antwort",
            events.filterIsInstance<AiStreamCompleted>().single().response.message.text
        )
        assertEquals(1, transport.requests.size)
        assertBamaFlowSystemMessage(transport.requests.single())
    }

    @Test
    fun retryableHttpStatusesRetryExactlyOnceBeforeFirstDelta() = runBlocking {
        listOf(408, 429, 503).forEach { statusCode ->
            val transport = QueueDesktopChatTransport(
                responsePlan(statusCode = statusCode, body = ""),
                responsePlan(body = openRouterCompleteStream("nach Retry"))
            )

            val events = openRouterAdapter(transport)
                .streamEvents(request(AiProviderId.OPENROUTER))
                .toList()

            assertEquals(2, transport.requests.size)
            assertEquals(
                "nach Retry",
                events.filterIsInstance<AiStreamCompleted>().single().response.message.text
            )
        }
    }

    @Test
    fun temporaryIoFailureRetriesExactlyOnceBeforeFirstDelta() = runBlocking {
        val transport = QueueDesktopChatTransport(
            failurePlan(IOException("temporary fixture failure")),
            responsePlan(body = openRouterCompleteStream("erholt"))
        )

        val events = openRouterAdapter(transport)
            .streamEvents(request(AiProviderId.OPENROUTER))
            .toList()

        assertEquals(2, transport.requests.size)
        assertEquals(
            "erholt",
            events.filterIsInstance<AiStreamCompleted>().single().response.message.text
        )
    }

    @Test
    fun ioFailureAfterFirstDeltaNeverRetries() = runBlocking {
        val partialFrame = """
            data: {"choices":[{"delta":{"content":"Teilantwort"}}]}

        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val transport = QueueDesktopChatTransport(
            responsePlan(body = FailingAfterContentInputStream(partialFrame)),
            responsePlan(body = openRouterCompleteStream("darf nicht gesendet werden"))
        )

        val events = openRouterAdapter(transport)
            .streamEvents(request(AiProviderId.OPENROUTER))
            .toList()

        assertEquals(1, transport.requests.size)
        assertEquals(
            listOf("Teilantwort"),
            events.filterIsInstance<AiStreamDelta>().map { it.text }
        )
        assertEquals(1, events.filterIsInstance<AiStreamError>().size)
        assertTrue(events.filterIsInstance<AiStreamCompleted>().isEmpty())
    }

    @Test
    fun nonRetryableHttpStatusesDoNotRetry() = runBlocking {
        listOf(400, 401, 403).forEach { statusCode ->
            val transport = QueueDesktopChatTransport(
                responsePlan(statusCode = statusCode, body = ""),
                responsePlan(body = openRouterCompleteStream("darf nicht gesendet werden"))
            )

            val events = openRouterAdapter(transport)
                .streamEvents(request(AiProviderId.OPENROUTER))
                .toList()

            assertEquals(1, transport.requests.size)
            val error = events.filterIsInstance<AiStreamError>().single()
            assertTrue(error.message.contains(statusCode.toString()))
            assertTrue(events.filterIsInstance<AiStreamCompleted>().isEmpty())
        }
    }

    @Test
    fun missingApiKeyIsConfigurationErrorWithoutTransportOrRetry() = runBlocking {
        val transport = QueueDesktopChatTransport(
            responsePlan(body = openRouterCompleteStream("unused"))
        )
        val gateway = DesktopChatGateway(transport = transport)
        val adapter = DesktopAiProviderAdapter(
            settings = settings(DesktopProvider.OPENROUTER).copy(openRouterApiKey = ""),
            gateway = gateway
        )

        val events = adapter.streamEvents(request(AiProviderId.OPENROUTER)).toList()

        assertTrue(transport.requests.isEmpty())
        assertEquals(1, events.filterIsInstance<AiStreamError>().size)
    }

    @Test
    fun malformedFrameProducesControlledProviderError() = runBlocking {
        val transport = QueueDesktopChatTransport(
            responsePlan(
                body = """
                    data: {not-json}

                    data: [DONE]

                """.trimIndent()
            )
        )

        val events = openRouterAdapter(transport)
            .streamEvents(request(AiProviderId.OPENROUTER))
            .toList()

        assertEquals(1, transport.requests.size)
        assertEquals(1, events.filterIsInstance<AiStreamError>().size)
        assertTrue(events.filterIsInstance<AiStreamCompleted>().isEmpty())
        assertIs<AiStreamFinished>(events.last())
        Unit
    }

    @Test
    fun cancellationInterruptsReaderAndClosesStreamWithoutRetry() = runBlocking {
        val blockingBody = BlockingInputStream()
        val transport = QueueDesktopChatTransport(responsePlan(body = blockingBody))
        val events = mutableListOf<Any>()
        val job = launch(Dispatchers.Default) {
            openRouterAdapter(transport)
                .streamEvents(request(AiProviderId.OPENROUTER))
                .collect { event -> events += event }
        }
        assertTrue(withContext(Dispatchers.IO) { blockingBody.awaitReadStarted() })

        withTimeout(2_000L) {
            job.cancelAndJoin()
        }

        assertTrue(job.isCancelled)
        assertTrue(blockingBody.closed.get())
        assertEquals(1, transport.requests.size)
        assertTrue(events.any { it is AiStreamStarted })
        assertFalse(events.any { it is AiStreamError })
    }

    @Test
    fun existingNonStreamingOpenRouterAndOllamaChatsRemainSuccessful() = runBlocking {
        val transport = QueueDesktopChatTransport(
            responsePlan(
                body = """{"choices":[{"message":{"content":"OpenRouter komplett"}}]}"""
            ),
            responsePlan(
                body = """{"message":{"role":"assistant","content":"Ollama komplett"}}"""
            )
        )
        val gateway = DesktopChatGateway(transport = transport)

        val openRouter = gateway.chat(
            settings(DesktopProvider.OPENROUTER),
            request(AiProviderId.OPENROUTER, stream = false)
        )
        val ollama = gateway.chat(
            settings(DesktopProvider.OLLAMA),
            request(AiProviderId.OLLAMA, stream = false)
        )

        assertEquals("OpenRouter komplett", openRouter.message.text)
        assertEquals("Ollama komplett", ollama.message.text)
        assertEquals(2, transport.requests.size)
        transport.requests.forEach { assertBamaFlowSystemMessage(it) }
        assertOpenRouterHeaders(transport.requests.first())
    }

    private fun assertBamaFlowSystemMessage(request: HttpRequest) {
        val bodySubscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8)
        request.bodyPublisher().orElseThrow().subscribe(object : Flow.Subscriber<ByteBuffer> {
            override fun onSubscribe(subscription: Flow.Subscription) = bodySubscriber.onSubscribe(subscription)

            override fun onNext(buffer: ByteBuffer) = bodySubscriber.onNext(listOf(buffer))

            override fun onError(error: Throwable) = bodySubscriber.onError(error)

            override fun onComplete() = bodySubscriber.onComplete()
        })
        val body = bodySubscriber.body.toCompletableFuture().get(1, TimeUnit.SECONDS)
        val messages = JsonParser.parseString(body).asJsonObject.getAsJsonArray("messages")
        val systemMessage = messages.first().asJsonObject
        assertEquals("system", systemMessage.get("role").asString)
        val content = systemMessage.get("content").asString
        assertEquals("Du bist BamaFlow Desktop.", content.lineSequence().first())
        assertFalse(content.contains("Du bist BamaChat Desktop."))
    }

    private fun assertOpenRouterHeaders(request: HttpRequest) {
        assertEquals(listOf("BamaChat Desktop"), request.headers().allValues("X-Title"))
        assertEquals(listOf("https://bamachat.app"), request.headers().allValues("HTTP-Referer"))
    }

    private fun openRouterAdapter(transport: DesktopChatHttpTransport): DesktopAiProviderAdapter {
        val gateway = DesktopChatGateway(transport = transport)
        return DesktopAiProviderAdapter(settings(DesktopProvider.OPENROUTER), gateway)
    }

    private fun ollamaAdapter(transport: DesktopChatHttpTransport): DesktopAiProviderAdapter {
        val gateway = DesktopChatGateway(transport = transport)
        return DesktopAiProviderAdapter(settings(DesktopProvider.OLLAMA), gateway)
    }

    private fun settings(provider: DesktopProvider): DesktopUserSettings = DesktopUserSettings(
        provider = provider,
        openRouterApiKey = "test-only-streaming-key",
        openRouterModel = "fixture-openrouter-model",
        ollamaBaseUrl = "http://127.0.0.1:11434/",
        ollamaModel = "fixture-ollama-model",
        enabledExtensionIds = emptySet(),
        firebaseApiKey = "public-fixture-config",
        firebaseProjectId = "fixture-project",
        googleOAuthClientId = "fixture-client"
    )

    private fun request(
        provider: AiProviderId,
        stream: Boolean = true
    ): AiChatRequest = AiChatRequest(
        provider = provider,
        model = if (provider == AiProviderId.OPENROUTER) {
            "fixture-openrouter-model"
        } else {
            "fixture-ollama-model"
        },
        messages = listOf(AiChatMessage(AiChatRole.USER, "fixture prompt")),
        quickAction = QuickActionSuggestion.AUTO,
        stream = stream
    )

    private fun openRouterCompleteStream(text: String): String = """
        data: {"choices":[{"delta":{"content":"$text"}}]}

        data: [DONE]

    """.trimIndent()

    private fun responsePlan(
        statusCode: Int = 200,
        body: String
    ): suspend (HttpRequest) -> DesktopChatHttpResponse = responsePlan(
        statusCode = statusCode,
        body = ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
    )

    private fun responsePlan(
        statusCode: Int = 200,
        body: InputStream
    ): suspend (HttpRequest) -> DesktopChatHttpResponse = {
        DesktopChatHttpResponse(statusCode = statusCode, body = body)
    }

    private fun failurePlan(
        failure: IOException
    ): suspend (HttpRequest) -> DesktopChatHttpResponse = {
        throw failure
    }

    private class QueueDesktopChatTransport(
        vararg plans: suspend (HttpRequest) -> DesktopChatHttpResponse
    ) : DesktopChatHttpTransport {
        private val plans = ArrayDeque(plans.toList())
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): DesktopChatHttpResponse {
            requests += request
            check(plans.isNotEmpty()) { "No fake response configured." }
            return plans.removeFirst().invoke(request)
        }
    }

    private class FailingAfterContentInputStream(
        private val content: ByteArray
    ) : InputStream() {
        private var index = 0

        override fun read(): Int {
            if (index < content.size) return content[index++].toInt() and 0xff
            throw IOException("Injected stream interruption")
        }
    }

    private class BlockingInputStream : InputStream() {
        private val readStarted = CountDownLatch(1)
        val closed = AtomicBoolean(false)

        override fun read(): Int {
            readStarted.countDown()
            try {
                while (!closed.get()) Thread.sleep(10_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted fake stream")
            }
            return -1
        }

        override fun close() {
            closed.set(true)
        }

        fun awaitReadStarted(): Boolean = readStarted.await(1, TimeUnit.SECONDS)
    }
}
