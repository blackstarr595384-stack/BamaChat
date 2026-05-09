package com.example.bamachat.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.util.PlayBillingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val chatDao = ChatDatabase.getDatabase(application).chatDao()
    private val billingManager = PlayBillingManager(
        context = application.applicationContext,
        onPremiumChanged = { premium ->
            _isPremiumActive.value = premium
            prefs.edit().putBoolean("premium_active", premium).apply()
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

    private val _streamingEnabled = MutableStateFlow(prefs.getBoolean("streaming_enabled", true))
    val streamingEnabled: StateFlow<Boolean> = _streamingEnabled.asStateFlow()

    private val _showTimestamps = MutableStateFlow(prefs.getBoolean("show_timestamps", true))
    val showTimestamps: StateFlow<Boolean> = _showTimestamps.asStateFlow()

    private val _bubbleAnimations = MutableStateFlow(prefs.getBoolean("bubble_animations", true))
    val bubbleAnimations: StateFlow<Boolean> = _bubbleAnimations.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString("language", "de") ?: "de")
    val language: StateFlow<String> = _language.asStateFlow()

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

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()

    init {
        billingManager.connect()
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
                setAgentName("Research Agent")
                setAgentGoal("Führe strukturierte Tiefenrecherche durch, liefere belastbare Erkenntnisse und zeige Unsicherheiten transparent.")
                setAgentRules("Behaupte nichts ohne Begründung. Trenne Fakten von Annahmen. Kennzeichne offene Punkte klar.")
                setAgentOutputStyle("Analytisch, mit klaren Zwischenüberschriften und kurzer Executive Summary.")
                setAgentTools("Recherche, Faktencheck, Quellenkritik")
            }
            "Entwickler" -> {
                setAgentName("Code Agent")
                setAgentGoal("Löse technische Probleme pragmatisch mit robusten, wartbaren Lösungen.")
                setAgentRules("Erst Anforderungen klären, dann Lösung. Keine unnötige Komplexität. Nenne Risiken und Teststrategie.")
                setAgentOutputStyle("Kurz, präzise, code-orientiert.")
                setAgentTools("Code-Analyse, Refactoring, Testplanung")
            }
            "Marketing" -> {
                setAgentName("Growth Agent")
                setAgentGoal("Erstelle umsetzbare Marketing-Ideen mit Fokus auf Conversion und Retention.")
                setAgentRules("Jede Empfehlung braucht Ziel, Kanal, KPI und nächsten Schritt.")
                setAgentOutputStyle("Klar, verkaufsorientiert, mit Priorisierung nach Wirkung.")
                setAgentTools("Copywriting, Kampagnenplanung, Funnel-Denken")
            }
            "Lager & Logistik" -> {
                setAgentName("Logistik Agent")
                setAgentGoal("Hilf bei Lager-, Kommissionier- und Prozessfragen mit sicheren und effizienten Abläufen.")
                setAgentRules("Sicherheit vor Geschwindigkeit. Gib Checklisten und klare Arbeitsschritte.")
                setAgentOutputStyle("Praxisnah, knapp, schrittweise.")
                setAgentTools("Checklisten, SOP-Entwurf, Prozessoptimierung")
            }
            else -> {
                // Generalist / Standard
                setAgentName("Bama Agent")
                setAgentGoal("Löse Nutzeranfragen zuverlässig, klar und handlungsorientiert.")
                setAgentRules("Antworte korrekt, nenne Unsicherheiten offen und halte die Antwort fokussiert.")
                setAgentOutputStyle("Klar und präzise")
                setAgentTools("Analyse, Strukturierung, Problemlösung")
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

    // Nützlich für interne Tests, bis alle Produkte in Play Console live sind.
    fun setPremiumActiveForDebug(enabled: Boolean) {
        _isPremiumActive.value = enabled
        prefs.edit().putBoolean("premium_active", enabled).apply()
    }

    fun clearAllData() {
        viewModelScope.launch {
            chatDao.deleteAllMessages()
            chatDao.deleteAllConversations()
            prefs.edit().clear().apply()
            _isPremiumActive.value = false
        }
    }

    override fun onCleared() {
        billingManager.disconnect()
        super.onCleared()
    }
}
