package com.example.bamachat.ui.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ModelInfo
import com.example.bamachat.data.model.OllamaChatRequest
import com.example.bamachat.data.model.OllamaMessage
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.util.SmartFeatureManager
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Suppress("UNUSED_PARAMETER")
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    data class UsageStatus(
        val isPremium: Boolean = false,
        val textUsed: Int = 0,
        val textLimit: Int = 0,
        val imageAnalysisUsed: Int = 0,
        val imageAnalysisLimit: Int = 0,
        val imageGenerationUsed: Int = 0,
        val imageGenerationLimit: Int = 0
    ) {
        val textRemaining: Int get() = (textLimit - textUsed).coerceAtLeast(0)
        val imageAnalysisRemaining: Int get() = (imageAnalysisLimit - imageAnalysisUsed).coerceAtLeast(0)
        val imageGenerationRemaining: Int get() = (imageGenerationLimit - imageGenerationUsed).coerceAtLeast(0)
    }

    private enum class QuotaType {
        TEXT_MESSAGE,
        IMAGE_ANALYSIS,
        IMAGE_GENERATION
    }

    companion object {
        private const val KEY_PREMIUM_ACTIVE = "premium_active"
        private const val KEY_USAGE_DAY = "usage_day"
        private const val KEY_USAGE_TEXT = "usage_text_count"
        private const val KEY_USAGE_IMAGE_ANALYSIS = "usage_image_analysis_count"
        private const val KEY_USAGE_IMAGE_GENERATION = "usage_image_generation_count"

        private const val FREE_DAILY_TEXT_LIMIT = 80
        private const val FREE_DAILY_IMAGE_ANALYSIS_LIMIT = 8
        private const val FREE_DAILY_IMAGE_GENERATION_LIMIT = 6
    }

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val db = ChatDatabase.getDatabase(application)
    private val repo = ChatRepository(db.chatDao())
    private val smartFeatures = SmartFeatureManager(application.applicationContext)
    private val gson = Gson()
    private var messagesJob: Job? = null

    // ===== State =====
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<ConversationEntity>> = _conversations

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(getModelsForProvider())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels

    private val _selectedModel = MutableStateFlow(
        prefs.getString("openrouter_model", "google/gemma-3-27b-it:free") ?: "google/gemma-3-27b-it:free"
    )
    val selectedModel: StateFlow<String> = _selectedModel

    private val _selectedPersona = MutableStateFlow(loadPersonaFromPrefs())
    val selectedPersona: StateFlow<Persona> = _selectedPersona

    private val _customPersonaPrompt = MutableStateFlow(
        prefs.getString("custom_persona_prompt", "") ?: ""
    )
    val customPersonaPrompt: StateFlow<String> = _customPersonaPrompt

    private val _chatSentiment = MutableStateFlow("neutral")
    val chatSentiment: StateFlow<String> = _chatSentiment

    private val _usageStatus = MutableStateFlow(UsageStatus())
    val usageStatus: StateFlow<UsageStatus> = _usageStatus

    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall

    // ===== Personas =====
    enum class Persona(val displayName: String, val emoji: String, val systemPrompt: String) {
        ASSISTANT(
            "Assistent", "🤖",
            "Du bist BamaChat, ein hilfreicher deutschsprachiger KI-Assistent. Antworte kurz und präzise."
        ),
        DEVELOPER(
            "Entwickler", "💻",
            "Du bist ein erfahrener Software-Entwickler. Hilf mit Code-Beispielen und technischen Erklärungen. Nutze Markdown-Codeblöcke. Antworte auf Deutsch."
        ),
        TEACHER(
            "Lehrer", "🎓",
            "Du bist ein geduldiger Lehrer. Erkläre Dinge einfach und verständlich, mit Beispielen. Antworte auf Deutsch."
        ),
        TRANSLATOR(
            "Übersetzer", "🌍",
            "Du bist ein professioneller Übersetzer. Übersetze den Text des Benutzers. Wenn er Deutsch ist, übersetze ins Englische. Wenn nicht, ins Deutsche. Erkläre kurz Schwierigkeiten."
        ),
        CHEF(
            "Koch", "👨‍🍳",
            "Du bist ein kreativer Koch. Hilf mit Rezepten, Zutaten-Tipps und Kochtechniken. Antworte auf Deutsch, locker und enthusiastisch."
        ),
        FITNESS(
            "Fitness-Coach", "💪",
            "Du bist ein motivierender Fitness-Coach. Gib Tipps zu Training, Ernährung und Motivation. Antworte auf Deutsch, energiegeladen."
        ),
        THERAPIST(
            "Reflexions-Begleiter", "🧘",
            "Du bist ein einfühlsamer Gesprächspartner. Höre zu, stelle hilfreiche Rückfragen und hilf beim Reflektieren. Du bist KEIN Ersatz für echte Therapie, weise bei ernsten Problemen freundlich darauf hin. Antworte auf Deutsch, warm und respektvoll."
        ),
        CUSTOM(
            "Eigene Persona", "✨",
            "" // Wird zur Laufzeit aus customPersonaPrompt gefüllt
        )
    }

    private fun loadPersonaFromPrefs(): Persona {
        val name = prefs.getString("selected_persona", Persona.ASSISTANT.name) ?: Persona.ASSISTANT.name
        return try { Persona.valueOf(name) } catch (_: Exception) { Persona.ASSISTANT }
    }

    private fun resolveSystemPrompt(): String {
        val personaPrompt = if (_selectedPersona.value == Persona.CUSTOM) {
            _customPersonaPrompt.value.ifBlank { Persona.ASSISTANT.systemPrompt }
        } else {
            _selectedPersona.value.systemPrompt
        }
        val agentStudioEnabled = prefs.getBoolean("agent_studio_enabled", false)
        if (!agentStudioEnabled) return personaPrompt

        val agentPreset = prefs.getString("agent_preset", "Generalist") ?: "Generalist"
        val agentName = prefs.getString("agent_name", "Bama Agent") ?: "Bama Agent"
        val agentGoal = prefs.getString("agent_goal", "") ?: ""
        val agentRules = prefs.getString("agent_rules", "") ?: ""
        val agentOutputStyle = prefs.getString("agent_output_style", "Klar und präzise") ?: "Klar und präzise"
        val agentTools = prefs.getString("agent_tools", "Analyse, Problemlösung") ?: "Analyse, Problemlösung"

        return """
$personaPrompt

[Agent-Studio aktiviert]
Name: $agentName
Rolle: $agentPreset
Ziel: ${agentGoal.ifBlank { "Löse die Nutzeranfrage zuverlässig und handlungsorientiert." }}
Regeln: ${agentRules.ifBlank { "Korrekt, transparent, fokussiert antworten." }}
Ausgabestil: $agentOutputStyle
Arbeitsweisen: $agentTools
""".trim()
    }

    // ===== Init =====
    init {
        createNotificationChannel()
        refreshMonetizationState()
        viewModelScope.launch {
            repo.getAllConversations().collectLatest { _conversations.value = it }
        }
        val lastConvId = prefs.getString("current_conversation_id", null)
        if (lastConvId != null) {
            switchConversation(lastConvId)
        } else {
            viewModelScope.launch { newConversation() }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "bamachat_ai_response",
            "KI-Antworten",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigungen bei neuen KI-Antworten"
        }
        val manager = getApplication<Application>().getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, text: String) {
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) return

        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return

        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(getApplication(), "bamachat_ai_response")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ===== Conversations =====
    fun newConversation() {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            repo.createConversation(id, "Neuer Chat", _selectedPersona.value.name)
            switchConversation(id)
        }
    }

    fun switchConversation(id: String) {
        _currentConversationId.value = id
        prefs.edit().putString("current_conversation_id", id).apply()
        _messages.value = emptyList()
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repo.getMessages(id).collectLatest { _messages.value = it }
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch { repo.renameConversation(id, newTitle.ifBlank { "Chat" }) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repo.deleteConversation(id)
            if (_currentConversationId.value == id) {
                val remaining = _conversations.value.filter { it.id != id }
                if (remaining.isNotEmpty()) switchConversation(remaining.first().id)
                else newConversation()
            }
        }
    }

    // ===== Models =====
    private fun getModelsForProvider(): List<ModelInfo> {
        val provider = prefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter"
        return modelsFor(provider)
    }

    fun updateModelsForProvider(provider: String) {
        _availableModels.value = modelsFor(provider)
    }

    private fun modelsFor(provider: String): List<ModelInfo> = when (provider) {
        "OpenRouter" -> {
            val visionOnly = prefs.getBoolean("openrouter_vision_only_models", true)
            val modelList = if (visionOnly) ApiClient.OPENROUTER_VISION_MODELS else ApiClient.FREE_MODELS
            modelList.map { id ->
                ModelInfo(name = ApiClient.FREE_MODEL_DISPLAY_NAMES[id] ?: id, model = id)
            }
        }
        "Gemini" -> listOf(ModelInfo("Gemini 1.5 Flash", "gemini-1.5-flash-latest"))
        "Ollama" -> listOf(ModelInfo("Ollama (Lokal)", "default"))
        else -> emptyList()
    }

    // ===== Send Message with Image =====
    fun sendMessageWithImage(text: String, imageUri: Uri) {
        if (text.isBlank() && imageUri == Uri.EMPTY) return
        val convId = _currentConversationId.value
        if (convId == null) {
            viewModelScope.launch {
                val newId = UUID.randomUUID().toString()
                repo.createConversation(newId, "Neuer Chat", _selectedPersona.value.name)
                switchConversation(newId)
                sendMessageWithImage(text, imageUri)
            }
            return
        }
        if (!consumeImageMessageQuota()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text.ifBlank { "Bild" },
            isUser = true,
            timestamp = System.currentTimeMillis(),
            imageUrl = imageUri.toString()
        )

        viewModelScope.launch {
            repo.saveMessage(convId, userMessage)

            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && current.title == "Neuer Chat") {
                val newTitle = text.take(40).ifBlank { "Bild-Chat" }
                repo.renameConversation(convId, newTitle)
            }

            _isLoading.value = true
            try {
                val provider = prefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter"
                val geminiKey = prefs.getString("gemini_api_key", "") ?: ""
                val canUseGeminiVision = geminiKey.isNotBlank()
                val imageAnalysisText = "Der Benutzer hat ein Bild gesendet. Analysiere das Bild präzise und antworte auf Deutsch."
                val combinedText = if (text.isNotBlank()) "$imageAnalysisText\n\nBenutzer-Nachricht: $text" else imageAnalysisText

                when (provider) {
                    "Gemini" -> sendViaGeminiWithImage(convId, combinedText, imageUri)
                    "OpenRouter" -> {
                        val openRouterVisionOk = sendViaOpenRouterWithImage(convId, combinedText, imageUri)
                        if (!openRouterVisionOk && canUseGeminiVision) {
                            sendViaGeminiWithImage(convId, combinedText, imageUri)
                        } else {
                            if (!openRouterVisionOk) {
                                val enhancedText = "$combinedText\n\n[System: Hinweis - Für echte Bildanalyse in BamaChat bitte Gemini API-Key hinterlegen.]"
                                sendViaOpenRouterStream(convId, enhancedText)
                            }
                        }
                    }
                    "Ollama" -> {
                        if (canUseGeminiVision) {
                            sendViaGeminiWithImage(convId, combinedText, imageUri)
                        } else {
                            val enhancedText = "$combinedText\n\n[System: Hinweis - Lokaler text-only Modus aktiv. Für Bildanalyse bitte Gemini API-Key setzen.]"
                            sendViaOllama(convId, enhancedText)
                        }
                    }
                    else -> _errorMessage.value = "Unbekannter KI-Anbieter: $provider"
                }
            } catch (e: retrofit2.HttpException) {
                handleHttpError(e)
            } catch (e: Exception) {
                handleGenericError(e)
            } finally {
                _isLoading.value = false
                _isStreaming.value = false
            }
        }
    }

    private suspend fun sendViaGeminiWithImage(convId: String, text: String, imageUri: Uri) {
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isBlank()) {
            _errorMessage.value = "Kein Gemini API-Key gesetzt! Für Bild-Analyse ist Gemini erforderlich."
            return
        }
        try {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmapFromUri(imageUri) }
            if (bitmap == null) {
                _errorMessage.value = "Bild konnte nicht geladen werden. Bitte anderes Bild wählen."
                return
            }

            val model = ApiClient.createGeminiModel(apiKey)
            val prompt = content {
                text("Systemanweisung: ${resolveSystemPrompt()}\n\n$text")
                image(bitmap)
            }
            val response = model.generateContent(prompt)
            val reply = response.text?.takeIf { it.isNotBlank() }
                ?: "Keine auswertbare Bildantwort erhalten."
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = reply,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            repo.saveMessage(convId, msg)
            showNotification("BamaChat (Bildanalyse)", reply)
        } catch (e: Exception) {
            _errorMessage.value = "Bild-Analyse fehlgeschlagen: ${e.localizedMessage ?: "Unbekannter Fehler"}"
        }
    }

    private suspend fun sendViaOpenRouterWithImage(convId: String, text: String, imageUri: Uri): Boolean {
        val apiKey = prefs.getString("openrouter_api_key", "") ?: ""
        if (apiKey.isBlank()) return false

        return try {
            val imageDataUrl = withContext(Dispatchers.IO) { encodeImageAsDataUrl(imageUri) } ?: return false
            val selectedModel = prefs.getString("openrouter_model", "google/gemma-3-27b-it:free")
                ?: "google/gemma-3-27b-it:free"
            val modelId = if (ApiClient.isVisionCapableOpenRouterModel(selectedModel)) {
                selectedModel
            } else {
                ApiClient.OPENROUTER_DEFAULT_VISION_MODEL
            }
            val request = OpenRouterVisionChatRequest(
                model = modelId,
                messages = listOf(
                    OpenRouterVisionMessage(
                        role = "system",
                        content = listOf(
                            OpenRouterVisionContentPart(
                                type = "text",
                                text = resolveSystemPrompt()
                            )
                        )
                    ),
                    OpenRouterVisionMessage(
                        role = "user",
                        content = listOf(
                            OpenRouterVisionContentPart(type = "text", text = text),
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
            val reply = response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
            if (reply.isBlank()) return false

            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = reply,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            repo.saveMessage(convId, msg)
            showNotification("BamaChat (OpenRouter Vision)", reply)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ===== Send Message =====
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Bild-Generierung: wenn der User nach einem Bild fragt
        if (isImageQuery(text)) {
            generateImage(text, skipUserMessage = true)
            return
        }

        if (!consumeQuota(QuotaType.TEXT_MESSAGE)) return

        val convId = _currentConversationId.value
        if (convId == null) {
            viewModelScope.launch {
                val newId = UUID.randomUUID().toString()
                repo.createConversation(newId, "Neuer Chat", _selectedPersona.value.name)
                switchConversation(newId)
                sendMessage(text)
            }
            return
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repo.saveMessage(convId, userMessage)

            // Auto-Titel: erste User-Nachricht wird Titel
            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && current.title == "Neuer Chat") {
                val newTitle = text.take(40).ifBlank { "Chat" }
                repo.renameConversation(convId, newTitle)
            }

            _isLoading.value = true

            // Wetter-Tool: wenn der User nach Wetter fragt, Standort holen + Antwort vorbereiten
            val weatherContext = if (smartFeatures.isWeatherQuery(text)) {
                try { smartFeatures.getWeatherData() } catch (_: Exception) { null }
            } else null

            try {
                val provider = prefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter"
                val effectiveText = if (weatherContext != null) {
                    "$text\n\n[System: Hier sind aktuelle Wetterdaten für den Nutzer-Standort: $weatherContext]"
                } else text

                when (provider) {
                    "OpenRouter" -> sendViaOpenRouterStream(convId, effectiveText)
                    "Gemini" -> sendViaGemini(convId, effectiveText)
                    "Ollama" -> sendViaOllama(convId, effectiveText)
                    else -> _errorMessage.value = "Unbekannter KI-Anbieter: $provider"
                }
            } catch (e: retrofit2.HttpException) {
                handleHttpError(e)
            } catch (e: Exception) {
                handleGenericError(e)
            } finally {
                _isLoading.value = false
                _isStreaming.value = false
            }
        }
    }

    /**
     * Multi-Provider Auto-Fallback. Versucht alle konfigurierten Provider in Reihenfolge.
     * Provider-Reihenfolge (von schnell/grosszügig zu sparsam):
     *   Cerebras → Groq → OpenRouter → Together
     */
    private suspend fun sendViaOpenRouterStream(convId: String, text: String) {
        val multiEnabled = prefs.getBoolean("multi_provider", true)

        data class ProviderConfig(
            val provider: ApiClient.Provider,
            val apiKey: String,
            val defaultModel: String
        )

        val cerebrasKey = prefs.getString("cerebras_api_key", "") ?: ""
        val groqKey = prefs.getString("groq_api_key", "") ?: ""
        val openRouterKey = prefs.getString("openrouter_api_key", "") ?: ""
        val togetherKey = prefs.getString("together_api_key", "") ?: ""
        val openRouterModel = prefs.getString("openrouter_model", "google/gemma-3-27b-it:free")
            ?: "google/gemma-3-27b-it:free"

        // Build try-list based on configured keys
        val tryList = if (multiEnabled) {
            listOfNotNull(
                if (cerebrasKey.isNotBlank()) ProviderConfig(ApiClient.Provider.CEREBRAS, cerebrasKey, ApiClient.CEREBRAS_DEFAULT) else null,
                if (groqKey.isNotBlank()) ProviderConfig(ApiClient.Provider.GROQ, groqKey, ApiClient.GROQ_DEFAULT) else null,
                if (openRouterKey.isNotBlank()) ProviderConfig(ApiClient.Provider.OPENROUTER, openRouterKey, openRouterModel) else null,
                if (togetherKey.isNotBlank()) ProviderConfig(ApiClient.Provider.TOGETHER, togetherKey, ApiClient.TOGETHER_DEFAULT) else null
            )
        } else {
            // Nur den expliziten Provider nutzen
            val explicit = prefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter"
            listOfNotNull(
                when (explicit) {
                    "OpenRouter" -> if (openRouterKey.isNotBlank()) ProviderConfig(ApiClient.Provider.OPENROUTER, openRouterKey, openRouterModel) else null
                    "Groq" -> if (groqKey.isNotBlank()) ProviderConfig(ApiClient.Provider.GROQ, groqKey, ApiClient.GROQ_DEFAULT) else null
                    "Cerebras" -> if (cerebrasKey.isNotBlank()) ProviderConfig(ApiClient.Provider.CEREBRAS, cerebrasKey, ApiClient.CEREBRAS_DEFAULT) else null
                    "Together" -> if (togetherKey.isNotBlank()) ProviderConfig(ApiClient.Provider.TOGETHER, togetherKey, ApiClient.TOGETHER_DEFAULT) else null
                    else -> null
                }
            )
        }

        if (tryList.isEmpty()) {
            _errorMessage.value = "Kein API-Key konfiguriert!\n\n" +
                    "Geh zu Einstellungen → KI-Anbieter und trage mindestens einen Key ein:\n\n" +
                    "🚀 Cerebras (cloud.cerebras.ai) — sehr schnell, kostenlos\n" +
                    "⚡ Groq (console.groq.com) — schnell, kostenlos\n" +
                    "🌐 OpenRouter (openrouter.ai) — viele freie Modelle"
            return
        }

        val messages = buildOpenRouterHistory(text)
        val errors = mutableListOf<String>()

        for ((index, config) in tryList.withIndex()) {
            try {
                val request = OpenRouterChatRequest(
                    model = config.defaultModel,
                    messages = messages,
                    maxTokens = 1024,
                    temperature = 0.7f,
                    stream = true
                )
                val service = ApiClient.createOpenAICompatibleService(config.provider, config.apiKey)
                val response = service.chatCompletionStream(request)

                if (!response.isSuccessful) {
                    val code = response.code()
                    val body = response.errorBody()?.string()
                    errors += "${config.provider.emoji} ${config.provider.id}: HTTP $code"
                    // Fallback nur bei spezifischen Codes (Rate-Limit, Modell-Fehler, Auth-Fehler)
                    if (code in listOf(401, 402, 403, 404, 429, 500, 502, 503)) {
                        if (index < tryList.size - 1) continue
                    }
                    _errorMessage.value = formatHttpError(code, body) + "\n\n${errors.joinToString("\n")}"
                    return
                }

                val body = response.body() ?: continue

                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                repo.saveMessage(convId, assistantMsg, touchConversation = false)
                _isStreaming.value = true

                val persistIntervalMs = 90L
                val minCharsPerPersist = 24
                val builder = StringBuilder()
                var lastPersistAt = 0L
                var lastPersistedLength = 0
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
                                    val now = System.currentTimeMillis()
                                    val enoughTime = (now - lastPersistAt) >= persistIntervalMs
                                    val enoughChars = (builder.length - lastPersistedLength) >= minCharsPerPersist
                                    if (enoughTime || enoughChars) {
                                        repo.saveMessage(
                                            convId,
                                            assistantMsg.copy(text = builder.toString()),
                                            touchConversation = false
                                        )
                                        lastPersistAt = now
                                        lastPersistedLength = builder.length
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                if (builder.isNotEmpty() && lastPersistedLength != builder.length) {
                    repo.saveMessage(
                        convId,
                        assistantMsg.copy(text = builder.toString()),
                        touchConversation = false
                    )
                }
                if (builder.isNotEmpty()) {
                    repo.touchConversation(convId)
                }

                if (builder.isNotEmpty()) {
                    showNotification("BamaChat", builder.toString())
                    return
                } else {
                    // Leere Antwort, nächster Provider
                    errors += "${config.provider.emoji} ${config.provider.id}: leere Antwort"
                    if (index < tryList.size - 1) continue
                }
            } catch (e: Exception) {
                errors += "${config.provider.emoji} ${config.provider.id}: ${e.message?.take(50) ?: "Fehler"}"
                if (index < tryList.size - 1) continue
                // Letzter Provider failed
                handleGenericError(e)
                _errorMessage.value = (_errorMessage.value ?: "") + "\n\nVersuchte Provider:\n${errors.joinToString("\n")}"
                return
            }
        }

        // Alle Provider haben versagt
        _errorMessage.value = "Alle Provider haben versagt:\n${errors.joinToString("\n")}\n\nGeh ggf. zu Mini-Apps für Beschäftigung."
    }

    private suspend fun sendViaGemini(convId: String, text: String) {
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isBlank()) {
            _errorMessage.value = "Kein Gemini API-Key gesetzt!"
            return
        }
        try {
            val model = ApiClient.createGeminiModel(apiKey)
            val response = model.generateContent(text)
            val reply = response.text ?: "Keine Antwort von Gemini erhalten."
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = reply,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            repo.saveMessage(convId, msg)
            showNotification("BamaChat (Gemini)", reply)
        } catch (e: Exception) {
            handleGenericError(e)
        }
    }

    private suspend fun sendViaOllama(convId: String, text: String) {
        try {
            val baseUrl = prefs.getString("ollama_url", "http://192.168.178.162:11434/")
                ?: "http://192.168.178.162:11434/"
            val ollamaMessages = buildOllamaHistory(text)
            val request = OllamaChatRequest(
                model = _selectedModel.value.ifBlank { "llama3" },
                messages = ollamaMessages,
                stream = false
            )
            val service = ApiClient.createOllamaService(baseUrl)
            val response = service.chat("${baseUrl}api/chat", request)
            val reply = response.message.content
            if (reply.isBlank()) {
                _errorMessage.value = "Keine Antwort von Ollama. Server erreichbar?"
                return
            }
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = reply,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            repo.saveMessage(convId, msg)
            showNotification("BamaChat (Ollama)", reply)
        } catch (e: Exception) {
            handleGenericError(e)
        }
    }

    // ===== Helpers =====
    private fun buildOpenRouterHistory(latestUserText: String? = null): List<OpenRouterMessage> {
        val list = mutableListOf<OpenRouterMessage>()
        list.add(OpenRouterMessage(role = "system", content = resolveSystemPrompt()))
        val recentMessages = _messages.value.takeLast(10).toMutableList()
        if (!latestUserText.isNullOrBlank()) {
            val last = recentMessages.lastOrNull()
            if (last == null || !last.isUser || last.text != latestUserText) {
                recentMessages.add(
                    ChatMessage(
                        id = "pending-user",
                        text = latestUserText,
                        isUser = true
                    )
                )
            }
        }
        recentMessages.forEach { msg ->
            list.add(OpenRouterMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.text
            ))
        }
        return list
    }

    private fun buildOllamaHistory(latestUserText: String? = null): List<OllamaMessage> {
        val list = mutableListOf<OllamaMessage>()
        list.add(OllamaMessage(role = "system", content = resolveSystemPrompt()))
        val recentMessages = _messages.value.takeLast(10).toMutableList()
        if (!latestUserText.isNullOrBlank()) {
            val last = recentMessages.lastOrNull()
            if (last == null || !last.isUser || last.text != latestUserText) {
                recentMessages.add(
                    ChatMessage(
                        id = "pending-user",
                        text = latestUserText,
                        isUser = true
                    )
                )
            }
        }
        recentMessages.forEach { msg ->
            list.add(OllamaMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.text
            ))
        }
        return list
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val app = getApplication<Application>()
            val contentResolver = app.contentResolver
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            val maxSide = 1600
            val width = bitmap.width
            val height = bitmap.height
            val largestSide = maxOf(width, height)
            if (largestSide <= maxSide) return bitmap

            val scale = maxSide.toFloat() / largestSide.toFloat()
            val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeImageAsDataUrl(uri: Uri): String? {
        val bitmap = decodeBitmapFromUri(uri) ?: return null
        val output = ByteArrayOutputStream()
        val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        if (!compressed) return null
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    fun refreshMonetizationState() {
        ensureUsageDayIsCurrent()
        val isPremium = prefs.getBoolean(KEY_PREMIUM_ACTIVE, false)
        if (isPremium) _showPaywall.value = false
        _usageStatus.value = UsageStatus(
            isPremium = isPremium,
            textUsed = prefs.getInt(KEY_USAGE_TEXT, 0),
            textLimit = FREE_DAILY_TEXT_LIMIT,
            imageAnalysisUsed = prefs.getInt(KEY_USAGE_IMAGE_ANALYSIS, 0),
            imageAnalysisLimit = FREE_DAILY_IMAGE_ANALYSIS_LIMIT,
            imageGenerationUsed = prefs.getInt(KEY_USAGE_IMAGE_GENERATION, 0),
            imageGenerationLimit = FREE_DAILY_IMAGE_GENERATION_LIMIT
        )
    }

    fun dismissPaywall() {
        _showPaywall.value = false
    }

    fun openPaywall() {
        _showPaywall.value = true
    }

    private fun consumeImageMessageQuota(): Boolean {
        val isPremium = prefs.getBoolean(KEY_PREMIUM_ACTIVE, false)
        if (isPremium) return true
        ensureUsageDayIsCurrent()
        val textUsed = prefs.getInt(KEY_USAGE_TEXT, 0)
        val imageUsed = prefs.getInt(KEY_USAGE_IMAGE_ANALYSIS, 0)
        if (textUsed >= FREE_DAILY_TEXT_LIMIT || imageUsed >= FREE_DAILY_IMAGE_ANALYSIS_LIMIT) {
            if (textUsed >= FREE_DAILY_TEXT_LIMIT) {
                _errorMessage.value = "Tageslimit erreicht: $FREE_DAILY_TEXT_LIMIT Nachrichten. Upgrade auf Premium für unbegrenzt."
            } else {
                _errorMessage.value = "Tageslimit erreicht: $FREE_DAILY_IMAGE_ANALYSIS_LIMIT Bildanalysen. Upgrade auf Premium für unbegrenzt."
            }
            refreshMonetizationState()
            _showPaywall.value = true
            return false
        }
        prefs.edit()
            .putInt(KEY_USAGE_TEXT, textUsed + 1)
            .putInt(KEY_USAGE_IMAGE_ANALYSIS, imageUsed + 1)
            .apply()
        refreshMonetizationState()
        return true
    }

    private fun consumeQuota(type: QuotaType): Boolean {
        val isPremium = prefs.getBoolean(KEY_PREMIUM_ACTIVE, false)
        if (isPremium) return true
        ensureUsageDayIsCurrent()

        val (prefKey, limit, label) = when (type) {
            QuotaType.TEXT_MESSAGE -> Triple(KEY_USAGE_TEXT, FREE_DAILY_TEXT_LIMIT, "Nachrichten")
            QuotaType.IMAGE_ANALYSIS -> Triple(KEY_USAGE_IMAGE_ANALYSIS, FREE_DAILY_IMAGE_ANALYSIS_LIMIT, "Bildanalysen")
            QuotaType.IMAGE_GENERATION -> Triple(KEY_USAGE_IMAGE_GENERATION, FREE_DAILY_IMAGE_GENERATION_LIMIT, "Bildgenerierungen")
        }

        val current = prefs.getInt(prefKey, 0)
        if (current >= limit) {
            _errorMessage.value = "Tageslimit erreicht: $limit $label. Upgrade auf Premium für unbegrenzt."
            refreshMonetizationState()
            _showPaywall.value = true
            return false
        }

        prefs.edit().putInt(prefKey, current + 1).apply()
        refreshMonetizationState()
        return true
    }

    private fun ensureUsageDayIsCurrent() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val storedDay = prefs.getString(KEY_USAGE_DAY, null)
        if (storedDay == today) return
        prefs.edit()
            .putString(KEY_USAGE_DAY, today)
            .putInt(KEY_USAGE_TEXT, 0)
            .putInt(KEY_USAGE_IMAGE_ANALYSIS, 0)
            .putInt(KEY_USAGE_IMAGE_GENERATION, 0)
            .apply()
    }

    private fun handleHttpError(e: retrofit2.HttpException) {
        val code = e.code()
        val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
        _errorMessage.value = formatHttpError(code, body)
    }

    private fun formatHttpError(code: Int, body: String?): String = when (code) {
        401 -> "API-Key ungültig (401). Prüfe deinen Key in den Einstellungen."
        402 -> "Guthaben aufgebraucht (402)."
        403 -> "Zugriff verweigert (403). Modell evtl. nicht freigeschaltet."
        404 -> "Modell nicht gefunden (404). Wähle ein anderes in den Einstellungen.\n\n${body?.take(200) ?: ""}"
        429 -> "Rate-Limit erreicht (429). Warte 1 Min oder wechsle das Modell."
        500, 502, 503 -> "OpenRouter-Server-Problem ($code). Gleich nochmal."
        else -> "HTTP $code\n\n${body?.take(300) ?: ""}"
    }

    private fun handleGenericError(e: Exception) {
        _errorMessage.value = when {
            e.message?.contains("timeout", ignoreCase = true) == true ->
                "Zeitüberschreitung. Prüfe Internetverbindung."
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                "Keine Internetverbindung."
            e.message?.contains("CLEARTEXT", ignoreCase = true) == true ->
                "HTTPS-Fehler. App-Update nötig."
            else ->
                "Fehler: ${e.localizedMessage ?: e.message ?: "Unbekannt"}"
        }
    }

    // ===== Image Generation =====
    fun generateImage(prompt: String, skipUserMessage: Boolean = false) {
        if (prompt.isBlank()) return
        if (!consumeQuota(QuotaType.IMAGE_GENERATION)) return

        viewModelScope.launch {
            // Ensure we have a conversation ID
            var convId = _currentConversationId.value
            if (convId == null) {
                val newId = UUID.randomUUID().toString()
                repo.createConversation(newId, "Neuer Chat", _selectedPersona.value.name)
                switchConversation(newId)
                convId = newId
            }

            if (!skipUserMessage) {
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = prompt,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
                repo.saveMessage(convId, userMessage)
            }

            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && current.title == "Neuer Chat") {
                repo.renameConversation(convId, "Bild: ${prompt.take(30)}")
            }

            _isLoading.value = true
            try {
                val seed = (1000..9999).random()
                val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
                val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&seed=$seed&model=flux"

                val imageMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    imageUrl = imageUrl
                )
                repo.saveMessage(convId, imageMessage)
                showNotification("BamaChat Bild", "Bild generiert: $prompt")
            } catch (e: Exception) {
                handleGenericError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun isImageQuery(text: String): Boolean {
        val lower = text.lowercase()
        val imageKeywords = listOf(
            "bild", "image", "foto", "photo", "zeichne", "draw",
            "generier", "generat", "erstelle", "create", "male", "paint"
        )
        return imageKeywords.any { lower.contains(it) }
    }

    // ===== Setters =====
    fun clearChat() {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch { repo.clearMessages(convId) }
    }

    fun dismissError() { _errorMessage.value = null }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
        prefs.edit().putString("openrouter_model", model).apply()
    }

    fun setSelectedPersona(persona: Persona) {
        _selectedPersona.value = persona
        prefs.edit().putString("selected_persona", persona.name).apply()
    }

    fun setCustomPersonaPrompt(prompt: String) {
        _customPersonaPrompt.value = prompt
        prefs.edit().putString("custom_persona_prompt", prompt).apply()
    }

    fun getChatExportText(): String {
        return _messages.value.joinToString("\n") {
            "${if (it.isUser) "Du" else "BamaChat"}: ${it.text}"
        }
    }

    override fun onCleared() {
        messagesJob?.cancel()
        super.onCleared()
    }
}
