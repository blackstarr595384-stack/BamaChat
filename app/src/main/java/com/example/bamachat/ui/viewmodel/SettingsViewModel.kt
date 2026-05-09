package com.example.bamachat.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.theme.AppDesignPreset
import com.example.bamachat.util.LocalDataSanitizer
import com.example.bamachat.util.MonetizationConfig
import com.example.bamachat.util.PlayBillingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val KEY_CLOUD_PERSONA_LAST_SYNC_AT = "cloud_persona_last_sync_at"
        private const val KEY_CLOUD_PERSONA_LAST_SYNC_STATUS = "cloud_persona_last_sync_status"
        private const val KEY_CREDITS_BALANCE = "credits_balance"
        private const val DEFAULT_LIVE_WEB_ENDPOINT = "https://websearch-xxf7qxk3wq-ew.a.run.app"
        private const val DEFAULT_LIVE_WEB_ALLOWED_DOMAINS =
            "wikipedia.org,reuters.com,tagesschau.de,bundesregierung.de,heise.de,github.com,dwd.de,wetteronline.de,wetter.com,open-meteo.com"
    }

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
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

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean("biometric_enabled", true))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _primaryColorInt = MutableStateFlow(prefs.getInt("primary_color", 0xFF6A11CB.toInt()))
    val primaryColorInt: StateFlow<Int> = _primaryColorInt.asStateFlow()

    private val _fontSize = MutableStateFlow(prefs.getFloat("font_size", 15f))
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _multiProviderEnabled = MutableStateFlow(prefs.getBoolean("multi_provider", true))
    val multiProviderEnabled: StateFlow<Boolean> = _multiProviderEnabled.asStateFlow()

    private val _aiProvider = MutableStateFlow(prefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter")
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _autoSendVoice = MutableStateFlow(prefs.getBoolean("auto_send_voice", true))
    val autoSendVoice: StateFlow<Boolean> = _autoSendVoice.asStateFlow()

    private val _voiceChatMode = MutableStateFlow(prefs.getBoolean("voice_chat_mode", false))
    val voiceChatMode: StateFlow<Boolean> = _voiceChatMode.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(prefs.getBoolean("tts_enabled", false))
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _ttsSpeed = MutableStateFlow(prefs.getFloat("tts_speed", 1.0f))
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    private val _ttsPitch = MutableStateFlow(prefs.getFloat("tts_pitch", 1.0f))
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsProVoiceEnabled = MutableStateFlow(prefs.getBoolean("tts_pro_voice_enabled", true))
    val ttsProVoiceEnabled: StateFlow<Boolean> = _ttsProVoiceEnabled.asStateFlow()

    private val _cloudVoiceEnabled = MutableStateFlow(prefs.getBoolean("cloud_voice_enabled", false))
    val cloudVoiceEnabled: StateFlow<Boolean> = _cloudVoiceEnabled.asStateFlow()

    private val _elevenLabsApiKey = MutableStateFlow(prefs.getString("elevenlabs_api_key", "") ?: "")
    val elevenLabsApiKey: StateFlow<String> = _elevenLabsApiKey.asStateFlow()

    private val _elevenLabsVoiceId = MutableStateFlow(
        prefs.getString("elevenlabs_voice_id", "JBFqnCBsd6RMkjVDRZzb") ?: "JBFqnCBsd6RMkjVDRZzb"
    )
    val elevenLabsVoiceId: StateFlow<String> = _elevenLabsVoiceId.asStateFlow()

    private val _elevenLabsModelId = MutableStateFlow(
        prefs.getString("elevenlabs_model_id", "eleven_multilingual_v2") ?: "eleven_multilingual_v2"
    )
    val elevenLabsModelId: StateFlow<String> = _elevenLabsModelId.asStateFlow()

    private val _streamingEnabled = MutableStateFlow(prefs.getBoolean("streaming_enabled", true))
    val streamingEnabled: StateFlow<Boolean> = _streamingEnabled.asStateFlow()

    private val _showTimestamps = MutableStateFlow(prefs.getBoolean("show_timestamps", true))
    val showTimestamps: StateFlow<Boolean> = _showTimestamps.asStateFlow()

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
            prefs.getString("ui_design_preset", AppDesignPreset.PROFESSIONAL.label)
        )
    )
    val uiDesignPreset: StateFlow<String> = _uiDesignPreset.asStateFlow()

    private val _guestAutoClearOnAccountSignIn = MutableStateFlow(
        prefs.getBoolean("guest_auto_clear_on_account_signin", true)
    )
    val guestAutoClearOnAccountSignIn: StateFlow<Boolean> = _guestAutoClearOnAccountSignIn.asStateFlow()

    private val _guestAutoClearOnSignOut = MutableStateFlow(
        prefs.getBoolean("guest_auto_clear_on_signout", true)
    )
    val guestAutoClearOnSignOut: StateFlow<Boolean> = _guestAutoClearOnSignOut.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(prefs.getString("openrouter_api_key", "") ?: "")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _groqApiKey = MutableStateFlow(prefs.getString("groq_api_key", "") ?: "")
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _cerebrasApiKey = MutableStateFlow(prefs.getString("cerebras_api_key", "") ?: "")
    val cerebrasApiKey: StateFlow<String> = _cerebrasApiKey.asStateFlow()

    private val _togetherApiKey = MutableStateFlow(prefs.getString("together_api_key", "") ?: "")
    val togetherApiKey: StateFlow<String> = _togetherApiKey.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(prefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _ollamaUrl = MutableStateFlow(prefs.getString("ollama_url", "http://192.168.178.162:11434/") ?: "http://192.168.178.162:11434/")
    val ollamaUrl: StateFlow<String> = _ollamaUrl.asStateFlow()
    private val _liveWebEnabled = MutableStateFlow(prefs.getBoolean("live_web_enabled", false))
    val liveWebEnabled: StateFlow<Boolean> = _liveWebEnabled.asStateFlow()
    private val _liveWebEndpoint = MutableStateFlow(prefs.getString("live_web_endpoint", "") ?: "")
    val liveWebEndpoint: StateFlow<String> = _liveWebEndpoint.asStateFlow()
    private val _liveWebApiToken = MutableStateFlow(prefs.getString("live_web_api_token", "") ?: "")
    val liveWebApiToken: StateFlow<String> = _liveWebApiToken.asStateFlow()
    private val _liveWebAllowedDomains = MutableStateFlow(
        prefs.getString("live_web_allowed_domains", DEFAULT_LIVE_WEB_ALLOWED_DOMAINS)
            ?: DEFAULT_LIVE_WEB_ALLOWED_DOMAINS
    )
    val liveWebAllowedDomains: StateFlow<String> = _liveWebAllowedDomains.asStateFlow()
    private val _liveWebPreferGithub = MutableStateFlow(
        prefs.getBoolean("live_web_prefer_github", true)
    )
    val liveWebPreferGithub: StateFlow<Boolean> = _liveWebPreferGithub.asStateFlow()

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

    private val _agentPreset = MutableStateFlow(
        prefs.getString("agent_preset", "Generalist") ?: "Generalist"
    )
    val agentPreset: StateFlow<String> = _agentPreset.asStateFlow()

    private val _agentName = MutableStateFlow(
        prefs.getString("agent_name", "Bama Agent") ?: "Bama Agent"
    )
    val agentName: StateFlow<String> = _agentName.asStateFlow()

    private val _agentGoal = MutableStateFlow(
        prefs.getString("agent_goal", "") ?: ""
    )
    val agentGoal: StateFlow<String> = _agentGoal.asStateFlow()

    private val _agentRules = MutableStateFlow(
        prefs.getString("agent_rules", "") ?: ""
    )
    val agentRules: StateFlow<String> = _agentRules.asStateFlow()

    private val _agentOutputStyle = MutableStateFlow(
        prefs.getString("agent_output_style", "Klar und präzise") ?: "Klar und präzise"
    )
    val agentOutputStyle: StateFlow<String> = _agentOutputStyle.asStateFlow()

    private val _agentTools = MutableStateFlow(
        prefs.getString("agent_tools", "Recherche, Faktencheck, Strukturierung") ?: "Recherche, Faktencheck, Strukturierung"
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

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_CLOUD_PERSONA_LAST_SYNC_AT -> _cloudPersonaLastSyncAt.value =
                prefs.getLong(KEY_CLOUD_PERSONA_LAST_SYNC_AT, 0L)
            KEY_CLOUD_PERSONA_LAST_SYNC_STATUS -> _cloudPersonaLastSyncStatus.value =
                prefs.getString(KEY_CLOUD_PERSONA_LAST_SYNC_STATUS, "idle") ?: "idle"
        }
    }

    init {
        ensureLiveWebAllowlistBaseline()
        ensureLiveWebEndpointBaseline()
        ensureAgentProfileBaseline()
        prefs.edit().putString("ui_design_preset", _uiDesignPreset.value).apply()
        billingManager.connect()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
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

    private fun ensureAgentProfileBaseline() {
        val hasStructuredProfile = _agentGoal.value.isNotBlank() &&
            _agentRules.value.isNotBlank() &&
            _agentOutputStyle.value.isNotBlank() &&
            _agentTools.value.isNotBlank()
        if (!hasStructuredProfile) {
            applyAgentPreset(_agentPreset.value.ifBlank { "Generalist" })
        }
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

    fun setVoiceChatMode(enabled: Boolean) {
        _voiceChatMode.value = enabled
        prefs.edit().putBoolean("voice_chat_mode", enabled).apply()
    }

    fun setTtsEnabled(enabled: Boolean) {
        _ttsEnabled.value = enabled
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
    }

    fun setTtsSpeed(speed: Float) {
        _ttsSpeed.value = speed
        prefs.edit().putFloat("tts_speed", speed).apply()
    }

    fun setTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
        prefs.edit().putFloat("tts_pitch", pitch).apply()
    }

    fun setTtsProVoiceEnabled(enabled: Boolean) {
        _ttsProVoiceEnabled.value = enabled
        prefs.edit().putBoolean("tts_pro_voice_enabled", enabled).apply()
    }

    fun setCloudVoiceEnabled(enabled: Boolean) {
        _cloudVoiceEnabled.value = enabled
        prefs.edit().putBoolean("cloud_voice_enabled", enabled).apply()
    }

    fun setElevenLabsApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _elevenLabsApiKey.value = clean
        prefs.edit().putString("elevenlabs_api_key", clean).apply()
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

    fun setStreamingEnabled(enabled: Boolean) {
        _streamingEnabled.value = enabled
        prefs.edit().putBoolean("streaming_enabled", enabled).apply()
    }

    fun setShowTimestamps(enabled: Boolean) {
        _showTimestamps.value = enabled
        prefs.edit().putBoolean("show_timestamps", enabled).apply()
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
        prefs.edit().putString("openrouter_api_key", clean).apply()
    }

    fun setGroqApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _groqApiKey.value = clean
        prefs.edit().putString("groq_api_key", clean).apply()
    }

    fun setCerebrasApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _cerebrasApiKey.value = clean
        prefs.edit().putString("cerebras_api_key", clean).apply()
    }

    fun setTogetherApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _togetherApiKey.value = clean
        prefs.edit().putString("together_api_key", clean).apply()
    }

    fun setGeminiApiKey(key: String) {
        val clean = key.trim().replace(Regex("[\\r\\n]+"), "")
        _geminiApiKey.value = clean
        prefs.edit().putString("gemini_api_key", clean).apply()
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
        prefs.edit().putString("live_web_api_token", clean).apply()
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
        setAgentPreset(preset)
        when (preset) {
            "Recherche" -> {
                setAgentName("Research Intelligence Agent")
                setAgentGoal("Liefere belastbare, aktuelle und entscheidungsrelevante Erkenntnisse aus mehreren Quellen mit klarer Einordnung.")
                setAgentRules("Kein Raten. Fakten, Annahmen und Unsicherheiten strikt trennen. Bei zeitkritischen Themen Stand und Quelle explizit nennen.")
                setAgentOutputStyle("Executive Summary zuerst, danach Evidenzblöcke mit Prioritäten, Risiken und offenen Fragen.")
                setAgentTools("Live-Web-Recherche, Quellenabgleich, Gegenpositionen, Faktencheck, Kurzsynthese")
            }
            "Entwickler" -> {
                setAgentName("Senior Engineering Agent")
                setAgentGoal("Liefer robuste, wartbare und produktionsnahe Lösungen mit klaren Trade-offs.")
                setAgentRules("Erst Problemrahmen, dann Lösung. Security, Fehlerfälle, Testbarkeit und Performance immer mitdenken. Keine Scheingenauigkeit bei Versionsfragen.")
                setAgentOutputStyle("Technisch präzise, mit umsetzbaren Schritten, minimalem Overhead und klaren Code-Entscheidungen.")
                setAgentTools("Code-Analyse, Refactoring, API-Debugging, Testdesign, Architekturbewertung")
            }
            "Marketing" -> {
                setAgentName("Growth Strategy Agent")
                setAgentGoal("Steigere qualifiziertes Wachstum mit messbaren Maßnahmen für Acquisition, Conversion und Retention.")
                setAgentRules("Jede Maßnahme braucht Zielgruppe, Kanal, KPI, Aufwand und erwarteten Impact. Keine Buzzword-Listen ohne Priorisierung.")
                setAgentOutputStyle("Klar priorisierte Growth-Playbooks mit Hypothese, Experimentdesign und Erfolgskriterium.")
                setAgentTools("Positionierung, Messaging, Funnel-Analyse, Experimentplanung, KPI-Diagnostik")
            }
            "Lager & Logistik" -> {
                setAgentName("Operations Excellence Agent")
                setAgentGoal("Optimiere Lager- und Logistikprozesse sicher, stabil und kostenbewusst bei hoher Servicequalität.")
                setAgentRules("Sicherheit und Compliance vor Tempo. Engpässe und Fehlerquellen benennen. Empfehlungen müssen operativ sofort umsetzbar sein.")
                setAgentOutputStyle("Praxisnahe SOP-Struktur mit klaren Schritten, Kontrollpunkten und Eskalationspfaden.")
                setAgentTools("Prozessmapping, SOP-Entwurf, Fehleranalyse, KPI-Tracking, Maßnahmenplanung")
            }
            else -> {
                // Generalist / Standard
                setAgentName("Bama Strategic Generalist")
                setAgentGoal("Löse komplexe Nutzerfragen schnell, präzise und mit maximalem praktischen Nutzen.")
                setAgentRules("Zuerst direkte Antwort, dann relevante Begründung. Unsicherheiten transparent markieren. Keine erfundenen Fakten oder Quellen.")
                setAgentOutputStyle("Kompakt, strukturiert, entscheidungsorientiert mit konkreten nächsten Schritten.")
                setAgentTools("Analyse, Strukturierung, Priorisierung, Optionenvergleich, Umsetzungsplanung")
            }
        }
    }

    fun getAgentPromptPreview(): String {
        val name = _agentName.value.ifBlank { "Bama Agent" }
        val goal = _agentGoal.value.ifBlank { "Löse Nutzeranfragen zuverlässig." }
        val rules = _agentRules.value.ifBlank { "Antworte korrekt und fokussiert." }
        val style = _agentOutputStyle.value.ifBlank { "Klar und präzise" }
        val tools = _agentTools.value.ifBlank { "Analyse, Problemlösung" }
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
            _isPremiumActive.value = false
            _subscriptionTier.value = MonetizationConfig.PlanTier.FREE.key
            _creditsBalance.value = 0
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
