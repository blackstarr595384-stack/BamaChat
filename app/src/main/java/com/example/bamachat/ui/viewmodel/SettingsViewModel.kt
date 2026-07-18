package com.example.bamachat.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.ApiClient
import com.example.bamachat.data.cloud.AndroidChatSyncCoordinator
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.theme.AppDesignPreset
import com.example.bamachat.util.AgentPresetLibrary
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.CloudVoiceManager
import com.example.bamachat.util.LegalPolicy
import com.example.bamachat.util.LocalDataSanitizer
import com.example.bamachat.util.MonetizationConfig
import com.example.bamachat.util.PhotoAiCloudConfigResolver
import com.example.bamachat.util.PlayBillingManager
import com.example.bamachat.util.ProjectWorkspace
import com.example.bamachat.util.ProjectWorkspaceStore
import com.example.bamachat.util.SecureSettingsStore
import com.example.bamachat.voice.VoiceInputProvider
import com.example.bamachat.voice.VoiceMode
import com.example.bamachat.voice.VoiceOutputProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.TimeUnit

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val chatSyncCoordinator: AndroidChatSyncCoordinator
) : AndroidViewModel(application) {
    companion object {
        private const val KEY_CLOUD_PERSONA_LAST_SYNC_AT = "cloud_persona_last_sync_at"
        private const val KEY_CLOUD_PERSONA_LAST_SYNC_STATUS = "cloud_persona_last_sync_status"
        private const val KEY_CREDITS_BALANCE = "credits_balance"
        private const val KEY_PROJECT_WORKSPACES_JSON = "project_workspaces_json"
        private const val KEY_ACTIVE_WORKSPACE_ID = "active_workspace_id"
        private const val KEY_ACTIVE_WORKSPACE_NAME = "active_workspace_name"
        private const val KEY_TTS_VOICE_STYLE = "tts_voice_style"
        private const val KEY_SHOW_LIVE_SOURCES = "show_live_sources"
        private const val KEY_SIMPLE_MODE_ENABLED = "simple_mode_enabled"
        private const val KEY_OPENCODE_ENDPOINT = "opencode_endpoint"
        private const val KEY_OPENCODE_MODEL = "opencode_model"
        private const val KEY_OPENCODE_API_KEY = "opencode_api_key"
        private const val KEY_CLOUD_VOICE_PROVIDER = "cloud_voice_provider"
        private const val KEY_PIPER_ENDPOINT = "piper_endpoint"
        private const val KEY_PIPER_VOICE_NAME = "piper_voice_name"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val KEY_VOICE_INPUT_PROVIDER = "voice_input_provider"
        private const val KEY_VOICE_OUTPUT_PROVIDER = "voice_output_provider"
        private const val KEY_VOICE_INTERRUPTION_ENABLED = "voice_interruption_enabled"
        private const val KEY_VOICE_PROVIDER_FALLBACK_ENABLED = "voice_provider_fallback_enabled"
        private const val KEY_VOICE_SILENCE_TIMEOUT_MS = "voice_silence_timeout_ms"
        private const val LEGACY_OPENCODE_ENDPOINT = "https://api.opencode.ai/v1/"
        private const val LEGACY_OPENCODE_MODEL = "openai/gpt-4.1-mini"
        private const val DEFAULT_OPENCODE_ENDPOINT = "https://opencode.ai/zen/v1/"
        private const val KEY_PHOTO_AI_CLOUD_ENDPOINT = "photo_ai_cloud_endpoint"
        private const val KEY_PHOTO_AI_CLOUD_API_TOKEN = "photo_ai_cloud_api_token"
        private const val KEY_IMAGE_GENERATION_MODE = "image_generation_mode"
        const val IMAGE_GENERATION_MODE_DISABLED = "Deaktiviert"
        const val IMAGE_GENERATION_MODE_EXTERNAL = "Externer Bilddienst"
        val IMAGE_GENERATION_MODE_OPTIONS = listOf(
            IMAGE_GENERATION_MODE_DISABLED,
            IMAGE_GENERATION_MODE_EXTERNAL
        )
        private const val DEFAULT_LIVE_WEB_ENDPOINT = "https://websearch-xxf7qxk3wq-ew.a.run.app"
        private const val DEFAULT_PHOTO_AI_CLOUD_ENDPOINT =
            "https://europe-west1-bamachat-d07fb.cloudfunctions.net/photoEdit"
        // WARN: Setze photo_ai_cloud_api_token in den App-Einstellungen.
        // Kein Hardcoded-Token im Source Code! Bei Bedarf via BuildConfig konfigurieren.
        private const val DEFAULT_PHOTO_AI_CLOUD_API_TOKEN = ""
        private const val DEFAULT_LIVE_WEB_ALLOWED_DOMAINS =
            "wikipedia.org,reuters.com,tagesschau.de,bundesregierung.de,heise.de,github.com,dwd.de,wetteronline.de,wetter.com,open-meteo.com"
        private const val NATURAL_TTS_SPEED = 0.94f
        private const val NATURAL_TTS_PITCH = 1.02f
        private const val CLEAR_TTS_SPEED = 1.0f
        private const val CLEAR_TTS_PITCH = 0.96f
        private const val DEFAULT_OPENCODE_MODEL = ApiClient.OPENCODE_DEFAULT_MODEL
        internal fun resolveDeveloperModePreference(storedValue: Boolean?): Boolean {
            return storedValue ?: false
        }
        internal fun resolveAutoSendVoicePreference(storedValue: Boolean?): Boolean {
            return storedValue ?: false
        }
        const val TTS_STYLE_NATURAL = "natural"
        const val TTS_STYLE_CLEAR = "clear"
        val DISPLAY_PRESET_OPTIONS = DisplaySettingsPresets.options
    }

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val appContext = application.applicationContext
    private val chatDao = ChatDatabase.getDatabase(application).chatDao()
    private val dataSanitizer = LocalDataSanitizer(application.applicationContext)
    private val billingManager = PlayBillingManager(
        context = application.applicationContext,
        onSubscriptionTierChanged = { tier ->
            val isPremium = tier != MonetizationConfig.PlanTier.FREE
            _subscriptionTier.value = tier.key
            _isPremiumActive.value = isPremium
            prefs.edit()
                .putString("subscription_tier", tier.key)
                .putBoolean("premium_active", isPremium)
                .apply()
        },
        onCreditsGranted = { amount ->
            if (amount > 0) {
                val current = prefs.getInt(KEY_CREDITS_BALANCE, 0)
                val updated = (current + amount).coerceAtLeast(0)
                _creditsBalance.value = updated
                prefs.edit().putInt(KEY_CREDITS_BALANCE, updated).apply()
            }
        },
        onBillingReadyChanged = { ready ->
            _billingReady.value = ready
        }
    )

    private val _onboardingCompleted = MutableStateFlow(
        prefs.getBoolean("onboarding_completed", false)
    )
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _legalAcknowledgedVersion = MutableStateFlow(
        prefs.getInt(LegalPolicy.KEY_ACK_VERSION, 0)
    )
    val legalAcknowledgedVersion: StateFlow<Int> = _legalAcknowledgedVersion.asStateFlow()

    private val _legalAcknowledgedTimestamp = MutableStateFlow(
        prefs.getLong(LegalPolicy.KEY_ACK_TIMESTAMP, 0L)
    )
    val legalAcknowledgedTimestamp: StateFlow<Long> = _legalAcknowledgedTimestamp.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean("biometric_enabled", false))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _primaryColorInt = MutableStateFlow(prefs.getInt("primary_color", 0xFF6A11CB.toInt()))
    val primaryColorInt: StateFlow<Int> = _primaryColorInt.asStateFlow()

    private val _fontSize = MutableStateFlow(prefs.getFloat("font_size", 15f))
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _multiProviderEnabled = MutableStateFlow(prefs.getBoolean("multi_provider", true))
    val multiProviderEnabled: StateFlow<Boolean> = _multiProviderEnabled.asStateFlow()

    private val _aiProvider = MutableStateFlow(
        prefs.getString("ai_provider", if (secureString(KEY_OPENCODE_API_KEY).isNotBlank()) "OpenCode" else "OpenRouter")
            ?: if (secureString(KEY_OPENCODE_API_KEY).isNotBlank()) "OpenCode" else "OpenRouter"
    )
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _autoSendVoice = MutableStateFlow(
        resolveAutoSendVoicePreference(
            prefs.takeIf { it.contains("auto_send_voice") }?.getBoolean("auto_send_voice", false)
        )
    )
    val autoSendVoice: StateFlow<Boolean> = _autoSendVoice.asStateFlow()

    private val _voiceMode = MutableStateFlow(
        VoiceMode.fromStorage(prefs.getString(KEY_VOICE_MODE, VoiceMode.UNIVERSAL.storageValue))
    )
    val voiceMode: StateFlow<VoiceMode> = _voiceMode.asStateFlow()

    private val _voiceInputProvider = MutableStateFlow(
        VoiceInputProvider.fromStorage(
            prefs.getString(KEY_VOICE_INPUT_PROVIDER, VoiceInputProvider.AUTOMATIC.storageValue)
        )
    )
    val voiceInputProvider: StateFlow<VoiceInputProvider> = _voiceInputProvider.asStateFlow()

    private val _voiceOutputProvider = MutableStateFlow(
        prefs.getString(KEY_VOICE_OUTPUT_PROVIDER, null)
            ?.let(VoiceOutputProvider::fromStorage)
            ?: if (prefs.getBoolean("cloud_voice_enabled", false)) {
                when (CloudVoiceManager.Provider.fromStorage(prefs.getString(KEY_CLOUD_VOICE_PROVIDER, null))) {
                    CloudVoiceManager.Provider.ELEVENLABS -> VoiceOutputProvider.ELEVENLABS
                    CloudVoiceManager.Provider.PIPER -> VoiceOutputProvider.PIPER
                }
            } else {
                VoiceOutputProvider.AUTOMATIC
            }
    )
    val voiceOutputProvider: StateFlow<VoiceOutputProvider> = _voiceOutputProvider.asStateFlow()

    private val _voiceInterruptionEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_VOICE_INTERRUPTION_ENABLED, true)
    )
    val voiceInterruptionEnabled: StateFlow<Boolean> = _voiceInterruptionEnabled.asStateFlow()

    private val _voiceProviderFallbackEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_VOICE_PROVIDER_FALLBACK_ENABLED, true)
    )
    val voiceProviderFallbackEnabled: StateFlow<Boolean> = _voiceProviderFallbackEnabled.asStateFlow()

    private val _voiceSilenceTimeoutMs = MutableStateFlow(
        prefs.getLong(KEY_VOICE_SILENCE_TIMEOUT_MS, 1_200L).coerceIn(700L, 5_000L)
    )
    val voiceSilenceTimeoutMs: StateFlow<Long> = _voiceSilenceTimeoutMs.asStateFlow()

    private val _voiceChatMode = MutableStateFlow(prefs.getBoolean("voice_chat_mode", false))
    val voiceChatMode: StateFlow<Boolean> = _voiceChatMode.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(prefs.getBoolean("tts_enabled", false))
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _ttsSpeed = MutableStateFlow(prefs.getFloat("tts_speed", 1.0f))
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    private val _ttsPitch = MutableStateFlow(prefs.getFloat("tts_pitch", 1.0f))
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsVoiceStyle = MutableStateFlow(
        normalizeTtsVoiceStyle(prefs.getString(KEY_TTS_VOICE_STYLE, TTS_STYLE_NATURAL))
    )
    val ttsVoiceStyle: StateFlow<String> = _ttsVoiceStyle.asStateFlow()

    private val _ttsProVoiceEnabled = MutableStateFlow(prefs.getBoolean("tts_pro_voice_enabled", true))
    val ttsProVoiceEnabled: StateFlow<Boolean> = _ttsProVoiceEnabled.asStateFlow()

    private val _cloudVoiceEnabled = MutableStateFlow(prefs.getBoolean("cloud_voice_enabled", false))
    val cloudVoiceEnabled: StateFlow<Boolean> = _cloudVoiceEnabled.asStateFlow()

    private val _cloudVoiceProvider = MutableStateFlow(
        normalizeCloudVoiceProvider(
            prefs.getString(
                KEY_CLOUD_VOICE_PROVIDER,
                CloudVoiceManager.Provider.ELEVENLABS.storageValue
            )
        )
    )
    val cloudVoiceProvider: StateFlow<String> = _cloudVoiceProvider.asStateFlow()

    private val _elevenLabsApiKey = MutableStateFlow(secureString("elevenlabs_api_key"))
    val elevenLabsApiKey: StateFlow<String> = _elevenLabsApiKey.asStateFlow()

    private val _elevenLabsVoiceId = MutableStateFlow(
        prefs.getString("elevenlabs_voice_id", "JBFqnCBsd6RMkjVDRZzb") ?: "JBFqnCBsd6RMkjVDRZzb"
    )
    val elevenLabsVoiceId: StateFlow<String> = _elevenLabsVoiceId.asStateFlow()

    private val _elevenLabsModelId = MutableStateFlow(
        prefs.getString("elevenlabs_model_id", "eleven_flash_v2_5") ?: "eleven_flash_v2_5"
    )
    val elevenLabsModelId: StateFlow<String> = _elevenLabsModelId.asStateFlow()

    private val _piperEndpoint = MutableStateFlow(
        prefs.getString(KEY_PIPER_ENDPOINT, "") ?: ""
    )
    val piperEndpoint: StateFlow<String> = _piperEndpoint.asStateFlow()

    private val _piperVoiceName = MutableStateFlow(
        prefs.getString(KEY_PIPER_VOICE_NAME, "") ?: ""
    )
    val piperVoiceName: StateFlow<String> = _piperVoiceName.asStateFlow()

    private val _streamingEnabled = MutableStateFlow(prefs.getBoolean("streaming_enabled", true))
    val streamingEnabled: StateFlow<Boolean> = _streamingEnabled.asStateFlow()
    private val _developerModeEnabled = MutableStateFlow(
        resolveDeveloperModePreference(
            prefs.takeIf { it.contains("developer_mode_enabled") }
                ?.getBoolean("developer_mode_enabled", false)
        )
    )
    val developerModeEnabled: StateFlow<Boolean> = _developerModeEnabled.asStateFlow()
    private val _developerUnlimitedTraining = MutableStateFlow(prefs.getBoolean("developer_unlimited_training", false))
    val developerUnlimitedTraining: StateFlow<Boolean> = _developerUnlimitedTraining.asStateFlow()
    private val _developerRealtimeCollabTesting = MutableStateFlow(prefs.getBoolean("developer_realtime_collab_testing", false))
    val developerRealtimeCollabTesting: StateFlow<Boolean> = _developerRealtimeCollabTesting.asStateFlow()
    private val _agentConfirmToolActions = MutableStateFlow(prefs.getBoolean("agent_confirm_tool_actions", true))
    val agentConfirmToolActions: StateFlow<Boolean> = _agentConfirmToolActions.asStateFlow()
    private val _automationQuickActionsEnabled = MutableStateFlow(prefs.getBoolean("automation_quick_actions_enabled", false))
    val automationQuickActionsEnabled: StateFlow<Boolean> = _automationQuickActionsEnabled.asStateFlow()
    private val _privacyStrictModeEnabled = MutableStateFlow(prefs.getBoolean("privacy_strict_mode_enabled", true))
    val privacyStrictModeEnabled: StateFlow<Boolean> = _privacyStrictModeEnabled.asStateFlow()
    private val _voicePushToTalkEnabled = MutableStateFlow(prefs.getBoolean("voice_push_to_talk_enabled", false))
    val voicePushToTalkEnabled: StateFlow<Boolean> = _voicePushToTalkEnabled.asStateFlow()

    private val _projectWorkspaces = MutableStateFlow(
        ProjectWorkspaceStore.decode(prefs.getString(KEY_PROJECT_WORKSPACES_JSON, ""))
    )
    val projectWorkspaces: StateFlow<List<ProjectWorkspace>> = _projectWorkspaces.asStateFlow()
    private val _activeWorkspaceId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_WORKSPACE_ID, "")?.takeIf { it.isNotBlank() }
            ?: _projectWorkspaces.value.firstOrNull()?.id.orEmpty()
    )
    val activeWorkspaceId: StateFlow<String> = _activeWorkspaceId.asStateFlow()
    private val _activeWorkspaceName = MutableStateFlow(
        _projectWorkspaces.value.firstOrNull { it.id == _activeWorkspaceId.value }?.name
            ?: _projectWorkspaces.value.firstOrNull()?.name
            ?: "Standard"
    )
    val activeWorkspaceName: StateFlow<String> = _activeWorkspaceName.asStateFlow()
    private val _workspaceChatFilterEnabled = MutableStateFlow(
        prefs.getBoolean("workspace_chat_filter_enabled", false)
    )
    val workspaceChatFilterEnabled: StateFlow<Boolean> = _workspaceChatFilterEnabled.asStateFlow()

    private val _showTimestamps = MutableStateFlow(prefs.getBoolean("show_timestamps", true))
    val showTimestamps: StateFlow<Boolean> = _showTimestamps.asStateFlow()
    private val _showLiveSources = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LIVE_SOURCES, true))
    val showLiveSources: StateFlow<Boolean> = _showLiveSources.asStateFlow()

    private val _bubbleAnimations = MutableStateFlow(prefs.getBoolean("bubble_animations", true))
    val bubbleAnimations: StateFlow<Boolean> = _bubbleAnimations.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString("language", "de") ?: "de")
    val language: StateFlow<String> = _language.asStateFlow()
    private val _autoLanguageDetectionEnabled = MutableStateFlow(
        prefs.getBoolean("auto_language_detection_enabled", true)
    )
    val autoLanguageDetectionEnabled: StateFlow<Boolean> = _autoLanguageDetectionEnabled.asStateFlow()
    private val _localOcrEnabled = MutableStateFlow(
        prefs.getBoolean("local_ocr_enabled", true)
    )
    val localOcrEnabled: StateFlow<Boolean> = _localOcrEnabled.asStateFlow()

    private val _uiDesignPreset = MutableStateFlow(
        AppDesignSystem.normalizePresetLabel(
            prefs.getString("ui_design_preset", AppDesignPreset.FUTURISTIC.label)
        )
    )
    val uiDesignPreset: StateFlow<String> = _uiDesignPreset.asStateFlow()
    private val _displayPreset = MutableStateFlow(
        DisplaySettingsPresets.normalize(prefs.getString("ui_display_preset", DisplaySettingsPresets.STANDARD))
    )
    val displayPreset: StateFlow<String> = _displayPreset.asStateFlow()
    private val _simpleModeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SIMPLE_MODE_ENABLED, true)
    )
    val simpleModeEnabled: StateFlow<Boolean> = _simpleModeEnabled.asStateFlow()
    private val _compactChatHeader = MutableStateFlow(
        prefs.getBoolean("compact_chat_header", true)
    )
    val compactChatHeader: StateFlow<Boolean> = _compactChatHeader.asStateFlow()
    private val _connectChatBottomBars = MutableStateFlow(
        prefs.getBoolean("connect_chat_bottom_bars", true)
    )
    val connectChatBottomBars: StateFlow<Boolean> = _connectChatBottomBars.asStateFlow()
    private val _glassEffectsEnabled = MutableStateFlow(
        prefs.getBoolean("glass_effects_enabled", true)
    )
    val glassEffectsEnabled: StateFlow<Boolean> = _glassEffectsEnabled.asStateFlow()
    private val _uiCornerRoundnessScale = MutableStateFlow(
        prefs.getFloat("ui_corner_roundness_scale", 1.0f)
    )
    val uiCornerRoundnessScale: StateFlow<Float> = _uiCornerRoundnessScale.asStateFlow()
    private val _uiShadowIntensityScale = MutableStateFlow(
        prefs.getFloat("ui_shadow_intensity_scale", 1.0f)
    )
    val uiShadowIntensityScale: StateFlow<Float> = _uiShadowIntensityScale.asStateFlow()
    private val _uiSurfaceOpacity = MutableStateFlow(
        prefs.getFloat("ui_surface_opacity", 0.85f)
    )
    val uiSurfaceOpacity: StateFlow<Float> = _uiSurfaceOpacity.asStateFlow()

    private val _guestAutoClearOnAccountSignIn = MutableStateFlow(
        prefs.getBoolean("guest_auto_clear_on_account_signin", true)
    )
    val guestAutoClearOnAccountSignIn: StateFlow<Boolean> = _guestAutoClearOnAccountSignIn.asStateFlow()

    private val _guestAutoClearOnSignOut = MutableStateFlow(
        prefs.getBoolean("guest_auto_clear_on_signout", true)
    )
    val guestAutoClearOnSignOut: StateFlow<Boolean> = _guestAutoClearOnSignOut.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(secureString("openrouter_api_key"))
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _groqApiKey = MutableStateFlow(secureString("groq_api_key"))
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _cerebrasApiKey = MutableStateFlow(secureString("cerebras_api_key"))
    val cerebrasApiKey: StateFlow<String> = _cerebrasApiKey.asStateFlow()

    private val _togetherApiKey = MutableStateFlow(secureString("together_api_key"))
    val togetherApiKey: StateFlow<String> = _togetherApiKey.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(secureString("gemini_api_key"))
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _openCodeApiKey = MutableStateFlow(secureString(KEY_OPENCODE_API_KEY))
    val openCodeApiKey: StateFlow<String> = _openCodeApiKey.asStateFlow()

    private val _openCodeEndpoint = MutableStateFlow(
        prefs.getString(KEY_OPENCODE_ENDPOINT, DEFAULT_OPENCODE_ENDPOINT) ?: DEFAULT_OPENCODE_ENDPOINT
    )
    val openCodeEndpoint: StateFlow<String> = _openCodeEndpoint.asStateFlow()
    private val _openCodeEndpointWarning = MutableStateFlow(buildOpenCodeEndpointWarning(_openCodeEndpoint.value))
    val openCodeEndpointWarning: StateFlow<String> = _openCodeEndpointWarning.asStateFlow()
    private val _openCodeModel = MutableStateFlow(
        prefs.getString(KEY_OPENCODE_MODEL, DEFAULT_OPENCODE_MODEL) ?: DEFAULT_OPENCODE_MODEL
    )
    val openCodeModel: StateFlow<String> = _openCodeModel.asStateFlow()

    private val _ollamaUrl = MutableStateFlow(prefs.getString("ollama_url", "http://192.168.178.162:11434/") ?: "http://192.168.178.162:11434/")
    val ollamaUrl: StateFlow<String> = _ollamaUrl.asStateFlow()
    private val _liveWebEnabled = MutableStateFlow(prefs.getBoolean("live_web_enabled", false))
    val liveWebEnabled: StateFlow<Boolean> = _liveWebEnabled.asStateFlow()
    private val _liveWebEndpoint = MutableStateFlow(prefs.getString("live_web_endpoint", "") ?: "")
    val liveWebEndpoint: StateFlow<String> = _liveWebEndpoint.asStateFlow()
    private val _liveWebApiToken = MutableStateFlow(secureString("live_web_api_token"))
    val liveWebApiToken: StateFlow<String> = _liveWebApiToken.asStateFlow()
    private val _mcpRemoteUrl = MutableStateFlow(prefs.getString("mcp_remote_url", "") ?: "")
    val mcpRemoteUrl: StateFlow<String> = _mcpRemoteUrl.asStateFlow()
    private val _mcpRemoteUrlWarning = MutableStateFlow(buildMcpRemoteUrlWarning(_mcpRemoteUrl.value))
    val mcpRemoteUrlWarning: StateFlow<String> = _mcpRemoteUrlWarning.asStateFlow()
    private val _mcpRemoteToken = MutableStateFlow(secureString("mcp_remote_token"))
    val mcpRemoteToken: StateFlow<String> = _mcpRemoteToken.asStateFlow()
    private val _liveWebAllowedDomains = MutableStateFlow(
        prefs.getString("live_web_allowed_domains", DEFAULT_LIVE_WEB_ALLOWED_DOMAINS)
            ?: DEFAULT_LIVE_WEB_ALLOWED_DOMAINS
    )
    val liveWebAllowedDomains: StateFlow<String> = _liveWebAllowedDomains.asStateFlow()
    private val _liveWebPreferGithub = MutableStateFlow(
        prefs.getBoolean("live_web_prefer_github", true)
    )
    val liveWebPreferGithub: StateFlow<Boolean> = _liveWebPreferGithub.asStateFlow()
    private val _photoAiCloudEndpoint = MutableStateFlow(
        prefs.getString(KEY_PHOTO_AI_CLOUD_ENDPOINT, "") ?: ""
    )
    val photoAiCloudEndpoint: StateFlow<String> = _photoAiCloudEndpoint.asStateFlow()
    private val _photoAiCloudApiToken = MutableStateFlow(secureString(KEY_PHOTO_AI_CLOUD_API_TOKEN))
    val photoAiCloudApiToken: StateFlow<String> = _photoAiCloudApiToken.asStateFlow()
    private val _imageGenerationMode = MutableStateFlow(
        prefs.getString(KEY_IMAGE_GENERATION_MODE, IMAGE_GENERATION_MODE_EXTERNAL)
            ?: IMAGE_GENERATION_MODE_EXTERNAL
    )
    val imageGenerationMode: StateFlow<String> = _imageGenerationMode.asStateFlow()

    private val _selectedOpenRouterModel = MutableStateFlow(
        prefs.getString("openrouter_model", "google/gemma-3-27b-it:free") ?: "google/gemma-3-27b-it:free"
    )
    val selectedOpenRouterModel: StateFlow<String> = _selectedOpenRouterModel.asStateFlow()

    private val _openRouterVisionOnlyModels = MutableStateFlow(
        prefs.getBoolean("openrouter_vision_only_models", true)
    )
    val openRouterVisionOnlyModels: StateFlow<Boolean> = _openRouterVisionOnlyModels.asStateFlow()

    private val _agentStudioEnabled = MutableStateFlow(
        prefs.getBoolean("agent_studio_enabled", false)
    )
    val agentStudioEnabled: StateFlow<Boolean> = _agentStudioEnabled.asStateFlow()

    private val defaultAgentPreset = AgentPresetLibrary.defaultPreset

    private val _agentPreset = MutableStateFlow(
        prefs.getString("agent_preset", defaultAgentPreset.label) ?: defaultAgentPreset.label
    )
    val agentPreset: StateFlow<String> = _agentPreset.asStateFlow()

    private val _agentName = MutableStateFlow(
        prefs.getString("agent_name", defaultAgentPreset.name) ?: defaultAgentPreset.name
    )
    val agentName: StateFlow<String> = _agentName.asStateFlow()

    private val _agentGoal = MutableStateFlow(
        prefs.getString("agent_goal", defaultAgentPreset.goal) ?: defaultAgentPreset.goal
    )
    val agentGoal: StateFlow<String> = _agentGoal.asStateFlow()

    private val _agentRules = MutableStateFlow(
        prefs.getString("agent_rules", defaultAgentPreset.rules) ?: defaultAgentPreset.rules
    )
    val agentRules: StateFlow<String> = _agentRules.asStateFlow()

    private val _agentOutputStyle = MutableStateFlow(
        prefs.getString("agent_output_style", defaultAgentPreset.outputStyle) ?: defaultAgentPreset.outputStyle
    )
    val agentOutputStyle: StateFlow<String> = _agentOutputStyle.asStateFlow()

    private val _agentTools = MutableStateFlow(
        prefs.getString("agent_tools", defaultAgentPreset.tools) ?: defaultAgentPreset.tools
    )
    val agentTools: StateFlow<String> = _agentTools.asStateFlow()

    private val _isPremiumActive = MutableStateFlow(prefs.getBoolean("premium_active", false))
    val isPremiumActive: StateFlow<Boolean> = _isPremiumActive.asStateFlow()
    private val _subscriptionTier = MutableStateFlow(
        prefs.getString("subscription_tier", MonetizationConfig.PlanTier.FREE.key)
            ?: MonetizationConfig.PlanTier.FREE.key
    )
    val subscriptionTier: StateFlow<String> = _subscriptionTier.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()
    private val _creditsBalance = MutableStateFlow(prefs.getInt(KEY_CREDITS_BALANCE, 0))
    val creditsBalance: StateFlow<Int> = _creditsBalance.asStateFlow()

    private val _cloudPersonaLastSyncAt = MutableStateFlow(prefs.getLong(KEY_CLOUD_PERSONA_LAST_SYNC_AT, 0L))
    val cloudPersonaLastSyncAt: StateFlow<Long> = _cloudPersonaLastSyncAt.asStateFlow()

    private val _cloudPersonaLastSyncStatus = MutableStateFlow(
        prefs.getString(KEY_CLOUD_PERSONA_LAST_SYNC_STATUS, "idle") ?: "idle"
    )
    val cloudPersonaLastSyncStatus: StateFlow<String> = _cloudPersonaLastSyncStatus.asStateFlow()
    private val _cloudChatSyncPreferenceRevision = MutableStateFlow(0)
    val cloudChatSyncPreferenceRevision: StateFlow<Int> = _cloudChatSyncPreferenceRevision.asStateFlow()
    val cloudChatSyncRuntimeStatus = chatSyncCoordinator.runtimeStatus

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "onboarding_completed" -> _onboardingCompleted.value =
                prefs.getBoolean("onboarding_completed", false)
            LegalPolicy.KEY_ACK_VERSION -> _legalAcknowledgedVersion.value =
                prefs.getInt(LegalPolicy.KEY_ACK_VERSION, 0)
            LegalPolicy.KEY_ACK_TIMESTAMP -> _legalAcknowledgedTimestamp.value =
                prefs.getLong(LegalPolicy.KEY_ACK_TIMESTAMP, 0L)
            KEY_CLOUD_PERSONA_LAST_SYNC_AT -> _cloudPersonaLastSyncAt.value =
                prefs.getLong(KEY_CLOUD_PERSONA_LAST_SYNC_AT, 0L)
            KEY_CLOUD_PERSONA_LAST_SYNC_STATUS -> _cloudPersonaLastSyncStatus.value =
                prefs.getString(KEY_CLOUD_PERSONA_LAST_SYNC_STATUS, "idle") ?: "idle"
        }
    }

    init {
        ensureOpenCodeEndpointBaseline()
        ensureOpenCodeModelBaseline()
        ensureLiveWebAllowlistBaseline()
        ensureLiveWebEndpointBaseline()
        ensurePhotoAiEndpointBaseline()
        ensurePhotoAiTokenBaseline()
        ensureAgentProfileBaseline()
        ensureWorkspaceBaseline()

        prefs.edit().putString("ui_design_preset", _uiDesignPreset.value).apply()
        prefs.edit().putString("ui_display_preset", _displayPreset.value).apply()
        billingManager.connect()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    private fun secureString(key: String, defaultValue: String = ""): String =
        SecureSettingsStore.getString(appContext, prefs, key, defaultValue)

    private fun persistSecret(key: String, value: String) {
        SecureSettingsStore.putString(appContext, key, value)
        prefs.edit().remove(key).apply()
    }

    private fun normalizeTtsVoiceStyle(style: String?): String {
        return when (style?.trim()?.lowercase()) {
            TTS_STYLE_CLEAR -> TTS_STYLE_CLEAR
            else -> TTS_STYLE_NATURAL
        }
    }

    private fun normalizeCloudVoiceProvider(provider: String?): String {
        return CloudVoiceManager.Provider.fromStorage(provider).storageValue
    }

    private fun buildMcpRemoteUrlWarning(url: String): String {
        val clean = url.trim()
        if (clean.isBlank()) return ""
        if (!clean.startsWith("http://", ignoreCase = true) &&
            !clean.startsWith("https://", ignoreCase = true)
        ) {
            return "URL sollte mit http:// oder https:// beginnen."
        }

        val parsed = runCatching { URI(clean) }.getOrNull()
            ?: return "URL ist ungueltig oder unvollstaendig."
        val scheme = parsed.scheme?.lowercase().orEmpty()
        val host = parsed.host?.lowercase().orEmpty()
        if (host.isBlank() || (scheme != "http" && scheme != "https")) {
            return "URL ist ungueltig oder unvollstaendig."
        }

        if (scheme == "http" && !isLikelyLocalMcpHost(host)) {
            return "Fuer oeffentliche Endpunkte HTTPS verwenden (HTTP nur im lokalen Netzwerk)."
        }

        return ""
    }

    private fun buildOpenCodeEndpointWarning(url: String): String {
        val clean = url.trim()
        if (clean.isBlank()) return ""
        if (!clean.startsWith("http://", ignoreCase = true) &&
            !clean.startsWith("https://", ignoreCase = true)
        ) {
            return "OpenCode Endpoint muss mit http:// oder https:// beginnen."
        }

        val parsed = runCatching { URI(clean) }.getOrNull()
            ?: return "OpenCode Endpoint ist ungueltig oder unvollstaendig."
        val scheme = parsed.scheme?.lowercase().orEmpty()
        val host = parsed.host?.lowercase().orEmpty()
        if (host.isBlank() || (scheme != "http" && scheme != "https")) {
            return "OpenCode Endpoint ist ungueltig oder unvollstaendig."
        }
        if (scheme == "http" && !isLikelyLocalMcpHost(host)) {
            return "OpenCode Endpoint: fuer externe Endpunkte HTTPS verwenden."
        }
        return ""
    }

    private fun ensureOpenCodeEndpointBaseline() {
        val current = prefs.getString(KEY_OPENCODE_ENDPOINT, "")?.trim().orEmpty()
        val shouldBackfill = current.isBlank() ||
            current.equals(LEGACY_OPENCODE_ENDPOINT, ignoreCase = true) ||
            current.equals(LEGACY_OPENCODE_ENDPOINT.removeSuffix("/"), ignoreCase = true)
        if (!shouldBackfill) return

        _openCodeEndpoint.value = DEFAULT_OPENCODE_ENDPOINT
        _openCodeEndpointWarning.value = buildOpenCodeEndpointWarning(DEFAULT_OPENCODE_ENDPOINT)
        prefs.edit().putString(KEY_OPENCODE_ENDPOINT, DEFAULT_OPENCODE_ENDPOINT).apply()
    }

    private fun ensureOpenCodeModelBaseline() {
        val current = prefs.getString(KEY_OPENCODE_MODEL, "")?.trim().orEmpty()
        val shouldBackfill = current.isBlank() || current.equals(LEGACY_OPENCODE_MODEL, ignoreCase = true)
        if (!shouldBackfill) return

        _openCodeModel.value = DEFAULT_OPENCODE_MODEL
        prefs.edit().putString(KEY_OPENCODE_MODEL, DEFAULT_OPENCODE_MODEL).apply()
    }

    private fun isLikelyLocalMcpHost(host: String): Boolean {
        val lower = host.lowercase()
        if (lower == "localhost" || lower == "::1") return true
        if (lower.endsWith(".local")) return true
        if (lower.startsWith("127.")) return true
        if (lower.startsWith("10.")) return true
        if (lower.startsWith("192.168.")) return true
        if (Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.").containsMatchIn(lower)) return true
        return false
    }

    private fun ensureLiveWebAllowlistBaseline() {
        val current = prefs.getString("live_web_allowed_domains", "")?.trim().orEmpty()
        val baseline = DEFAULT_LIVE_WEB_ALLOWED_DOMAINS.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val merged = (current.split(",").map { it.trim() }.filter { it.isNotBlank() } + baseline)
            .map { it.lowercase() }
            .distinct()
            .joinToString(",")
        if (merged.isNotBlank() && merged != current) {
            _liveWebAllowedDomains.value = merged
            prefs.edit().putString("live_web_allowed_domains", merged).apply()
        }
    }

    private fun ensureLiveWebEndpointBaseline() {
        val current = prefs.getString("live_web_endpoint", "")?.trim().orEmpty()
        val needsBackfill = current.isBlank() || current.contains("<project>") || current.contains("cloudfunctions.net/webSearch")
        if (needsBackfill) {
            _liveWebEndpoint.value = DEFAULT_LIVE_WEB_ENDPOINT
            prefs.edit()
                .putString("live_web_endpoint", DEFAULT_LIVE_WEB_ENDPOINT)
                .putBoolean("live_web_enabled", true)
                .apply()
            _liveWebEnabled.value = true
        }
    }

    private fun ensurePhotoAiEndpointBaseline() {
        val current = prefs.getString(KEY_PHOTO_AI_CLOUD_ENDPOINT, "")?.trim().orEmpty()
        if (current.isNotBlank()) {
            _photoAiCloudEndpoint.value = current
            return
        }
        val liveWebEndpoint = prefs.getString("live_web_endpoint", "")?.trim().orEmpty()
        val derived = PhotoAiCloudConfigResolver.deriveFromLiveWebEndpoint(liveWebEndpoint)
        val resolved = derived.ifBlank { DEFAULT_PHOTO_AI_CLOUD_ENDPOINT }
        if (resolved.isBlank()) return
        _photoAiCloudEndpoint.value = resolved
        prefs.edit().putString(KEY_PHOTO_AI_CLOUD_ENDPOINT, resolved).apply()
    }

    private fun ensurePhotoAiTokenBaseline() {
        val current = secureString(KEY_PHOTO_AI_CLOUD_API_TOKEN).trim()
        if (current.isNotBlank()) {
            _photoAiCloudApiToken.value = current
            return
        }
        val liveWebToken = secureString("live_web_api_token").trim()
        val resolved = liveWebToken.ifBlank { DEFAULT_PHOTO_AI_CLOUD_API_TOKEN }
        if (resolved.isBlank()) return
        _photoAiCloudApiToken.value = resolved
        persistSecret(KEY_PHOTO_AI_CLOUD_API_TOKEN, resolved)
    }

    private fun ensureAgentProfileBaseline() {
        val hasStructuredProfile = _agentGoal.value.isNotBlank() &&
            _agentRules.value.isNotBlank() &&
            _agentOutputStyle.value.isNotBlank() &&
            _agentTools.value.isNotBlank()
        if (!hasStructuredProfile) {
            applyAgentPreset(_agentPreset.value.ifBlank { "Generalist" })
        }
    }

    private fun ensureWorkspaceBaseline() {
        val sanitized = _projectWorkspaces.value
            .distinctBy { it.id }
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .ifEmpty { ProjectWorkspaceStore.defaultWorkspaces() }
        _projectWorkspaces.value = sanitized
        val activeId = _activeWorkspaceId.value
        val resolvedActiveId = sanitized.firstOrNull { it.id == activeId }?.id ?: sanitized.first().id
        _activeWorkspaceId.value = resolvedActiveId
        _activeWorkspaceName.value = sanitized.firstOrNull { it.id == resolvedActiveId }?.name ?: "Standard"
        prefs.edit()
            .putString(KEY_PROJECT_WORKSPACES_JSON, ProjectWorkspaceStore.encode(sanitized))
            .putString(KEY_ACTIVE_WORKSPACE_ID, resolvedActiveId)
            .putString(KEY_ACTIVE_WORKSPACE_NAME, _activeWorkspaceName.value)
            .apply()
    }

    fun refreshCloudSyncStatus() {
        _cloudPersonaLastSyncAt.value = prefs.getLong(KEY_CLOUD_PERSONA_LAST_SYNC_AT, 0L)
        _cloudPersonaLastSyncStatus.value = prefs.getString(KEY_CLOUD_PERSONA_LAST_SYNC_STATUS, "idle") ?: "idle"
    }

    fun formatCloudSyncStatus(lastSyncAt: Long, status: String): String {
        if (lastSyncAt <= 0L) return "Noch kein Cloud-Sync"
        val now = System.currentTimeMillis()
        val diffMs = (now - lastSyncAt).coerceAtLeast(0L)
        val age = when {
            diffMs < TimeUnit.MINUTES.toMillis(1) -> "gerade eben"
            diffMs < TimeUnit.HOURS.toMillis(1) -> "vor ${TimeUnit.MILLISECONDS.toMinutes(diffMs)} Min."
            diffMs < TimeUnit.DAYS.toMillis(1) -> "vor ${TimeUnit.MILLISECONDS.toHours(diffMs)} Std."
            else -> "vor ${TimeUnit.MILLISECONDS.toDays(diffMs)} Tagen"
        }
        val statusText = when (status.lowercase()) {
            "ok" -> "OK"
            "error" -> "Fehler"
            else -> "Unbekannt"
        }
        return "$statusText • $age"
    }

    fun isCloudChatSyncEnabledForUser(uid: String?): Boolean =
        chatSyncCoordinator.isEnabledForUid(uid)

    fun setCloudChatSyncEnabledForUser(uid: String?, enabled: Boolean) {
        val cleanUid = uid?.trim().orEmpty()
        if (cleanUid.isBlank()) return
        chatSyncCoordinator.setEnabledForUid(cleanUid, enabled)
        _cloudChatSyncPreferenceRevision.value += 1
    }

    fun hasLegacyGlobalCloudChatSyncPreference(): Boolean =
        chatSyncCoordinator.hasLegacyGlobalPreference()

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }

    fun acceptLegalPolicy() {
        val now = System.currentTimeMillis()
        _legalAcknowledgedVersion.value = LegalPolicy.CURRENT_ACK_VERSION
        _legalAcknowledgedTimestamp.value = now
        prefs.edit()
            .putInt(LegalPolicy.KEY_ACK_VERSION, LegalPolicy.CURRENT_ACK_VERSION)
            .putLong(LegalPolicy.KEY_ACK_TIMESTAMP, now)
            .apply()
        AppTelemetry.setCollectionEnabled(true)
        AppTelemetry.logEvent(
            "legal_accepted",
            mapOf("version" to LegalPolicy.CURRENT_ACK_VERSION.toString())
        )
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _isBiometricEnabled.value = enabled
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    fun setPrimaryColor(color: Int) {
        _primaryColorInt.value = color
        prefs.edit().putInt("primary_color", color).apply()
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
        prefs.edit().putFloat("font_size", size).apply()
    }

    fun setMultiProviderEnabled(enabled: Boolean) {
        _multiProviderEnabled.value = enabled
        prefs.edit().putBoolean("multi_provider", enabled).apply()
    }

    fun setAiProvider(provider: String) {
        _aiProvider.value = provider
        prefs.edit().putString("ai_provider", provider).apply()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }

    fun setAutoSendVoice(enabled: Boolean) {
        _autoSendVoice.value = enabled
        prefs.edit().putBoolean("auto_send_voice", enabled).apply()
    }

    fun setVoiceMode(mode: VoiceMode) {
        _voiceMode.value = mode
        prefs.edit().putString(KEY_VOICE_MODE, mode.storageValue).apply()
    }

    fun setVoiceInputProvider(provider: VoiceInputProvider) {
        _voiceInputProvider.value = provider
        prefs.edit().putString(KEY_VOICE_INPUT_PROVIDER, provider.storageValue).apply()
    }

    fun setVoiceOutputProvider(provider: VoiceOutputProvider) {
        _voiceOutputProvider.value = provider
        val editor = prefs.edit().putString(KEY_VOICE_OUTPUT_PROVIDER, provider.storageValue)
        when (provider) {
            VoiceOutputProvider.ELEVENLABS -> {
                _cloudVoiceEnabled.value = true
                _cloudVoiceProvider.value = CloudVoiceManager.Provider.ELEVENLABS.storageValue
                editor.putBoolean("cloud_voice_enabled", true)
                    .putString(KEY_CLOUD_VOICE_PROVIDER, CloudVoiceManager.Provider.ELEVENLABS.storageValue)
            }
            VoiceOutputProvider.PIPER -> {
                _cloudVoiceEnabled.value = true
                _cloudVoiceProvider.value = CloudVoiceManager.Provider.PIPER.storageValue
                editor.putBoolean("cloud_voice_enabled", true)
                    .putString(KEY_CLOUD_VOICE_PROVIDER, CloudVoiceManager.Provider.PIPER.storageValue)
            }
            VoiceOutputProvider.ANDROID -> {
                _cloudVoiceEnabled.value = false
                editor.putBoolean("cloud_voice_enabled", false)
            }
            VoiceOutputProvider.AUTOMATIC,
            VoiceOutputProvider.OPENAI_LIVE -> Unit
        }
        editor.apply()
    }

    fun setVoiceInterruptionEnabled(enabled: Boolean) {
        _voiceInterruptionEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_INTERRUPTION_ENABLED, enabled).apply()
    }

    fun setVoiceProviderFallbackEnabled(enabled: Boolean) {
        _voiceProviderFallbackEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_PROVIDER_FALLBACK_ENABLED, enabled).apply()
    }

    fun setVoiceSilenceTimeoutMs(timeoutMs: Long) {
        val clamped = timeoutMs.coerceIn(700L, 5_000L)
        _voiceSilenceTimeoutMs.value = clamped
        prefs.edit().putLong(KEY_VOICE_SILENCE_TIMEOUT_MS, clamped).apply()
    }

    fun setVoiceChatMode(enabled: Boolean) {
        _voiceChatMode.value = enabled
        prefs.edit().putBoolean("voice_chat_mode", enabled).apply()
    }

    fun setTtsEnabled(enabled: Boolean) {
        _ttsEnabled.value = enabled
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
    }

    fun setTtsSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _ttsSpeed.value = clamped
        prefs.edit().putFloat("tts_speed", clamped).apply()
    }

    fun setTtsPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.8f, 1.2f)
        _ttsPitch.value = clamped
        prefs.edit().putFloat("tts_pitch", clamped).apply()
    }

    fun setTtsVoiceStyle(style: String) {
        val normalized = normalizeTtsVoiceStyle(style)
        _ttsVoiceStyle.value = normalized
        prefs.edit().putString(KEY_TTS_VOICE_STYLE, normalized).apply()
    }

    fun applyNaturalTtsPreset() {
        setTtsSpeed(NATURAL_TTS_SPEED)
        setTtsPitch(NATURAL_TTS_PITCH)
        setTtsVoiceStyle(TTS_STYLE_NATURAL)
    }

    fun applyClearTtsPreset() {
        setTtsSpeed(CLEAR_TTS_SPEED)
        setTtsPitch(CLEAR_TTS_PITCH)
        setTtsVoiceStyle(TTS_STYLE_CLEAR)
    }

    fun setTtsProVoiceEnabled(enabled: Boolean) {
        _ttsProVoiceEnabled.value = enabled
        prefs.edit().putBoolean("tts_pro_voice_enabled", enabled).apply()
    }

    fun setCloudVoiceEnabled(enabled: Boolean) {
        _cloudVoiceEnabled.value = enabled
        if (!enabled && _voiceOutputProvider.value in setOf(VoiceOutputProvider.ELEVENLABS, VoiceOutputProvider.PIPER)) {
            _voiceOutputProvider.value = VoiceOutputProvider.AUTOMATIC
        }
        prefs.edit()
            .putBoolean("cloud_voice_enabled", enabled)
            .putString(KEY_VOICE_OUTPUT_PROVIDER, _voiceOutputProvider.value.storageValue)
            .apply()
    }

    fun setCloudVoiceProvider(provider: String) {
        val clean = normalizeCloudVoiceProvider(provider)
        _cloudVoiceProvider.value = clean
        if (_cloudVoiceEnabled.value) {
            _voiceOutputProvider.value = when (CloudVoiceManager.Provider.fromStorage(clean)) {
                CloudVoiceManager.Provider.ELEVENLABS -> VoiceOutputProvider.ELEVENLABS
                CloudVoiceManager.Provider.PIPER -> VoiceOutputProvider.PIPER
            }
        }
        prefs.edit()
            .putString(KEY_CLOUD_VOICE_PROVIDER, clean)
            .putString(KEY_VOICE_OUTPUT_PROVIDER, _voiceOutputProvider.value.storageValue)
            .apply()
    }

    fun setElevenLabsApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _elevenLabsApiKey.value = clean
        persistSecret("elevenlabs_api_key", clean)
    }

    fun setElevenLabsVoiceId(voiceId: String) {
        val clean = voiceId.trim()
        _elevenLabsVoiceId.value = clean
        prefs.edit().putString("elevenlabs_voice_id", clean).apply()
    }

    fun setElevenLabsModelId(modelId: String) {
        val clean = modelId.trim()
        _elevenLabsModelId.value = clean
        prefs.edit().putString("elevenlabs_model_id", clean).apply()
    }

    fun setPiperEndpoint(endpoint: String) {
        val clean = endpoint.trim()
        _piperEndpoint.value = clean
        prefs.edit().putString(KEY_PIPER_ENDPOINT, clean).apply()
    }

    fun setPiperVoiceName(voiceName: String) {
        val clean = voiceName.trim()
        _piperVoiceName.value = clean
        prefs.edit().putString(KEY_PIPER_VOICE_NAME, clean).apply()
    }

    fun setStreamingEnabled(enabled: Boolean) {
        _streamingEnabled.value = enabled
        prefs.edit().putBoolean("streaming_enabled", enabled).apply()
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        _developerModeEnabled.value = enabled
        prefs.edit().putBoolean("developer_mode_enabled", enabled).apply()
        if (!enabled) {
            setDeveloperUnlimitedTraining(false)
            setDeveloperRealtimeCollabTesting(false)
        }
    }

    fun setDeveloperUnlimitedTraining(enabled: Boolean) {
        val safeEnabled = enabled && _developerModeEnabled.value
        _developerUnlimitedTraining.value = safeEnabled
        prefs.edit().putBoolean("developer_unlimited_training", safeEnabled).apply()
    }

    fun setDeveloperRealtimeCollabTesting(enabled: Boolean) {
        val safeEnabled = enabled && _developerModeEnabled.value
        _developerRealtimeCollabTesting.value = safeEnabled
        prefs.edit().putBoolean("developer_realtime_collab_testing", safeEnabled).apply()
    }

    fun setAgentConfirmToolActions(enabled: Boolean) {
        _agentConfirmToolActions.value = enabled
        prefs.edit().putBoolean("agent_confirm_tool_actions", enabled).apply()
    }

    fun setAutomationQuickActionsEnabled(enabled: Boolean) {
        _automationQuickActionsEnabled.value = enabled
        prefs.edit().putBoolean("automation_quick_actions_enabled", enabled).apply()
    }

    fun setPrivacyStrictModeEnabled(enabled: Boolean) {
        _privacyStrictModeEnabled.value = enabled
        prefs.edit().putBoolean("privacy_strict_mode_enabled", enabled).apply()
    }

    fun setVoicePushToTalkEnabled(enabled: Boolean) {
        _voicePushToTalkEnabled.value = enabled
        prefs.edit().putBoolean("voice_push_to_talk_enabled", enabled).apply()
    }

    fun setWorkspaceChatFilterEnabled(enabled: Boolean) {
        _workspaceChatFilterEnabled.value = enabled
        prefs.edit().putBoolean("workspace_chat_filter_enabled", enabled).apply()
    }

    fun setShowTimestamps(enabled: Boolean) {
        _showTimestamps.value = enabled
        prefs.edit().putBoolean("show_timestamps", enabled).apply()
    }

    fun setShowLiveSources(enabled: Boolean) {
        _showLiveSources.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_LIVE_SOURCES, enabled).apply()
    }

    fun setBubbleAnimations(enabled: Boolean) {
        _bubbleAnimations.value = enabled
        prefs.edit().putBoolean("bubble_animations", enabled).apply()
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        prefs.edit().putString("language", lang).apply()
    }

    fun setAutoLanguageDetectionEnabled(enabled: Boolean) {
        _autoLanguageDetectionEnabled.value = enabled
        prefs.edit().putBoolean("auto_language_detection_enabled", enabled).apply()
    }

    fun setLocalOcrEnabled(enabled: Boolean) {
        _localOcrEnabled.value = enabled
        prefs.edit().putBoolean("local_ocr_enabled", enabled).apply()
    }

    fun setUiDesignPreset(preset: String) {
        val normalized = AppDesignSystem.normalizePresetLabel(preset)
        _uiDesignPreset.value = normalized
        prefs.edit().putString("ui_design_preset", normalized).apply()
    }

    fun setSimpleModeEnabled(enabled: Boolean) {
        _simpleModeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SIMPLE_MODE_ENABLED, enabled).apply()
    }

    fun setCompactChatHeader(enabled: Boolean) {
        _compactChatHeader.value = enabled
        prefs.edit().putBoolean("compact_chat_header", enabled).apply()
    }

    fun setConnectChatBottomBars(enabled: Boolean) {
        _connectChatBottomBars.value = enabled
        prefs.edit().putBoolean("connect_chat_bottom_bars", enabled).apply()
    }

    fun setGlassEffectsEnabled(enabled: Boolean) {
        _glassEffectsEnabled.value = enabled
        prefs.edit().putBoolean("glass_effects_enabled", enabled).apply()
    }

    fun setUiCornerRoundnessScale(scale: Float) {
        val clamped = scale.coerceIn(0.7f, 1.4f)
        _uiCornerRoundnessScale.value = clamped
        prefs.edit().putFloat("ui_corner_roundness_scale", clamped).apply()
    }

    fun setUiShadowIntensityScale(scale: Float) {
        val clamped = scale.coerceIn(0.6f, 1.8f)
        _uiShadowIntensityScale.value = clamped
        prefs.edit().putFloat("ui_shadow_intensity_scale", clamped).apply()
    }

    fun setUiSurfaceOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.55f, 1.0f)
        _uiSurfaceOpacity.value = clamped
        prefs.edit().putFloat("ui_surface_opacity", clamped).apply()
    }

    fun setDisplayPreset(preset: String) {
        val normalized = DisplaySettingsPresets.normalize(preset)
        _displayPreset.value = normalized
        prefs.edit().putString("ui_display_preset", normalized).apply()
        val tuning = DisplaySettingsPresets.tuningFor(normalized)
        setCompactChatHeader(tuning.compactChatHeader)
        setConnectChatBottomBars(tuning.connectChatBottomBars)
        setGlassEffectsEnabled(tuning.glassEffectsEnabled)
        setUiCornerRoundnessScale(tuning.cornerRoundnessScale)
        setUiShadowIntensityScale(tuning.shadowIntensityScale)
        setUiSurfaceOpacity(tuning.surfaceOpacity)
        setFontSize(tuning.fontSizeSp)
    }

    fun resetDisplaySettings() {
        setPrimaryColor(DisplaySettingsPresets.DEFAULT_PRIMARY_COLOR)
        setUiDesignPreset(AppDesignPreset.FUTURISTIC.label)
        setShowTimestamps(true)
        setBubbleAnimations(true)
        setStreamingEnabled(true)
        setDisplayPreset(DisplaySettingsPresets.STANDARD)
    }

    fun setGuestAutoClearOnAccountSignIn(enabled: Boolean) {
        _guestAutoClearOnAccountSignIn.value = enabled
        prefs.edit().putBoolean("guest_auto_clear_on_account_signin", enabled).apply()
    }

    fun setGuestAutoClearOnSignOut(enabled: Boolean) {
        _guestAutoClearOnSignOut.value = enabled
        prefs.edit().putBoolean("guest_auto_clear_on_signout", enabled).apply()
    }

    fun setOpenRouterApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _openRouterApiKey.value = clean
        persistSecret("openrouter_api_key", clean)
    }

    fun setGroqApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _groqApiKey.value = clean
        persistSecret("groq_api_key", clean)
    }

    fun setCerebrasApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _cerebrasApiKey.value = clean
        persistSecret("cerebras_api_key", clean)
    }

    fun setTogetherApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _togetherApiKey.value = clean
        persistSecret("together_api_key", clean)
    }

    fun setGeminiApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _geminiApiKey.value = clean
        persistSecret("gemini_api_key", clean)
    }

    fun setOpenCodeApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _openCodeApiKey.value = clean
        persistSecret(KEY_OPENCODE_API_KEY, clean)
    }

    fun setOpenCodeEndpoint(endpoint: String) {
        val clean = endpoint.trim()
        _openCodeEndpoint.value = clean
        _openCodeEndpointWarning.value = buildOpenCodeEndpointWarning(clean)
        prefs.edit().putString(KEY_OPENCODE_ENDPOINT, clean).apply()
    }

    fun setOpenCodeModel(model: String) {
        val clean = model.trim()
        _openCodeModel.value = if (clean.isBlank()) DEFAULT_OPENCODE_MODEL else clean
        prefs.edit().putString(KEY_OPENCODE_MODEL, _openCodeModel.value).apply()
    }

    fun setOllamaUrl(url: String) {
        _ollamaUrl.value = url
        prefs.edit().putString("ollama_url", url).apply()
    }

    fun setLiveWebEnabled(enabled: Boolean) {
        _liveWebEnabled.value = enabled
        prefs.edit().putBoolean("live_web_enabled", enabled).apply()
    }

    fun setLiveWebEndpoint(endpoint: String) {
        val clean = endpoint.trim()
        _liveWebEndpoint.value = clean
        prefs.edit().putString("live_web_endpoint", clean).apply()
    }

    fun setLiveWebApiToken(token: String) {
        val clean = token.trim().replace(Regex("[\\r\\n]+"), "")
        _liveWebApiToken.value = clean
        persistSecret("live_web_api_token", clean)
    }

    fun setMcpRemoteUrl(url: String) {
        val clean = url.trim()
        _mcpRemoteUrl.value = clean
        _mcpRemoteUrlWarning.value = buildMcpRemoteUrlWarning(clean)
        prefs.edit().putString("mcp_remote_url", clean).apply()
    }

    fun setMcpRemoteToken(token: String) {
        val clean = token.trim().replace(Regex("[\\r\\n]+"), "")
        _mcpRemoteToken.value = clean
        persistSecret("mcp_remote_token", clean)
    }

    fun setLiveWebAllowedDomains(domains: String) {
        val clean = domains.trim()
        _liveWebAllowedDomains.value = clean
        prefs.edit().putString("live_web_allowed_domains", clean).apply()
    }

    fun setLiveWebPreferGithub(prefer: Boolean) {
        _liveWebPreferGithub.value = prefer
        prefs.edit().putBoolean("live_web_prefer_github", prefer).apply()
    }

    fun setPhotoAiCloudEndpoint(endpoint: String) {
        val clean = endpoint.trim()
        _photoAiCloudEndpoint.value = clean
        prefs.edit().putString(KEY_PHOTO_AI_CLOUD_ENDPOINT, clean).apply()
    }

    fun setPhotoAiCloudApiToken(token: String) {
        val clean = token.trim().replace(Regex("[\\r\\n]+"), "")
        _photoAiCloudApiToken.value = clean
        persistSecret(KEY_PHOTO_AI_CLOUD_API_TOKEN, clean)
    }

    fun setImageGenerationMode(mode: String) {
        val normalized = if (IMAGE_GENERATION_MODE_OPTIONS.contains(mode)) {
            mode
        } else {
            IMAGE_GENERATION_MODE_EXTERNAL
        }
        _imageGenerationMode.value = normalized
        prefs.edit().putString(KEY_IMAGE_GENERATION_MODE, normalized).apply()
    }

    fun setSelectedOpenRouterModel(model: String) {
        _selectedOpenRouterModel.value = model
        prefs.edit().putString("openrouter_model", model).apply()
    }

    fun setOpenRouterVisionOnlyModels(enabled: Boolean) {
        _openRouterVisionOnlyModels.value = enabled
        prefs.edit().putBoolean("openrouter_vision_only_models", enabled).apply()
    }

    fun setAgentStudioEnabled(enabled: Boolean) {
        _agentStudioEnabled.value = enabled
        prefs.edit().putBoolean("agent_studio_enabled", enabled).apply()
    }

    fun setAgentPreset(preset: String) {
        _agentPreset.value = preset
        prefs.edit().putString("agent_preset", preset).apply()
    }

    fun setAgentName(name: String) {
        _agentName.value = name
        prefs.edit().putString("agent_name", name).apply()
    }

    fun setAgentGoal(goal: String) {
        _agentGoal.value = goal
        prefs.edit().putString("agent_goal", goal).apply()
    }

    fun setAgentRules(rules: String) {
        _agentRules.value = rules
        prefs.edit().putString("agent_rules", rules).apply()
    }

    fun setAgentOutputStyle(style: String) {
        _agentOutputStyle.value = style
        prefs.edit().putString("agent_output_style", style).apply()
    }

    fun setAgentTools(tools: String) {
        _agentTools.value = tools
        prefs.edit().putString("agent_tools", tools).apply()
    }

    fun applyAgentPreset(preset: String) {
        val definition = AgentPresetLibrary.find(preset) ?: defaultAgentPreset
        setAgentPreset(definition.label)
        setAgentName(definition.name)
        setAgentGoal(definition.goal)
        setAgentRules(definition.rules)
        setAgentOutputStyle(definition.outputStyle)
        setAgentTools(definition.tools)
    }

    fun getAgentPromptPreview(): String {
        val name = _agentName.value.ifBlank { defaultAgentPreset.name }
        val goal = _agentGoal.value.ifBlank { defaultAgentPreset.goal }
        val rules = _agentRules.value.ifBlank { defaultAgentPreset.rules }
        val style = _agentOutputStyle.value.ifBlank { defaultAgentPreset.outputStyle }
        val tools = _agentTools.value.ifBlank { defaultAgentPreset.tools }
        return """
[Agent-Profil]
Name: $name
Rolle: ${_agentPreset.value}

[Ziel]
$goal

[Regeln]
$rules

[Ausgabestil]
$style

[Erlaubte Arbeitsweisen]
$tools
""".trim()
    }

    fun createWorkspace(name: String, description: String = ""): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        if (_projectWorkspaces.value.any { it.name.equals(cleanName, ignoreCase = true) }) return false
        val id = "ws-" + java.util.UUID.randomUUID().toString().take(8)
        val updated = _projectWorkspaces.value + ProjectWorkspace(
            id = id,
            name = cleanName,
            description = description.trim()
        )
        _projectWorkspaces.value = updated
        setActiveWorkspace(id)
        persistWorkspaces()
        return true
    }

    /**
     * P1-1: Rename an existing workspace. Returns false when the id is unknown,
     * the new name is blank, or a different workspace already uses that name.
     */
    fun renameWorkspace(workspaceId: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return false
        val current = _projectWorkspaces.value
        val target = current.firstOrNull { it.id == workspaceId } ?: return false
        if (target.name == cleanName) return true
        if (current.any { it.id != workspaceId && it.name.equals(cleanName, ignoreCase = true) }) return false
        val updated = current.map {
            if (it.id == workspaceId) it.copy(name = cleanName) else it
        }
        _projectWorkspaces.value = updated
        if (_activeWorkspaceId.value == workspaceId) {
            _activeWorkspaceName.value = cleanName
        }
        persistWorkspaces()
        return true
    }

    fun setActiveWorkspace(workspaceId: String) {
        val target = _projectWorkspaces.value.firstOrNull { it.id == workspaceId } ?: return
        _activeWorkspaceId.value = target.id
        _activeWorkspaceName.value = target.name
        prefs.edit()
            .putString(KEY_ACTIVE_WORKSPACE_ID, target.id)
            .putString(KEY_ACTIVE_WORKSPACE_NAME, target.name)
            .apply()
    }

    fun deleteWorkspace(workspaceId: String): Boolean {
        val current = _projectWorkspaces.value
        if (current.size <= 1) return false
        if (workspaceId == "ws-default") return false
        if (current.none { it.id == workspaceId }) return false
        val updated = current.filterNot { it.id == workspaceId }
        _projectWorkspaces.value = updated
        if (_activeWorkspaceId.value == workspaceId) {
            val fallback = updated.first()
            _activeWorkspaceId.value = fallback.id
            _activeWorkspaceName.value = fallback.name
            prefs.edit().putString(KEY_ACTIVE_WORKSPACE_ID, fallback.id).apply()
        }
        persistWorkspaces()
        return true
    }

    private fun persistWorkspaces() {
        prefs.edit()
            .putString(KEY_PROJECT_WORKSPACES_JSON, ProjectWorkspaceStore.encode(_projectWorkspaces.value))
            .putString(KEY_ACTIVE_WORKSPACE_ID, _activeWorkspaceId.value)
            .putString(KEY_ACTIVE_WORKSPACE_NAME, _activeWorkspaceName.value)
            .apply()
    }

    fun getActiveWorkspaceTag(): String {
        val name = _activeWorkspaceName.value.trim()
        return if (name.isBlank()) "Neuer Chat" else "[$name] Neuer Chat"
    }

    fun refreshBillingState() {
        billingManager.connect()
        billingManager.queryProductDetails()
        billingManager.queryActivePremium()
    }

    fun startSubscriptionCheckout(activity: Activity, planId: String): Boolean {
        _purchaseInProgress.value = true
        val started = billingManager.launchSubscriptionPurchase(activity, planId)
        _purchaseInProgress.value = false
        return started
    }

    fun startCreditsCheckout(activity: Activity, productId: String): Boolean {
        _purchaseInProgress.value = true
        val started = billingManager.launchCreditPurchase(activity, productId)
        _purchaseInProgress.value = false
        return started
    }

    // Nützlich für interne Tests, bis alle Produkte in Play Console live sind.
    fun setPremiumActiveForDebug(enabled: Boolean) {
        _isPremiumActive.value = enabled
        val tier = if (enabled) MonetizationConfig.PlanTier.PRO else MonetizationConfig.PlanTier.FREE
        _subscriptionTier.value = tier.key
        prefs.edit()
            .putBoolean("premium_active", enabled)
            .putString("subscription_tier", tier.key)
            .apply()
    }

    fun clearAllData() {
        viewModelScope.launch {
            chatDao.deleteAllMessages()
            chatDao.deleteAllConversations()
            chatDao.deleteAllPersonaMemory()
            chatDao.deleteAllPersonaFeedback()
            chatDao.deleteAllPromptVersions()
            chatDao.deleteAllUserMemoryFacts()
            chatDao.deleteAllKnowledgeChunks()
            chatDao.deleteAllKnowledgeEdges()
            chatDao.deleteAllPersonaTrainingExamples()
            prefs.edit().clear().apply()
            _onboardingCompleted.value = false
            _legalAcknowledgedVersion.value = 0
            _legalAcknowledgedTimestamp.value = 0L
            _isPremiumActive.value = false
            _subscriptionTier.value = MonetizationConfig.PlanTier.FREE.key
            _creditsBalance.value = 0
            _projectWorkspaces.value = ProjectWorkspaceStore.defaultWorkspaces()
            _activeWorkspaceId.value = _projectWorkspaces.value.first().id
            _activeWorkspaceName.value = _projectWorkspaces.value.first().name
            _workspaceChatFilterEnabled.value = false
            AppTelemetry.setUserId(null)
            AppTelemetry.setCollectionEnabled(false)
            persistWorkspaces()
        }
    }

    fun clearGuestPrivateData() {
        viewModelScope.launch {
            dataSanitizer.clearGuestSessionData(clearApiKeys = false)
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        billingManager.disconnect()
        super.onCleared()
    }
}
