package com.example.bamachat.data.provider.chat

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderDefinition
import com.google.gson.Gson
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OllamaLocalChatAdapter @Inject constructor() : ProviderChatAdapter {
    private val gson = Gson()

    override suspend fun execute(
        provider: ProviderDefinition,
        normalizedBaseUrl: String,
        modelId: String,
        secret: String?,
        messages: List<ProviderChatMessage>,
        onChunk: suspend (ProviderChatChunk) -> Unit
    ): ProviderChatResult {
        if (provider.authenticationType != ProviderAuthenticationType.NONE_LOCAL_ONLY) {
            throw ProviderChatException(ProviderChatError.UNSUPPORTED_FEATURE, message = "Unsupported Ollama authentication")
        }
        val streaming = provider.capabilities.streaming
        val payload = gson.toJson(mapOf("model" to modelId, "messages" to messages, "stream" to streaming))
        val request = Request.Builder()
            .url(ProviderEndpointBuilder.ollamaChat(normalizedBaseUrl))
            .header("Content-Type", "application/json")
            .header("Accept", if (streaming) "application/x-ndjson" else "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = ProviderHttpSupport.execute(ProviderHttpSupport.client(provider.timeoutMs).newCall(request))
        response.use {
            ProviderHttpSupport.ensureSuccess(it)
            val body = it.body ?: throw ProviderChatException(ProviderChatError.EMPTY_RESPONSE, message = "Ollama body missing")
            return if (streaming) parseNdjson(body.charStream().buffered(), onChunk) else parseJson(ProviderHttpSupport.readBounded(body), onChunk)
        }
    }

    private suspend fun parseNdjson(reader: java.io.BufferedReader, onChunk: suspend (ProviderChatChunk) -> Unit): ProviderChatResult =
        withContext(Dispatchers.IO) {
            val full = StringBuilder()
            reader.use { source ->
                while (true) {
                    val line = source.readLine() ?: break
                    if (line.isBlank()) continue
                    if (line.length > ProviderHttpSupport.MAX_LINE_CHARS) throw ProviderChatException(ProviderChatError.RESPONSE_TOO_LARGE, message = "Ollama line too large")
                    val text = runCatching {
                        JsonParser.parseString(line).asJsonObject
                            .getAsJsonObject("message")?.get("content")?.takeUnless { it.isJsonNull }?.asString
                    }.getOrElse { throw ProviderChatException(ProviderChatError.INVALID_RESPONSE, message = "Invalid Ollama NDJSON") }
                    if (!text.isNullOrEmpty()) {
                        ProviderHttpSupport.appendBounded(full, text)
                        onChunk(ProviderChatChunk(text))
                    }
                }
            }
            val result = full.toString()
            if (result.isBlank()) throw ProviderChatException(ProviderChatError.EMPTY_RESPONSE, message = "Empty Ollama stream")
            ProviderChatResult(result)
        }

    private suspend fun parseJson(raw: String, onChunk: suspend (ProviderChatChunk) -> Unit): ProviderChatResult {
        if (raw.length > ProviderHttpSupport.MAX_RESPONSE_CHARS) throw ProviderChatException(ProviderChatError.RESPONSE_TOO_LARGE, message = "Ollama response too large")
        val text = runCatching {
            JsonParser.parseString(raw).asJsonObject
                .getAsJsonObject("message")?.get("content")?.takeUnless { it.isJsonNull }?.asString
        }.getOrElse { throw ProviderChatException(ProviderChatError.INVALID_RESPONSE, message = "Invalid Ollama JSON") }
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) throw ProviderChatException(ProviderChatError.EMPTY_RESPONSE, message = "Empty Ollama response")
        onChunk(ProviderChatChunk(clean))
        return ProviderChatResult(clean)
    }
}
