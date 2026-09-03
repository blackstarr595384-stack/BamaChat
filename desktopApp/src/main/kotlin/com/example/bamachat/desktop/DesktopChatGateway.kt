package com.example.bamachat.desktop

import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRequest
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.AiPromptEngine
import com.example.bamachat.shared.core.ExtensionRuntimeDecision
import com.example.bamachat.shared.core.QuickActionSuggestion
import com.example.bamachat.shared.core.ai.AiEngine
import com.example.bamachat.shared.core.ai.AiProviderRegistry
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamDelta
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamEvent
import com.example.bamachat.shared.core.ai.AiStreamFinished
import com.example.bamachat.shared.core.ai.AiStreamStarted
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration

sealed class DesktopChatException(message: String) : Exception(message)
class DesktopMissingApiKeyException(provider: String) :
    DesktopChatException("$provider-API-Key fehlt in den Desktop-Einstellungen.")
class DesktopModelUnavailableException(provider: String, model: String) :
    DesktopChatException("Modell $model von $provider nicht verfügbar.")
class DesktopProviderHttpException(
    val provider: String,
    val statusCode: Int
) : DesktopChatException("$provider antwortet nicht (HTTP $statusCode).")
class DesktopStreamProtocolException(provider: String) :
    DesktopChatException("$provider lieferte ein ungültiges Streaming-Frame.")
class DesktopUnknownProviderException(cause: String) :
    DesktopChatException(cause)

class DesktopChatGateway internal constructor(
    private val transport: DesktopChatHttpTransport = JdkDesktopChatHttpTransport(),
    private val gson: Gson = Gson(),
    private val openRouterEndpoint: URI = URI.create(
        "https://openrouter.ai/api/v1/chat/completions"
    )
) {
    suspend fun requestAssistantReply(
        settings: DesktopUserSettings,
        chatHistory: List<AiChatMessage>,
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?
    ): String = withContext(Dispatchers.IO) {
        val request = settings.toAiChatRequest(
            chatHistory = chatHistory,
            quickAction = quickAction,
            runtimeDecision = runtimeDecision,
            stream = false
        )
        val registry = AiProviderRegistry()
            .register(DesktopAiProviderAdapter(settings, this@DesktopChatGateway))
        AiEngine(registry).chat(request).message.text
    }

    fun streamAssistantReply(
        settings: DesktopUserSettings,
        chatHistory: List<AiChatMessage>,
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?
    ): Flow<AiStreamEvent> {
        val request = settings.toAiChatRequest(
            chatHistory = chatHistory,
            quickAction = quickAction,
            runtimeDecision = runtimeDecision,
            stream = true
        )
        val registry = AiProviderRegistry()
            .register(DesktopAiProviderAdapter(settings, this))
        return AiEngine(registry).stream(request)
    }

    suspend fun chat(
        settings: DesktopUserSettings,
        request: AiChatRequest
    ): AiChatResponse = when (request.provider) {
        AiProviderId.OPENROUTER -> requestOpenRouter(settings, request)
        AiProviderId.OLLAMA -> requestOllama(settings, request)
        else -> throw IllegalStateException(
            "Desktop provider is not supported: ${request.provider}"
        )
    }

    fun stream(
        settings: DesktopUserSettings,
        request: AiChatRequest
    ): Flow<AiStreamEvent> = flow {
        emit(AiStreamStarted(provider = request.provider, model = request.model))
        val combinedText = StringBuilder()
        var retryUsed = false
        while (true) {
            try {
                streamOnce(settings, request) { delta ->
                    combinedText.append(delta)
                    emit(
                        AiStreamDelta(
                            text = delta,
                            provider = request.provider,
                            model = request.model
                        )
                    )
                }
                break
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!retryUsed && combinedText.isEmpty() && failure.isRetryableBeforeFirstDelta()) {
                    retryUsed = true
                    continue
                }
                emit(failure.toStreamError(request))
                emit(AiStreamFinished(provider = request.provider, model = request.model))
                return@flow
            }
        }
        val response = AiChatResponse(
            provider = request.provider,
            model = request.model,
            message = AiChatMessage(
                role = AiChatRole.ASSISTANT,
                text = combinedText.toString()
            )
        )
        emit(AiStreamCompleted(response))
        emit(AiStreamFinished(provider = request.provider, model = request.model))
    }.flowOn(Dispatchers.IO)

    private suspend fun streamOnce(
        settings: DesktopUserSettings,
        request: AiChatRequest,
        onDelta: suspend (String) -> Unit
    ) {
        when (request.provider) {
            AiProviderId.OPENROUTER -> streamOpenRouter(settings, request, onDelta)
            AiProviderId.OLLAMA -> streamOllama(settings, request, onDelta)
            else -> throw DesktopUnknownProviderException(
                "Desktop-Streaming unterstützt diesen Provider nicht."
            )
        }
    }

    private suspend fun requestOpenRouter(
        settings: DesktopUserSettings,
        request: AiChatRequest
    ): AiChatResponse {
        val responseBody = executeText(
            provider = "OpenRouter",
            request = buildOpenRouterRequest(settings, request, stream = false)
        )
        return AiChatResponse(
            provider = request.provider,
            model = request.model,
            message = AiChatMessage(
                role = AiChatRole.ASSISTANT,
                text = parseOpenRouterResponse(responseBody)
            )
        )
    }

    private suspend fun requestOllama(
        settings: DesktopUserSettings,
        request: AiChatRequest
    ): AiChatResponse {
        val responseBody = executeText(
            provider = "Ollama",
            request = buildOllamaRequest(settings, request, stream = false)
        )
        return AiChatResponse(
            provider = request.provider,
            model = request.model,
            message = AiChatMessage(
                role = AiChatRole.ASSISTANT,
                text = parseOllamaResponse(responseBody)
            )
        )
    }

    private suspend fun streamOpenRouter(
        settings: DesktopUserSettings,
        request: AiChatRequest,
        onDelta: suspend (String) -> Unit
    ) {
        val httpRequest = buildOpenRouterRequest(settings, request, stream = true)
        withStreamingResponse("OpenRouter", httpRequest) { reader ->
            parseOpenRouterStream(reader, onDelta)
        }
    }

    private suspend fun streamOllama(
        settings: DesktopUserSettings,
        request: AiChatRequest,
        onDelta: suspend (String) -> Unit
    ) {
        val httpRequest = buildOllamaRequest(settings, request, stream = true)
        withStreamingResponse("Ollama", httpRequest) { reader ->
            parseOllamaStream(reader, onDelta)
        }
    }

    private fun buildOpenRouterRequest(
        settings: DesktopUserSettings,
        request: AiChatRequest,
        stream: Boolean
    ): HttpRequest {
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isBlank()) throw DesktopMissingApiKeyException("OpenRouter")
        val body = gson.toJson(
            mapOf(
                "model" to request.model,
                "messages" to buildProviderMessages(request),
                "max_tokens" to request.maxTokens,
                "temperature" to request.temperature,
                "stream" to stream
            )
        )
        return HttpRequest.newBuilder()
            .uri(openRouterEndpoint)
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://bamachat.app")
            .header("X-Title", "BamaChat Desktop")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
    }

    private fun buildOllamaRequest(
        settings: DesktopUserSettings,
        request: AiChatRequest,
        stream: Boolean
    ): HttpRequest {
        val body = gson.toJson(
            mapOf(
                "model" to request.model,
                "messages" to buildProviderMessages(request),
                "stream" to stream
            )
        )
        return HttpRequest.newBuilder()
            .uri(URI.create("${normalizeBaseUrl(settings.ollamaBaseUrl)}api/chat"))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
    }

    private suspend fun executeText(provider: String, request: HttpRequest): String {
        val response = transport.execute(request)
        try {
            currentCoroutineContext().ensureActive()
            val bytes = runInterruptible(Dispatchers.IO) { response.body.readAllBytes() }
            val body = try {
                bytes.toString(StandardCharsets.UTF_8)
            } finally {
                bytes.fill(0)
            }
            ensureSuccess(provider, response.statusCode, body)
            return body
        } finally {
            response.closeQuietly()
        }
    }

    private suspend fun withStreamingResponse(
        provider: String,
        request: HttpRequest,
        block: suspend (BufferedReader) -> Unit
    ) {
        val response = transport.execute(request)
        try {
            currentCoroutineContext().ensureActive()
            if (response.statusCode !in 200..299) {
                throw DesktopProviderHttpException(provider, response.statusCode)
            }
            block(response.body.bufferedReader(StandardCharsets.UTF_8))
        } finally {
            response.closeQuietly()
        }
    }

    private suspend fun parseOpenRouterStream(
        reader: BufferedReader,
        onDelta: suspend (String) -> Unit
    ) {
        var completed = false
        var emittedText = false
        while (true) {
            val line = reader.readLineCancellable() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
            if (!trimmed.startsWith("data:")) {
                throw DesktopStreamProtocolException("OpenRouter")
            }
            val payload = trimmed.removePrefix("data:").trim()
            if (payload.isEmpty()) continue
            if (payload == "[DONE]") {
                completed = true
                break
            }
            val delta = parseOpenRouterStreamDelta(payload)
            if (!delta.isNullOrEmpty()) {
                emittedText = true
                onDelta(delta)
            }
        }
        if (!completed || !emittedText) throw DesktopStreamProtocolException("OpenRouter")
    }

    private suspend fun parseOllamaStream(
        reader: BufferedReader,
        onDelta: suspend (String) -> Unit
    ) {
        var completed = false
        var emittedText = false
        while (true) {
            val line = reader.readLineCancellable() ?: break
            val payload = line.trim()
            if (payload.isEmpty()) continue
            val frame = parseJsonObject(payload, "Ollama")
            if (!frame.getString("error").isNullOrBlank()) {
                throw DesktopUnknownProviderException("Ollama meldete einen Streaming-Fehler.")
            }
            val done = frame.getBoolean("done") ?: false
            val message = frame.getObject("message")
            if (message == null && !done) throw DesktopStreamProtocolException("Ollama")
            val delta = message?.getString("content")
            if (!delta.isNullOrEmpty()) {
                emittedText = true
                onDelta(delta)
            }
            if (done) {
                completed = true
                break
            }
        }
        if (!completed || !emittedText) throw DesktopStreamProtocolException("Ollama")
    }

    private fun parseOpenRouterStreamDelta(payload: String): String? {
        val frame = parseJsonObject(payload, "OpenRouter")
        if (frame.getObject("error") != null) {
            throw DesktopUnknownProviderException("OpenRouter meldete einen Streaming-Fehler.")
        }
        val choices = frame.getArray("choices")
            ?: throw DesktopStreamProtocolException("OpenRouter")
        if (choices.size() == 0) return null
        val delta = choices.firstObjectOrNull()?.getObject("delta")
            ?: throw DesktopStreamProtocolException("OpenRouter")
        return delta.getString("content")
    }

    private fun parseJsonObject(payload: String, provider: String): JsonObject = try {
        val element = JsonParser.parseString(payload)
        if (!element.isJsonObject) throw DesktopStreamProtocolException(provider)
        element.asJsonObject
    } catch (known: DesktopChatException) {
        throw known
    } catch (_: Exception) {
        throw DesktopStreamProtocolException(provider)
    }

    private fun buildProviderMessages(request: AiChatRequest): List<Map<String, String>> {
        val messagePayload = mutableListOf<Map<String, String>>()
        messagePayload += mapOf(
            "role" to "system",
            "content" to buildSystemPrompt(request.quickAction, request.runtimeDecision)
        )
        request.messages.forEach { message ->
            messagePayload += mapOf(
                "role" to message.role.asProviderRole(),
                "content" to message.text
            )
        }
        return messagePayload
    }

    private fun buildSystemPrompt(
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?
    ): String = AiPromptEngine.buildSystemPrompt(
        appName = "BamaChat Desktop",
        quickAction = quickAction,
        runtimeDecision = runtimeDecision
    )

    private fun parseOpenRouterResponse(body: String): String {
        val root = JsonParser.parseString(body).asJsonObject
        val errorMessage = root.getObject("error")?.getString("message")
        if (!errorMessage.isNullOrBlank()) {
            if (isModelUnavailableError(errorMessage, 0)) {
                throw DesktopModelUnavailableException("OpenRouter", "")
            }
            throw DesktopUnknownProviderException(errorMessage.take(120))
        }
        val first = root.getArray("choices")?.firstObjectOrNull()
            ?: throw DesktopUnknownProviderException("OpenRouter antwortete ohne choices.")
        val message = first.getObject("message")
            ?: throw DesktopUnknownProviderException("OpenRouter antwortete ohne message.")
        return message.getString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw DesktopUnknownProviderException("OpenRouter antwortete ohne Text.")
    }

    private fun parseOllamaResponse(body: String): String {
        val root = JsonParser.parseString(body).asJsonObject
        val errorMessage = root.getString("error")
        if (!errorMessage.isNullOrBlank()) {
            throw DesktopUnknownProviderException(errorMessage.take(120))
        }
        val message = root.getObject("message")
            ?: throw DesktopUnknownProviderException("Ollama antwortete ohne message.")
        return message.getString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw DesktopUnknownProviderException("Ollama antwortete ohne Text.")
    }

    private fun ensureSuccess(provider: String, statusCode: Int, body: String) {
        if (statusCode in 200..299) return
        val cleanError = runCatching {
            JsonParser.parseString(body).asJsonObject
                .getObject("error")
                ?.getString("message")
                ?.take(120)
        }.getOrNull()
        if (!cleanError.isNullOrBlank()) {
            if (isModelUnavailableError(cleanError, statusCode)) {
                throw DesktopModelUnavailableException(provider, "")
            }
            throw DesktopUnknownProviderException(cleanError)
        }
        if (isModelUnavailableError("", statusCode)) {
            throw DesktopModelUnavailableException(provider, "")
        }
        throw DesktopProviderHttpException(provider, statusCode)
    }

    private fun isModelUnavailableError(message: String, statusCode: Int): Boolean {
        val lower = message.lowercase()
        return statusCode == 402 || statusCode == 404 ||
            "unavailable" in lower ||
            "not found" in lower ||
            "free" in lower ||
            "quota" in lower ||
            "exceeded" in lower ||
            "paid" in lower
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().ifBlank { DEFAULT_OLLAMA_BASE_URL }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    private fun DesktopUserSettings.toAiChatRequest(
        chatHistory: List<AiChatMessage>,
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?,
        stream: Boolean
    ): AiChatRequest {
        val providerId = provider.toAiProviderId()
        val model = when (provider) {
            DesktopProvider.OPENROUTER -> openRouterModel.trim().ifBlank {
                DEFAULT_OPENROUTER_MODEL
            }
            DesktopProvider.OLLAMA -> ollamaModel.trim().ifBlank { DEFAULT_OLLAMA_MODEL }
        }
        return AiChatRequest(
            provider = providerId,
            model = model,
            messages = chatHistory,
            quickAction = quickAction,
            runtimeDecision = runtimeDecision,
            stream = stream
        )
    }

    private fun Exception.isRetryableBeforeFirstDelta(): Boolean = when (this) {
        is IOException -> true
        is DesktopProviderHttpException ->
            statusCode == 408 || statusCode == 429 || statusCode in 500..599
        else -> false
    }

    private fun Exception.toStreamError(request: AiChatRequest): AiStreamError {
        val message = when (this) {
            is DesktopMissingApiKeyException ->
                "OpenRouter API-Key fehlt. Bitte in den Einstellungen hinterlegen."
            is DesktopModelUnavailableException ->
                "Das ausgewählte Modell ist aktuell nicht verfügbar."
            is DesktopProviderHttpException ->
                "$provider antwortet nicht (HTTP $statusCode)."
            is DesktopStreamProtocolException -> message.orEmpty()
            is IOException -> "Die Streaming-Verbindung wurde unterbrochen."
            else -> "Die Streaming-Anfrage ist kontrolliert fehlgeschlagen."
        }
        return AiStreamError(
            message = message,
            exceptionClass = javaClass.simpleName,
            provider = request.provider,
            model = request.model
        )
    }

    private suspend fun BufferedReader.readLineCancellable(): String? =
        runInterruptible(Dispatchers.IO) { readLine() }

    private fun DesktopChatHttpResponse.closeQuietly() {
        try {
            close()
        } catch (_: Exception) {
        }
    }

    private fun AiChatRole.asProviderRole(): String = when (this) {
        AiChatRole.SYSTEM -> "system"
        AiChatRole.USER -> "user"
        AiChatRole.ASSISTANT -> "assistant"
    }

    private fun JsonObject.getString(key: String): String? {
        if (!has(key)) return null
        val value = get(key)
        if (value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            return null
        }
        return value.asString
    }

    private fun JsonObject.getBoolean(key: String): Boolean? {
        if (!has(key)) return null
        val value = get(key)
        if (value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) {
            throw DesktopStreamProtocolException("Ollama")
        }
        return value.asBoolean
    }

    private fun JsonObject.getObject(key: String): JsonObject? {
        if (!has(key)) return null
        val value = get(key)
        if (!value.isJsonObject) return null
        return value.asJsonObject
    }

    private fun JsonObject.getArray(key: String): JsonArray? {
        if (!has(key)) return null
        val value = get(key)
        if (!value.isJsonArray) return null
        return value.asJsonArray
    }

    private fun JsonArray.firstObjectOrNull(): JsonObject? {
        if (size() == 0) return null
        val first = get(0)
        if (!first.isJsonObject) return null
        return first.asJsonObject
    }
}
