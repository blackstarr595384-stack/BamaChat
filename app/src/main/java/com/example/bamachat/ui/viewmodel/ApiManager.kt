package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.ApiClient
import com.example.bamachat.data.OpenRouterChatRequest
import com.example.bamachat.data.OpenRouterImageUrl
import com.example.bamachat.data.OpenRouterMessage
import com.example.bamachat.data.OpenRouterStreamChunk
import com.example.bamachat.data.OpenRouterVisionChatRequest
import com.example.bamachat.data.OpenRouterVisionContentPart
import com.example.bamachat.data.OpenRouterVisionMessage
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.EmotionAnalyzer
import com.example.bamachat.util.EmotionSignal
import com.example.bamachat.util.MonetizationConfig
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.math.pow

/**
 * Zentraler API-Manager für alle Provider (OpenRouter, Groq, Cerebras, Together, Ollama, Gemini).
 * Kümmert sich um:
 * - Multi-Provider Fallback-Logik
 * - Streaming & One-Shot Requests
 * - Vision/Multimodal-Anfragen
 * - Retry-Logik mit Exponential Backoff
 * - Error-Recovery
 */
class ApiManager(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val webClient = OkHttpClient.Builder().build()
    private val researchCache = mutableMapOf<String, CachedResearch>()

    data class WebSource(
        val title: String,
        val url: String,
        val snippet: String,
        val publishedAt: String? = null
    )

    data class WebResearchResult(
        val success: Boolean,
        val query: String,
        val sources: List<WebSource> = emptyList(),
        val provider: String = "unknown",
        val fetchedAtIso: String = "",
        val error: String = ""
    )

    data class ProviderConfig(
        val provider: ApiClient.Provider,
        val apiKey: String,
        val model: String
    )

    data class ApiResponse(
        val success: Boolean,
        val content: String = "",
        val error: String = "",
        val usedProvider: ApiClient.Provider? = null
    )

    private data class CachedResearch(
        val cachedAtMs: Long,
        val value: WebResearchResult
    )

    /**
     * Stream-basierte Chat-Anfrage mit Multi-Provider Fallback & Retry-Logik
     */
    suspend fun streamChatResponse(
        systemPrompt: String,
        userMessages: List<OpenRouterMessage>,
        onChunkReceived: (String) -> Unit,
        onError: (String) -> Unit
    ): ApiResponse {
        val providers = buildProviderFallbackList()
        var lastError = ""

        for ((index, config) in providers.withIndex()) {
            val result = retryWithBackoff(maxAttempts = 2) {
                streamFromProvider(config, systemPrompt, userMessages, onChunkReceived)
            }

            if (result.success) {
                AppTelemetry.logEvent(
                    "api_stream_success",
                    mapOf("provider" to config.provider.id, "attempt" to (index + 1).toString())
                )
                return result
            }

            lastError = sanitizeSensitiveText(result.error)
            AppTelemetry.logEvent(
                "api_stream_fallback",
                mapOf(
                    "failed_provider" to config.provider.id,
                    "next_provider" to (if (index < providers.size - 1) providers[index + 1].provider.id else "none"),
                    "error" to lastError.take(100)
                )
            )

            if (index < providers.size - 1) {
                onError("${config.provider.id} fehlgeschlagen, versuche ${providers[index + 1].provider.id}...")
            }
        }

        onError("Alle Provider fehlgeschlagen. Letzer Fehler: $lastError")
        return ApiResponse(success = false, error = lastError)
    }

    /**
     * One-Shot Chat-Anfrage (für Persona-Perspektiven in Multi-Agent)
     */
    suspend fun generateReply(
        systemPrompt: String,
        userPrompt: String
    ): ApiResponse {
        val providers = buildProviderFallbackList()

        for (config in providers) {
            val result = retryWithBackoff(maxAttempts = 2) {
                oneShootFromProvider(config, systemPrompt, userPrompt)
            }

            if (result.success) {
                return result
            }
        }

        return ApiResponse(success = false, error = "Keine Provider verfügbar")
    }

    /**
     * Vision/Multimodal-Anfrage (Bild-Analyse)
     */
    suspend fun analyzeImage(
        systemPrompt: String,
        userText: String,
        imageDataUrl: String
    ): ApiResponse {
        val openRouterKey = getApiKeyForProvider(ApiClient.Provider.OPENROUTER)
        val geminiKey = prefs.getString("gemini_api_key", "")?.takeIf { it.isNotBlank() }

        // Versuche OpenRouter Vision zuerst
        if (openRouterKey != null) {
            val result = retryWithBackoff {
                visionViaOpenRouter(openRouterKey, systemPrompt, userText, imageDataUrl)
            }
            if (result.success) return result
        }

        // Fallback: Gemini
        if (!geminiKey.isNullOrBlank()) {
            return visionViaGemini(geminiKey, systemPrompt, userText)
        }

        return ApiResponse(success = false, error = "Kein Vision-Provider konfiguriert")
    }

    fun shouldUseLiveWebResearch(userText: String): Boolean {
        if (!prefs.getBoolean(KEY_LIVE_WEB_ENABLED, false)) return false
        val lower = userText.lowercase(Locale.getDefault())
        if (lower.contains("web:")) return true
        val triggerWords = listOf(
            "aktuell", "heute", "neueste", "latest", "news", "ticker", "kurs",
            "preis", "wetter", "ergebnis", "spiel", "update", "breaking",
            "recherchiere", "im internet", "online", "heise", "tagesschau",
            "github", "repo", "repository", "issue", "release", "2026"
        )
        return triggerWords.any { lower.contains(it) }
    }

    suspend fun runLiveWebResearch(query: String): WebResearchResult {
        val cleanedQuery = query.replace(Regex("\\s+"), " ").trim()
        if (cleanedQuery.isBlank()) {
            return WebResearchResult(
                success = false,
                query = query,
                error = "Leere Suchanfrage"
            )
        }
        if (!prefs.getBoolean(KEY_LIVE_WEB_ENABLED, false)) {
            return WebResearchResult(
                success = false,
                query = cleanedQuery,
                error = "Live-Web-Recherche ist deaktiviert."
            )
        }

        val ttlMinutes = prefs.getInt(KEY_LIVE_WEB_CACHE_TTL_MINUTES, 10).coerceIn(1, 120)
        val ttlMs = ttlMinutes * 60_000L
        val now = System.currentTimeMillis()
        val cached = researchCache[cleanedQuery]
        if (cached != null && now - cached.cachedAtMs <= ttlMs) {
            return cached.value
        }

        val endpoint = prefs.getString(KEY_LIVE_WEB_ENDPOINT, "")?.trim().orEmpty()
        if (endpoint.isBlank()) {
            return WebResearchResult(
                success = false,
                query = cleanedQuery,
                error = "Kein Live-Web-Endpunkt konfiguriert."
            )
        }

        val maxResults = prefs.getInt(KEY_LIVE_WEB_MAX_RESULTS, 5).coerceIn(2, 8)
        val allowlist = prefs.getString(KEY_LIVE_WEB_ALLOWED_DOMAINS, DEFAULT_ALLOWED_DOMAINS)
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()

        if (isWeatherQuery(cleanedQuery)) {
            WEATHER_ALLOWED_DOMAINS.forEach { allowlist.add(it) }
        }

        val payload = JsonObject().apply {
            addProperty("query", cleanedQuery)
            addProperty("maxResults", maxResults)
            add("allowedDomains", JsonArray().also { arr -> allowlist.forEach { arr.add(it) } })
            addProperty("locale", Locale.getDefault().toLanguageTag())
            addProperty("safeSearch", true)
            addProperty(
                "preferGithub",
                prefs.getBoolean(KEY_LIVE_WEB_PREFER_GITHUB, true) || isCodingQuery(cleanedQuery)
            )
        }

        val token = prefs.getString(KEY_LIVE_WEB_API_TOKEN, "")?.trim().orEmpty()
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))

        if (token.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return withContext(Dispatchers.IO) {
            try {
                val response = webClient.newCall(requestBuilder.build()).execute()
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = "Web-Recherche fehlgeschlagen (${response.code}): ${body.take(160)}"
                    AppTelemetry.logEvent(
                        "web_research_error",
                        mapOf("code" to response.code.toString(), "error" to error.take(120))
                    )
                    return@withContext WebResearchResult(
                        success = false,
                        query = cleanedQuery,
                        error = error
                    )
                }

                val parsed = parseWebResearchResponse(cleanedQuery, body)
                if (parsed.success) {
                    AppTelemetry.logEvent(
                        "web_research_success",
                        mapOf("sources" to parsed.sources.size.toString(), "provider" to parsed.provider)
                    )
                    pruneResearchCache(now, ttlMs)
                    researchCache[cleanedQuery] = CachedResearch(now, parsed)
                }
                parsed
            } catch (e: Exception) {
                AppTelemetry.logError("web_research_exception", e)
                WebResearchResult(
                    success = false,
                    query = cleanedQuery,
                    error = e.message ?: "Unbekannter Web-Fehler"
                )
            }
        }
    }

    // ===== Privat: Provider-spezifische Implementierungen =====

    private suspend fun streamFromProvider(
        config: ProviderConfig,
        systemPrompt: String,
        userMessages: List<OpenRouterMessage>,
        onChunkReceived: (String) -> Unit
    ): ApiResponse {
        return try {
            val request = OpenRouterChatRequest(
                model = config.model,
                messages = listOf(OpenRouterMessage("system", systemPrompt)) + userMessages,
                maxTokens = 1024,
                temperature = 0.7f,
                stream = true
            )

            val service = ApiClient.createOpenAICompatibleService(config.provider, config.apiKey)
            val response = service.chatCompletionStream(request)

            if (!response.isSuccessful) {
                val code = response.code()
                val body = response.errorBody()?.string() ?: ""
                return ApiResponse(
                    success = false,
                    error = formatHttpError(code, sanitizeSensitiveText(body)),
                    usedProvider = config.provider
                )
            }

            val body = response.body() ?: return ApiResponse(success = false, error = "Empty response body")
            val builder = StringBuilder()
            var persistCounter = 0

            withContext(Dispatchers.IO) {
                body.byteStream().bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (!l.startsWith("data:")) continue
                        val payload = l.removePrefix("data:").trim()
                        if (payload.isBlank() || payload == "[DONE]") continue

                        try {
                            val chunk = gson.fromJson(payload, OpenRouterStreamChunk::class.java)
                            val delta = chunk.choices?.firstOrNull()?.delta?.content
                            if (!delta.isNullOrEmpty()) {
                                builder.append(delta)
                                onChunkReceived(delta)
                                persistCounter++
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            ApiResponse(success = true, content = builder.toString(), usedProvider = config.provider)
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Unknown error"),
                usedProvider = config.provider
            )
        }
    }

    private suspend fun oneShootFromProvider(
        config: ProviderConfig,
        systemPrompt: String,
        userPrompt: String
    ): ApiResponse {
        return try {
            val service = ApiClient.createOpenAICompatibleService(config.provider, config.apiKey)
            val request = OpenRouterChatRequest(
                model = config.model,
                messages = listOf(
                    OpenRouterMessage("system", systemPrompt),
                    OpenRouterMessage("user", userPrompt)
                ),
                maxTokens = 800,
                temperature = 0.65f,
                stream = false
            )

            val response = service.chatCompletion(request)
            val content = response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()

            if (content.isBlank()) {
                return ApiResponse(success = false, error = "Empty response", usedProvider = config.provider)
            }

            ApiResponse(success = true, content = content, usedProvider = config.provider)
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Unknown error"),
                usedProvider = config.provider
            )
        }
    }

    private suspend fun visionViaOpenRouter(
        apiKey: String,
        systemPrompt: String,
        userText: String,
        imageDataUrl: String
    ): ApiResponse {
        return try {
            val modelId = ApiClient.OPENROUTER_DEFAULT_VISION_MODEL
            val request = OpenRouterVisionChatRequest(
                model = modelId,
                messages = listOf(
                    OpenRouterVisionMessage(
                        role = "system",
                        content = listOf(
                            OpenRouterVisionContentPart(type = "text", text = systemPrompt)
                        )
                    ),
                    OpenRouterVisionMessage(
                        role = "user",
                        content = listOf(
                            OpenRouterVisionContentPart(type = "text", text = userText),
                            OpenRouterVisionContentPart(
                                type = "image_url",
                                imageUrl = OpenRouterImageUrl(url = imageDataUrl)
                            )
                        )
                    )
                ),
                maxTokens = 1024,
                temperature = 0.4f,
                stream = false
            )

            val service = ApiClient.createOpenAICompatibleService(ApiClient.Provider.OPENROUTER, apiKey)
            val response = service.chatCompletionVision(request)
            val content = response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()

            if (content.isBlank()) {
                return ApiResponse(success = false, error = "Empty vision response")
            }

            ApiResponse(success = true, content = content, usedProvider = ApiClient.Provider.OPENROUTER)
        } catch (e: Exception) {
            ApiResponse(success = false, error = sanitizeSensitiveText(e.message ?: "Vision error"))
        }
    }

    private suspend fun visionViaGemini(
        apiKey: String,
        systemPrompt: String,
        userText: String
    ): ApiResponse {
        // Placeholder: würde Bitmap benötigen
        return ApiResponse(success = false, error = "Gemini Vision not implemented yet")
    }

    // ===== Hilfsfunktionen =====

    private fun buildProviderFallbackList(): List<ProviderConfig> {
        val multiEnabled = prefs.getBoolean("multi_provider", true)

        val cerebrasKey = getApiKeyForProvider(ApiClient.Provider.CEREBRAS)
        val groqKey = getApiKeyForProvider(ApiClient.Provider.GROQ)
        val openRouterKey = getApiKeyForProvider(ApiClient.Provider.OPENROUTER)
        val togetherKey = getApiKeyForProvider(ApiClient.Provider.TOGETHER)
        val openRouterModel = prefs.getString("openrouter_model", "google/gemma-3-27b-it:free") ?: "google/gemma-3-27b-it:free"

        return if (multiEnabled) {
            listOfNotNull(
                cerebrasKey?.let { ProviderConfig(ApiClient.Provider.CEREBRAS, it, ApiClient.CEREBRAS_DEFAULT) },
                groqKey?.let { ProviderConfig(ApiClient.Provider.GROQ, it, ApiClient.GROQ_DEFAULT) },
                openRouterKey?.let { ProviderConfig(ApiClient.Provider.OPENROUTER, it, openRouterModel) },
                togetherKey?.let { ProviderConfig(ApiClient.Provider.TOGETHER, it, ApiClient.TOGETHER_DEFAULT) }
            )
        } else {
            val explicit = prefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter"
            listOfNotNull(
                when (explicit) {
                    "OpenRouter" -> openRouterKey?.let { ProviderConfig(ApiClient.Provider.OPENROUTER, it, openRouterModel) }
                    "Groq" -> groqKey?.let { ProviderConfig(ApiClient.Provider.GROQ, it, ApiClient.GROQ_DEFAULT) }
                    "Cerebras" -> cerebrasKey?.let { ProviderConfig(ApiClient.Provider.CEREBRAS, it, ApiClient.CEREBRAS_DEFAULT) }
                    "Together" -> togetherKey?.let { ProviderConfig(ApiClient.Provider.TOGETHER, it, ApiClient.TOGETHER_DEFAULT) }
                    else -> null
                }
            )
        }
    }

    private fun getApiKeyForProvider(provider: ApiClient.Provider): String? {
        val prefKey = when (provider) {
            ApiClient.Provider.OPENROUTER -> "openrouter_api_key"
            ApiClient.Provider.GROQ -> "groq_api_key"
            ApiClient.Provider.CEREBRAS -> "cerebras_api_key"
            ApiClient.Provider.TOGETHER -> "together_api_key"
        }
        return prefs.getString(prefKey, null)?.takeIf { it.length > 10 }
    }

    /**
     * Retry mit Exponential Backoff: 0.5s, 1s, 2s, etc.
     */
    private suspend inline fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var attempt = 0

        while (attempt < maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                attempt++
                if (attempt >= maxAttempts) break

                val delayMs = (initialDelayMs * 2.0.pow(attempt - 1)).toLong()
                delay(delayMs)
            }
        }

        throw lastException ?: Exception("Max retries exceeded")
    }

    private fun formatHttpError(code: Int, body: String?): String = when (code) {
        401 -> "API-Key ungültig (401). Prüfe deinen Key in Einstellungen."
        402 -> "Guthaben aufgebraucht (402)."
        403 -> "Zugriff verweigert (403)."
        404 -> "Modell nicht gefunden (404)."
        429 -> "Rate-Limit erreicht (429). Warte oder wechsle Provider."
        500, 502, 503 -> "Server-Fehler ($code). Gleich nochmal."
        else -> "HTTP $code: ${body?.take(100) ?: ""}"
    }

    private fun sanitizeSensitiveText(raw: String): String {
        if (raw.isBlank()) return raw
        val masked = raw
            .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE), "Bearer ***")
            .replace(Regex("sk-or-[A-Za-z0-9_\\-]+", RegexOption.IGNORE_CASE), "sk-or-***")
            .replace(Regex("gsk_[A-Za-z0-9_\\-]+", RegexOption.IGNORE_CASE), "gsk_***")
            .replace(Regex("csk-[A-Za-z0-9_\\-]+", RegexOption.IGNORE_CASE), "csk-***")
            .replace(Regex("\"api[_-]?key\"\\s*:\\s*\"[^\"]+\"", RegexOption.IGNORE_CASE), "\"apiKey\":\"***\"")
        if (!prefs.getBoolean("privacy_strict_mode_enabled", true)) {
            return masked
        }
        return masked
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[url]")
            .replace(Regex("[\\r\\n]+"), " ")
            .take(140)
    }

    private fun parseWebResearchResponse(query: String, body: String): WebResearchResult {
        return try {
            val root = gson.fromJson(body, JsonObject::class.java)
            val items = root.getAsJsonArray("results")
            val sources = items?.mapNotNull { item ->
                val obj = item?.asJsonObject ?: return@mapNotNull null
                val url = obj.get("url")?.asString?.trim().orEmpty()
                val title = obj.get("title")?.asString?.trim().orEmpty()
                if (url.isBlank() || title.isBlank()) return@mapNotNull null
                WebSource(
                    title = title,
                    url = url,
                    snippet = obj.get("snippet")?.asString?.trim().orEmpty(),
                    publishedAt = obj.get("publishedAt")?.asString?.trim()
                )
            }.orEmpty()

            WebResearchResult(
                success = sources.isNotEmpty(),
                query = root.get("query")?.asString ?: query,
                sources = sources,
                provider = root.get("provider")?.asString ?: "unknown",
                fetchedAtIso = root.get("fetchedAt")?.asString ?: "",
                error = if (sources.isEmpty()) "Keine Treffer für Live-Recherche." else ""
            )
        } catch (e: Exception) {
            WebResearchResult(
                success = false,
                query = query,
                error = "Ungültige Web-Recherche-Antwort: ${e.message}"
            )
        }
    }

    private fun pruneResearchCache(now: Long, ttlMs: Long) {
        val iterator = researchCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.cachedAtMs > ttlMs) {
                iterator.remove()
            }
        }
    }

    private fun isWeatherQuery(query: String): Boolean {
        val lower = query.lowercase(Locale.getDefault())
        val weatherKeywords = listOf(
            "wetter", "temperatur", "regen", "wind", "vorhersage",
            "forecast", "weather", "niederschlag", "gewitter"
        )
        return weatherKeywords.any { lower.contains(it) }
    }

    private fun isCodingQuery(query: String): Boolean {
        val lower = query.lowercase(Locale.getDefault())
        val codingKeywords = listOf(
            "github", "repo", "repository", "issue", "pull request",
            "kotlin", "android", "compose", "gradle", "api", "sdk",
            "code", "bug", "exception", "stacktrace", "library"
        )
        return codingKeywords.any { lower.contains(it) }
    }

    companion object {
        private const val KEY_LIVE_WEB_ENABLED = "live_web_enabled"
        private const val KEY_LIVE_WEB_ENDPOINT = "live_web_endpoint"
        private const val KEY_LIVE_WEB_API_TOKEN = "live_web_api_token"
        private const val KEY_LIVE_WEB_ALLOWED_DOMAINS = "live_web_allowed_domains"
        private const val KEY_LIVE_WEB_PREFER_GITHUB = "live_web_prefer_github"
        private const val KEY_LIVE_WEB_MAX_RESULTS = "live_web_max_results"
        private const val KEY_LIVE_WEB_CACHE_TTL_MINUTES = "live_web_cache_ttl_minutes"
        private const val DEFAULT_ALLOWED_DOMAINS =
            "wikipedia.org,reuters.com,tagesschau.de,bundesregierung.de,heise.de,github.com"
        private val WEATHER_ALLOWED_DOMAINS = setOf(
            "dwd.de",
            "wetteronline.de",
            "wetter.com",
            "open-meteo.com",
            "meteoblue.com"
        )
    }
}
