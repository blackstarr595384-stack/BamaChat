package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.cloud.AndroidChatSyncCoordinator
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ConversationPersonaMetadata
import com.example.bamachat.data.model.ChatSource
import com.example.bamachat.data.model.ModelInfo
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.service.ChatEngine
import com.example.bamachat.service.ChatErrorRecoveryPolicy
import com.example.bamachat.service.ConversationService
import com.example.bamachat.service.KnowledgeService
import com.example.bamachat.service.MediaService
import com.example.bamachat.service.NotificationService
import com.example.bamachat.service.UserFacingAiErrorMapper
import com.example.bamachat.data.provider.chat.ActiveChatProviderResolution
import com.example.bamachat.data.provider.chat.ActiveChatProviderResolver
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelection
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelectionStore
import com.example.bamachat.data.provider.chat.ProviderChatErrorMessages
import com.example.bamachat.data.provider.chat.ProviderChatException
import com.example.bamachat.data.provider.chat.ProviderChatExecutionEngine
import com.example.bamachat.data.provider.chat.ProviderChatMessage
import com.example.bamachat.data.provider.chat.ProviderChatRequest
import com.example.bamachat.service.ImageUrlResolver
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
import com.example.bamachat.data.AndroidAiOrchestrator
import com.example.bamachat.data.AgentLoopRequestFactory
import com.example.bamachat.data.ApiClient
import com.example.bamachat.util.SecureSettingsStore
import com.example.bamachat.util.UserErrorMessage
import com.example.bamachat.data.OpenRouterMessage
import com.example.bamachat.data.OpenRouterSseTextChunkStream
import com.example.bamachat.data.OpenRouterStreamChunk
import com.example.bamachat.data.toAiChatRequestForValidation
import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatResponse
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.AiProviderId
import com.example.bamachat.shared.core.ai.AiStreamCompleted
import com.example.bamachat.shared.core.ai.AiStreamDelta
import com.example.bamachat.shared.core.ai.AiStreamError
import com.example.bamachat.shared.core.ai.AiStreamEvent
import com.example.bamachat.shared.core.ai.AiStreamFinished
import com.example.bamachat.shared.core.ai.AiStreamStarted
import com.example.bamachat.voice.RealtimeFinalizedTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import kotlin.concurrent.read
import kotlin.concurrent.write

data class ToolCallProgress(
    val toolName: String,
    val arguments: String,
    val status: ToolCallStatus = ToolCallStatus.RUNNING,
    val result: String? = null
)

data class ChatProviderRuntimeStatus(
    val summary: String = "Bisherige KI-Konfiguration",
    val customSelection: Boolean = false,
    val valid: Boolean = true,
    val warning: String? = null
)

enum class ToolCallStatus { RUNNING, DONE, ERROR }

@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    private val chatSyncCoordinator: AndroidChatSyncCoordinator,
    private val chatProviderSelectionStore: ActiveChatProviderSelectionStore,
    private val chatProviderResolver: ActiveChatProviderResolver,
    private val providerChatExecutionEngine: ProviderChatExecutionEngine,
    val mcpServerManager: McpServerManager,
    val mcpWorkflowManager: McpWorkflowManager
) : AndroidViewModel(application) {
    companion object {
        // Pagination: only display last 100 messages max (50 for context during loading)
        private const val INITIAL_VISIBLE_MESSAGE_LIMIT = 100
        private const val LOAD_MORE_STEP = 50
        private const val DEV_INITIAL_VISIBLE_MESSAGE_LIMIT = 200
        private const val DEV_LOAD_MORE_STEP = 100
        private const val DEFAULT_HISTORY_LIMIT = 10
        private const val DEV_HISTORY_LIMIT = 40
        private const val KEY_EXTENSION_STATES_JSON = "workspace_extension_states_json"
        private const val KEY_EXTENSION_QUICK_ACTION = "extension_quick_action"
        private const val KEY_IMAGE_GENERATION_MODE = "image_generation_mode"
        internal const val KEY_AGENT_TOOLS_ENABLED = "agent_tools_enabled"
        private const val IMAGE_GENERATION_MODE_DISABLED = "Deaktiviert"
        // Cache system prompts for 5 minutes to reduce API load
        private const val SYSTEM_PROMPT_CACHE_TTL_MS = 5 * 60 * 1000L

        internal fun shouldUseAgentLoop(hasTools: Boolean, agentToolsEnabled: Boolean): Boolean =
            agentToolsEnabled && hasTools

        internal fun computeWindowedMessages(all: List<ChatMessage>, limit: Int): List<ChatMessage> {
            if (all.isEmpty()) return emptyList()
            val safeLimit = limit.coerceAtLeast(1)
            return if (all.size <= safeLimit) all.toList() else all.takeLast(safeLimit)
        }

        internal fun normalizeForDedup(raw: String): String =
            ChatSendDeduplicator.normalizeForDedup(raw)

        internal fun normalizeWorkspaceName(raw: String): String =
            WorkspaceNaming.normalizeWorkspaceName(raw)

        internal fun toRealtimeChatMessage(turn: RealtimeFinalizedTurn): ChatMessage? {
            val cleanText = turn.text.trim()
            val cleanId = turn.messageId.trim()
            if (cleanText.isBlank() || cleanId.isBlank() || cleanId.length > 200) return null
            return ChatMessage(
                id = cleanId,
                text = cleanText,
                isUser = turn.isUser,
                timestamp = turn.timestamp,
                role = if (turn.isUser) "USER" else "ASSISTANT"
            )
        }

        internal suspend fun consumeAiStreamEvents(
            events: Flow<AiStreamEvent>,
            convId: String,
            assistantMsg: ChatMessage,
            webSources: List<ChatSource>,
            webFetchedAtIso: String?,
            streamingBuffer: StringBuilder,
            streamFlushInterval: Long,
            lastFlushAtProvider: () -> Long,
            updateLastFlushAt: (Long) -> Unit,
            saveMessage: suspend (String, ChatMessage, Boolean) -> Unit,
            clearRetryContext: suspend () -> Unit,
            showNotification: suspend (String) -> Unit,
            streamTelemetrySource: String? = null,
            streamTelemetryModel: String? = null,
            streamStartedAtMs: Long? = null,
            logStreamEvent: (String, Map<String, String>) -> Unit = { _, _ -> }
        ): AiStreamConsumptionResult {
            var completedText: String? = null
            var fallbackReason: String? = null
            var errorMessage: String? = null
            var eventProvider: AiProviderId? = null
            var eventModel: String? = streamTelemetryModel

            fun telemetryParams(reason: String? = null): Map<String, String> {
                val params = mutableMapOf<String, String>()
                streamTelemetrySource?.let { params["source"] = it }
                eventProvider?.name?.let { params["provider"] = it }
                eventModel?.takeIf { it.isNotBlank() }?.let { params["model"] = it }
                streamStartedAtMs?.let { params["duration_ms"] = (System.currentTimeMillis() - it).toString() }
                reason?.let { params["reason"] = it }
                return params
            }

            events.collect { event ->
                when (event) {
                    is AiStreamStarted -> {
                        eventProvider = event.provider
                        eventModel = event.model
                        streamTelemetrySource?.let {
                            logStreamEvent("stream_event_started", telemetryParams())
                        }
                    }
                    is AiStreamDelta -> {
                        eventProvider = event.provider
                        eventModel = event.model
                        streamingBuffer.append(event.text)
                        val now = System.currentTimeMillis()
                        if (now - lastFlushAtProvider() >= streamFlushInterval) {
                            updateLastFlushAt(now)
                            saveMessage(
                                convId,
                                assistantMsg.copy(text = streamingBuffer.toString()),
                                false
                            )
                        }
                    }
                    is AiStreamCompleted -> {
                        eventProvider = event.provider
                        eventModel = event.model
                        completedText = event.response.message.text
                    }
                    is AiStreamError -> {
                        eventProvider = event.provider
                        eventModel = event.model
                        fallbackReason = "provider_error"
                        errorMessage = event.message
                    }
                    is AiStreamFinished -> Unit
                }
            }

            fallbackReason?.let {
                streamTelemetrySource?.let { _ ->
                    logStreamEvent("stream_event_error", telemetryParams(reason = it))
                }
                return AiStreamConsumptionResult(
                    success = false,
                    fallbackReason = it,
                    errorMessage = errorMessage
                )
            }

            val finalText = completedText
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    streamTelemetrySource?.let {
                        logStreamEvent("stream_event_error", telemetryParams(reason = "empty_stream"))
                    }
                    return AiStreamConsumptionResult(success = false, fallbackReason = "empty_stream")
                }

            val finalized = assistantMsg.copy(
                text = finalText,
                sources = webSources,
                webFetchedAtIso = webFetchedAtIso
            )
            saveMessage(convId, finalized, true)
            clearRetryContext()
            showNotification(finalText)
            streamTelemetrySource?.let {
                logStreamEvent("stream_event_completed", telemetryParams())
            }
            return AiStreamConsumptionResult(success = true, finalText = finalText)
        }

        internal fun legacyStreamAsAiEvents(
            provider: AiProviderId,
            model: String,
            streamChatResponse: suspend (
                onChunkReceived: (String) -> Unit,
                onError: (String) -> Unit
            ) -> ApiManager.ApiResponse,
            onIntermediateError: (String) -> Unit = {},
            onTerminalError: (ApiManager.ApiResponse) -> Unit = {}
        ): Flow<AiStreamEvent> = channelFlow {
            send(AiStreamStarted(provider = provider, model = model))

            val response = try {
                streamChatResponse(
                    { chunk ->
                        if (chunk.isNotEmpty()) {
                            trySend(
                                AiStreamDelta(
                                    text = chunk,
                                    provider = provider,
                                    model = model
                                )
                            )
                        }
                    },
                    { error ->
                        onIntermediateError(error)
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val userFailure = UserFacingAiErrorMapper.terminal(provider, error.message)
                send(
                    AiStreamError(
                        message = userFailure.message,
                        exceptionClass = error::class.java.simpleName,
                        provider = provider,
                        model = model
                    )
                )
                send(AiStreamFinished(provider = provider, model = model))
                return@channelFlow
            }

            val finalProvider = response.usedProvider?.toAiProviderId() ?: provider
            if (response.success && response.content.isNotBlank()) {
                send(
                    AiStreamCompleted(
                        AiChatResponse(
                            provider = finalProvider,
                            model = model,
                            message = AiChatMessage(
                                role = AiChatRole.ASSISTANT,
                                text = response.content
                            )
                        )
                    )
                )
            } else {
                onTerminalError(response)
                val userFailure = UserFacingAiErrorMapper.terminal(finalProvider, response.error)
                send(
                    AiStreamError(
                        message = userFailure.message,
                        provider = finalProvider,
                        model = model
                    )
                )
            }

            send(AiStreamFinished(provider = finalProvider, model = model))
        }

        private fun ApiClient.Provider.toAiProviderId(): AiProviderId = when (this) {
            ApiClient.Provider.OPENROUTER -> AiProviderId.OPENROUTER
            ApiClient.Provider.GROQ -> AiProviderId.GROQ
            ApiClient.Provider.CEREBRAS -> AiProviderId.CEREBRAS
            ApiClient.Provider.TOGETHER -> AiProviderId.TOGETHER
            ApiClient.Provider.OPENCODE -> AiProviderId.OPENCODE
        }
    }

    internal data class AiStreamConsumptionResult(
        val success: Boolean,
        val finalText: String? = null,
        val fallbackReason: String? = null,
        val errorMessage: String? = null
    )

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
    private val imageUrlResolver = ImageUrlResolver()

    val personaViewModel = PersonaViewModel(application)
    val multiAgentViewModel = MultiAgentViewModel(application, apiManager, personaViewModel)
    val monetizationViewModel = MonetizationViewModel(application)

    // ===== Chat State =====
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    private val allMessagesBuffer = mutableListOf<ChatMessage>()
    private val bufferLock = ReentrantReadWriteLock()
    private val _visibleMessageLimit = MutableStateFlow(INITIAL_VISIBLE_MESSAGE_LIMIT)
    private val _hasOlderMessages = MutableStateFlow(false)
    val hasOlderMessages: StateFlow<Boolean> = _hasOlderMessages

    private val _conversations = MutableStateFlow<List<com.example.bamachat.data.local.ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<com.example.bamachat.data.local.ConversationEntity>> = _conversations

    private val _chatWorkspaceId = MutableStateFlow<String?>(null)
    val chatWorkspaceId: StateFlow<String?> = _chatWorkspaceId
    val chatWorkspaceName: StateFlow<String> = _chatWorkspaceId.map { chatWsId ->
        if (chatWsId.isNullOrBlank()) ""
        else conversationService.findWorkspaceNameById(chatWsId).orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setChatWorkspaceContext(workspaceId: String?) {
        _chatWorkspaceId.value = workspaceId
    }

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    private val _providerFallbackMessage = MutableStateFlow<String?>(null)
    val providerFallbackMessage: StateFlow<String?> = _providerFallbackMessage.asStateFlow()
    private val _chatProviderStatus = MutableStateFlow(ChatProviderRuntimeStatus())
    val chatProviderStatus: StateFlow<ChatProviderRuntimeStatus> = _chatProviderStatus.asStateFlow()
    private val _errorActionLabel = MutableStateFlow<String?>(null)
    val errorActionLabel: StateFlow<String?> = _errorActionLabel
    private val _isErrorRetryable = MutableStateFlow(false)
    val isErrorRetryable: StateFlow<Boolean> = _isErrorRetryable
    private val _lastRetryableUserMessage = MutableStateFlow<String?>(null)

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
    // P1-1: handle for the currently-running generation/streaming coroutine,
    // so the UI can request cancellation via cancelStream().
    private var activeGenerationJob: Job? = null
    private var lastAcceptedTextSend: String? = null
    private var lastAcceptedConversationId: String? = null
    private var lastAcceptedTextSendAtMs: Long = 0L
    private var pendingUserMessageForRetry: String? = null

    // System prompt cache: 5-min TTL to reduce repeated API calls
    private var systemPromptCache: String? = null
    private var systemPromptCacheExpireAt: Long = 0L

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
        ASSISTANT(ConversationPersonaMetadata.DEFAULT_PERSONA_DISPLAY_NAME, "🤖", "Du bist BamaChat, ein hilfreicher deutschsprachiger KI-Assistent. Antworte kurz und präzise."),
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
        viewModelScope.launch {
            chatProviderResolver.observeResolution().collectLatest { resolution ->
                _chatProviderStatus.value = when (resolution) {
                    ActiveChatProviderResolution.Legacy -> ChatProviderRuntimeStatus()
                    is ActiveChatProviderResolution.ResolvedCustomProvider -> ChatProviderRuntimeStatus(
                        summary = "${resolution.definition.displayName} · ${resolution.model.displayName}",
                        customSelection = true,
                        valid = true
                    )
                    is ActiveChatProviderResolution.Invalid -> ChatProviderRuntimeStatus(
                        summary = "Eigene Anbieterwahl prüfen",
                        customSelection = true,
                        valid = false,
                        warning = resolution.userMessage
                    )
                }
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
            val personaName = personaViewModel.selectedPersona.value.displayName
            val chatWsId = _chatWorkspaceId.value
            if (chatWsId != null) {
                val wsName = conversationService.activeWorkspaceName()
                val conv = conversationService.createConversation(personaName, wsName)
                switchConversation(conv.id)
            } else {
                val conv = conversationService.createNormalConversation(personaName)
                switchConversation(conv.id)
            }
        }
    }

    suspend fun openOrCreateWorkspaceConversation(workspaceId: String) {
        val wsName = conversationService.findWorkspaceNameById(workspaceId)
            ?: conversationService.activeWorkspaceName()
        val existing = conversationService.findLatestConversationForWorkspace(
            _conversations.value, wsName
        )
        if (existing != null) {
            switchConversation(existing.id)
        } else {
            val personaName = personaViewModel.selectedPersona.value.displayName
            val conv = conversationService.createConversation(personaName, wsName)
            switchConversation(conv.id)
        }
    }

    suspend fun openOrCreateNormalConversation() {
        val existing = conversationService.findLatestConversationWithoutWorkspace(
            _conversations.value
        )
        if (existing != null) {
            switchConversation(existing.id)
        } else {
            val personaName = personaViewModel.selectedPersona.value.displayName
            val conv = conversationService.createNormalConversation(personaName)
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
        bufferLock.write { allMessagesBuffer.clear() }
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repo.getMessages(id).collectLatest { items ->
                bufferLock.write {
                    allMessagesBuffer.clear()
                    allMessagesBuffer.addAll(items)
                }
                publishVisibleMessages()
                bufferLock.read { syncFeedbackForMessages(allMessagesBuffer) }
            }
        }
    }

    fun loadOlderMessages() {
        _visibleMessageLimit.value += currentLoadMoreStep()
        publishVisibleMessages()
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            conversationService.rename(id, newTitle)
            scheduleConversationMetadataSync(id)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationService.delete(id)
            scheduleConversationSoftDelete(id)
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

    private suspend fun saveMessageLocally(
        conversationId: String,
        message: ChatMessage,
        touchConversation: Boolean = true
    ) {
        repo.saveMessage(conversationId, message, touchConversation = touchConversation)
    }

    private fun scheduleMessageSync(conversationId: String, message: ChatMessage) {
        val activePersonaName = personaViewModel.selectedPersona.value.displayName
        viewModelScope.launch {
            chatSyncCoordinator.syncMessageAfterLocalSave(conversationId, message, activePersonaName)
        }
    }

    private fun scheduleConversationMetadataSync(conversationId: String) {
        val activePersonaName = personaViewModel.selectedPersona.value.displayName
        viewModelScope.launch {
            chatSyncCoordinator.syncConversationMetadataAfterLocalChange(
                conversationId,
                activePersonaName
            )
        }
    }

    private fun scheduleConversationSoftDelete(conversationId: String) {
        viewModelScope.launch {
            chatSyncCoordinator.softDeleteConversationAfterLocalDelete(conversationId)
        }
    }

    fun persistRealtimeVoiceTurn(turn: RealtimeFinalizedTurn): Boolean {
        val message = toRealtimeChatMessage(turn) ?: return false
        viewModelScope.launch {
            var conversationId = _currentConversationId.value
            if (conversationId == null) {
                val personaName = personaViewModel.selectedPersona.value.displayName
                val conversation = if (_chatWorkspaceId.value != null) {
                    conversationService.createConversation(
                        personaName,
                        conversationService.activeWorkspaceName()
                    )
                } else {
                    conversationService.createNormalConversation(personaName)
                }
                conversationId = conversation.id
                switchConversation(conversation.id)
            }
            val resolvedConversationId = conversationId ?: return@launch
            saveMessageLocally(resolvedConversationId, message)
            val current = repo.getConversation(resolvedConversationId)
            if (message.isUser && current != null && conversationService.isPlaceholderTitle(current.title)) {
                repo.renameConversation(
                    resolvedConversationId,
                    message.text.take(40).ifBlank { "Chat" }
                )
            }
            scheduleMessageSync(resolvedConversationId, message)
        }
        return true
    }

    /**
     * P1-1: Cancel the currently running generation/streaming coroutine.
     * Safe to call when nothing is running (no-op). The launched job's
     * `finally` block resets `_isLoading` / `_isStreaming`; we also reset them
     * defensively here in case cancellation happens before any finally runs.
     */
    fun cancelStream() {
        val job = activeGenerationJob
        if (job != null && job.isActive) {
            job.cancel()
        }
        activeGenerationJob = null
        _isStreaming.value = false
        _isLoading.value = false
    }

    // ===== Message Sending =====
    fun sendMessage(text: String, quickAction: ExtensionQuickAction = _selectedExtensionQuickAction.value): Boolean {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return false
        if (_isLoading.value || _isStreaming.value) return false
        if (chatProviderSelectionStore.selection.value is ActiveChatProviderSelection.Custom &&
            (!_chatProviderStatus.value.customSelection || !_chatProviderStatus.value.valid)
        ) {
            publishError(
                _chatProviderStatus.value.warning ?: "Die eigene Anbieterwahl ist noch nicht einsatzbereit.",
                retryable = false,
                actionLabel = null
            )
            return false
        }

        val convId = _currentConversationId.value
        val now = System.currentTimeMillis()
        val normalizedText = chatEngine.normalizeForDedup(trimmedText)
        if (chatEngine.isDuplicateSend(lastAcceptedTextSend, lastAcceptedConversationId, lastAcceptedTextSendAtMs,
                normalizedText, convId, now)) return false

        val emotion = chatEngine.analyzeEmotion(trimmedText)
        _emotionSignal.value = emotion
        _chatSentiment.value = emotion.sentiment

        if (mediaService.isImageQuery(trimmedText)) {
            if (chatProviderSelectionStore.selection.value is ActiveChatProviderSelection.Custom) {
                publishError(
                    ProviderChatErrorMessages.message(com.example.bamachat.data.provider.chat.ProviderChatError.UNSUPPORTED_FEATURE),
                    retryable = false,
                    actionLabel = null
                )
                return false
            }
            generateImage(trimmedText, skipUserMessage = true)
            return true
        }

        if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.TEXT_MESSAGE)) return false

        lastAcceptedTextSend = normalizedText
        lastAcceptedConversationId = convId
        lastAcceptedTextSendAtMs = now
        pendingUserMessageForRetry = trimmedText

        if (convId == null) {
            viewModelScope.launch {
                val personaName = personaViewModel.selectedPersona.value.displayName
                val wsName = conversationService.activeWorkspaceName()
                val conv = conversationService.createConversation(personaName, wsName)
                switchConversation(conv.id)
                sendMessage(trimmedText, quickAction)
            }
            return true
        }

        val userMessage = ChatMessage(id = UUID.randomUUID().toString(), text = trimmedText, isUser = true, timestamp = System.currentTimeMillis())
        _isLoading.value = true
        activeGenerationJob = viewModelScope.launch {
            saveMessageLocally(convId, userMessage)

            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && conversationService.isPlaceholderTitle(current.title)) {
                repo.renameConversation(convId, trimmedText.take(40).ifBlank { "Chat" })
            }
            scheduleMessageSync(convId, userMessage)

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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        if (chatProviderSelectionStore.selection.value is ActiveChatProviderSelection.Custom) {
            publishError(
                "Bilder werden mit eigenen Anbietern in dieser Phase noch nicht unterstützt. Es wurde kein anderer Anbieter verwendet.",
                retryable = false,
                actionLabel = null
            )
            return false
        }
        if (text.isBlank() && imageUri == Uri.EMPTY) return false
        if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.IMAGE_ANALYSIS)) return false

        val convId = _currentConversationId.value ?: run {
            viewModelScope.launch {
                val personaName = personaViewModel.selectedPersona.value.displayName
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
        activeGenerationJob = viewModelScope.launch {
            saveMessageLocally(convId, userMessage)
            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && conversationService.isPlaceholderTitle(current.title)) {
                repo.renameConversation(convId, text.take(40).ifBlank { "Bild-Chat" })
            }
            scheduleMessageSync(convId, userMessage)
            try {
                val systemPrompt = getSystemPromptWithCache(personaViewModel.selectedPersona.value)
                val result = mediaService.analyzeImage(systemPrompt, text, imageUri,
                    enableOcr = prefs.getBoolean("local_ocr_enabled", true))
                if (result.success) {
                    val assistantMessage = ChatMessage(id = UUID.randomUUID().toString(),
                        text = result.content, isUser = false, timestamp = System.currentTimeMillis())
                    saveMessageLocally(convId, assistantMessage)
                    scheduleMessageSync(convId, assistantMessage)
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
        if (prompt.isBlank()) {
            _errorMessage.value = "Beschreibe zuerst, welches Bild du erstellen möchtest."
            return
        }
        if (prefs.getString(KEY_IMAGE_GENERATION_MODE, "Externer Bilddienst") == IMAGE_GENERATION_MODE_DISABLED) {
            _errorMessage.value = "Bildgenerierung ist in den Einstellungen deaktiviert. Aktiviere unter KI & Modelle den externen Bilddienst."
            return
        }

        activeGenerationJob = viewModelScope.launch {
            var convId = _currentConversationId.value
            if (convId == null) {
                val personaName = personaViewModel.selectedPersona.value.displayName
                val wsName = conversationService.activeWorkspaceName()
                val conv = conversationService.createConversation(personaName, wsName)
                switchConversation(conv.id)
                convId = conv.id
            }
            if (!skipUserMessage) {
                val userMessage = ChatMessage(id = UUID.randomUUID().toString(),
                    text = prompt, isUser = true, timestamp = System.currentTimeMillis())
                saveMessageLocally(convId, userMessage)
                scheduleMessageSync(convId, userMessage)
            }
            val current = _conversations.value.firstOrNull { it.id == convId }
            if (current != null && conversationService.isPlaceholderTitle(current.title)) {
                repo.renameConversation(convId, "Bild: ${prompt.take(30)}")
            }
            _isLoading.value = true
            try {
                val genReq = mediaService.buildImageGenerationRequest(prompt)
                val imageUrl = imageUrlResolver.resolveFirstWorkingUrl(genReq.candidateUrls)
                if (imageUrl == null) {
                    // P0-A fix: Keine kaputte Bildkarte speichern, wenn der externe Bilddienst 402/403/Fehler liefert.
                    _errorMessage.value = "Bildgenerierung ist aktuell nicht erreichbar oder erfordert Auth/Zahlung beim Bilddienst. Bitte später erneut versuchen oder Bild-KI in den Einstellungen konfigurieren."
                    return@launch
                }
                if (!monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.IMAGE_GENERATION)) return@launch
                val assistantMessage = ChatMessage(id = UUID.randomUUID().toString(),
                    text = genReq.displayPrompt, isUser = false, timestamp = System.currentTimeMillis(),
                    imageUrl = imageUrl)
                saveMessageLocally(convId, assistantMessage)
                scheduleMessageSync(convId, assistantMessage)
                notificationService.show("BamaChat Bild", "Bild generiert: $prompt", prefs.getBoolean("notifications_enabled", true))
            } catch (e: Exception) { handleError(e) }
            finally { _isLoading.value = false }
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
        if (!hasActiveInternetConnection()) {
            val networkError = com.example.bamachat.util.ErrorRecoveryManager
                .mapErrorToUserMessage(java.io.IOException("No network"))
            publishError(
                message = ChatErrorRecoveryPolicy.buildErrorDisplayText(networkError),
                retryable = networkError.isRetryable,
                actionLabel = networkError.actionLabel
            )
            return
        }

        val startedAt = System.currentTimeMillis()
        val systemPrompt = getSystemPromptWithCache(personaViewModel.selectedPersona.value)
        val mergedRuntimeContext = listOfNotNull(runtimeContext, extensionRuntime?.promptContext)
            .filter { it.isNotBlank() }.joinToString("\n\n").takeIf { it.isNotBlank() }
        val selection = chatProviderSelectionStore.selection.value
        if (selection is ActiveChatProviderSelection.Custom) {
            val toolsRequested = extensionRuntime?.forceWebResearch == true ||
                (prefs.getBoolean(KEY_AGENT_TOOLS_ENABLED, false) &&
                    (mcpServerManager.getToolDefinitionsOpenAI().isNotEmpty() || mcpWorkflowManager.getOpenAIToolDefinitions().isNotEmpty()))
            if (toolsRequested) {
                publishError(
                    ProviderChatErrorMessages.message(com.example.bamachat.data.provider.chat.ProviderChatError.UNSUPPORTED_FEATURE),
                    retryable = false,
                    actionLabel = null
                )
                return
            }
            runCustomProviderChat(convId, text, systemPrompt, mergedRuntimeContext, selection)
            return
        }
        val forceWebResearch = extensionRuntime?.forceWebResearch == true
        val appliedExtensions = extensionRuntime?.appliedExtensionNames.orEmpty()

        val webContext = if (forceWebResearch || chatEngine.chargesForWebResearch()) {
            val canResearch = forceWebResearch || monetizationViewModel.consumeQuota(MonetizationViewModel.QuotaType.WEB_RESEARCH)
            if (canResearch) chatEngine.resolveLiveWebContext(text, forceByExtension = forceWebResearch) else null
        } else null

        val messages = chatEngine.buildOpenRouterHistory(
            _messages.value, latestUserText = text,
            liveWebContext = webContext?.promptContext,
            runtimeContext = mergedRuntimeContext,
            historyLimit = if (isDeveloperUnlimitedTrainingEnabled()) DEV_HISTORY_LIMIT else DEFAULT_HISTORY_LIMIT
        )
        val pilotContent = runExperimentalNonStreamingChat(systemPrompt, messages)
        if (!pilotContent.isNullOrBlank()) {
            saveExperimentalNonStreamingResponse(convId, pilotContent, webContext)
            return
        }

        val toolDefs = mcpServerManager.getToolDefinitionsOpenAI() + mcpWorkflowManager.getOpenAIToolDefinitions()
        val hasTools = toolDefs.isNotEmpty()
        val agentToolsEnabled = prefs.getBoolean(KEY_AGENT_TOOLS_ENABLED, false)

        if (shouldUseAgentLoop(hasTools = hasTools, agentToolsEnabled = agentToolsEnabled)) {
            AppTelemetry.logEvent(
                "chat_route_agent_loop",
                mapOf(
                    "has_tools" to hasTools.toString(),
                    "agent_tools_enabled" to agentToolsEnabled.toString()
                )
            )
            runAgentLoop(convId, text, systemPrompt, startedAt, webContext, toolDefs)
        } else {
            AppTelemetry.logEvent(
                "chat_route_streaming",
                mapOf(
                    "has_tools" to hasTools.toString(),
                    "agent_tools_enabled" to agentToolsEnabled.toString()
                )
            )
            runStreamingChat(convId, systemPrompt, messages, webContext, startedAt)
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
        saveMessageLocally(convId, assistantMsg, touchConversation = false)

        var finalContent: String? = null
        var iteration = 0
        val maxIter = 5

        try {
            while (iteration < maxIter) {
                iteration++
                val request = AgentLoopRequestFactory.buildAgentChatRequest(
                    model = selectedModel.value,
                    messages = messages,
                    toolDefs = toolDefs
                )
                runCatching {
                    messages.toAiChatRequestForValidation(
                        provider = AiProviderId.OPENROUTER,
                        model = selectedModel.value,
                        maxTokens = 2048,
                        stream = false
                    )
                }.onSuccess { dryRunRequest ->
                    AppTelemetry.logEvent(
                        "ai_request_builder_dry_run",
                        mapOf(
                            "provider" to dryRunRequest.provider.name,
                            "message_count" to dryRunRequest.messages.size.toString()
                        )
                    )
                }.onFailure { error ->
                    AppTelemetry.logError("ai_request_builder_dry_run_failed", error)
                }

                val response = apiManager.oneShotChatCompletion(request, fullSystemPrompt)
                if (response == null) {
                    publishError(
                        message = "Agent: Keine Antwort vom Provider",
                        retryable = true,
                        actionLabel = "Erneut versuchen"
                    )
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

                    messages.add(
                        AgentLoopRequestFactory.createToolResultMessage(
                            toolCallId = toolCall.id,
                            content = resultText
                        )
                    )
                }
            }

            if (finalContent == null) finalContent = "Agent: Maximale Iterationen erreicht."

            val trimmedContent = finalContent.trim()
            if (trimmedContent.isBlank()) {
                publishError(
                    message = "Agent: Leere Antwort vom Provider",
                    retryable = true,
                    actionLabel = "Erneut versuchen"
                )
                repo.deleteMessage(assistantMsg.id)
                return
            }

            val finalAssistantMessage = assistantMsg.copy(
                text = trimmedContent,
                sources = webContext?.sources.orEmpty(),
                webFetchedAtIso = webContext?.fetchedAtIso
            )
            saveMessageLocally(convId, finalAssistantMessage, touchConversation = true)
            scheduleMessageSync(convId, finalAssistantMessage)
            clearRetryContext()
            notificationService.show("BamaChat", trimmedContent, prefs.getBoolean("notifications_enabled", true))
        } catch (e: Exception) {
            handleError(e)
            repo.deleteMessage(assistantMsg.id)
        } finally {
            _activeToolCalls.value = emptyList()
            _isStreaming.value = false
        }
    }

    private suspend fun runStreamingChat(
        convId: String, systemPrompt: String, messages: List<OpenRouterMessage>,
        webContext: ChatEngine.LiveWebContext?, startedAt: Long
    ) {
        _providerFallbackMessage.value = null
        _isStreaming.value = true
        val assistantMsg = ChatMessage(id = UUID.randomUUID().toString(), text = "",
            isUser = false, timestamp = System.currentTimeMillis())
        val streamingBuffer = StringBuilder()
        val streamFlushInterval = 250L
        var lastFlushAt = System.currentTimeMillis()
        if (runExperimentalStreamingChat(
                convId = convId,
                systemPrompt = systemPrompt,
                messages = messages,
                webContext = webContext,
                startedAt = startedAt,
                assistantMsg = assistantMsg,
                streamingBuffer = streamingBuffer,
                streamFlushInterval = streamFlushInterval,
                lastFlushAtProvider = { lastFlushAt },
                updateLastFlushAt = { lastFlushAt = it }
            )
        ) {
            return
        }

        try {
            AppTelemetry.logEvent(
                "legacy_event_stream_started",
                mapOf("model" to selectedModel.value)
            )
            var terminalLegacyError: ApiManager.ApiResponse? = null
            val result = consumeAiStreamEvents(
                events = legacyStreamAsAiEvents(
                    provider = AiProviderId.OPENROUTER,
                    model = selectedModel.value,
                    streamChatResponse = { onChunkReceived, onError ->
                        apiManager.streamChatResponse(
                            systemPrompt = systemPrompt,
                            userMessages = messages,
                            onChunkReceived = onChunkReceived,
                            onError = onError
                        )
                    },
                    onIntermediateError = { safeStatus ->
                        _providerFallbackMessage.value = safeStatus
                        AppTelemetry.logEvent(
                            "chat_stream_fallback",
                            mapOf(
                                "category" to "provider_fallback",
                                "duration_ms" to (System.currentTimeMillis() - startedAt).toString()
                            )
                        )
                    },
                    onTerminalError = { response ->
                        terminalLegacyError = response
                    }
                ),
                convId = convId,
                assistantMsg = assistantMsg,
                webSources = webContext?.sources.orEmpty(),
                webFetchedAtIso = webContext?.fetchedAtIso,
                streamingBuffer = streamingBuffer,
                streamFlushInterval = streamFlushInterval,
                lastFlushAtProvider = { lastFlushAt },
                updateLastFlushAt = { lastFlushAt = it },
                saveMessage = { targetConvId, message, touchConversation ->
                    saveMessageLocally(targetConvId, message, touchConversation = touchConversation)
                    if (touchConversation) {
                        scheduleMessageSync(targetConvId, message)
                    }
                },
                clearRetryContext = { clearRetryContext() },
                showNotification = { finalText ->
                    notificationService.show(
                        "BamaChat",
                        finalText,
                        prefs.getBoolean("notifications_enabled", true)
                    )
                },
                streamTelemetrySource = "legacy",
                streamTelemetryModel = selectedModel.value,
                streamStartedAtMs = startedAt,
                logStreamEvent = AppTelemetry::logEvent
            )
            _providerFallbackMessage.value = null

            if (result.success) {
                AppTelemetry.logEvent(
                    "legacy_event_stream_completed",
                    mapOf(
                        "model" to selectedModel.value,
                        "duration_ms" to (System.currentTimeMillis() - startedAt).toString()
                    )
                )
            } else {
                val terminalError = terminalLegacyError
                if (terminalError != null || !result.errorMessage.isNullOrBlank()) {
                    val userFailure = UserFacingAiErrorMapper.terminal(
                        AiProviderId.OPENROUTER,
                        terminalError?.error
                    )
                    publishError(
                        message = result.errorMessage ?: userFailure.message,
                        retryable = terminalError?.retryable ?: true,
                        actionLabel = if (terminalError?.retryable != false) "Erneut versuchen" else null
                    )
                }
                AppTelemetry.logEvent(
                    "legacy_event_stream_error",
                    mapOf(
                        "model" to selectedModel.value,
                        "reason" to (result.fallbackReason ?: "unknown"),
                        "duration_ms" to (System.currentTimeMillis() - startedAt).toString()
                    )
                )
                repo.deleteMessage(assistantMsg.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleError(e)
            repo.deleteMessage(assistantMsg.id)
        } finally {
            _providerFallbackMessage.value = null
        }
    }

    private suspend fun runExperimentalStreamingChat(
        convId: String,
        systemPrompt: String,
        messages: List<OpenRouterMessage>,
        webContext: ChatEngine.LiveWebContext?,
        startedAt: Long,
        assistantMsg: ChatMessage,
        streamingBuffer: StringBuilder,
        streamFlushInterval: Long,
        lastFlushAtProvider: () -> Long,
        updateLastFlushAt: (Long) -> Unit
    ): Boolean {
        val sharedAiExperimental = prefs.getBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_EXPERIMENTAL, false)
        val developerModeEnabled = prefs.getBoolean("developer_mode_enabled", false)
        val streamingPilotEnabled = prefs.getBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_STREAMING_PILOT, false)
        val pilotEnabled = AndroidAiOrchestrator.isStreamingPilotEnabled(
            sharedAiExperimental = sharedAiExperimental,
            developerModeEnabled = developerModeEnabled,
            sharedAiStreamingPilot = streamingPilotEnabled
        )
        if (!pilotEnabled) {
            AppTelemetry.logEvent(
                "stream_pilot_legacy_selected",
                mapOf("reason" to "flag_off", "model" to selectedModel.value)
            )
            return false
        }

        val apiKey = SecureSettingsStore
            .getString(appContext, prefs, "openrouter_api_key")
            .takeIf { it.length > 10 }
        if (apiKey == null) {
            AppTelemetry.logEvent(
                "stream_pilot_legacy_selected",
                mapOf("reason" to "missing_api_key", "model" to selectedModel.value)
            )
            return false
        }

        AppTelemetry.logEvent(
            "stream_pilot_path_selected",
            mapOf("model" to selectedModel.value)
        )

        return try {
            val request = (listOf(OpenRouterMessage("system", systemPrompt)) + messages)
                .toAiChatRequestForValidation(
                    provider = AiProviderId.OPENROUTER,
                    model = selectedModel.value,
                    maxTokens = 4096,
                    temperature = 0.7,
                    stream = true
                )
            val service = ApiClient.createOpenAICompatibleService(ApiClient.Provider.OPENROUTER, apiKey)
            val textChunkStream = OpenRouterSseTextChunkStream(service)
            val orchestrator = AndroidAiOrchestrator(
                isExperimentalEnabled = { false },
                chatCompletion = service::chatCompletion,
                isStreamingExperimentalEnabled = { true },
                streamTextChunks = textChunkStream::streamTextChunks,
                legacyStreamEvents = {
                    flow {
                        throw StreamingPilotLegacyFallbackException("orchestrator_fallback")
                    }
                }
            )

            val result = consumeAiStreamEvents(
                events = orchestrator.streamEvents(request),
                convId = convId,
                assistantMsg = assistantMsg,
                webSources = webContext?.sources.orEmpty(),
                webFetchedAtIso = webContext?.fetchedAtIso,
                streamingBuffer = streamingBuffer,
                streamFlushInterval = streamFlushInterval,
                lastFlushAtProvider = lastFlushAtProvider,
                updateLastFlushAt = updateLastFlushAt,
                saveMessage = { targetConvId, message, touchConversation ->
                    saveMessageLocally(targetConvId, message, touchConversation = touchConversation)
                    if (touchConversation) {
                        scheduleMessageSync(targetConvId, message)
                    }
                },
                clearRetryContext = { clearRetryContext() },
                showNotification = { finalText ->
                    notificationService.show(
                        "BamaChat",
                        finalText,
                        prefs.getBoolean("notifications_enabled", true)
                    )
                },
                streamTelemetrySource = "pilot",
                streamTelemetryModel = selectedModel.value,
                streamStartedAtMs = startedAt,
                logStreamEvent = AppTelemetry::logEvent
            )
            if (!result.success) {
                throw StreamingPilotLegacyFallbackException(result.fallbackReason ?: "empty_stream")
            }
            AppTelemetry.logEvent(
                "stream_pilot_completed",
                mapOf(
                    "model" to selectedModel.value,
                    "duration_ms" to (System.currentTimeMillis() - startedAt).toString()
                )
            )
            true
        } catch (error: CancellationException) {
            throw error
        } catch (fallback: StreamingPilotLegacyFallbackException) {
            AppTelemetry.logEvent(
                "stream_pilot_legacy_selected",
                mapOf("reason" to fallback.reason, "model" to selectedModel.value)
            )
            false
        } catch (error: Exception) {
            AppTelemetry.logError("android_ai_orchestrator_stream_pilot_integration_failed", error)
            AppTelemetry.logEvent(
                "stream_pilot_legacy_selected",
                mapOf(
                    "reason" to "exception",
                    "model" to selectedModel.value,
                    "exception" to error::class.java.simpleName
                )
            )
            false
        }
    }

    private class StreamingPilotLegacyFallbackException(
        val reason: String
    ) : RuntimeException(reason)
    private suspend fun runExperimentalNonStreamingChat(
        systemPrompt: String,
        messages: List<OpenRouterMessage>
    ): String? {
        val sharedAiExperimental = prefs.getBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_EXPERIMENTAL, false)
        val developerModeEnabled = prefs.getBoolean("developer_mode_enabled", false)
        val pilotEnabled = AndroidAiOrchestrator.isSharedAiPilotEnabled(
            sharedAiExperimental = sharedAiExperimental,
            developerModeEnabled = developerModeEnabled
        )
        if (!pilotEnabled) return null

        val apiKey = SecureSettingsStore
            .getString(appContext, prefs, "openrouter_api_key")
            .takeIf { it.length > 10 }
            ?: return null

        return runCatching {
            val request = (listOf(OpenRouterMessage("system", systemPrompt)) + messages)
                .toAiChatRequestForValidation(
                    provider = AiProviderId.OPENROUTER,
                    model = selectedModel.value,
                    maxTokens = 4096,
                    temperature = 0.7,
                    stream = false
                )
            val service = ApiClient.createOpenAICompatibleService(ApiClient.Provider.OPENROUTER, apiKey)
            val orchestrator = AndroidAiOrchestrator(
                isExperimentalEnabled = { pilotEnabled },
                chatCompletion = service::chatCompletion
            )
            orchestrator.chatOrNull(request)?.message?.text?.trim()?.takeIf { it.isNotBlank() }
        }.onFailure { error ->
            AppTelemetry.logError("android_ai_orchestrator_pilot_failed", error)
        }.getOrNull()
    }

    private suspend fun saveExperimentalNonStreamingResponse(
        convId: String,
        content: String,
        webContext: ChatEngine.LiveWebContext?
    ) {
        val assistantMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = content,
            isUser = false,
            timestamp = System.currentTimeMillis(),
            sources = webContext?.sources.orEmpty(),
            webFetchedAtIso = webContext?.fetchedAtIso
        )
        saveMessageLocally(convId, assistantMsg, touchConversation = true)
        scheduleMessageSync(convId, assistantMsg)
        clearRetryContext()
        notificationService.show("BamaChat", content, prefs.getBoolean("notifications_enabled", true))
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
    fun dismissError() {
        _errorMessage.value = null
        _errorActionLabel.value = null
        _isErrorRetryable.value = false
    }

    private suspend fun runCustomProviderChat(
        convId: String,
        latestUserText: String,
        systemPrompt: String,
        runtimeContext: String?,
        selection: ActiveChatProviderSelection.Custom
    ) {
        val resolution = chatProviderResolver.resolve(selection)
        if (resolution is ActiveChatProviderResolution.Invalid) {
            publishError(resolution.userMessage, retryable = false, actionLabel = null)
            return
        }
        if (resolution !is ActiveChatProviderResolution.ResolvedCustomProvider) return
        val messages = buildList {
            add(ProviderChatMessage("system", listOfNotNull(systemPrompt, runtimeContext).joinToString("\n\n")))
            _messages.value.takeLast(DEFAULT_HISTORY_LIMIT).forEach { message ->
                val clean = message.text.trim()
                if (clean.isNotEmpty()) add(ProviderChatMessage(if (message.isUser) "user" else "assistant", clean))
            }
            if (lastOrNull()?.role != "user" || lastOrNull()?.content != latestUserText) {
                add(ProviderChatMessage("user", latestUserText))
            }
        }
        val assistantId = UUID.randomUUID().toString()
        var assistantCreated = false
        val buffer = StringBuilder()
        var lastFlushAt = 0L
        _isStreaming.value = true
        try {
            val result = providerChatExecutionEngine.execute(
                ProviderChatRequest(selection, messages)
            ) { chunk ->
                if (chunk.text.isEmpty()) return@execute
                buffer.append(chunk.text)
                val now = System.currentTimeMillis()
                if (!assistantCreated || now - lastFlushAt >= 250L) {
                    saveMessageLocally(
                        convId,
                        ChatMessage(assistantId, buffer.toString(), false, System.currentTimeMillis()),
                        touchConversation = false
                    )
                    assistantCreated = true
                    lastFlushAt = now
                }
            }
            val finalText = result.text.trim()
            if (finalText.isEmpty()) throw ProviderChatException(
                com.example.bamachat.data.provider.chat.ProviderChatError.EMPTY_RESPONSE,
                message = "Custom provider returned empty response"
            )
            val finalMessage = ChatMessage(assistantId, finalText, false, System.currentTimeMillis())
            saveMessageLocally(convId, finalMessage, touchConversation = true)
            scheduleMessageSync(convId, finalMessage)
            clearRetryContext()
            notificationService.show("BamaChat", finalText, prefs.getBoolean("notifications_enabled", true))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (assistantCreated) repo.deleteMessage(assistantId)
            throw cancelled
        } catch (error: ProviderChatException) {
            if (assistantCreated) repo.deleteMessage(assistantId)
            publishError(ProviderChatErrorMessages.message(error.error), retryable = true, actionLabel = "Erneut versuchen")
        } finally {
            _isStreaming.value = false
        }
    }

    fun dismissProviderFallbackStatus() {
        _providerFallbackMessage.value = null
    }

    fun retryLastFailedMessage(): Boolean {
        val retryText = _lastRetryableUserMessage.value
            ?.takeIf { ChatErrorRecoveryPolicy.isValidRetryCandidate(it) }
            ?: return false
        lastAcceptedTextSend = null
        lastAcceptedConversationId = null
        lastAcceptedTextSendAtMs = 0L
        _errorMessage.value = null
        _errorActionLabel.value = null
        _isErrorRetryable.value = false
        return sendMessage(retryText)
    }

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

    // ===== Private/Utilities =====
    private fun getSystemPromptWithCache(persona: Persona): String {
        val now = System.currentTimeMillis()
        val cached = systemPromptCache
        if (cached != null && now < systemPromptCacheExpireAt) {
            return cached
        }
        val prompt = personaViewModel.getSystemPromptCached(persona)
        systemPromptCache = prompt
        systemPromptCacheExpireAt = now + SYSTEM_PROMPT_CACHE_TTL_MS
        return prompt
    }

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
        val userErrorMessage = com.example.bamachat.util.ErrorRecoveryManager.mapErrorToUserMessage(e)
        publishError(
            message = ChatErrorRecoveryPolicy.buildErrorDisplayText(userErrorMessage),
            retryable = userErrorMessage.isRetryable,
            actionLabel = userErrorMessage.actionLabel
        )
    }

    private fun publishError(message: String, retryable: Boolean, actionLabel: String?) {
        val candidate = pendingUserMessageForRetry?.takeIf { it.isNotBlank() }
        val canRetry = ChatErrorRecoveryPolicy.shouldEnableRetry(retryable, candidate)
        if (canRetry) {
            _lastRetryableUserMessage.value = candidate
            _isErrorRetryable.value = true
            _errorActionLabel.value = actionLabel?.takeIf { it.isNotBlank() } ?: "Erneut versuchen"
        } else {
            _isErrorRetryable.value = false
            _errorActionLabel.value = null
        }
        _errorMessage.value = message
    }

    private fun clearRetryContext() {
        pendingUserMessageForRetry = null
        _lastRetryableUserMessage.value = null
        _isErrorRetryable.value = false
        _errorActionLabel.value = null
    }

    private fun hasActiveInternetConnection(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return hasInternetCapability && isValidated
    }

    private fun publishVisibleMessages() {
        val all = bufferLock.read { allMessagesBuffer.toList() }
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

    // ===== Persona Character & Autonomy Profile =====
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
        systemPromptCache = null
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
        systemPromptCache = null
    }

    fun resetPromptForPersona(persona: Persona) { personaViewModel.resetPromptForPersona(persona); systemPromptCache = null }
    fun getPersonaProfile(persona: Persona): PersonaCharacterProfile = getPersonaCharacterProfile(persona)

    data class PersonaCharacterProfile(val empathy: Int = 50, val creativity: Int = 50, val directness: Int = 50)
    data class AutonomyProfile(val coreBelief: String = "", val instinct: String = "", val signatureOpinionStyle: String = "", val selfCorrectionStrictness: Int = 50)

    override fun onCleared() {
        messagesJob?.cancel()
        activeGenerationJob?.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        imageUrlResolver.shutdown()
        bufferLock.write { allMessagesBuffer.clear() }
        _messageFeedback.value = emptyMap()
        systemPromptCache = null
        super.onCleared()
    }
}
