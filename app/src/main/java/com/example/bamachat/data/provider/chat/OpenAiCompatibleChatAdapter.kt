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
class OpenAiCompatibleChatAdapter @Inject constructor() : ProviderChatAdapter {
    private val gson = Gson()

    override suspend fun execute(
        provider: ProviderDefinition,
        normalizedBaseUrl: String,
        modelId: String,
        secret: String?,
        messages: List<ProviderChatMessage>,
        onChunk: suspend (ProviderChatChunk) -> Unit
    ): ProviderChatResult {
        val streaming = provider.capabilities.streaming
        val payload = gson.toJson(mapOf("model" to modelId, "messages" to messages, "stream" to streaming))
        val requestBuilder = Request.Builder()
            .url(ProviderEndpointBuilder.openAiChatCompletions(normalizedBaseUrl))
            .header("Content-Type", "application/json")
            .header("Accept", if (streaming) "text/event-stream" else "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
        if (provider.authenticationType == ProviderAuthenticationType.BEARER) {
            val key = secret?.takeIf { it.isNotBlank() }
                ?: throw ProviderChatException(ProviderChatError.SECRET_MISSING, message = "Provider secret missing")
            requestBuilder.header("Authorization", "Bearer $key")
        }
        val response = ProviderHttpSupport.execute(ProviderHttpSupport.client(provider.timeoutMs).newCall(requestBuilder.build()))
        response.use {
            ProviderHttpSupport.ensureSuccess(it)
            val body = it.body ?: throw ProviderChatException(ProviderChatError.EMPTY_RESPONSE, message = "Provider body missing")
            return if (streaming) parseSse(body.charStream().buffered(), onChunk) else parseJson(ProviderHttpSupport.readBounded(body), onChunk)
        }
    }

    private suspend fun parseSse(reader: java.io.BufferedReader, onChunk: suspend (ProviderChatChunk) -> Unit): ProviderChatResult =
        withContext(Dispatchers.IO) {
            val full = StringBuilder()
            reader.use { source ->
                while (true) {
                    val line = source.readLine() ?: break
                    if (line.length > ProviderHttpSupport.MAX_LINE_CHARS) throw ProviderChatException(ProviderChatError.RESPONSE_TOO_LARGE, message = "Provider line too large")
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank()) continue
                    if (data == "[DONE]") break
                    val text = runCatching {
                        JsonParser.parseString(data).asJsonObject
                            .getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("delta")?.get("content")?.takeUnless { it.isJsonNull }?.asString
                    }.getOrElse { throw ProviderChatException(ProviderChatError.INVALID_RESPONSE, message = "Invalid provider SSE") }
                    if (!text.isNullOrEmpty()) {
                        ProviderHttpSupport.appendBounded(full, text)
                        onChunk(ProviderChatChunk(text))
                    }
                }
            }
            val result = full.toString()
            if (result.isBlank()) throw ProviderChatException(ProviderChatError.EMPTY_RESPONSE, message = "Empty provider stream")
            ProviderChatResult(result)
        }

    private suspend fun parseJson(raw: String, onChunk: suspend (ProviderChatChunk) -> Unit): ProviderChatResult {
        if (raw.length > ProviderHttpSupport.MAX_RESPONSE_CHARS) throw ProviderChatException(ProviderChatError.RESPONSE_TOO_LARGE, message = "Provider response too large")
        val text = runCatching {
            JsonParser.parseString(raw).asJsonObject
                .getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.takeUnless { it.isJsonNull }?.asString
        }.getOrElse { throw ProviderChatException(ProviderChatError.INVALID_RESPONSE, message = "Invalid provider JSON") }
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) throw ProviderChatException(ProviderChatError.EMPTY_RESPONSE, message = "Empty provider response")
        onChunk(ProviderChatChunk(clean))
        return ProviderChatResult(clean)
    }
}
