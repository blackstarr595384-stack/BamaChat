package com.example.bamachat.desktop

import com.example.bamachat.shared.core.ExtensionRuntimeDecision
import com.example.bamachat.shared.core.QuickActionSuggestion
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

enum class DesktopChatRole {
    USER,
    ASSISTANT
}

data class DesktopChatMessage(
    val role: DesktopChatRole,
    val text: String
)

class DesktopChatGateway(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build(),
    private val gson: Gson = Gson()
) {
    suspend fun requestAssistantReply(
        settings: DesktopUserSettings,
        chatHistory: List<DesktopChatMessage>,
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?
    ): String = withContext(Dispatchers.IO) {
        when (settings.provider) {
            DesktopProvider.OPENROUTER -> requestOpenRouter(
                settings = settings,
                chatHistory = chatHistory,
                quickAction = quickAction,
                runtimeDecision = runtimeDecision
            )
            DesktopProvider.OLLAMA -> requestOllama(
                settings = settings,
                chatHistory = chatHistory,
                quickAction = quickAction,
                runtimeDecision = runtimeDecision
            )
        }
    }

    private fun requestOpenRouter(
        settings: DesktopUserSettings,
        chatHistory: List<DesktopChatMessage>,
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?
    ): String {
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("OpenRouter API-Key fehlt in den Desktop-Einstellungen.")
        }
        val model = settings.openRouterModel.trim().ifBlank { DEFAULT_OPENROUTER_MODEL }
        val messages = buildProviderMessages(
            chatHistory = chatHistory,
            quickAction = quickAction,
            runtimeDecision = runtimeDecision
        )

        val body = gson.toJson(
            mapOf(
                "model" to model,
                "messages" to messages,
                "max_tokens" to 1200,
                "temperature" to 0.7
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://bamachat.app")
            .header("X-Title", "BamaChat Desktop")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        )
        ensureSuccess("OpenRouter", response.statusCode(), response.body())
        return parseOpenRouterResponse(response.body())
    }

    private fun requestOllama(
        settings: DesktopUserSettings,
        chatHistory: List<DesktopChatMessage>,
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?
    ): String {
        val baseUrl = normalizeBaseUrl(settings.ollamaBaseUrl)
        val model = settings.ollamaModel.trim().ifBlank { DEFAULT_OLLAMA_MODEL }
        val messages = buildProviderMessages(
            chatHistory = chatHistory,
            quickAction = quickAction,
            runtimeDecision = runtimeDecision
        )
        val body = gson.toJson(
            mapOf(
                "model" to model,
                "messages" to messages,
                "stream" to false
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl}api/chat"))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        )
        ensureSuccess("Ollama", response.statusCode(), response.body())
        return parseOllamaResponse(response.body())
    }

    private fun buildProviderMessages(
        chatHistory: List<DesktopChatMessage>,
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?
    ): List<Map<String, String>> {
        val messagePayload = mutableListOf<Map<String, String>>()
        val systemPrompt = buildSystemPrompt(quickAction, runtimeDecision)
        messagePayload += mapOf("role" to "system", "content" to systemPrompt)
        chatHistory.forEach { message ->
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
    ): String {
        val lines = mutableListOf(
            "Du bist BamaChat Desktop.",
            "Antworte klar, direkt und in der Sprache der letzten Nutzernachricht.",
            "Strukturiere Ergebnisse so, dass sie direkt umsetzbar sind."
        )
        when (quickAction) {
            QuickActionSuggestion.RESEARCH -> {
                lines += "Quick Action: Research. Liefere belastbare Aussagen und nenne Unsicherheiten klar."
            }
            QuickActionSuggestion.CODE_REVIEW -> {
                lines += "Quick Action: Code Review. Priorisiere Bugs, Risiken, Fixes und Tests."
            }
            QuickActionSuggestion.PLAN -> {
                lines += "Quick Action: Plan. Gib priorisierte Schritte mit Verantwortlichkeit und Reihenfolge."
            }
            QuickActionSuggestion.AUTO -> Unit
        }
        runtimeDecision?.let { decision ->
            lines += "Extension-Kontext:"
            lines += decision.promptContext
            if (decision.forceWebResearch) {
                lines += "Hinweis: Wenn aktuelle Fakten fehlen, explizit sagen, welche Quellen der User selbst nachziehen soll."
            }
        }
        return lines.joinToString("\n")
    }

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

    private fun DesktopChatRole.asProviderRole(): String = when (this) {
        DesktopChatRole.USER -> "user"
        DesktopChatRole.ASSISTANT -> "assistant"
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
