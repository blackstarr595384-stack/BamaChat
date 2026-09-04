package com.example.bamachat.ui.viewmodel

import com.example.bamachat.data.ApiClient
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ChatSource
import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamDelta
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamEvent
import com.example.bamachat.shared.core.ai.AiStreamFinished
import com.example.bamachat.shared.core.ai.AiStreamStarted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelAiStreamConsumerTest {

    @Test
    fun deltaUpdatesBufferAndFlushesPartialMessage() = runBlocking {
        val saved = mutableListOf<SavedMessage>()
        val buffer = StringBuilder()

        val result = consume(
            events = flowOf(
                AiStreamDelta("Hel", AiProviderId.OPENROUTER, MODEL),
                completed("Hello")
            ),
            buffer = buffer,
            lastFlushAt = { 0L },
            saveMessage = { convId, message, touchConversation ->
                saved.add(SavedMessage(convId, message, touchConversation))
            }
        )

        assertTrue(result.success)
        assertEquals("Hel", buffer.toString())
        assertEquals("Hel", saved.first().message.text)
        assertFalse(saved.first().touchConversation)
    }

    @Test
    fun completedSavesFinalMessageWithSourcesAndNotification() = runBlocking {
        val saved = mutableListOf<SavedMessage>()
        val notifications = mutableListOf<String>()
        val sources = listOf(ChatSource(title = "Source", url = "https://example.test"))

        val result = consume(
            events = flowOf(
                AiStreamDelta("Hel", AiProviderId.OPENROUTER, MODEL),
                completed("Hello")
            ),
            webSources = sources,
            webFetchedAtIso = "2026-07-07T12:00:00Z",
            saveMessage = { convId, message, touchConversation ->
                saved.add(SavedMessage(convId, message, touchConversation))
            },
            showNotification = { notifications.add(it) }
        )

        assertTrue(result.success)
        val final = saved.last()
        assertEquals(CONV_ID, final.convId)
        assertEquals("Hello", final.message.text)
        assertEquals(sources, final.message.sources)
        assertEquals("2026-07-07T12:00:00Z", final.message.webFetchedAtIso)
        assertTrue(final.touchConversation)
        assertEquals(listOf("Hello"), notifications)
    }

    @Test
    fun errorReturnsFallbackWithoutFinalSave() = runBlocking {
        val saved = mutableListOf<SavedMessage>()

        val result = consume(
            events = flowOf(
                AiStreamDelta("partial", AiProviderId.OPENROUTER, MODEL),
                AiStreamError("failed", provider = AiProviderId.OPENROUTER, model = MODEL)
            ),
            saveMessage = { convId, message, touchConversation ->
                saved.add(SavedMessage(convId, message, touchConversation))
            }
        )

        assertFalse(result.success)
        assertEquals("provider_error", result.fallbackReason)
        assertTrue(saved.none { it.touchConversation })
    }

    @Test
    fun finishedWithoutCompletedIsNotSuccess() = runBlocking {
        val result = consume(
            events = flowOf(
                AiStreamDelta("partial", AiProviderId.OPENROUTER, MODEL),
                AiStreamFinished(AiProviderId.OPENROUTER, MODEL)
            )
        )

        assertFalse(result.success)
        assertEquals("empty_stream", result.fallbackReason)
    }

    @Test
    fun cancellationIsNotSwallowed() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                consume(
                    events = flow {
                        emit(AiStreamDelta("partial", AiProviderId.OPENROUTER, MODEL))
                        throw CancellationException("cancelled")
                    }
                )
            }
        }
    }

    @Test
    fun legacyChunkMapsToAiStreamDelta() = runBlocking {
        val events = legacyEvents { onChunk, _ ->
            onChunk("Hel")
            ApiManager.ApiResponse(success = true, content = "Hel", usedProvider = ApiClient.Provider.OPENROUTER)
        }

        val deltas = events.filterIsInstance<AiStreamDelta>()

        assertEquals(listOf("Hel"), deltas.map { it.text })
        assertTrue(events.first() is AiStreamStarted)
        assertTrue(events.last() is AiStreamFinished)
    }

    @Test
    fun legacyChunksKeepOrder() = runBlocking {
        val events = legacyEvents { onChunk, _ ->
            onChunk("Hel")
            onChunk("lo")
            onChunk("!")
            ApiManager.ApiResponse(success = true, content = "Hello!", usedProvider = ApiClient.Provider.OPENROUTER)
        }

        assertEquals(listOf("Hel", "lo", "!"), events.filterIsInstance<AiStreamDelta>().map { it.text })
    }

    @Test
    fun legacySuccessMapsFinalResponseToCompleted() = runBlocking {
        val events = legacyEvents { onChunk, _ ->
            onChunk("done")
            ApiManager.ApiResponse(success = true, content = "done", usedProvider = ApiClient.Provider.GROQ)
        }

        val completed = events.filterIsInstance<AiStreamCompleted>().single()

        assertEquals(AiProviderId.GROQ, completed.provider)
        assertEquals(MODEL, completed.model)
        assertEquals("done", completed.response.message.text)
    }

    @Test
    fun legacyFinalErrorMapsToAiStreamError() = runBlocking {
        val events = legacyEvents { _, _ ->
            ApiManager.ApiResponse(
                success = false,
                error = "all providers failed",
                usedProvider = ApiClient.Provider.OPENROUTER,
                retryable = false
            )
        }

        val error = events.filterIsInstance<AiStreamError>().single()

        assertEquals(
            "OpenRouter hat nicht geantwortet. Bitte versuche es erneut oder wähle einen anderen Anbieter.",
            error.message
        )
        assertEquals(AiProviderId.OPENROUTER, error.provider)
        assertTrue(events.last() is AiStreamFinished)
    }

    @Test
    fun legacyIntermediateOnErrorDoesNotEmitTerminalError() = runBlocking {
        val intermediateErrors = mutableListOf<String>()
        val safeFallbackStatus =
            "OpenRouter antwortet gerade nicht. BamaFlow versucht einen anderen Anbieter."
        val events = legacyEvents(
            onIntermediateError = { intermediateErrors.add(it) }
        ) { onChunk, onError ->
            onError(safeFallbackStatus)
            onChunk("ok")
            ApiManager.ApiResponse(success = true, content = "ok", usedProvider = ApiClient.Provider.GROQ)
        }

        assertEquals(listOf(safeFallbackStatus), intermediateErrors)
        assertEquals(emptyList<AiStreamError>(), events.filterIsInstance<AiStreamError>())
        assertEquals("ok", events.filterIsInstance<AiStreamCompleted>().single().response.message.text)
    }

    @Test
    fun legacyCancellationIsForwarded() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                legacyEvents { _, _ ->
                    throw CancellationException("cancel legacy")
                }
            }
        }
    }

    @Test
    fun legacyFinishedIsEmittedAtEnd() = runBlocking {
        val events = legacyEvents { onChunk, _ ->
            onChunk("done")
            ApiManager.ApiResponse(success = true, content = "done", usedProvider = ApiClient.Provider.OPENROUTER)
        }

        assertTrue(events.last() is AiStreamFinished)
    }

    @Test
    fun legacyEventRouteFlushesChunkThroughConsumer() = runBlocking {
        val saved = mutableListOf<SavedMessage>()
        val buffer = StringBuilder()

        val result = consumeLegacy(
            buffer = buffer,
            lastFlushAt = { 0L },
            saveMessage = { convId, message, touchConversation ->
                saved.add(SavedMessage(convId, message, touchConversation))
            }
        ) { onChunk, _ ->
            onChunk("live")
            ApiManager.ApiResponse(success = true, content = "live", usedProvider = ApiClient.Provider.OPENROUTER)
        }

        assertTrue(result.success)
        assertEquals("live", buffer.toString())
        assertEquals("live", saved.first().message.text)
        assertFalse(saved.first().touchConversation)
    }

    @Test
    fun legacyEventRouteFinalSaveStaysCorrect() = runBlocking {
        val saved = mutableListOf<SavedMessage>()
        val notifications = mutableListOf<String>()

        val result = consumeLegacy(
            webSources = listOf(ChatSource(title = "Doc", url = "https://example.test/doc")),
            webFetchedAtIso = "2026-07-07T13:00:00Z",
            saveMessage = { convId, message, touchConversation ->
                saved.add(SavedMessage(convId, message, touchConversation))
            },
            showNotification = { notifications.add(it) }
        ) { onChunk, _ ->
            onChunk("final")
            ApiManager.ApiResponse(success = true, content = "final", usedProvider = ApiClient.Provider.OPENROUTER)
        }

        assertTrue(result.success)
        val final = saved.last()
        assertEquals("final", final.message.text)
        assertEquals("Doc", final.message.sources.single().title)
        assertEquals("2026-07-07T13:00:00Z", final.message.webFetchedAtIso)
        assertTrue(final.touchConversation)
        assertEquals(listOf("final"), notifications)
    }

    @Test
    fun legacyEventRouteReturnsErrorForFinalFailure() = runBlocking {
        var terminalError: ApiManager.ApiResponse? = null

        val result = consumeLegacy(
            onTerminalError = { terminalError = it }
        ) { _, _ ->
            ApiManager.ApiResponse(
                success = false,
                error = "legacy failed",
                usedProvider = ApiClient.Provider.OPENROUTER,
                retryable = false
            )
        }

        assertFalse(result.success)
        assertEquals("provider_error", result.fallbackReason)
        assertEquals(
            "OpenRouter hat nicht geantwortet. Bitte versuche es erneut oder wähle einen anderen Anbieter.",
            result.errorMessage
        )
        assertEquals(false, terminalError?.retryable)
    }

    @Test
    fun legacyEmptyResponseDoesNotSaveEmptyAssistantMessage() = runBlocking {
        val saved = mutableListOf<SavedMessage>()

        val result = consumeLegacy(
            saveMessage = { convId, message, touchConversation ->
                saved.add(SavedMessage(convId, message, touchConversation))
            }
        ) { _, _ ->
            ApiManager.ApiResponse(
                success = true,
                content = "",
                usedProvider = ApiClient.Provider.OPENROUTER
            )
        }

        assertFalse(result.success)
        assertTrue(saved.none { it.touchConversation || it.message.text.isBlank() })
        assertFalse(result.errorMessage.orEmpty().contains("Legacy", ignoreCase = true))
    }

    @Test
    fun legacyEventRouteCancellationIsNotSwallowed() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                consumeLegacy { _, _ ->
                    throw CancellationException("cancel route")
                }
            }
        }
    }

    @Test
    fun pilotEventRouteUsesCommonConsumerTelemetry() = runBlocking {
        val telemetry = mutableListOf<TelemetryEvent>()

        val result = consume(
            events = flowOf(
                AiStreamStarted(AiProviderId.OPENROUTER, MODEL),
                AiStreamDelta("pilot", AiProviderId.OPENROUTER, MODEL),
                completed("pilot"),
                AiStreamFinished(AiProviderId.OPENROUTER, MODEL)
            ),
            streamTelemetrySource = "pilot",
            logStreamEvent = { name, params -> telemetry.add(TelemetryEvent(name, params)) }
        )

        assertTrue(result.success)
        assertEquals(listOf("stream_event_started", "stream_event_completed"), telemetry.map { it.name })
        assertEquals("pilot", telemetry.first().params["source"])
        assertEquals("OPENROUTER", telemetry.first().params["provider"])
        assertEquals(MODEL, telemetry.first().params["model"])
    }

    @Test
    fun legacyEventRouteUsesCommonConsumerTelemetry() = runBlocking {
        val telemetry = mutableListOf<TelemetryEvent>()

        val result = consumeLegacy(
            streamTelemetrySource = "legacy",
            logStreamEvent = { name, params -> telemetry.add(TelemetryEvent(name, params)) }
        ) { onChunk, _ ->
            onChunk("legacy")
            ApiManager.ApiResponse(success = true, content = "legacy", usedProvider = ApiClient.Provider.OPENROUTER)
        }

        assertTrue(result.success)
        assertEquals(listOf("stream_event_started", "stream_event_completed"), telemetry.map { it.name })
        assertEquals("legacy", telemetry.first().params["source"])
        assertEquals("OPENROUTER", telemetry.first().params["provider"])
    }

    @Test
    fun commonConsumerFinalSaveAndNotificationHappenOnce() = runBlocking {
        val saved = mutableListOf<SavedMessage>()
        val notifications = mutableListOf<String>()

        val result = consume(
            events = flowOf(
                AiStreamStarted(AiProviderId.OPENROUTER, MODEL),
                AiStreamDelta("partial", AiProviderId.OPENROUTER, MODEL),
                completed("final"),
                AiStreamFinished(AiProviderId.OPENROUTER, MODEL)
            ),
            lastFlushAt = { System.currentTimeMillis() },
            saveMessage = { convId, message, touchConversation ->
                saved.add(SavedMessage(convId, message, touchConversation))
            },
            showNotification = { notifications.add(it) },
            streamTelemetrySource = "pilot"
        )

        assertTrue(result.success)
        assertEquals(1, saved.size)
        assertEquals("final", saved.single().message.text)
        assertTrue(saved.single().touchConversation)
        assertEquals(listOf("final"), notifications)
    }

    @Test
    fun commonConsumerErrorTelemetryIsIdenticalForPilotAndLegacy() = runBlocking {
        val pilotTelemetry = mutableListOf<TelemetryEvent>()
        val legacyTelemetry = mutableListOf<TelemetryEvent>()

        val pilotResult = consume(
            events = flowOf(
                AiStreamStarted(AiProviderId.OPENROUTER, MODEL),
                AiStreamError("failed", provider = AiProviderId.OPENROUTER, model = MODEL),
                AiStreamFinished(AiProviderId.OPENROUTER, MODEL)
            ),
            streamTelemetrySource = "pilot",
            logStreamEvent = { name, params -> pilotTelemetry.add(TelemetryEvent(name, params)) }
        )
        val legacyResult = consumeLegacy(
            streamTelemetrySource = "legacy",
            logStreamEvent = { name, params -> legacyTelemetry.add(TelemetryEvent(name, params)) }
        ) { _, _ ->
            ApiManager.ApiResponse(success = false, error = "failed", usedProvider = ApiClient.Provider.OPENROUTER)
        }

        assertFalse(pilotResult.success)
        assertFalse(legacyResult.success)
        assertEquals("stream_event_error", pilotTelemetry.last().name)
        assertEquals("stream_event_error", legacyTelemetry.last().name)
        assertEquals("provider_error", pilotTelemetry.last().params["reason"])
        assertEquals("provider_error", legacyTelemetry.last().params["reason"])
    }

    @Test
    fun commonConsumerCancellationDoesNotEmitErrorTelemetry() {
        val telemetry = mutableListOf<TelemetryEvent>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                consume(
                    events = flow {
                        emit(AiStreamStarted(AiProviderId.OPENROUTER, MODEL))
                        throw CancellationException("cancel common route")
                    },
                    streamTelemetrySource = "pilot",
                    logStreamEvent = { name, params -> telemetry.add(TelemetryEvent(name, params)) }
                )
            }
        }

        assertEquals(listOf("stream_event_started"), telemetry.map { it.name })
    }

    private suspend fun consume(
        events: Flow<AiStreamEvent>,
        buffer: StringBuilder = StringBuilder(),
        webSources: List<ChatSource> = emptyList(),
        webFetchedAtIso: String? = null,
        lastFlushAt: () -> Long = { 0L },
        saveMessage: suspend (String, ChatMessage, Boolean) -> Unit = { _, _, _ -> },
        showNotification: suspend (String) -> Unit = {},
        streamTelemetrySource: String? = null,
        logStreamEvent: (String, Map<String, String>) -> Unit = { _, _ -> }
    ): ChatViewModel.AiStreamConsumptionResult {
        var lastFlush = 0L
        return ChatViewModel.consumeAiStreamEvents(
            events = events,
            convId = CONV_ID,
            assistantMsg = ChatMessage(id = "assistant", text = "", isUser = false, timestamp = 1L),
            webSources = webSources,
            webFetchedAtIso = webFetchedAtIso,
            streamingBuffer = buffer,
            streamFlushInterval = 250L,
            lastFlushAtProvider = lastFlushAt,
            updateLastFlushAt = { lastFlush = it },
            saveMessage = saveMessage,
            clearRetryContext = {},
            showNotification = showNotification,
            streamTelemetrySource = streamTelemetrySource,
            streamTelemetryModel = MODEL,
            streamStartedAtMs = 0L,
            logStreamEvent = logStreamEvent
        ).also {
            if (lastFlush < 0L) error("unreachable")
        }
    }

    private fun completed(text: String): AiStreamCompleted {
        return AiStreamCompleted(
            AiChatResponse(
                provider = AiProviderId.OPENROUTER,
                model = MODEL,
                message = AiChatMessage(AiChatRole.ASSISTANT, text)
            )
        )
    }

    private suspend fun legacyEvents(
        onIntermediateError: (String) -> Unit = {},
        onTerminalError: (ApiManager.ApiResponse) -> Unit = {},
        streamChatResponse: suspend (
            onChunkReceived: (String) -> Unit,
            onError: (String) -> Unit
        ) -> ApiManager.ApiResponse
    ): List<AiStreamEvent> {
        return ChatViewModel.legacyStreamAsAiEvents(
            provider = AiProviderId.OPENROUTER,
            model = MODEL,
            streamChatResponse = streamChatResponse,
            onIntermediateError = onIntermediateError,
            onTerminalError = onTerminalError
        ).toList()
    }

    private suspend fun consumeLegacy(
        buffer: StringBuilder = StringBuilder(),
        webSources: List<ChatSource> = emptyList(),
        webFetchedAtIso: String? = null,
        lastFlushAt: () -> Long = { 0L },
        saveMessage: suspend (String, ChatMessage, Boolean) -> Unit = { _, _, _ -> },
        showNotification: suspend (String) -> Unit = {},
        onTerminalError: (ApiManager.ApiResponse) -> Unit = {},
        streamTelemetrySource: String? = null,
        logStreamEvent: (String, Map<String, String>) -> Unit = { _, _ -> },
        streamChatResponse: suspend (
            onChunkReceived: (String) -> Unit,
            onError: (String) -> Unit
        ) -> ApiManager.ApiResponse
    ): ChatViewModel.AiStreamConsumptionResult {
        return consume(
            events = ChatViewModel.legacyStreamAsAiEvents(
                provider = AiProviderId.OPENROUTER,
                model = MODEL,
                streamChatResponse = streamChatResponse,
                onTerminalError = onTerminalError
            ),
            buffer = buffer,
            webSources = webSources,
            webFetchedAtIso = webFetchedAtIso,
            lastFlushAt = lastFlushAt,
            saveMessage = saveMessage,
            showNotification = showNotification,
            streamTelemetrySource = streamTelemetrySource,
            logStreamEvent = logStreamEvent
        )
    }

    private data class TelemetryEvent(
        val name: String,
        val params: Map<String, String>
    )

    private data class SavedMessage(
        val convId: String,
        val message: ChatMessage,
        val touchConversation: Boolean
    )

    private companion object {
        const val CONV_ID = "conversation"
        const val MODEL = "openrouter/test"
    }
}
