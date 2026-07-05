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
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class DesktopChatGateway(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build(),
    private val gson: Gson = Gson()
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
            runtimeDecision = runtimeDecision
        )
        val registry = AiProviderRegistry()
            .register(DesktopAiProviderAdapter(settings, this@DesktopChatGateway))
        val response = AiEngine(registry).chat(request)
        response.message.text
    }

    suspend fun chat(
        settings: DesktopUserSettings,
        request: AiChatRequest
    ): AiChatResponse = withContext(Dispatchers.IO) {
        when (request.provider) {
            AiProviderId.OPENROUTER -> requestOpenRouter(settings, request)
            AiProviderId.OLLAMA -> requestOllama(settings, request)
            else -> throw IllegalStateException("Desktop provider is not supported: ${request.provider}")
        }
    }

    private fun requestOpenRouter(
        settings: DesktopUserSettings,
        request: AiChatRequest
    ): AiChatResponse {
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("OpenRouter API-Key fehlt in den Desktop-Einstellungen.")
        }
        val messages = buildProviderMessages(request)

        val body = gson.toJson(
            mapOf(
                "model" to request.model,
                "messages" to messages,
                "max_tokens" to request.maxTokens,
                "temperature" to request.temperature
            )
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://bamachat.app")
            .header("X-Title", "BamaChat Desktop")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(
            httpRequest,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        )
        ensureSuccess("OpenRouter", response.statusCode(), response.body())
        return AiChatResponse(
            provider = request.provider,
            model = request.model,
            message = AiChatMessage(
                role = AiChatRole.ASSISTANT,
                text = parseOpenRouterResponse(response.body())
            )
        )
    }

    private fun requestOllama(
        settings: DesktopUserSettings,
        request: AiChatRequest
    ): AiChatResponse {
        val baseUrl = normalizeBaseUrl(settings.ollamaBaseUrl)
        val messages = buildProviderMessages(request)
        val body = gson.toJson(
            mapOf(
                "model" to request.model,
                "messages" to messages,
                "stream" to request.stream
            )
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl}api/chat"))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(
            httpRequest,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        )
        ensureSuccess("Ollama", response.statusCode(), response.body())
        return AiChatResponse(
            provider = request.provider,
            model = request.model,
            message = AiChatMessage(
                role = AiChatRole.ASSISTANT,
                text = parseOllamaResponse(response.body())
            )
        )
    }

    private fun buildProviderMessages(
        request: AiChatRequest
    ): List<Map<String, String>> {
        val messagePayload = mutableListOf<Map<String, String>>()
        val systemPrompt = buildSystemPrompt(request.quickAction, request.runtimeDecision)
        messagePayload += mapOf("role" to "system", "content" to systemPrompt)
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
            throw IllegalStateException("OpenRouter Fehler: $errorMessage")
        }
        val choices = root.getArray("choices")
        val first = choices?.firstObjectOrNull()
            ?: throw IllegalStateException("OpenRouter Antwort ohne choices.")
        val message = first.getObject("message")
            ?: throw IllegalStateException("OpenRouter Antwort ohne message.")
        return message.getString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OpenRouter Antwort enthielt keinen Text.")
    }

    private fun parseOllamaResponse(body: String): String {
        val root = JsonParser.parseString(body).asJsonObject
        val errorMessage = root.getString("error")
        if (!errorMessage.isNullOrBlank()) {
            throw IllegalStateException("Ollama Fehler: $errorMessage")
        }
        val message = root.getObject("message")
            ?: throw IllegalStateException("Ollama Antwort ohne message.")
        return message.getString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Ollama Antwort enthielt keinen Text.")
    }

    private fun ensureSuccess(provider: String, statusCode: Int, body: String) {
        if (statusCode in 200..299) return
        val snippet = body.take(280).replace('\n', ' ')
        throw IllegalStateException("$provider Request fehlgeschlagen ($statusCode): $snippet")
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
        runtimeDecision: ExtensionRuntimeDecision?
    ): AiChatRequest {
        val provider = when (this.provider) {
            DesktopProvider.OPENROUTER -> DesktopProvider.OPENROUTER.toAiProviderId()
            DesktopProvider.OLLAMA -> DesktopProvider.OLLAMA.toAiProviderId()
        }
        val model = when (this.provider) {
            DesktopProvider.OPENROUTER -> openRouterModel.trim().ifBlank { DEFAULT_OPENROUTER_MODEL }
            DesktopProvider.OLLAMA -> ollamaModel.trim().ifBlank { DEFAULT_OLLAMA_MODEL }
        }
        return AiChatRequest(
            provider = provider,
            model = model,
            messages = chatHistory,
            quickAction = quickAction,
            runtimeDecision = runtimeDecision
        )
    }

    private fun AiChatRole.asProviderRole(): String = when (this) {
        AiChatRole.SYSTEM -> "system"
        AiChatRole.USER -> "user"
        AiChatRole.ASSISTANT -> "assistant"
    }

    private fun JsonObject.getString(key: String): String? {
        if (!has(key)) return null
        val value = get(key)
        if (value.isJsonNull) return null
        return value.asString
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
