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

        assertEquals("all providers failed", error.message)
        assertEquals(AiProviderId.OPENROUTER, error.provider)
        assertTrue(events.last() is AiStreamFinished)
    }

    @Test
    fun legacyIntermediateOnErrorDoesNotEmitTerminalError() = runBlocking {
        val intermediateErrors = mutableListOf<String>()
        val events = legacyEvents(
            onIntermediateError = { intermediateErrors.add(it) }
        ) { onChunk, onError ->
            onError("OpenRouter fehlgeschlagen, versuche Groq...")
            onChunk("ok")
            ApiManager.ApiResponse(success = true, content = "ok", usedProvider = ApiClient.Provider.GROQ)
        }

        assertEquals(listOf("OpenRouter fehlgeschlagen, versuche Groq..."), intermediateErrors)
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
        assertEquals("legacy failed", result.errorMessage)
        assertEquals(false, terminalError?.retryable)
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

    private suspend fun consume(
        events: Flow<AiStreamEvent>,
        buffer: StringBuilder = StringBuilder(),
        webSources: List<ChatSource> = emptyList(),
        webFetchedAtIso: String? = null,
        lastFlushAt: () -> Long = { 0L },
        saveMessage: suspend (String, ChatMessage, Boolean) -> Unit = { _, _, _ -> },
        showNotification: suspend (String) -> Unit = {}
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
            showNotification = showNotification
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
            showNotification = showNotification
        )
    }

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
