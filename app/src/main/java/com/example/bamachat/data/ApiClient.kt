package com.example.bamachat.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit

object ApiClient {
    // ===== Provider URLs =====
    private const val OPENROUTER_BASE = "https://openrouter.ai/api/v1/"
    private const val GROQ_BASE = "https://api.groq.com/openai/v1/"
    private const val CEREBRAS_BASE = "https://api.cerebras.ai/v1/"
    private const val TOGETHER_BASE = "https://api.together.xyz/v1/"

    // ===== OpenRouter Free Models =====
    val OPENROUTER_FREE_MODELS = listOf(
        "meta-llama/llama-3.3-70b-instruct:free",
        "google/gemma-3-27b-it:free",
        "qwen/qwen3-next-80b-a3b-instruct:free",
        "openai/gpt-oss-20b:free",
        "google/gemma-3-12b-it:free",
        "meta-llama/llama-3.2-3b-instruct:free"
    )

    val OPENROUTER_VISION_MODELS = listOf(
        "openrouter/free",
        "google/gemma-3-27b-it:free",
        "google/gemma-3-12b-it:free",
        "meta-llama/llama-3.2-11b-vision-instruct"
    )
    val OPENROUTER_DEFAULT_VISION_MODEL = "openrouter/free"

    val FREE_MODELS = OPENROUTER_FREE_MODELS

    val FREE_MODEL_DISPLAY_NAMES = mapOf(
        "meta-llama/llama-3.3-70b-instruct:free" to "Llama 3.3 70B (Stark)",
        "google/gemma-3-27b-it:free" to "Gemma 3 27B (Empfohlen)",
        "qwen/qwen3-next-80b-a3b-instruct:free" to "Qwen3 80B (Stark)",
        "openai/gpt-oss-20b:free" to "GPT-OSS 20B (OpenAI)",
        "google/gemma-3-12b-it:free" to "Gemma 3 12B (Schnell)",
        "meta-llama/llama-3.2-3b-instruct:free" to "Llama 3.2 3B (Sehr schnell)",
        "openrouter/free" to "OpenRouter Free Router (Auto, Vision-fähig)",
        "meta-llama/llama-3.2-11b-vision-instruct" to "Llama 3.2 11B Vision"
    )

    fun isVisionCapableOpenRouterModel(modelId: String): Boolean {
        if (modelId in OPENROUTER_VISION_MODELS) return true
        val normalized = modelId.lowercase()
        return normalized.contains("vision") || normalized.contains("gemma-3")
    }

    // ===== Groq Models (kostenlos, sehr schnell) =====
    val GROQ_MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "gemma2-9b-it",
        "mixtral-8x7b-32768"
    )
    val GROQ_DEFAULT = "llama-3.3-70b-versatile"

    // ===== Cerebras Models (kostenlos, ULTRA schnell) =====
    val CEREBRAS_MODELS = listOf(
        "llama3.1-8b",
        "llama-3.3-70b"
    )
    val CEREBRAS_DEFAULT = "llama3.1-8b"

    // ===== Together AI Free Models =====
    val TOGETHER_MODELS = listOf(
        "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
        "meta-llama/Llama-Vision-Free"
    )
    val TOGETHER_DEFAULT = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free"

    // ===== Provider Definitions =====
    enum class Provider(
        val id: String,
        val displayName: String,
        val baseUrl: String,
        val signupUrl: String,
        val keyPrefix: String,
        val emoji: String
    ) {
        OPENROUTER("OpenRouter", "OpenRouter", OPENROUTER_BASE, "https://openrouter.ai/keys", "sk-or-", "🌐"),
        GROQ("Groq", "Groq (sehr schnell)", GROQ_BASE, "https://console.groq.com/keys", "gsk_", "⚡"),
        CEREBRAS("Cerebras", "Cerebras (ULTRA schnell)", CEREBRAS_BASE, "https://cloud.cerebras.ai/", "csk-", "🚀"),
        TOGETHER("Together", "Together AI", TOGETHER_BASE, "https://api.together.xyz/settings/api-keys", "", "🤝")
    }

    /**
     * Generischer OpenAI-kompatibler Client für OpenRouter/Groq/Cerebras/Together.
     * Alle nutzen das gleiche /chat/completions Format.
     */
    fun createOpenAICompatibleService(provider: Provider, apiKey: String): OpenAICompatibleService {
        val cleanKey = apiKey.trim().replace(Regex("[\\r\\n]+"), "")
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $cleanKey")
                    .addHeader("Content-Type", "application/json")

                if (provider == Provider.OPENROUTER) {
                    builder.addHeader("HTTP-Referer", "https://bamachat.app")
                    builder.addHeader("X-Title", "BamaChat")
                }
                chain.proceed(builder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(provider.baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAICompatibleService::class.java)
    }

    fun createOllamaService(baseUrl: String): com.example.bamachat.data.api.OllamaApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.example.bamachat.data.api.OllamaApiService::class.java)
    }

    fun createGeminiModel(apiKey: String): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-1.5-flash-latest",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.75f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 1024
            }
        )
    }
}

/**
 * OpenAI-kompatibler Service — funktioniert für OpenRouter, Groq, Cerebras, Together AI.
 */
interface OpenAICompatibleService {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Body request: OpenRouterChatRequest
    ): OpenRouterChatResponse

    @POST("chat/completions")
    suspend fun chatCompletionVision(
        @Body request: OpenRouterVisionChatRequest
    ): OpenRouterChatResponse

    @Streaming
    @POST("chat/completions")
    suspend fun chatCompletionStream(
        @Body request: OpenRouterChatRequest
    ): retrofit2.Response<ResponseBody>
}

// Legacy alias für Rückwärtskompatibilität
typealias OpenRouterApiService = OpenAICompatibleService

data class OpenRouterChatRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val stream: Boolean = false
)

data class OpenRouterVisionChatRequest(
    val model: String,
    val messages: List<OpenRouterVisionMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    val temperature: Float = 0.4f,
    val stream: Boolean = false
)

data class OpenRouterStreamChunk(
    val choices: List<OpenRouterStreamChoice>?
)

data class OpenRouterStreamChoice(
    val delta: OpenRouterStreamDelta?,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class OpenRouterStreamDelta(
    val role: String? = null,
    val content: String? = null
)

data class OpenRouterMessage(
    val role: String,
    val content: String
)

data class OpenRouterVisionMessage(
    val role: String,
    val content: List<OpenRouterVisionContentPart>
)

data class OpenRouterVisionContentPart(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: OpenRouterImageUrl? = null
)

data class OpenRouterImageUrl(
    val url: String
)

data class OpenRouterChatResponse(
    val choices: List<OpenRouterChoice>?,
    val error: OpenRouterError? = null
)

data class OpenRouterChoice(
    val message: OpenRouterMessage
)

data class OpenRouterError(
    val message: String?,
    val code: Int? = null
)
