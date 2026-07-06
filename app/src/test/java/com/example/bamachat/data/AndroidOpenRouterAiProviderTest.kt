package com.example.bamachat.data

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.QuickActionSuggestion
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamDelta
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamEvent
import com.example.bamachat.shared.core.ai.AiStreamFinished
import com.example.bamachat.shared.core.ai.AiStreamStarted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOpenRouterAiProviderTest {
    @Test
    fun chatMapsAiChatRequestThroughOpenRouterDtoAndFakeCompletion() = runBlocking {
        var captured: OpenRouterChatRequest? = null
        val provider = AndroidOpenRouterAiProvider(chatCompletion = { request ->
            captured = request
            OpenRouterChatResponse(
                choices = listOf(
                    OpenRouterChoice(
                        OpenRouterMessage(
                            role = request.messages.last().role,
                            content = "Antwort auf: ${request.messages.last().content}"
                        )
                    )
                )
            )
        })
        val request = AiChatRequest(
            provider = AiProviderId.OPENROUTER,
            model = "openrouter/test-model",
            messages = listOf(
                AiChatMessage(AiChatRole.SYSTEM, "Du bist knapp."),
                AiChatMessage(AiChatRole.USER, "Erklaere Compose."),
                AiChatMessage(AiChatRole.ASSISTANT, "Compose ist deklarativ."),
                AiChatMessage(AiChatRole.USER, "Und State?")
            ),
            quickAction = QuickActionSuggestion.AUTO,
            maxTokens = 777,
            temperature = 0.35,
            stream = false
        )

        val response = provider.chat(request)

        val openRouterRequest = requireNotNull(captured)
        assertEquals("openrouter/test-model", openRouterRequest.model)
        assertEquals(777, openRouterRequest.maxTokens)
        assertEquals(0.35f, openRouterRequest.temperature)
        assertEquals(false, openRouterRequest.stream)
        assertEquals(
            listOf("system", "user", "assistant", "user"),
            openRouterRequest.messages.map { it.role }
        )
        assertEquals(
            listOf("Du bist knapp.", "Erklaere Compose.", "Compose ist deklarativ.", "Und State?"),
            openRouterRequest.messages.map { it.content }
        )
        assertEquals(AiProviderId.OPENROUTER, response.provider)
        assertEquals("openrouter/test-model", response.model)
        assertEquals(AiChatRole.USER, response.message.role)
        assertEquals("Antwort auf: Und State?", response.message.text)
    }

    @Test
    fun exposesOpenRouterProviderIdAndNoStreamingSupportYet() {
        val provider = AndroidOpenRouterAiProvider(
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) }
        )

        assertEquals(AiProviderId.OPENROUTER, provider.id())
        assertFalse(provider.supportsStreaming())
    }

    @Test
    fun buildsExistingOpenRouterRequestDtoForChat() = runBlocking {
        var captured: OpenRouterChatRequest? = null
        val provider = AndroidOpenRouterAiProvider(chatCompletion = { request ->
            captured = request
            OpenRouterChatResponse(
                choices = listOf(OpenRouterChoice(OpenRouterMessage(role = "assistant", content = "Hallo")))
            )
        })

        val response = provider.chat(sampleRequest())

        assertEquals("test-model", captured?.model)
        assertEquals(false, captured?.stream)
        assertEquals(321, captured?.maxTokens)
        assertEquals(0.2f, captured?.temperature)
        assertEquals(listOf("system", "user"), captured?.messages?.map { it.role })
        assertEquals(listOf("Kurz antworten.", "Hallo"), captured?.messages?.map { it.content })
        assertEquals(AiProviderId.OPENROUTER, response.provider)
        assertEquals("test-model", response.model)
        assertEquals(AiChatRole.ASSISTANT, response.message.role)
        assertEquals("Hallo", response.message.text)
    }

    @Test
    fun rejectsNonOpenRouterRequests() {
        val provider = AndroidOpenRouterAiProvider(
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) }
        )
        val request = sampleRequest(provider = AiProviderId.GROQ)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { provider.chat(request) }
        }
    }

    @Test
    fun streamIsExplicitlyUnsupportedForSkeleton() {
        val provider = AndroidOpenRouterAiProvider(
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) }
        )

        assertThrows(UnsupportedOperationException::class.java) {
            provider.stream(sampleRequest())
        }
    }

    @Test
    fun streamEventsMapsTextChunksToDeltaEvents() = runBlocking {
        val provider = AndroidOpenRouterAiProvider(
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) },
            streamTextChunks = { flowOf("Hel", "lo") }
        )

        val events = provider.streamEvents(sampleRequest()).toList()
        val deltas = events.filterIsInstance<AiStreamDelta>()

        assertEquals(listOf("Hel", "lo"), deltas.map { it.text })
        assertEquals(AiProviderId.OPENROUTER, deltas.first().provider)
        assertEquals("test-model", deltas.first().model)
    }

    @Test
    fun streamEventsEmitsCompletedWithAccumulatedResponse() = runBlocking {
        val provider = AndroidOpenRouterAiProvider(
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) },
            streamTextChunks = { flowOf("Hallo", " Welt") }
        )

        val events = provider.streamEvents(sampleRequest()).toList()
        val completed = events.filterIsInstance<AiStreamCompleted>().single()

        assertTrue(events.first() is AiStreamStarted)
        assertEquals(AiProviderId.OPENROUTER, completed.provider)
        assertEquals("test-model", completed.model)
        assertEquals(AiChatRole.ASSISTANT, completed.response.message.role)
        assertEquals("Hallo Welt", completed.response.message.text)
    }

    @Test
    fun streamEventsMapsStreamErrorsToErrorEvent() = runBlocking {
        val provider = AndroidOpenRouterAiProvider(
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) },
            streamTextChunks = {
                flow {
                    emit("partial")
                    throw IllegalStateException("stream failed")
                }
            }
        )

        val events = provider.streamEvents(sampleRequest()).toList()
        val error = events.filterIsInstance<AiStreamError>().single()

        assertEquals("stream failed", error.message)
        assertEquals("IllegalStateException", error.exceptionClass)
        assertEquals(AiProviderId.OPENROUTER, error.provider)
        assertEquals("test-model", error.model)
    }

    @Test
    fun streamEventsAlwaysEmitsFinishedAtEnd() = runBlocking {
        val provider = AndroidOpenRouterAiProvider(
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) },
            streamTextChunks = { flowOf("done") }
        )

        val events = provider.streamEvents(sampleRequest()).toList()

        assertTrue(events.last() is AiStreamFinished)
        assertEquals(AiProviderId.OPENROUTER, events.last().provider)
        assertEquals("test-model", events.last().model)
    }

    @Test
    fun streamEventsDoesNotEmitFinishedForCancellation() {
        val provider = AndroidOpenRouterAiProvider(
            chatCompletion = { OpenRouterChatResponse(choices = emptyList()) },
            streamTextChunks = {
                flow {
                    emit("partial")
                    throw CancellationException("cancelled")
                }
            }
        )
        val events = mutableListOf<AiStreamEvent>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                provider.streamEvents(sampleRequest()).collect { event ->
                    events.add(event)
                }
            }
        }

        assertTrue(events.first() is AiStreamStarted)
        assertEquals(listOf("partial"), events.filterIsInstance<AiStreamDelta>().map { it.text })
        assertEquals(emptyList<AiStreamCompleted>(), events.filterIsInstance<AiStreamCompleted>())
        assertEquals(emptyList<AiStreamError>(), events.filterIsInstance<AiStreamError>())
        assertEquals(emptyList<AiStreamFinished>(), events.filterIsInstance<AiStreamFinished>())
    }

    private fun sampleRequest(provider: AiProviderId = AiProviderId.OPENROUTER): AiChatRequest {
        return AiChatRequest(
            provider = provider,
            model = "test-model",
            messages = listOf(
                AiChatMessage(AiChatRole.SYSTEM, "Kurz antworten."),
                AiChatMessage(AiChatRole.USER, "Hallo")
            ),
            quickAction = QuickActionSuggestion.AUTO,
            maxTokens = 321,
            temperature = 0.2,
            stream = false
        )
    }
}
