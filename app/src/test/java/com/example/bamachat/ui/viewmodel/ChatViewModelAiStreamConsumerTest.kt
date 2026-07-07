package com.example.bamachat.ui.viewmodel

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
