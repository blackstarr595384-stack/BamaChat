package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ChatSource
import com.example.bamachat.data.model.ModelInfo
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.service.ChatEngine
import com.example.bamachat.service.ConversationService
import com.example.bamachat.service.KnowledgeService
import com.example.bamachat.service.MediaService
import com.example.bamachat.service.NotificationService
import com.example.bamachat.service.ServiceLocator
import com.example.bamachat.shared.core.ChatSendDeduplicator
import com.example.bamachat.shared.core.QuickActionSuggestion
import com.example.bamachat.shared.core.WorkspaceNaming
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.EmotionSignal
import com.example.bamachat.util.McpServerManager
import com.example.bamachat.util.McpToolResult
import com.example.bamachat.util.McpContentItem
import com.example.bamachat.util.McpWorkflowManager
import com.example.bamachat.util.McpWorkflowStatus
import com.example.bamachat.util.MonetizationConfig
import com.example.bamachat.util.SecureSettingsStore
import com.example.bamachat.data.OpenRouterChatRequest
import com.example.bamachat.data.OpenRouterMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.OkHttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ToolCallProgress(
    val toolName: String,
    val arguments: String,
    val status: ToolCallStatus = ToolCallStatus.RUNNING,
    val result: String? = null
)

enum class ToolCallStatus { RUNNING, DONE, ERROR }

@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    val mcpServerManager: McpServerManager,
    val mcpWorkflowManager: McpWorkflowManager
) : AndroidViewModel(application) {
    companion object {
        private const val INITIAL_VISIBLE_MESSAGE_LIMIT = 100
        private const val LOAD_MORE_STEP = 70
        private const val DEV_INITIAL_VISIBLE_MESSAGE_LIMIT = 280
        private const val DEV_LOAD_MORE_STEP = 160
        private const val DEFAULT_HISTORY_LIMIT = 10
        private const val DEV_HISTORY_LIMIT = 40
        private const val KEY_EXTENSION_STATES_JSON = "workspace_extension_states_json"
        private const val KEY_EXTENSION_QUICK_ACTION = "extension_quick_action"

        internal fun computeWindowedMessages(all: List<ChatMessage>, limit: Int): List<ChatMessage> {
            if (all.isEmpty()) return emptyList()
            val safeLimit = limit.coerceAtLeast(1)
            return if (all.size <= safeLimit) all.toList() else all.takeLast(safeLimit)
        }

        internal fun normalizeForDedup(raw: String): String =
            ChatSendDeduplicator.normalizeForDedup(raw)

        internal fun normalizeWorkspaceName(raw: String): String =
            WorkspaceNaming.normalizeWorkspaceName(raw)
    }

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val appContext = getApplication<Application>().applicationContext

    // Services from ServiceLocator
    private val conversationService: ConversationService
        get() = ServiceLocator.conversationService
    private val knowledgeService: KnowledgeService
        get() = ServiceLocator.knowledgeService
    private val mediaService: MediaService
        get() = ServiceLocator.mediaService
    private val chatEngine: ChatEngine
        get() = ServiceLocator.chatEngine
    private val apiManager: ApiManager
        get() = ServiceLocator.apiManager
    private val notificationService: NotificationService
        get() = ServiceLocator.notificationService

    private val repo = ChatRepository(
        com.example.bamachat.data.local.ChatDatabase.getDatabase(application).chatDao()
    )
    private val imageHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    val personaViewModel = PersonaViewModel(application)
    val multiAgentViewModel = MultiAgentViewModel(application, apiManager, personaViewModel)
    val monetizationViewModel = MonetizationViewModel(application)

    // ===== Chat State =====
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    private val allMessagesBuffer = mutableListOf<ChatMessage>()
    private val _visibleMessageLimit = MutableStateFlow(INITIAL_VISIBLE_MESSAGE_LIMIT)
    private val _hasOlderMessages = MutableStateFlow(false)
    val hasOlderMessages: StateFlow<Boolean> = _hasOlderMessages

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
        EmotionSignal(label = "neutral", sentiment = "neutral", empathyHint = "Neutrale Stimmung. Antworte klar und hilfreich.")
    )
    val emotionSignal: StateFlow<EmotionSignal> = _emotionSignal

    private val _messageFeedback = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val messageFeedback: StateFlow<Map<String, Boolean>> = _messageFeedback

    private val _activeToolCalls = MutableStateFlow<List<ToolCallProgress>>(emptyList())
    val activeToolCalls: StateFlow<List<ToolCallProgress>> = _activeToolCalls

    private val _isBiometricAuthenticated = MutableStateFlow(false)
    val isBiometricAuthenticated: StateFlow<Boolean> = _isBiometricAuthenticated

    private val _userMemoryFacts = MutableStateFlow<List<String>>(emptyList())
    val userMemoryFacts: StateFlow<List<String>> = _userMemoryFacts

    private val _knowledgeGraphHints = MutableStateFlow<List<String>>(emptyList())
    val knowledgeGraphHints: StateFlow<List<String>> = _knowledgeGraphHints

    private val _selectedExtensionQuickAction = MutableStateFlow(
        ExtensionQuickAction.fromKey(prefs.getString(KEY_EXTENSION_QUICK_ACTION, ExtensionQuickAction.AUTO.key))
    )
    val selectedExtensionQuickAction: StateFlow<ExtensionQuickAction> = _selectedExtensionQuickAction

    private val _activeExtensionNames = MutableStateFlow<List<String>>(emptyList())
    val activeExtensionNames: StateFlow<List<String>> = _activeExtensionNames

    private val _lastAppliedExtensionNames = MutableStateFlow<List<String>>(emptyList())
    val lastAppliedExtensionNames: StateFlow<List<String>> = _lastAppliedExtensionNames

    private var messagesJob: Job? = null
    private var lastAcceptedTextSend: String? = null
    private var lastAcceptedConversationId: String? = null
    private var lastAcceptedTextSendAtMs: Long = 0L

    enum class ExtensionQuickAction(val key: String, val label: String) {
        AUTO("auto", "Auto"), RESEARCH("research", "Research"),
        CODE_REVIEW("code_review", "Code Review"), PLAN("plan", "Plan");

        companion object {
            fun fromKey(raw: String?): ExtensionQuickAction {
                val normalized = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
                return entries.firstOrNull { it.key == normalized } ?: AUTO
            }
        }
    }

    enum class Persona(val displayName: String, val emoji: String, val systemPrompt: String) {
        ASSISTANT("Assistent", "🤖", "Du bist BamaChat, ein hilfreicher deutschsprachiger KI-Assistent. Antworte kurz und präzise."),
        DEVELOPER("Entwickler", "💻", "Du bist ein erfahrener Software-Entwickler. Hilf mit Code-Beispielen und technischen Erklärungen. Nutze Markdown-Codeblöcke. Antworte auf Deutsch."),
        TEACHER("Lehrer", "🎓", "Du bist ein geduldiger Lehrer. Erkläre Dinge einfach und verständlich, mit Beispielen. Antworte auf Deutsch."),
        TRANSLATOR("Übersetzer", "🌍", "Du bist ein professioneller Übersetzer. Übersetze den Text des Benutzers. Wenn er Deutsch ist, übersetze ins Englische. Wenn nicht, ins Deutsche. Erkläre kurz Schwierigkeiten."),
        CHEF("Koch", "👨‍🍳", "Du bist ein kreativer Koch. Hilf mit Rezepten, Zutaten-Tipps und Kochtechniken. Antworte auf Deutsch, locker und enthusiastisch."),
        FITNESS("Fitness-Coach", "💪", "Du bist ein motivierender Fitness-Coach. Gib Tipps zu Training, Ernährung und Motivation. Antworte auf Deutsch, energiegeladen."),
        THERAPIST("Reflexions-Begleiter", "🧘", "Du bist ein einfühlsamer Gesprächspartner. Höre zu, stelle hilfreiche Rückfragen und hilf beim Reflektieren. Du bist KEIN Ersatz für echte Therapie. Antworte auf Deutsch, warm und respektvoll."),
        CUSTOM("Eigene Persona", "✨", "")
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_EXTENSION_STATES_JSON) refreshActiveExtensions()
    }

    init {
        notificationService.createChannel()
        monetizationViewModel.refreshMonetizationState()
        refreshActiveExtensions()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)

        viewModelScope.launch {
            repo.getAllConversations().collectLatest {
                _conversations.value = it
            }
        }

        val lastConvId = conversationService.getCurrentConversationId()
        if (lastConvId != null) {
            switchConversation(lastConvId)
        } else {
            viewModelScope.launch { newConversation() }
        }
    }

    // ===== Conversations =====
    fun newConversation() {
        viewModelScope.launch {
            val personaName = personaViewModel.selectedPersona.value.name
            val wsName = conversationService.activeWorkspaceName()
            val conv = conversationService.createConversation(personaName, wsName)
            switchConversation(conv.id)
        }
    }

    fun switchConversation(id: String) {
        _currentConversationId.value = id
        viewModelScope.launch {
            conversationService.switchConversation(id)
        }
        _messages.value = emptyList()
        _visibleMessageLimit.value = currentInitialVisibleMessageLimit()
        _hasOlderMessages.value = false
        allMessagesBuffer.clear()
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repo.getMessages(id).collectLatest { items ->
                allMessagesBuffer.clear()
                allMessagesBuffer.addAll(items)
                publishVisibleMessages()
                syncFeedbackForMessages(allMessagesBuffer)
            }
        }
    }

    fun loadOlderMessages() {
        _visibleMessageLimit.value += currentLoadMoreStep()
        publishVisibleMessages()
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch { conversationService.rename(id, newTitle) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationService.delete(id)
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
    fun sendMessage(text: String, quickAction: ExtensionQuickAction = _selectedExtensionQuickAction.value): Boolean {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return false
        if (_isLoading.value || _isStreaming.value) return false

        val convId = _currentConversationId.value
        val now = System.currentTimeMillis()
        val normalizedText = chatEngine.normalizeForDedup(trimmedText)
        if (chatEngine.isDuplicateSend(lastAcceptedTextSend, lastAcceptedConversationId, lastAcceptedTextSendAtMs,
                normalizedText, convId, now)) return false

        val emotion = chatEngine.analyzeEmotion(trimmedText)
        _emotionSignal.value = emotion
        _chatSentiment.value = emotion.sentiment

        if (mediaService.isImageQuery(trimmedText)) {
            generateImage(trimmedText, skipUserMessage = true)
            return true
        }

        if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.TEXT_MESSAGE)) return false

        lastAcceptedTextSend = normalizedText
        lastAcceptedConversationId = convId
        lastAcceptedTextSendAtMs = now

        if (convId == null) {
            viewModelScope.launch {
                val personaName = personaViewModel.selectedPersona.value.name
                val wsName = conversationService.activeWorkspaceName()
                val conv = conversationService.createConversation(personaName, wsName)
                switchConversation(conv.id)
                sendMessage(trimmedText, quickAction)
            }
            return true
        }

        val userMessage = ChatMessage(id = UUID.randomUUID().toString(), text = trimmedText, isUser = true, timestamp = System.currentTimeMillis())
        _isLoading.value = true
        viewModelScope.launch {
            repo.saveMessage(convId, userMessage)

            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && conversationService.isPlaceholderTitle(current.title)) {
                repo.renameConversation(convId, trimmedText.take(40).ifBlank { "Chat" })
            }

            knowledgeService.extractAndSaveFacts(trimmedText, "GLOBAL", userMessage.id)
            knowledgeService.extractAndSaveEdges(trimmedText)

            val personaName = personaViewModel.selectedPersona.value.name
            val knowledgeContext = knowledgeService.retrieveRelevantContext(trimmedText, personaName)

            try {
                val runtimeContext = chatEngine.buildRuntimeContext(trimmedText)
                val mergedContext = listOfNotNull(runtimeContext, knowledgeContext.takeIf { it.isNotBlank() })
                    .filter { it.isNotBlank() }.joinToString("\n\n").takeIf { it.isNotBlank() }
                val activeExt = resolveActiveExtensions()
                val extensionRuntime = chatEngine.buildExtensionRuntimeContext(
                    trimmedText, quickAction.toShared(),
                    activeExt, listOf()
                )
                _lastAppliedExtensionNames.value = extensionRuntime?.appliedExtensionNames.orEmpty()
                sendChatViaApi(convId, trimmedText, runtimeContext = mergedContext, extensionRuntime)
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
                _isStreaming.value = false
            }
        }
        return true
    }

    fun sendMessageWithImage(text: String, imageUri: Uri): Boolean {
        if (_isLoading.value || _isStreaming.value) return false
        if (text.isBlank() && imageUri == Uri.EMPTY) return false
        if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.IMAGE_ANALYSIS)) return false

        val convId = _currentConversationId.value ?: run {
            viewModelScope.launch {
                val personaName = personaViewModel.selectedPersona.value.name
                val wsName = conversationService.activeWorkspaceName()
                val conv = conversationService.createConversation(personaName, wsName)
                switchConversation(conv.id)
                sendMessageWithImage(text, imageUri)
            }
            return true
        }

        val userMessage = ChatMessage(id = UUID.randomUUID().toString(), text = text.ifBlank { "Bild" },
            isUser = true, timestamp = System.currentTimeMillis(), imageUrl = imageUri.toString())

        _isLoading.value = true
        viewModelScope.launch {
            repo.saveMessage(convId, userMessage)
            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && conversationService.isPlaceholderTitle(current.title)) {
                repo.renameConversation(convId, text.take(40).ifBlank { "Bild-Chat" })
            }
            try {
                val systemPrompt = personaViewModel.getSystemPromptCached(personaViewModel.selectedPersona.value)
                val result = mediaService.analyzeImage(systemPrompt, text, imageUri,
                    enableOcr = prefs.getBoolean("local_ocr_enabled", true))
                if (result.success) {
                    repo.saveMessage(convId, ChatMessage(id = UUID.randomUUID().toString(),
                        text = result.content, isUser = false, timestamp = System.currentTimeMillis()))
                    notificationService.show("BamaChat (Bildanalyse)", result.content, prefs.getBoolean("notifications_enabled", true))
                } else {
                    _errorMessage.value = result.error
                }
            } catch (e: Exception) { handleError(e) }
            finally { _isLoading.value = false }
        }
        return true
    }

    fun generateImage(prompt: String, skipUserMessage: Boolean = false) {
        if (prompt.isBlank()) return
        if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.IMAGE_GENERATION)) return

        viewModelScope.launch {
            var convId = _currentConversationId.value
            if (convId == null) {
                val personaName = personaViewModel.selectedPersona.value.name
                val wsName = conversationService.activeWorkspaceName()
                val conv = conversationService.createConversation(personaName, wsName)
                switchConversation(conv.id)
                convId = conv.id
            }
            if (!skipUserMessage) {
                repo.saveMessage(convId, ChatMessage(id = UUID.randomUUID().toString(),
                    text = prompt, isUser = true, timestamp = System.currentTimeMillis()))
            }
            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && conversationService.isPlaceholderTitle(current.title)) {
                repo.renameConversation(convId, "Bild: ${prompt.take(30)}")
            }
            _isLoading.value = true
            try {
                val genReq = mediaService.buildImageGenerationRequest(prompt)
                val imageUrl = resolveWorkingImageUrl(genReq.candidateUrls) ?: genReq.candidateUrls.first()
                repo.saveMessage(convId, ChatMessage(id = UUID.randomUUID().toString(),
                    text = genReq.displayPrompt, isUser = false, timestamp = System.currentTimeMillis(),
                    imageUrl = imageUrl))
                notificationService.show("BamaChat Bild", "Bild generiert: $prompt", prefs.getBoolean("notifications_enabled", true))
            } catch (e: Exception) { handleError(e) }
            finally { _isLoading.value = false }
        }
    }

    private suspend fun resolveWorkingImageUrl(candidates: List<String>): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            candidates.firstOrNull { candidate ->
                runCatching {
                    val request = okhttp3.Request.Builder().url(candidate).get().build()
                    imageHttpClient.newCall(request).execute().use { it.isSuccessful }
                }.getOrDefault(false)
            }
        }
    }

    fun importAdvancedMultimodalAsset(uri: Uri) {
        viewModelScope.launch {
            try {
                val groqKey = SecureSettingsStore.getString(appContext, prefs, "groq_api_key")
                val result = mediaService.importMultimodal(uri, groqKey)
                when {
                    result == "image" -> sendMessageWithImage("Analysiere dieses Bild.", uri)
                    result?.startsWith("document_imported:") == true -> {
                        val title = result.substringAfter("document_imported:")
                        _errorMessage.value = "Wissensdokument importiert: $title"
                    }
                    result != null -> _errorMessage.value = "Multimodal importiert"
                    else -> _errorMessage.value = "Dateityp nicht unterstützt."
                }
            } catch (e: Exception) {
                AppTelemetry.logError("multimodal_import", e)
                _errorMessage.value = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    // ===== API Calls =====
    private suspend fun sendChatViaApi(
        convId: String, text: String,
        runtimeContext: String? = null,
        extensionRuntime: ChatEngine.ExtensionRuntime? = null
    ) {
        val startedAt = System.currentTimeMillis()
        val systemPrompt = personaViewModel.getSystemPromptCached(personaViewModel.selectedPersona.value)
        val mergedRuntimeContext = listOfNotNull(runtimeContext, extensionRuntime?.promptContext)
            .filter { it.isNotBlank() }.joinToString("\n\n").takeIf { it.isNotBlank() }
        val forceWebResearch = extensionRuntime?.forceWebResearch == true
        val appliedExtensions = extensionRuntime?.appliedExtensionNames.orEmpty()

        val webContext = if (forceWebResearch || chatEngine.chargesForWebResearch()) {
            val canResearch = forceWebResearch || monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.WEB_RESEARCH)
            if (canResearch) chatEngine.resolveLiveWebContext(text, forceByExtension = forceWebResearch) else null
        } else null

        val toolDefs = mcpServerManager.getToolDefinitionsOpenAI() + mcpWorkflowManager.getOpenAIToolDefinitions()
        val hasTools = toolDefs.isNotEmpty()

        if (hasTools) {
            runAgentLoop(convId, text, systemPrompt, startedAt, webContext, toolDefs)
        } else {
            runStreamingChat(convId, text, systemPrompt, mergedRuntimeContext, webContext, forceWebResearch, startedAt)
        }
    }

    private suspend fun runAgentLoop(
        convId: String, userText: String, systemPrompt: String,
        startedAt: Long, webContext: ChatEngine.LiveWebContext?, toolDefs: List<Map<String, Any>>
    ) {
        val mcpToolHint = """
Du hast Zugriff auf Werkzeuge (Tools). Wenn du ein Werkzeug benötigst, antworte mit einem tool_call.
Nach jedem tool_call erhältst du das Ergebnis und kannst entscheiden, ob du ein weiteres Werkzeug brauchst oder die Antwort formulierst.
Werkzeuge: ${toolDefs.joinToString(", ") { it["function"]?.let { f -> (f as Map<*, *>)["name"] }?.toString() ?: "?" }}
""".trim()
        val fullSystemPrompt = "$systemPrompt\n\n$mcpToolHint"

        val messages = chatEngine.buildOpenRouterHistory(
            _messages.value, latestUserText = userText,
            liveWebContext = webContext?.promptContext,
            runtimeContext = null,
            historyLimit = if (isDeveloperUnlimitedTrainingEnabled()) DEV_HISTORY_LIMIT else DEFAULT_HISTORY_LIMIT
        ).toMutableList()

        _isStreaming.value = true
        val assistantMsg = ChatMessage(id = UUID.randomUUID().toString(), text = "",
            isUser = false, timestamp = System.currentTimeMillis())
        repo.saveMessage(convId, assistantMsg, touchConversation = false)

        var finalContent: String? = null
        var iteration = 0
        val maxIter = 5

        try {
            while (iteration < maxIter) {
                iteration++
                val request = OpenRouterChatRequest(
                    model = selectedModel.value,
                    messages = messages,
                    stream = false,
                    tools = toolDefs,
                    toolChoice = "auto",
                    maxTokens = 2048
                )

                val response = apiManager.oneShotChatCompletion(request, fullSystemPrompt)
                if (response == null) {
                    _errorMessage.value = "Agent: Keine Antwort vom Provider"
                    repo.deleteMessage(assistantMsg.id)
                    return
                }

                val choice = response.choices?.firstOrNull() ?: break
                val replyMsg = choice.message
                messages.add(replyMsg)

                val toolCalls = replyMsg.toolCalls
                if (toolCalls.isNullOrEmpty()) {
                    finalContent = replyMsg.content ?: ""
                    _activeToolCalls.value = emptyList()
                    break
                }

                _activeToolCalls.value = toolCalls.map { tc ->
                    ToolCallProgress(toolName = tc.function.name, arguments = tc.function.arguments)
                }

                for (toolCall in toolCalls) {
                    val jsonObj = try {
                        org.json.JSONObject(toolCall.function.arguments)
                    } catch (_: Exception) { null }
                    val args = if (jsonObj != null) {
                        jsonObj.keys().asSequence().associateWith { key ->
                            jsonObj.get(key)
                        }
                    } else emptyMap<String, Any>()

                    val result = if (toolCall.function.name.startsWith("workflow_")) {
                        val wfId = toolCall.function.name.removePrefix("workflow_")
                        val execResult = mcpWorkflowManager.executeWorkflow(wfId, args)
                        McpToolResult(
                            success = execResult.status == McpWorkflowStatus.COMPLETED,
                            content = listOf(McpContentItem(type = "text", text = execResult.finalOutput ?: execResult.error ?: "")))
                    } else {
                        mcpServerManager.callTool(toolCall.function.name, args)
                    }
                    val resultText = result.content.joinToString("\n") { it.text ?: it.data ?: "" }

                    _activeToolCalls.value = _activeToolCalls.value.map {
                        if (it.toolName == toolCall.function.name) it.copy(
                            status = if (result.success) ToolCallStatus.DONE else ToolCallStatus.ERROR,
                            result = resultText.take(200)
                        ) else it
                    }

                    messages.add(OpenRouterMessage(
                        role = "tool",
                        toolCallId = toolCall.id,
                        content = resultText
                    ))
                }
            }

            if (finalContent == null) finalContent = "Agent: Maximale Iterationen erreicht."

            val trimmedContent = finalContent.trim()
            if (trimmedContent.isBlank()) {
                _errorMessage.value = "Agent: Leere Antwort vom Provider"
                repo.deleteMessage(assistantMsg.id)
                return
            }

            repo.saveMessage(convId, assistantMsg.copy(text = trimmedContent, sources = webContext?.sources.orEmpty(), webFetchedAtIso = webContext?.fetchedAtIso), touchConversation = true)
            notificationService.show("BamaChat", trimmedContent, prefs.getBoolean("notifications_enabled", true))
        } catch (e: Exception) {
            AppTelemetry.logError("chat_agent_error", e)
            _errorMessage.value = "Agent-Fehler: ${e.message ?: "Unbekannt"}"
            repo.deleteMessage(assistantMsg.id)
        } finally {
            _activeToolCalls.value = emptyList()
            _isStreaming.value = false
        }
    }

    private suspend fun runStreamingChat(
        convId: String, text: String, systemPrompt: String,
        mergedRuntimeContext: String?, webContext: ChatEngine.LiveWebContext?,
        forceWebResearch: Boolean, startedAt: Long
    ) {
        val messages = chatEngine.buildOpenRouterHistory(
            _messages.value, latestUserText = text,
            liveWebContext = webContext?.promptContext,
            runtimeContext = mergedRuntimeContext,
            historyLimit = if (isDeveloperUnlimitedTrainingEnabled()) DEV_HISTORY_LIMIT else DEFAULT_HISTORY_LIMIT
        )

        _isStreaming.value = true
        val assistantMsg = ChatMessage(id = UUID.randomUUID().toString(), text = "",
            isUser = false, timestamp = System.currentTimeMillis())
        repo.saveMessage(convId, assistantMsg, touchConversation = false)
        val streamingBuffer = StringBuilder()
        val streamFlushInterval = 250L
        var lastFlushAt = System.currentTimeMillis()

        try {
            val result = apiManager.streamChatResponse(
                systemPrompt = systemPrompt, userMessages = messages,
                onChunkReceived = { chunk ->
                    streamingBuffer.append(chunk)
                    val now = System.currentTimeMillis()
                    if (now - lastFlushAt >= streamFlushInterval) {
                        lastFlushAt = now
                        viewModelScope.launch {
                            repo.saveMessage(convId, assistantMsg.copy(text = streamingBuffer.toString()), touchConversation = false)
                        }
                    }
                },
                onError = { error ->
                    _errorMessage.value = error
                    AppTelemetry.logEvent("chat_stream_error", mapOf("duration_ms" to (System.currentTimeMillis() - startedAt).toString()))
                }
            )

            if (result.success && result.content.isNotBlank()) {
                val finalized = assistantMsg.copy(text = result.content, sources = webContext?.sources.orEmpty(), webFetchedAtIso = webContext?.fetchedAtIso)
                repo.saveMessage(convId, finalized, touchConversation = true)
                notificationService.show("BamaChat", result.content, prefs.getBoolean("notifications_enabled", true))
            } else {
                repo.deleteMessage(assistantMsg.id)
            }
        } catch (e: Exception) {
            AppTelemetry.logError("chat_stream_exception", e)
            _errorMessage.value = "Stream-Fehler: ${e.message ?: "Unbekannt"}"
            repo.deleteMessage(assistantMsg.id)
        }
    }

    fun getConversationsForWorkspace(activeWorkspaceName: String, onlyActiveWorkspace: Boolean): List<com.example.bamachat.data.local.ConversationEntity> {
        return conversationService.getConversationsForWorkspace(_conversations.value, activeWorkspaceName, onlyActiveWorkspace)
    }

    // ===== Feedback =====
    fun setMessageFeedback(messageId: String, helpful: Boolean) {
        val persona = personaViewModel.selectedPersona.value
        val assistantMsg = _messages.value.firstOrNull { it.id == messageId && !it.isUser } ?: return
        val userContext = _messages.value.takeWhile { it.id != messageId }.lastOrNull { it.isUser }?.text.orEmpty()

        viewModelScope.launch {
            repo.savePersonaFeedback(persona.name, messageId, helpful)
            _messageFeedback.value = _messageFeedback.value.toMutableMap().apply { put(messageId, helpful) }
            trimMessageFeedback()
            if (helpful && userContext.isNotBlank() && assistantMsg.text.isNotBlank()) {
                personaViewModel.addManualTrainingExample(persona, userContext, assistantMsg.text)
            }
        }
    }

    fun getFeedbackForMessage(messageId: String): Boolean? = _messageFeedback.value[messageId]

    fun formatConversationAsMarkdown(): String {
        val msgs = _messages.value
        val convId = _currentConversationId.value
        val convTitle = _conversations.value.firstOrNull { it.id == convId }?.title ?: "Chat"
        val sb = StringBuilder()
        sb.appendLine("# $convTitle\n")
        sb.appendLine("Exportiert am ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}\n")
        sb.appendLine("---\n")
        msgs.forEach { msg ->
            val role = if (msg.isUser) "**Du**" else "**KI**"
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
            sb.appendLine("### $role ($time)")
            sb.appendLine()
            if (msg.imageUrl != null) {
                sb.appendLine("![Bild](${msg.imageUrl})")
            }
            sb.appendLine(msg.text)
            sb.appendLine()
        }
        return sb.toString()
    }

    // ===== State Management =====
    fun setSelectedModel(model: String) {
        _selectedModel.value = model
        prefs.edit().putString("openrouter_model", model).apply()
    }

    fun setBiometricAuthenticated(authenticated: Boolean) { _isBiometricAuthenticated.value = authenticated }
    fun dismissError() { _errorMessage.value = null }

    fun addManualTrainingExample(persona: Persona, userInput: String, idealResponse: String) {
        personaViewModel.addManualTrainingExample(persona, userInput, idealResponse)
    }

    fun rollbackPromptForPersona(persona: Persona, versionId: Long) {
        personaViewModel.rollbackPromptForPersona(persona, versionId)
    }

    fun setSelectedPersona(persona: Persona) { personaViewModel.setSelectedPersona(persona) }

    fun getChatExportText(): String = _messages.value.joinToString("\n") {
        "${if (it.isUser) "Du" else "BamaChat"}: ${it.text}"
    }

    fun setExtensionQuickAction(action: ExtensionQuickAction) {
        _selectedExtensionQuickAction.value = action
        prefs.edit().putString(KEY_EXTENSION_QUICK_ACTION, action.key).apply()
    }

    fun refreshMonetizationState() { monetizationViewModel.refreshMonetizationState() }
    fun openPaywall() { monetizationViewModel.openPaywall() }
    fun dismissPaywall() { monetizationViewModel.dismissPaywall() }

    val selectedPersona: StateFlow<Persona> get() = personaViewModel.selectedPersona
    val customPersonaPrompt: StateFlow<String> get() = personaViewModel.customPersonaPrompt
    val usageStatus: StateFlow<MonetizationViewModel.UsageStatus> get() = monetizationViewModel.usageStatus
    val showPaywall: StateFlow<Boolean> get() = monetizationViewModel.showPaywall

    // ===== Privat =====
    private fun refreshActiveExtensions() {
        val raw = prefs.getString(KEY_EXTENSION_STATES_JSON, "")
        val list = chatEngine.resolveActiveExtensions(raw)
        _activeExtensionNames.value = list.map { it.manifest.name }
    }

    private fun resolveActiveExtensions(): List<com.example.bamachat.util.ActiveWorkspaceExtension> {
        val raw = prefs.getString(KEY_EXTENSION_STATES_JSON, "")
        return chatEngine.resolveActiveExtensions(raw)
    }

    private fun handleError(e: Exception) {
        AppTelemetry.logError("chat_error", e)
        _errorMessage.value = when {
            e.message?.contains("timeout", ignoreCase = true) == true -> "Zeitüberschreitung. Internet prüfen."
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "Keine Internetverbindung."
            else -> "Fehler: ${e.message ?: "Unbekannt"}"
        }
    }

    private fun publishVisibleMessages() {
        val all = allMessagesBuffer
        if (all.isEmpty()) { _messages.value = emptyList(); _hasOlderMessages.value = false; return }
        val visible = computeWindowedMessages(all, _visibleMessageLimit.value)
        _messages.value = visible
        _hasOlderMessages.value = all.size > visible.size
    }

    private fun currentInitialVisibleMessageLimit(): Int =
        if (isDeveloperUnlimitedTrainingEnabled()) DEV_INITIAL_VISIBLE_MESSAGE_LIMIT else INITIAL_VISIBLE_MESSAGE_LIMIT

    private fun currentLoadMoreStep(): Int =
        if (isDeveloperUnlimitedTrainingEnabled()) DEV_LOAD_MORE_STEP else LOAD_MORE_STEP

    private fun isDeveloperUnlimitedTrainingEnabled(): Boolean =
        prefs.getBoolean("developer_mode_enabled", false) && prefs.getBoolean("developer_unlimited_training", false)

    private suspend fun syncFeedbackForMessages(items: List<ChatMessage>) {
        val current = _messageFeedback.value.toMutableMap()
        var changed = false
        items.forEach { msg ->
            if (!msg.isUser && msg.id.isNotBlank() && !current.containsKey(msg.id)) {
                val feedback = repo.getFeedbackForMessage(msg.id)
                if (feedback != null) { current[msg.id] = feedback; changed = true }
            }
        }
        if (changed) _messageFeedback.value = current
        trimMessageFeedback()
    }

    private fun trimMessageFeedback(maxEntries: Int = 200) {
        val current = _messageFeedback.value
        if (current.size <= maxEntries) return
        _messageFeedback.value = current.entries
            .toList()
            .takeLast(maxEntries)
            .associate { it.toPair() }
    }

    private fun ExtensionQuickAction.toShared(): QuickActionSuggestion = when (this) {
        ExtensionQuickAction.AUTO -> QuickActionSuggestion.AUTO
        ExtensionQuickAction.RESEARCH -> QuickActionSuggestion.RESEARCH
        ExtensionQuickAction.CODE_REVIEW -> QuickActionSuggestion.CODE_REVIEW
        ExtensionQuickAction.PLAN -> QuickActionSuggestion.PLAN
    }

    // ===== Persona Character & Autonomy Profile (für Screen-Dialog) =====
    fun getPersonaCharacterProfile(persona: Persona): PersonaCharacterProfile {
        val profile = personaViewModel.getPersonaProfile(persona)
        return PersonaCharacterProfile(empathy = profile.empathy, creativity = profile.creativity, directness = profile.directness)
    }

    fun setPersonaCharacterProfile(persona: Persona, profile: PersonaCharacterProfile) {
        prefs.edit()
            .putInt("persona_character_${persona.name.lowercase()}_empathy", profile.empathy)
            .putInt("persona_character_${persona.name.lowercase()}_creativity", profile.creativity)
            .putInt("persona_character_${persona.name.lowercase()}_directness", profile.directness)
            .apply()
        personaViewModel.systemPromptCache.clear()
    }

    fun getPersonaAutonomyProfile(persona: Persona): AutonomyProfile {
        return AutonomyProfile(
            coreBelief = prefs.getString("autonomy_core_belief_${persona.name.lowercase()}", "") ?: "",
            instinct = prefs.getString("autonomy_instinct_${persona.name.lowercase()}", "") ?: "",
            signatureOpinionStyle = prefs.getString("autonomy_opinion_style_${persona.name.lowercase()}", "") ?: "",
            selfCorrectionStrictness = prefs.getInt("autonomy_self_correction_${persona.name.lowercase()}", 50)
        )
    }

    fun setPersonaAutonomyProfile(persona: Persona, profile: AutonomyProfile) {
        prefs.edit()
            .putString("autonomy_core_belief_${persona.name.lowercase()}", profile.coreBelief)
            .putString("autonomy_instinct_${persona.name.lowercase()}", profile.instinct)
            .putString("autonomy_opinion_style_${persona.name.lowercase()}", profile.signatureOpinionStyle)
            .putInt("autonomy_self_correction_${persona.name.lowercase()}", profile.selfCorrectionStrictness)
            .apply()
        personaViewModel.systemPromptCache.clear()
    }

    fun resetPromptForPersona(persona: Persona) { personaViewModel.resetPromptForPersona(persona) }
    fun getPersonaProfile(persona: Persona): PersonaCharacterProfile = getPersonaCharacterProfile(persona)

    data class PersonaCharacterProfile(val empathy: Int = 50, val creativity: Int = 50, val directness: Int = 50)
    data class AutonomyProfile(val coreBelief: String = "", val instinct: String = "", val signatureOpinionStyle: String = "", val selfCorrectionStrictness: Int = 50)

    override fun onCleared() {
        messagesJob?.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onCleared()
    }
}
