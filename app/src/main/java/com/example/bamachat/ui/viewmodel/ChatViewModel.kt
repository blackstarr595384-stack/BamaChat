package com.example.bamachat.ui.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.OpenRouterMessage
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ChatSource
import com.example.bamachat.data.model.ModelInfo
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.AudioTranscriptionManager
import com.example.bamachat.util.DocumentIngestor
import com.example.bamachat.util.EmotionAnalyzer
import com.example.bamachat.util.EmotionSignal
import com.example.bamachat.util.KnowledgeGraphExtractor
import com.example.bamachat.util.MemoryFactExtractor
import com.example.bamachat.util.MonetizationConfig
import com.example.bamachat.util.MultimodalAsset
import com.example.bamachat.util.MultimodalProcessor
import com.example.bamachat.util.VideoKeyframeExtractor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * ChatViewModel: Zentraler View-State für Chat-Screen
 * - Conversations & Messages
 * - Image Generation & Analysis
 * - Multimodal-Asset-Handling (Bilder, Dokumente, Audio, Video)
 * - Emotion-Detection
 * - Delegation zu:
 *   - PersonaViewModel (Personas, Prompts, Training)
 *   - MultiAgentViewModel (Multi-Agent-Collaboration)
 *   - MonetizationViewModel (Quotas, Credits)
 *   - ApiManager (API-Calls mit Retry-Logik)
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val db = ChatDatabase.getDatabase(application)
    private val repo = ChatRepository(db.chatDao())
    private val auth = FirebaseAuth.getInstance()
    private val imageHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    // ===== Delegated ViewModels =====
    val personaViewModel = PersonaViewModel(application)
    val multiAgentViewModel = MultiAgentViewModel(application, ApiManager(application), personaViewModel)
    val monetizationViewModel = MonetizationViewModel(application)
    private val apiManager = ApiManager(application)

    // ===== Chat State =====
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _conversations = MutableStateFlow<List<com.example.bamachat.data.local.ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<com.example.bamachat.data.local.ConversationEntity>> = _conversations

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels

    private val _selectedModel = MutableStateFlow(
        prefs.getString("openrouter_model", "google/gemma-3-27b-it:free") ?: "google/gemma-3-27b-it:free"
    )
    val selectedModel: StateFlow<String> = _selectedModel

    private val _chatSentiment = MutableStateFlow("neutral")
    val chatSentiment: StateFlow<String> = _chatSentiment

    private val _emotionSignal = MutableStateFlow(
        EmotionSignal(
            label = "neutral",
            sentiment = "neutral",
            empathyHint = "Neutrale Stimmung. Antworte klar und hilfreich."
        )
    )
    val emotionSignal: StateFlow<EmotionSignal> = _emotionSignal

    private val _messageFeedback = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val messageFeedback: StateFlow<Map<String, Boolean>> = _messageFeedback

    private val _isBiometricAuthenticated = MutableStateFlow(false)
    val isBiometricAuthenticated: StateFlow<Boolean> = _isBiometricAuthenticated

    private val _userMemoryFacts = MutableStateFlow<List<String>>(emptyList())
    val userMemoryFacts: StateFlow<List<String>> = _userMemoryFacts

    private val _knowledgeGraphHints = MutableStateFlow<List<String>>(emptyList())
    val knowledgeGraphHints: StateFlow<List<String>> = _knowledgeGraphHints

    private var messagesJob: Job? = null

    private data class LiveWebContext(
        val promptContext: String,
        val sources: List<ChatSource>,
        val fetchedAtIso: String
    )

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
            "Du bist ein einfühlsamer Gesprächspartner. Höre zu, stelle hilfreiche Rückfragen und hilf beim Reflektieren. Du bist KEIN Ersatz für echte Therapie. Antworte auf Deutsch, warm und respektvoll."
        ),
        CUSTOM(
            "Eigene Persona", "✨",
            ""
        )
    }

    // ===== Init =====
    init {
        createNotificationChannel()
        monetizationViewModel.refreshMonetizationState()

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
            repo.createConversation(id, "Neuer Chat", personaViewModel.selectedPersona.value.name)
            switchConversation(id)
        }
    }

    fun switchConversation(id: String) {
        _currentConversationId.value = id
        prefs.edit().putString("current_conversation_id", id).apply()
        _messages.value = emptyList()
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repo.getMessages(id).collectLatest { items ->
                _messages.value = items
                syncFeedbackForMessages(items)
            }
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

    fun clearChat() {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch { repo.clearMessages(convId) }
    }

    // ===== Message Sending =====
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val emotion = EmotionAnalyzer.analyze(text)
        _emotionSignal.value = emotion
        _chatSentiment.value = emotion.sentiment

        AppTelemetry.logEvent("chat_user_message", mapOf("length_bucket" to text.length.coerceAtMost(400).toString()))

        // Image Generation Query Detection
        if (isImageQuery(text)) {
            generateImage(text, skipUserMessage = true)
            return
        }

        // Check Quota
        val explicitWebQuery = isExplicitWebQuery(text)
        if (explicitWebQuery) {
            if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.WEB_RESEARCH)) {
                _errorMessage.value = "Live-Web-Limit erreicht. Upgrade oder Credits nötig."
                return
            }
        } else if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.TEXT_MESSAGE)) {
            return
        }

        val convId = _currentConversationId.value
        if (convId == null) {
            viewModelScope.launch {
                val newId = UUID.randomUUID().toString()
                repo.createConversation(newId, "Neuer Chat", personaViewModel.selectedPersona.value.name)
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

            // Auto-title first message
            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && current.title == "Neuer Chat") {
                val newTitle = text.take(40).ifBlank { "Chat" }
                repo.renameConversation(convId, newTitle)
            }

            // Extract memory facts & knowledge graph
            val extractedFacts = MemoryFactExtractor.extractFacts(text)
            extractedFacts.forEach { fact ->
                repo.saveUserMemoryFact(
                    personaName = "GLOBAL",
                    factText = fact,
                    confidence = 0.72f,
                    sourceMessageId = userMessage.id
                )
            }

            val extractedEdges = KnowledgeGraphExtractor.extractEdges(text)
            extractedEdges.forEach { edge ->
                repo.saveKnowledgeEdge(edge.from, edge.relation, edge.to, weight = 0.7f)
            }

            // Send via API
            _isLoading.value = true
            try {
                val runtimeContext = buildRuntimeContextForUserText(text)
                sendChatViaApi(convId, text, runtimeContext)
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
                _isStreaming.value = false
            }
        }
    }

    fun sendMessageWithImage(text: String, imageUri: Uri) {
        if (text.isBlank() && imageUri == Uri.EMPTY) return

        if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.IMAGE_ANALYSIS)) {
            return
        }

        val convId = _currentConversationId.value
        if (convId == null) {
            viewModelScope.launch {
                val newId = UUID.randomUUID().toString()
                repo.createConversation(newId, "Neuer Chat", personaViewModel.selectedPersona.value.name)
                switchConversation(newId)
                sendMessageWithImage(text, imageUri)
            }
            return
        }

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
                val systemPrompt = personaViewModel.getSystemPromptCached(personaViewModel.selectedPersona.value)
                val userInstruction = text.ifBlank { "Beschreibe den Bildinhalt präzise und strukturiert." }
                val ocrContext = if (prefs.getBoolean("local_ocr_enabled", true)) {
                    MultimodalProcessor.extractImageText(getApplication(), imageUri)
                        .takeIf { it.isNotBlank() }
                } else {
                    null
                }
                val analysisText = buildString {
                    append("Analysiere dieses Bild präzise und antworte auf Deutsch.")
                    append("\n\nNutzerhinweis:\n")
                    append(userInstruction)
                    if (!ocrContext.isNullOrBlank()) {
                        append("\n\nZusätzlicher OCR-Text aus dem Bild (kann Fehler enthalten):\n")
                        append(ocrContext.take(3_500))
                    }
                }
                
                val imageDataUrl = withContext(Dispatchers.IO) { encodeImageAsDataUrl(imageUri) }
                if (imageDataUrl != null) {
                    val result = apiManager.analyzeImage(systemPrompt, analysisText, imageDataUrl)
                    if (result.success) {
                        repo.saveMessage(
                            convId,
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = result.content,
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        showNotification("BamaChat (Bildanalyse)", result.content)
                    } else {
                        _errorMessage.value = "Bildanalyse fehlgeschlagen: ${result.error}"
                    }
                } else {
                    _errorMessage.value = "Bild konnte nicht kodiert werden."
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ===== Image Generation =====
    fun generateImage(prompt: String, skipUserMessage: Boolean = false) {
        if (prompt.isBlank()) return

        if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.IMAGE_GENERATION)) {
            return
        }

        viewModelScope.launch {
            var convId = _currentConversationId.value
            if (convId == null) {
                val newId = UUID.randomUUID().toString()
                repo.createConversation(newId, "Neuer Chat", personaViewModel.selectedPersona.value.name)
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
                val generationRequest = buildImageGenerationRequest(prompt)
                val imageUrl = resolveWorkingImageUrl(generationRequest.candidateUrls)
                    ?: generationRequest.candidateUrls.first()

                val imageMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = generationRequest.displayPrompt,
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    imageUrl = imageUrl
                )
                repo.saveMessage(convId, imageMessage)
                showNotification("BamaChat Bild", "Bild generiert: $prompt")
            } catch (e: Exception) {
                handleError(e)
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

    private data class ImageGenerationRequest(
        val displayPrompt: String,
        val candidateUrls: List<String>
    )

    private fun buildImageGenerationRequest(userPrompt: String): ImageGenerationRequest {
        val cleanPrompt = userPrompt.replace(Regex("\\s+"), " ").trim()
        val enhancedPrompt = buildEnhancedImagePrompt(cleanPrompt)
        val encodedPrompt = URLEncoder.encode(enhancedPrompt, "UTF-8")
        val seed = (10_000..99_999).random()
        val (width, height) = chooseImageResolution(cleanPrompt)
        val models = listOf("flux", "flux-realism", "turbo")
        val base = "https://image.pollinations.ai/prompt/$encodedPrompt"
        val urls = models.map { model ->
            "$base?width=$width&height=$height&seed=$seed&model=$model&nologo=true&enhance=true"
        }
        return ImageGenerationRequest(
            displayPrompt = cleanPrompt,
            candidateUrls = urls
        )
    }

    private fun buildEnhancedImagePrompt(prompt: String): String {
        val lower = prompt.lowercase(Locale.getDefault())
        val baseStyle = when {
            lower.contains("logo") -> "clean vector logo, minimal, sharp edges, white background"
            lower.contains("portrait") || lower.contains("gesicht") -> "portrait photography, natural skin texture, cinematic rim light, 85mm lens"
            lower.contains("anime") || lower.contains("manga") -> "high quality anime art, dynamic composition, crisp line art"
            lower.contains("produkt") || lower.contains("product") -> "studio product photography, softbox lighting, high detail"
            else -> "ultra detailed, professional composition, realistic lighting, high contrast, sharp focus"
        }
        return "$prompt. Style: $baseStyle. Avoid: blurry, low quality, distorted anatomy, artifacts, extra fingers, unreadable text."
    }

    private fun chooseImageResolution(prompt: String): Pair<Int, Int> {
        val lower = prompt.lowercase(Locale.getDefault())
        return when {
            lower.contains("banner") -> 1536 to 896
            lower.contains("wallpaper") -> 1344 to 768
            lower.contains("portrait") || lower.contains("hochformat") -> 896 to 1344
            else -> 1024 to 1024
        }
    }

    private suspend fun resolveWorkingImageUrl(candidates: List<String>): String? {
        return withContext(Dispatchers.IO) {
            candidates.firstOrNull { candidate ->
                runCatching {
                    val request = Request.Builder().url(candidate).get().build()
                    imageHttpClient.newCall(request).execute().use { response ->
                        response.isSuccessful
                    }
                }.getOrDefault(false)
            }
        }
    }

    // ===== Multimodal Assets =====
    fun importAdvancedMultimodalAsset(uri: Uri) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val fileInfo = queryFileInfo(uri)
                val fileSizeMb = fileInfo.second?.div(1024 * 1024) ?: 0L
                val rawCategory = detectCategoryForLimit(uri, fileInfo.first)
                val maxBytes = if (rawCategory == MultimodalAsset.Category.AUDIO || rawCategory == MultimodalAsset.Category.VIDEO) {
                    100L * 1024L * 1024L
                } else {
                    40L * 1024L * 1024L
                }

                if (fileInfo.second != null && fileInfo.second!! > maxBytes) {
                    val maxMb = maxBytes / (1024L * 1024L)
                    _errorMessage.value = "Datei zu groß (${fileSizeMb} MB). Max: ${maxMb} MB."
                    return@launch
                }

                val asset = MultimodalProcessor.parse(app, uri)
                when (asset.category) {
                    MultimodalAsset.Category.IMAGE -> {
                        sendMessageWithImage("Analysiere dieses Bild.", uri)
                    }
                    MultimodalAsset.Category.AUDIO -> {
                        transcribeAndIngestMedia(uri, asset.title, "audio")
                    }
                    MultimodalAsset.Category.VIDEO -> {
                        transcribeAndIngestMedia(uri, asset.title, "video")
                    }
                    MultimodalAsset.Category.PDF,
                    MultimodalAsset.Category.DOCX,
                    MultimodalAsset.Category.XLSX,
                    MultimodalAsset.Category.TEXT_DOC -> {
                        importKnowledgeDocument(uri)
                    }
                    else -> {
                        _errorMessage.value = "Dateityp nicht unterstützt."
                    }
                }
            } catch (e: Exception) {
                AppTelemetry.logError("multimodal_import", e)
                _errorMessage.value = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    fun importKnowledgeDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                val doc = DocumentIngestor.ingest(getApplication(), uri)
                if (doc == null) {
                    _errorMessage.value = "Dokument konnte nicht gelesen werden."
                    return@launch
                }

                splitIntoChunks(doc.text, 700, 120)
                    .take(40)
                    .forEach { chunk ->
                        val keywords = extractKeywords(chunk).joinToString(",")
                        repo.saveKnowledgeChunk(
                            sourceTitle = doc.title,
                            content = chunk,
                            keywords = keywords,
                            sourceType = doc.sourceType
                        )
                        KnowledgeGraphExtractor.extractEdges(chunk).forEach { edge ->
                            repo.saveKnowledgeEdge(edge.from, edge.relation, edge.to, weight = 1.0f)
                        }
                    }

                AppTelemetry.logEvent("knowledge_import_success", mapOf("source" to doc.sourceType))
                _errorMessage.value = "Wissensdokument importiert: ${doc.title}"
            } catch (e: Exception) {
                AppTelemetry.logError("knowledge_import", e)
                _errorMessage.value = "Dokument-Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    private suspend fun transcribeAndIngestMedia(uri: Uri, title: String, sourceType: String) {
        val groqKey = prefs.getString("groq_api_key", "") ?: ""

        if (groqKey.isNotBlank()) {
            val transcriptionManager = AudioTranscriptionManager(getApplication())
            val transcript = transcriptionManager.transcribeWithGroq(uri, groqKey)

            if (!transcript.isNullOrBlank()) {
                ingestKnowledgeText(title, "${sourceType}_transcript", transcript)
            }
        }

        if (sourceType == "video") {
            val keyframeSummary = VideoKeyframeExtractor.summarize(getApplication(), uri)
            if (keyframeSummary.isNotBlank()) {
                ingestKnowledgeText(title, "video_keyframes", keyframeSummary)
            }
        }

        _errorMessage.value = if (groqKey.isBlank()) {
            "Für Transkription Groq API-Key setzen."
        } else {
            "Multimodal importiert: $title"
        }
    }

    private suspend fun ingestKnowledgeText(title: String, sourceType: String, text: String): Boolean {
        if (text.length < 20) return false

        splitIntoChunks(text, 700, 120)
            .take(50)
            .forEach { chunk ->
                val keywords = extractKeywords(chunk).joinToString(",")
                repo.saveKnowledgeChunk(
                    sourceTitle = title,
                    content = chunk,
                    keywords = keywords,
                    sourceType = sourceType
                )
                KnowledgeGraphExtractor.extractEdges(chunk).forEach { edge ->
                    repo.saveKnowledgeEdge(edge.from, edge.relation, edge.to, weight = 0.9f)
                }
            }
        return true
    }

    // ===== API Calls =====
    private suspend fun sendChatViaApi(
        convId: String,
        text: String,
        runtimeContext: String? = null
    ) {
        val systemPrompt = personaViewModel.getSystemPromptCached(personaViewModel.selectedPersona.value)
        val webContext = resolveLiveWebContext(text)
        val messages = buildOpenRouterHistory(
            latestUserText = text,
            liveWebContext = webContext?.promptContext,
            runtimeContext = runtimeContext
        )

        _isStreaming.value = true

        val assistantMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = "",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        repo.saveMessage(convId, assistantMsg, touchConversation = false)
        val streamingBuffer = StringBuilder()

        val result = apiManager.streamChatResponse(
            systemPrompt = systemPrompt,
            userMessages = messages,
            onChunkReceived = { chunk ->
                viewModelScope.launch {
                    streamingBuffer.append(chunk)
                    repo.saveMessage(
                        convId,
                        assistantMsg.copy(text = streamingBuffer.toString()),
                        touchConversation = false
                    )
                }
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )

        if (result.success && result.content.isNotBlank()) {
            val finalized = assistantMsg.copy(
                text = result.content,
                sources = webContext?.sources.orEmpty(),
                webFetchedAtIso = webContext?.fetchedAtIso
            )
            repo.saveMessage(convId, finalized, touchConversation = true)
            showNotification("BamaChat", result.content)
        }
    }

    private fun buildOpenRouterHistory(
        latestUserText: String? = null,
        liveWebContext: String? = null,
        runtimeContext: String? = null
    ): List<OpenRouterMessage> {
        val list = mutableListOf<OpenRouterMessage>()
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

        val lastUserMessageId = recentMessages.lastOrNull { it.isUser }?.id
        recentMessages.forEach { msg ->
            val isLatestUserTurn = msg.isUser && msg.id == lastUserMessageId
            val content = if (isLatestUserTurn) {
                buildString {
                    append(msg.text)
                    if (!runtimeContext.isNullOrBlank()) {
                        append("\n\n")
                        append(runtimeContext)
                    }
                    if (!liveWebContext.isNullOrBlank()) {
                        append("\n\n")
                        append(liveWebContext)
                    }
                }
            } else {
                msg.text
            }
            list.add(OpenRouterMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = content
            ))
        }

        return list
    }

    private suspend fun resolveLiveWebContext(text: String): LiveWebContext? {
        if (!apiManager.shouldUseLiveWebResearch(text)) return null

        val explicitWeb = isExplicitWebQuery(text)
        if (!explicitWeb && !monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.WEB_RESEARCH)) {
            return null
        }

        val cleanedQuery = text.replace("web:", "", ignoreCase = true).trim()
        val research = apiManager.runLiveWebResearch(cleanedQuery)
        if (!research.success || research.sources.isEmpty()) {
            if (explicitWeb) {
                _errorMessage.value = "Live-Recherche fehlgeschlagen: ${research.error.ifBlank { "Keine Quellen gefunden." }}"
            }
            return null
        }

        val sourceRows = research.sources.mapIndexed { index, source ->
            val published = source.publishedAt?.takeIf { it.isNotBlank() }?.let { " (Stand: $it)" } ?: ""
            "${index + 1}. ${source.title}$published\nURL: ${source.url}\nSnippet: ${source.snippet.take(280)}"
        }
        val weatherQuery = isWeatherIntent(text)
        val contextBlock = buildString {
            appendLine("Live-Web-Recherche (aktuell, verifizierbar):")
            appendLine("Query: ${research.query}")
            sourceRows.forEach { row ->
                appendLine(row)
                appendLine()
            }
            if (weatherQuery) {
                appendLine("Wenn die Quellen Wetterdaten enthalten: antworte zuerst konkret mit Ort, Temperatur/Trend und kurzer Empfehlung.")
            }
            appendLine("Nutze diese Quellen nur wenn relevant. Erfinde keine URLs.")
        }.trim()

        return LiveWebContext(
            promptContext = contextBlock,
            sources = research.sources.map {
                ChatSource(
                    title = it.title,
                    url = it.url,
                    snippet = it.snippet,
                    publishedAt = it.publishedAt
                )
            },
            fetchedAtIso = research.fetchedAtIso
        )
    }

    private fun isExplicitWebQuery(text: String): Boolean {
        return text.trimStart().lowercase(Locale.getDefault()).startsWith("web:")
    }

    private fun isWeatherIntent(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val weatherKeywords = listOf(
            "wetter", "temperatur", "regen", "wind", "vorhersage",
            "forecast", "weather", "niederschlag", "gewitter"
        )
        return weatherKeywords.any { lower.contains(it) }
    }

    private suspend fun buildRuntimeContextForUserText(text: String): String? {
        if (!prefs.getBoolean("auto_language_detection_enabled", true)) return null

        val detectedLanguage = MultimodalProcessor.detectLanguageCode(text) ?: return null
        val appLanguage = prefs.getString("language", "de")?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (appLanguage.isBlank() || detectedLanguage == appLanguage) return null

        val appLanguageName = languageDisplayName(appLanguage)
        return "Sprach-Kontext: Nutzertext vermutlich in '$detectedLanguage'. " +
            "Verstehe die Anfrage in dieser Sprache; antworte standardmäßig in $appLanguageName " +
            "(Code: $appLanguage), außer der Nutzer verlangt explizit eine andere Ausgabesprache."
    }

    private fun languageDisplayName(code: String): String = when (code.lowercase(Locale.ROOT)) {
        "de" -> "Deutsch"
        "en" -> "Englisch"
        "fr" -> "Französisch"
        "es" -> "Spanisch"
        "pl" -> "Polnisch"
        "tr" -> "Türkisch"
        "ar" -> "Arabisch"
        else -> "der App-Sprache"
    }

    // ===== Message Feedback =====
    fun setMessageFeedback(messageId: String, helpful: Boolean) {
        val persona = personaViewModel.selectedPersona.value
        val assistantMsg = _messages.value.firstOrNull { it.id == messageId && !it.isUser } ?: return
        val userContext = _messages.value
            .takeWhile { it.id != messageId }
            .lastOrNull { it.isUser }
            ?.text
            .orEmpty()

        viewModelScope.launch {
            repo.savePersonaFeedback(persona.name, messageId, helpful)
            _messageFeedback.value = _messageFeedback.value.toMutableMap().apply {
                put(messageId, helpful)
            }

            if (helpful && userContext.isNotBlank() && assistantMsg.text.isNotBlank()) {
                personaViewModel.addManualTrainingExample(persona, userContext, assistantMsg.text)
            }
        }
    }

    fun getFeedbackForMessage(messageId: String): Boolean? = _messageFeedback.value[messageId]

    // ===== State Management =====
    fun setSelectedModel(model: String) {
        _selectedModel.value = model
        prefs.edit().putString("openrouter_model", model).apply()
    }

    fun setBiometricAuthenticated(authenticated: Boolean) {
        _isBiometricAuthenticated.value = authenticated
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun addManualTrainingExample(
        persona: Persona,
        userInput: String,
        idealResponse: String
    ) {
        personaViewModel.addManualTrainingExample(persona, userInput, idealResponse)
    }

    fun rollbackPromptForPersona(persona: Persona, versionId: Long) {
        personaViewModel.rollbackPromptForPersona(persona, versionId)
    }

    fun setSelectedPersona(persona: Persona) {
        personaViewModel.setSelectedPersona(persona)
    }

    fun getChatExportText(): String {
        return _messages.value.joinToString("\n") {
            "${if (it.isUser) "Du" else "BamaChat"}: ${it.text}"
        }
    }

    // ===== Privat: Hilfsfunktionen =====

    private suspend fun syncFeedbackForMessages(items: List<ChatMessage>) {
        val current = _messageFeedback.value.toMutableMap()
        var changed = false
        items.forEach { msg ->
            if (!msg.isUser && msg.id.isNotBlank() && !current.containsKey(msg.id)) {
                val feedback = repo.getFeedbackForMessage(msg.id)
                if (feedback != null) {
                    current[msg.id] = feedback
                    changed = true
                }
            }
        }
        if (changed) _messageFeedback.value = current
    }

    private fun queryFileInfo(uri: Uri): Pair<String?, Long?> {
        return runCatching {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (!cursor.moveToFirst()) return@use null
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else null
                name to size
            }
        }.getOrNull() ?: (null to null)
    }

    private fun detectCategoryForLimit(uri: Uri, fileName: String?): MultimodalAsset.Category {
        val app = getApplication<Application>()
        val mime = app.contentResolver.getType(uri).orEmpty().lowercase()
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when {
            mime.startsWith("audio/") || ext in setOf("mp3", "wav", "m4a", "ogg") -> MultimodalAsset.Category.AUDIO
            mime.startsWith("video/") || ext in setOf("mp4", "mov", "mkv", "webm") -> MultimodalAsset.Category.VIDEO
            else -> MultimodalAsset.Category.UNKNOWN
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val contentResolver = getApplication<Application>().contentResolver
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            val maxSide = 1600
            if (maxOf(bitmap.width, bitmap.height) <= maxSide) return bitmap

            val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeImageAsDataUrl(uri: Uri): String? {
        val bitmap = decodeBitmapFromUri(uri) ?: return null
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) return null
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun splitIntoChunks(text: String, chunkSize: Int, overlap: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= chunkSize) return listOf(clean)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            val end = (start + chunkSize).coerceAtMost(clean.length)
            chunks += clean.substring(start, end).trim()
            if (end == clean.length) break
            start = (end - overlap).coerceAtLeast(0)
        }
        return chunks
    }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            "und", "oder", "aber", "nicht", "mit", "für", "der", "die", "das", "ein", "eine",
            "ist", "sind", "war", "wie", "ich", "du", "wir", "sie", "man", "dass", "wenn"
        )
        return text.lowercase()
            .replace(Regex("[^a-zA-ZäöüÄÖÜß0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 4 && it !in stopWords }
            .distinct()
    }

    private fun handleError(e: Exception) {
        AppTelemetry.logError("chat_error", e)
        _errorMessage.value = when {
            e.message?.contains("timeout", ignoreCase = true) == true -> "Zeitüberschreitung. Internet prüfen."
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "Keine Internetverbindung."
            else -> "Fehler: ${e.message ?: "Unbekannt"}"
        }
    }

    override fun onCleared() {
        messagesJob?.cancel()
        super.onCleared()
    }

    // ===== Persona Character & Autonomy Profile (für Screen-Dialog) =====
    fun getPersonaCharacterProfile(persona: Persona): PersonaCharacterProfile {
        val profile = personaViewModel.getPersonaProfile(persona)
        return PersonaCharacterProfile(
            empathy = profile.empathy,
            creativity = profile.creativity,
            directness = profile.directness
        )
    }

    fun setPersonaCharacterProfile(persona: Persona, profile: PersonaCharacterProfile) {
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("persona_character_${persona.name.lowercase()}_empathy", profile.empathy)
            .putInt("persona_character_${persona.name.lowercase()}_creativity", profile.creativity)
            .putInt("persona_character_${persona.name.lowercase()}_directness", profile.directness)
            .apply()
        personaViewModel.systemPromptCache.clear()
    }

    fun getPersonaAutonomyProfile(persona: Persona): AutonomyProfile {
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        return AutonomyProfile(
            coreBelief = prefs.getString("autonomy_core_belief_${persona.name.lowercase()}", "") ?: "",
            instinct = prefs.getString("autonomy_instinct_${persona.name.lowercase()}", "") ?: "",
            signatureOpinionStyle = prefs.getString("autonomy_opinion_style_${persona.name.lowercase()}", "") ?: "",
            selfCorrectionStrictness = prefs.getInt("autonomy_self_correction_${persona.name.lowercase()}", 50)
        )
    }

    fun setPersonaAutonomyProfile(persona: Persona, profile: AutonomyProfile) {
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("autonomy_core_belief_${persona.name.lowercase()}", profile.coreBelief)
            .putString("autonomy_instinct_${persona.name.lowercase()}", profile.instinct)
            .putString("autonomy_opinion_style_${persona.name.lowercase()}", profile.signatureOpinionStyle)
            .putInt("autonomy_self_correction_${persona.name.lowercase()}", profile.selfCorrectionStrictness)
            .apply()
        personaViewModel.systemPromptCache.clear()
    }

    fun resetPromptForPersona(persona: Persona) {
        personaViewModel.resetPromptForPersona(persona)
    }

    fun getPersonaProfile(persona: Persona): PersonaCharacterProfile {
        val profile = personaViewModel.getPersonaProfile(persona)
        return PersonaCharacterProfile(
            empathy = profile.empathy,
            creativity = profile.creativity,
            directness = profile.directness
        )
    }

    fun refreshMonetizationState() {
        monetizationViewModel.refreshMonetizationState()
    }

    fun openPaywall() {
        monetizationViewModel.openPaywall()
    }

    fun dismissPaywall() {
        monetizationViewModel.dismissPaywall()
    }

    data class PersonaCharacterProfile(
        val empathy: Int = 50,
        val creativity: Int = 50,
        val directness: Int = 50
    )

    data class AutonomyProfile(
        val coreBelief: String = "",
        val instinct: String = "",
        val signatureOpinionStyle: String = "",
        val selfCorrectionStrictness: Int = 50
    )

    val selectedPersona: StateFlow<Persona>
        get() = personaViewModel.selectedPersona

    val customPersonaPrompt: StateFlow<String>
        get() = personaViewModel.customPersonaPrompt

    val usageStatus: StateFlow<MonetizationViewModel.UsageStatus>
        get() = monetizationViewModel.usageStatus

    val showPaywall: StateFlow<Boolean>
        get() = monetizationViewModel.showPaywall
}
