package com.example.bamachat.data

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * OpenRouter SSE Text-Chunk Stream.
 * Liest Server-Sent-Events aus dem Streaming-Response und extrahiert reinen Text-Content.
 *
 * Diese Klasse kapselt die OpenRouter-spezifische SSE-Parsing-Logik, damit der
 * ChatViewModel keine Kenntnis über den konkreten Streaming-Transport benötigt.
 */
class OpenRouterSseTextChunkStream(
    private val service: OpenAICompatibleService,
    private val gson: Gson = Gson()
) {
    /**
     * Streamt Text-Chunks aus einem OpenRouter-Chat-Completion-Stream.
     *
     * @param request Die OpenRouter-Chat-Request
     * @return Flow von Text-Chunks
     * @throws IllegalStateException bei HTTP-Fehlern oder leeren Response-Body
     */
    fun streamTextChunks(request: OpenRouterChatRequest): Flow<String> = flow {
        val response = service.chatCompletionStream(request)
        if (!response.isSuccessful) {
            val body = response.errorBody()?.string().orEmpty()
            throw IllegalStateException("OpenRouter stream failed: ${response.code()} ${body.take(120)}")
        }

        val body = response.body() ?: throw IllegalStateException("OpenRouter stream failed: empty response body")
        body.byteStream().bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val current = line ?: continue
                if (!current.startsWith("data:")) continue
                val payload = current.removePrefix("data:").trim()
                if (payload.isBlank() || payload == "[DONE]") continue

                runCatching {
                    gson.fromJson(payload, OpenRouterStreamChunk::class.java)
                        .choices
                        ?.firstOrNull()
                        ?.delta
                        ?.content
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { emit(it) }
            }
        }
    }
}
