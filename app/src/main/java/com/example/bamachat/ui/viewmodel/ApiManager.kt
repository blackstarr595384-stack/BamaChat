package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.ApiClient
import com.example.bamachat.data.OpenCodeZenMessage
import com.example.bamachat.data.OpenCodeZenRequest
import com.example.bamachat.data.OpenCodeZenResponsesInputContent
import com.example.bamachat.data.OpenCodeZenResponsesInputItem
import com.example.bamachat.data.OpenCodeZenResponsesRequest
import com.example.bamachat.data.OpenCodeZenResponsesResponse
import com.example.bamachat.data.OpenRouterChatRequest
import com.example.bamachat.data.OpenRouterChatResponse
import com.example.bamachat.data.OpenRouterChoice
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
import com.example.bamachat.util.SecureSettingsStore
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
import java.io.IOException
import java.util.LinkedHashMap
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.CancellationException
import kotlin.math.pow

/**
 * Zentraler API-Manager für alle Provider (OpenRouter, OpenCode, Groq, Cerebras, Together, Ollama, Gemini).
 * Kümmert sich um:
 * - Multi-Provider Fallback-Logik
 * - Streaming & One-Shot Requests
 * - Vision/Multimodal-Anfragen
 * - Retry-Logik mit Exponential Backoff
 * - Error-Recovery
 */
class ApiManager(
    private val app: Application,
    private val gson: Gson = Gson()
) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val webClient = OkHttpClient.Builder().build()
    // Size-bounded cache: evicts oldest entry when limit reached (prevents OOM)
    private val researchCache = object : LinkedHashMap<String, CachedResearch>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResearch>?): Boolean {
            return size > MAX_RESEARCH_CACHE_SIZE
        }
    }

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
        val error: String = "",
        val retryable: Boolean = true
    )

    data class ProviderConfig(
        val provider: ApiClient.Provider,
        val apiKey: String,
        val model: String,
        val baseUrlOverride: String? = null
    )

    data class ApiResponse(
        val success: Boolean,
        val content: String = "",
        val error: String = "",
        val usedProvider: ApiClient.Provider? = null,
        val retryable: Boolean = true
    )

    private data class CachedResearch(
        val cachedAtMs: Long,
        val value: WebResearchResult
    )

    private data class ChatCompletionAttempt(
        val response: OpenRouterChatResponse? = null,
        val retryable: Boolean = false
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
            val result = retryWithBackoff(
                maxAttempts = 2,
                shouldRetryResult = { response -> !response.success && response.retryable }
            ) {
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
        return ApiResponse(success = false, error = lastError, retryable = false)
    }

    /**
     * One-Shot Chat-Anfrage (für Persona-Perspektiven in Multi-Agent)
     */
    suspend fun generateReply(
        systemPrompt: String,
        userPrompt: String
    ): ApiResponse {
        val providers = buildProviderFallbackList()
        var lastError = ""

        for (config in providers) {
            val result = retryWithBackoff(
                maxAttempts = 2,
                shouldRetryResult = { response -> !response.success && response.retryable }
            ) {
                oneShootFromProvider(config, systemPrompt, userPrompt)
            }

            if (result.success) {
                return result
            }

            lastError = sanitizeSensitiveText(result.error)
        }

        return ApiResponse(
            success = false,
            error = if (lastError.isNotBlank()) lastError else "Keine Provider verfügbar",
            retryable = false
        )
    }

    suspend fun oneShotChatCompletion(
        request: OpenRouterChatRequest,
        systemPrompt: String
    ): OpenRouterChatResponse? {
        val providers = buildProviderFallbackList()
        var lastError: String? = null
        for (config in providers) {
            val attempt = retryWithBackoff(
                maxAttempts = 2,
                shouldRetryResult = { result -> result.retryable },
                shouldRetryException = { exception -> isRetryableThrowable(exception) }
            ) {
                try {
                    val messagesWithSystem = listOf(OpenRouterMessage("system", systemPrompt)) + request.messages
                    val response = if (isOpenCodeZenConfig(config)) {
                        if (isOpenCodeResponsesModel(config.model)) {
                            chatCompletionViaOpenCodeResponses(
                                config = config,
                                messages = messagesWithSystem,
                                maxTokens = request.maxTokens,
                                temperature = request.temperature
                            )
                        } else {
                            chatCompletionViaOpenCodeZen(
                                config = config,
                                messages = messagesWithSystem,
                                maxTokens = request.maxTokens,
                                temperature = request.temperature
                            )
                        }
                    } else {
                        val service = createServiceForProvider(config)
                        val fullRequest = request.copy(
                            model = config.model,
                            messages = messagesWithSystem
                        )
                        service.chatCompletion(fullRequest)
                    }
                    if (response.choices?.isNotEmpty() == true) {
                        ChatCompletionAttempt(response = response)
                    } else {
                        lastError = "Leere Antwort von ${config.provider.id}"
                        ChatCompletionAttempt(retryable = true)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (e is HttpException) {
                        val code = e.code()
                        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                        lastError = formatHttpError(code, sanitizeSensitiveText(body ?: ""))
                        if (!isRetryableHttpCode(code)) {
                            ChatCompletionAttempt(retryable = false)
                        } else {
                            ChatCompletionAttempt(retryable = true)
                        }
                    } else {
                        lastError = sanitizeSensitiveText(e.message ?: "Unknown error")
                    }
                    if (isRetryableThrowable(e)) {
                        ChatCompletionAttempt(retryable = true)
                    } else {
                        ChatCompletionAttempt(retryable = false)
                    }
                }
            }
            attempt.response?.let { return it }
        }
        if (!lastError.isNullOrBlank()) {
            throw IllegalStateException(lastError)
        }
        return null
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
        val geminiKey = secureString("gemini_api_key").takeIf { it.isNotBlank() }

        // Versuche OpenRouter Vision zuerst
        if (openRouterKey != null) {
            val result = retryWithBackoff(
                maxAttempts = 2,
                shouldRetryResult = { response -> !response.success && response.retryable }
            ) {
                visionViaOpenRouter(openRouterKey, systemPrompt, userText, imageDataUrl)
            }
            if (result.success) return result
        }

        // Fallback: Gemini
        if (!geminiKey.isNullOrBlank()) {
            return visionViaGemini(geminiKey, systemPrompt, userText)
        }

        return ApiResponse(
            success = false,
            error = "Kein Vision-Provider konfiguriert",
            retryable = false
        )
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
                error = "Leere Suchanfrage",
                retryable = false
            )
        }
        if (!prefs.getBoolean(KEY_LIVE_WEB_ENABLED, false)) {
            return WebResearchResult(
                success = false,
                query = cleanedQuery,
                error = "Live-Web-Recherche ist deaktiviert.",
                retryable = false
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
                error = "Kein Live-Web-Endpunkt konfiguriert.",
                retryable = false
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

        val token = secureString(KEY_LIVE_WEB_API_TOKEN).trim()
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))

        if (token.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return retryWithBackoff(
            maxAttempts = 3,
            initialDelayMs = 750,
            shouldRetryResult = { result -> !result.success && result.retryable }
        ) {
            withContext(Dispatchers.IO) {
                try {
                    val response = webClient.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val retryable = isRetryableHttpCode(response.code)
                        val error = "Web-Recherche fehlgeschlagen (${response.code}): ${body.take(160)}"
                        AppTelemetry.logEvent(
                            "web_research_error",
                            mapOf("code" to response.code.toString(), "error" to error.take(120))
                        )
                        return@withContext WebResearchResult(
                            success = false,
                            query = cleanedQuery,
                            error = error,
                            retryable = retryable
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
                    if (e is CancellationException) throw e
                    AppTelemetry.logError("web_research_exception", e)
                    WebResearchResult(
                        success = false,
                        query = cleanedQuery,
                        error = e.message ?: "Unbekannter Web-Fehler",
                        retryable = isRetryableThrowable(e)
                    )
                }
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
        if (isOpenCodeZenConfig(config)) {
            return if (isOpenCodeResponsesModel(config.model)) {
                streamFromOpenCodeResponses(config, systemPrompt, userMessages, onChunkReceived)
            } else {
                streamFromOpenCodeZen(config, systemPrompt, userMessages, onChunkReceived)
            }
        }

        return try {
            val request = OpenRouterChatRequest(
                model = config.model,
                messages = listOf(OpenRouterMessage("system", systemPrompt)) + userMessages,
                maxTokens = 4096,
                temperature = 0.7f,
                stream = true
            )

            val service = createServiceForProvider(config)
            val response = service.chatCompletionStream(request)

            if (!response.isSuccessful) {
                val code = response.code()
                val body = response.errorBody()?.string() ?: ""
                return ApiResponse(
                    success = false,
                    error = formatHttpError(code, sanitizeSensitiveText(body)),
                    usedProvider = config.provider,
                    retryable = isRetryableHttpCode(code)
                )
            }

            val body = response.body() ?: return ApiResponse(
                success = false,
                error = "Empty response body",
                usedProvider = config.provider,
                retryable = true
            )
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

            ApiResponse(
                success = true,
                content = builder.toString(),
                usedProvider = config.provider,
                retryable = false
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Unknown error"),
                usedProvider = config.provider,
                retryable = isRetryableThrowable(e)
            )
        }
    }

    private suspend fun oneShootFromProvider(
        config: ProviderConfig,
        systemPrompt: String,
        userPrompt: String
    ): ApiResponse {
        if (isOpenCodeZenConfig(config)) {
            return if (isOpenCodeResponsesModel(config.model)) {
                oneShotFromOpenCodeResponses(config, systemPrompt, userPrompt)
            } else {
                oneShotFromOpenCodeZen(config, systemPrompt, userPrompt)
            }
        }

        return try {
            val service = createServiceForProvider(config)
            val request = OpenRouterChatRequest(
                model = config.model,
                messages = listOf(
                    OpenRouterMessage("system", systemPrompt),
                    OpenRouterMessage("user", userPrompt)
                ),
                maxTokens = 2048,
                temperature = 0.65f,
                stream = false
            )

            val response = service.chatCompletion(request)
            val content = response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()

            if (content.isBlank()) {
                return ApiResponse(
                    success = false,
                    error = "Empty response",
                    usedProvider = config.provider,
                    retryable = true
                )
            }

            ApiResponse(
                success = true,
                content = content,
                usedProvider = config.provider,
                retryable = false
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Unknown error"),
                usedProvider = config.provider,
                retryable = isRetryableThrowable(e)
            )
        }
    }

    private suspend fun streamFromOpenCodeZen(
        config: ProviderConfig,
        systemPrompt: String,
        userMessages: List<OpenRouterMessage>,
        onChunkReceived: (String) -> Unit
    ): ApiResponse {
        return try {
            val service = createOpenCodeZenServiceForProvider(config)
            val request = buildOpenCodeZenRequest(
                model = config.model,
                messages = listOf(OpenRouterMessage("system", systemPrompt)) + userMessages,
                maxTokens = 4096,
                temperature = 0.7f
            )
            val response = service.message(request)
            val content = extractOpenCodeZenText(response)

            if (content.isBlank()) {
                return ApiResponse(
                    success = false,
                    error = response.error?.message?.takeIf { it.isNotBlank() } ?: "Empty response",
                    usedProvider = config.provider,
                    retryable = true
                )
            }

            onChunkReceived(content)
            ApiResponse(
                success = true,
                content = content,
                usedProvider = config.provider,
                retryable = false
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is HttpException) {
                val code = e.code()
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                return ApiResponse(
                    success = false,
                    error = formatHttpError(code, sanitizeSensitiveText(body ?: "")),
                    usedProvider = config.provider,
                    retryable = isRetryableHttpCode(code)
                )
            }
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Unknown error"),
                usedProvider = config.provider,
                retryable = isRetryableThrowable(e)
            )
        }
    }

    private suspend fun oneShotFromOpenCodeZen(
        config: ProviderConfig,
        systemPrompt: String,
        userPrompt: String
    ): ApiResponse {
        return try {
            val service = createOpenCodeZenServiceForProvider(config)
            val request = buildOpenCodeZenRequest(
                model = config.model,
                messages = listOf(
                    OpenRouterMessage("system", systemPrompt),
                    OpenRouterMessage("user", userPrompt)
                ),
                maxTokens = 2048,
                temperature = 0.65f
            )
            val response = service.message(request)
            val content = extractOpenCodeZenText(response)

            if (content.isBlank()) {
                return ApiResponse(
                    success = false,
                    error = response.error?.message?.takeIf { it.isNotBlank() } ?: "Empty response",
                    usedProvider = config.provider,
                    retryable = true
                )
            }

            ApiResponse(
                success = true,
                content = content,
                usedProvider = config.provider,
                retryable = false
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is HttpException) {
                val code = e.code()
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                return ApiResponse(
                    success = false,
                    error = formatHttpError(code, sanitizeSensitiveText(body ?: "")),
                    usedProvider = config.provider,
                    retryable = isRetryableHttpCode(code)
                )
            }
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Unknown error"),
                usedProvider = config.provider,
                retryable = isRetryableThrowable(e)
            )
        }
    }

    private suspend fun streamFromOpenCodeResponses(
        config: ProviderConfig,
        systemPrompt: String,
        userMessages: List<OpenRouterMessage>,
        onChunkReceived: (String) -> Unit
    ): ApiResponse {
        return try {
            val service = createOpenCodeResponsesServiceForProvider(config)
            val request = buildOpenCodeResponsesRequest(
                model = config.model,
                messages = listOf(OpenRouterMessage("system", systemPrompt)) + userMessages,
                maxTokens = 4096,
                temperature = 0.7f
            )
            val response = service.response(request)
            val content = extractOpenCodeResponsesText(response)
            if (content.isBlank()) {
                return ApiResponse(
                    success = false,
                    error = response.error?.message?.takeIf { it.isNotBlank() } ?: "Empty response",
                    usedProvider = config.provider,
                    retryable = true
                )
            }
            onChunkReceived(content)
            ApiResponse(
                success = true,
                content = content,
                usedProvider = config.provider,
                retryable = false
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is HttpException) {
                val code = e.code()
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                return ApiResponse(
                    success = false,
                    error = formatHttpError(code, sanitizeSensitiveText(body ?: "")),
                    usedProvider = config.provider,
                    retryable = isRetryableHttpCode(code)
                )
            }
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Unknown error"),
                usedProvider = config.provider,
                retryable = isRetryableThrowable(e)
            )
        }
    }

    private suspend fun oneShotFromOpenCodeResponses(
        config: ProviderConfig,
        systemPrompt: String,
        userPrompt: String
    ): ApiResponse {
        return try {
            val service = createOpenCodeResponsesServiceForProvider(config)
            val request = buildOpenCodeResponsesRequest(
                model = config.model,
                messages = listOf(
                    OpenRouterMessage("system", systemPrompt),
                    OpenRouterMessage("user", userPrompt)
                ),
                maxTokens = 2048,
                temperature = 0.65f
            )
            val response = service.response(request)
            val content = extractOpenCodeResponsesText(response)
            if (content.isBlank()) {
                return ApiResponse(
                    success = false,
                    error = response.error?.message?.takeIf { it.isNotBlank() } ?: "Empty response",
                    usedProvider = config.provider,
                    retryable = true
                )
            }
            ApiResponse(
                success = true,
                content = content,
                usedProvider = config.provider,
                retryable = false
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is HttpException) {
                val code = e.code()
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                return ApiResponse(
                    success = false,
                    error = formatHttpError(code, sanitizeSensitiveText(body ?: "")),
                    usedProvider = config.provider,
                    retryable = isRetryableHttpCode(code)
                )
            }
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Unknown error"),
                usedProvider = config.provider,
                retryable = isRetryableThrowable(e)
            )
        }
    }

    private suspend fun chatCompletionViaOpenCodeResponses(
        config: ProviderConfig,
        messages: List<OpenRouterMessage>,
        maxTokens: Int,
        temperature: Float
    ): OpenRouterChatResponse {
        val service = createOpenCodeResponsesServiceForProvider(config)
        val request = buildOpenCodeResponsesRequest(
            model = config.model,
            messages = messages,
            maxTokens = maxTokens,
            temperature = temperature
        )
        val response = service.response(request)
        val content = extractOpenCodeResponsesText(response)
        if (content.isBlank()) {
            val reason = response.error?.message?.takeIf { it.isNotBlank() } ?: "Empty response"
            throw IllegalStateException(reason)
        }

        return OpenRouterChatResponse(
            choices = listOf(
                OpenRouterChoice(
                    message = OpenRouterMessage(role = "assistant", content = content)
                )
            )
        )
    }

    private suspend fun chatCompletionViaOpenCodeZen(
        config: ProviderConfig,
        messages: List<OpenRouterMessage>,
        maxTokens: Int,
        temperature: Float
    ): OpenRouterChatResponse {
        val service = createOpenCodeZenServiceForProvider(config)
        val request = buildOpenCodeZenRequest(
            model = config.model,
            messages = messages,
            maxTokens = maxTokens,
            temperature = temperature
        )
        val response = service.message(request)
        val content = extractOpenCodeZenText(response)
        if (content.isBlank()) {
            val reason = response.error?.message?.takeIf { it.isNotBlank() } ?: "Empty response"
            throw IllegalStateException(reason)
        }

        return OpenRouterChatResponse(
            choices = listOf(
                OpenRouterChoice(
                    message = OpenRouterMessage(role = "assistant", content = content)
                )
            )
        )
    }

    private fun buildOpenCodeZenRequest(
        model: String,
        messages: List<OpenRouterMessage>,
        maxTokens: Int,
        temperature: Float
    ): OpenCodeZenRequest {
        val system = messages
            .filter { it.role.equals("system", ignoreCase = true) }
            .mapNotNull { it.content?.trim()?.takeIf { value -> value.isNotBlank() } }
            .joinToString("\n\n")

        val mappedMessages = messages
            .mapNotNull { mapOpenRouterMessageToOpenCodeZen(it) }
            .ifEmpty {
                listOf(OpenCodeZenMessage(role = "user", content = "Hi"))
            }

        return OpenCodeZenRequest(
            model = model,
            messages = mappedMessages,
            maxTokens = maxTokens.coerceAtLeast(64),
            system = system.ifBlank { null },
            temperature = temperature
        )
    }

    private fun buildOpenCodeResponsesRequest(
        model: String,
        messages: List<OpenRouterMessage>,
        maxTokens: Int,
        temperature: Float
    ): OpenCodeZenResponsesRequest {
        val mappedInput = messages
            .mapNotNull { message ->
                if (message.role.equals("system", ignoreCase = true)) return@mapNotNull null
                val text = mapOpenRouterMessageToText(message) ?: return@mapNotNull null
                val role = if (message.role.equals("assistant", ignoreCase = true)) "assistant" else "user"
                OpenCodeZenResponsesInputItem(
                    role = role,
                    content = listOf(OpenCodeZenResponsesInputContent(text = text))
                )
            }
            .ifEmpty {
                listOf(
                    OpenCodeZenResponsesInputItem(
                        role = "user",
                        content = listOf(OpenCodeZenResponsesInputContent(text = "Hi"))
                    )
                )
            }

        return OpenCodeZenResponsesRequest(
            model = model,
            input = mappedInput,
            maxOutputTokens = maxTokens.coerceAtLeast(64),
            temperature = temperature,
            stream = false
        )
    }

    private fun mapOpenRouterMessageToOpenCodeZen(message: OpenRouterMessage): OpenCodeZenMessage? {
        if (message.role.equals("system", ignoreCase = true)) return null

        val role = if (message.role.equals("assistant", ignoreCase = true)) "assistant" else "user"
        val content = mapOpenRouterMessageToText(message)

        if (content.isNullOrBlank()) return null
        return OpenCodeZenMessage(role = role, content = content)
    }

    private fun mapOpenRouterMessageToText(message: OpenRouterMessage): String? {
        return message.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: message.toolCalls
                ?.joinToString(separator = "\n") { call ->
                    "${call.function.name}: ${call.function.arguments}"
                }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
    }

    private fun extractOpenCodeZenText(response: com.example.bamachat.data.OpenCodeZenResponse): String {
        return response.content
            .orEmpty()
            .mapNotNull { part ->
                if (part.type == null || part.type == "text") part.text?.trim() else null
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
    }

    private fun extractOpenCodeResponsesText(response: OpenCodeZenResponsesResponse): String {
        val direct = response.outputText?.trim().orEmpty()
        if (direct.isNotBlank()) return direct

        return response.output
            .orEmpty()
            .flatMap { it.content.orEmpty() }
            .mapNotNull { content ->
                if (content.type == null || content.type == "output_text") content.text?.trim() else null
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
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
                maxTokens = 2048,
                temperature = 0.4f,
                stream = false
            )

            val service = ApiClient.createOpenAICompatibleService(ApiClient.Provider.OPENROUTER, apiKey)
            val response = service.chatCompletionVision(request)
            val content = response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()

            if (content.isBlank()) {
                return ApiResponse(
                    success = false,
                    error = "Empty vision response",
                    usedProvider = ApiClient.Provider.OPENROUTER,
                    retryable = true
                )
            }

            ApiResponse(
                success = true,
                content = content,
                usedProvider = ApiClient.Provider.OPENROUTER,
                retryable = false
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ApiResponse(
                success = false,
                error = sanitizeSensitiveText(e.message ?: "Vision error"),
                retryable = isRetryableThrowable(e)
            )
        }
    }

    private suspend fun visionViaGemini(
        apiKey: String,
        systemPrompt: String,
        userText: String
    ): ApiResponse {
        // Gemini Vision is not yet fully implemented.
        // Return a clear user-facing message so the fallback chain can report this properly.
        return ApiResponse(
            success = false,
            error = "Gemini Vision ist aktuell noch nicht implementiert. Bitte nutze OpenRouter für Bildanalyse oder konfiguriere einen Vision-fähigen Provider in den Einstellungen.",
            retryable = false
        )
    }

    // ===== Hilfsfunktionen =====

    private fun buildProviderFallbackList(): List<ProviderConfig> {
        val multiEnabled = prefs.getBoolean("multi_provider", true)

        val cerebrasKey = getApiKeyForProvider(ApiClient.Provider.CEREBRAS)
        val groqKey = getApiKeyForProvider(ApiClient.Provider.GROQ)
        val openRouterKey = getApiKeyForProvider(ApiClient.Provider.OPENROUTER)
        val openCodeKey = getApiKeyForProvider(ApiClient.Provider.OPENCODE)
        val togetherKey = getApiKeyForProvider(ApiClient.Provider.TOGETHER)
        val openRouterModel = prefs.getString("openrouter_model", "google/gemma-3-27b-it:free") ?: "google/gemma-3-27b-it:free"
        val openCodeEndpoint = prefs.getString("opencode_endpoint", "")?.trim().orEmpty()
        val resolvedOpenCodeEndpoint = if (openCodeEndpoint.isBlank()) {
            ApiClient.Provider.OPENCODE.baseUrl
        } else {
            openCodeEndpoint
        }
        val openCodeModel = prefs.getString("opencode_model", ApiClient.OPENCODE_DEFAULT_MODEL)
            ?: ApiClient.OPENCODE_DEFAULT_MODEL

        return if (multiEnabled) {
            listOfNotNull(
                openCodeKey?.let {
                    ProviderConfig(
                        provider = ApiClient.Provider.OPENCODE,
                        apiKey = it,
                        model = openCodeModel,
                        baseUrlOverride = resolvedOpenCodeEndpoint
                    )
                },
                cerebrasKey?.let { ProviderConfig(ApiClient.Provider.CEREBRAS, it, ApiClient.CEREBRAS_DEFAULT) },
                groqKey?.let { ProviderConfig(ApiClient.Provider.GROQ, it, ApiClient.GROQ_DEFAULT) },
                openRouterKey?.let { ProviderConfig(ApiClient.Provider.OPENROUTER, it, openRouterModel) },
                togetherKey?.let { ProviderConfig(ApiClient.Provider.TOGETHER, it, ApiClient.TOGETHER_DEFAULT) }
            )
        } else {
            val explicitDefault = if (openCodeKey != null) "OpenCode" else "OpenRouter"
            val explicit = prefs.getString("ai_provider", explicitDefault) ?: explicitDefault
            listOfNotNull(
                when (explicit) {
                    "OpenRouter" -> openRouterKey?.let { ProviderConfig(ApiClient.Provider.OPENROUTER, it, openRouterModel) }
                    "Groq" -> groqKey?.let { ProviderConfig(ApiClient.Provider.GROQ, it, ApiClient.GROQ_DEFAULT) }
                    "Cerebras" -> cerebrasKey?.let { ProviderConfig(ApiClient.Provider.CEREBRAS, it, ApiClient.CEREBRAS_DEFAULT) }
                    "OpenCode" -> {
                        openCodeKey?.let {
                            ProviderConfig(
                                provider = ApiClient.Provider.OPENCODE,
                                apiKey = it,
                                model = openCodeModel,
                                baseUrlOverride = resolvedOpenCodeEndpoint
                            )
                        }
                    }
                    "Together" -> togetherKey?.let { ProviderConfig(ApiClient.Provider.TOGETHER, it, ApiClient.TOGETHER_DEFAULT) }
                    else -> null
                }
            )
        }
    }

    private fun isOpenCodeZenConfig(config: ProviderConfig): Boolean {
        if (config.provider != ApiClient.Provider.OPENCODE) return false
        val baseUrl = config.baseUrlOverride?.trim().orEmpty().ifBlank { ApiClient.Provider.OPENCODE.baseUrl }
        val normalized = baseUrl.lowercase(Locale.getDefault())
        if (normalized.contains("/zen/")) return true
        return normalized.contains("opencode.ai")
    }

    private fun isOpenCodeResponsesModel(model: String): Boolean {
        val normalized = model.trim().lowercase(Locale.getDefault())
        if (normalized.isBlank()) return false
        return normalized.startsWith("gpt-") ||
            normalized.contains("codex") ||
            normalized.startsWith("openai/")
    }

    private fun createOpenCodeZenServiceForProvider(config: ProviderConfig) =
        ApiClient.createOpenCodeZenService(
            baseUrl = config.baseUrlOverride?.takeIf { it.isNotBlank() } ?: ApiClient.Provider.OPENCODE.baseUrl,
            apiKey = config.apiKey
        )

    private fun createOpenCodeResponsesServiceForProvider(config: ProviderConfig) =
        ApiClient.createOpenCodeZenResponsesService(
            baseUrl = config.baseUrlOverride?.takeIf { it.isNotBlank() } ?: ApiClient.Provider.OPENCODE.baseUrl,
            apiKey = config.apiKey
        )

    private fun createServiceForProvider(config: ProviderConfig) =
        if (!config.baseUrlOverride.isNullOrBlank()) {
            ApiClient.createOpenAICompatibleService(
                baseUrl = config.baseUrlOverride,
                apiKey = config.apiKey,
                includeOpenRouterHeaders = false
            )
        } else {
            ApiClient.createOpenAICompatibleService(config.provider, config.apiKey)
        }

    private fun getApiKeyForProvider(provider: ApiClient.Provider): String? {
        val prefKey = when (provider) {
            ApiClient.Provider.OPENROUTER -> "openrouter_api_key"
            ApiClient.Provider.GROQ -> "groq_api_key"
            ApiClient.Provider.CEREBRAS -> "cerebras_api_key"
            ApiClient.Provider.TOGETHER -> "together_api_key"
            ApiClient.Provider.OPENCODE -> "opencode_api_key"
        }
        return secureString(prefKey).takeIf { it.length > 10 }
    }

    /**
     * Retry mit Exponential Backoff: 0.5s, 1s, 2s, etc.
     */
    private suspend inline fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500,
        shouldRetryResult: (T) -> Boolean = { false },
        shouldRetryException: (Exception) -> Boolean = { true },
        block: suspend () -> T
    ): T {
        val attempts = maxAttempts.coerceAtLeast(1)
        var attempt = 0

        while (attempt < attempts) {
            try {
                val result = block()
                if (attempt >= attempts - 1 || !shouldRetryResult(result)) {
                    return result
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (attempt >= attempts - 1 || !shouldRetryException(e)) {
                    throw e
                }
            }

            attempt++
            val delayMs = (initialDelayMs * 2.0.pow((attempt - 1).toDouble())).toLong()
            delay(delayMs)
        }

        throw Exception("Max retries exceeded")
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
            .replace(Regex("sk-[A-Za-z0-9_\\-]{16,}", RegexOption.IGNORE_CASE), "sk-***")
            .replace(Regex("gsk_[A-Za-z0-9_\\-]+", RegexOption.IGNORE_CASE), "gsk_***")
            .replace(Regex("csk-[A-Za-z0-9_\\-]+", RegexOption.IGNORE_CASE), "csk-***")
            .replace(Regex("oc_[A-Za-z0-9_\\-]+", RegexOption.IGNORE_CASE), "oc_***")
            .replace(Regex("\"api[_-]?key\"\\s*:\\s*\"[^\"]+\"", RegexOption.IGNORE_CASE), "\"apiKey\":\"***\"")
        if (!prefs.getBoolean("privacy_strict_mode_enabled", true)) {
            return masked
        }
        return masked
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[url]")
            .replace(Regex("[\\r\\n]+"), " ")
            .take(140)
    }

    private fun secureString(key: String, defaultValue: String = ""): String =
        SecureSettingsStore.getString(app, prefs, key, defaultValue)

    private fun isRetryableThrowable(throwable: Throwable): Boolean = when (throwable) {
        is HttpException -> isRetryableHttpCode(throwable.code())
        is IOException -> true
        else -> {
            val message = throwable.message.orEmpty().lowercase(Locale.getDefault())
            message.contains("timeout") ||
                message.contains("temporar") ||
                message.contains("network") ||
                message.contains("connection") ||
                message.contains("429") ||
                message.contains("5xx")
        }
    }

    private fun isRetryableHttpCode(code: Int): Boolean =
        code == 408 || code == 425 || code == 429 || code in 500..599

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
                error = if (sources.isEmpty()) "Keine Treffer für Live-Recherche." else "",
                retryable = false
            )
        } catch (e: Exception) {
            WebResearchResult(
                success = false,
                query = query,
                error = "Ungültige Web-Recherche-Antwort: ${e.message}",
                retryable = true
            )
        }
    }

    private fun pruneResearchCache(now: Long, ttlMs: Long) {
        val iterator = researchCache.entries.iterator()
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
        private const val MAX_RESEARCH_CACHE_SIZE = 50
        private val WEATHER_ALLOWED_DOMAINS = setOf(
            "dwd.de",
            "wetteronline.de",
            "wetter.com",
            "open-meteo.com",
            "meteoblue.com"
        )
    }
}
