package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import com.example.bamachat.util.CloudVoiceManager
import com.example.bamachat.util.SecureSettingsStore
import com.example.bamachat.voice.BamaVoiceSessionController
import com.example.bamachat.voice.FallbackSpeechOutputEngine
import com.example.bamachat.voice.SpeechOutputEngine
import com.example.bamachat.voice.UnavailableSpeechOutputEngine
import com.example.bamachat.voice.VoiceFinalTranscript
import com.example.bamachat.voice.VoiceInputProvider
import com.example.bamachat.voice.VoiceMode
import com.example.bamachat.voice.VoiceOutputProvider
import com.example.bamachat.voice.VoiceProviderPolicy
import com.example.bamachat.voice.RealtimeFinalizedTurn
import com.example.bamachat.voice.RealtimeVoiceEngineFactory
import com.example.bamachat.voice.RealtimeTurnTaking
import com.example.bamachat.voice.RealtimeVoice
import com.example.bamachat.voice.VoiceSessionConfiguration
import com.example.bamachat.voice.VoiceDiagnostics
import com.example.bamachat.voice.VoiceSessionUiState
import com.example.bamachat.voice.android.AndroidSpeechRecognizerEngine
import com.example.bamachat.voice.android.AndroidTextToSpeechEngine
import com.example.bamachat.voice.android.AndroidVoiceAudioSession
import com.example.bamachat.voice.android.CloudSpeechOutputEngine
import com.example.bamachat.voice.realtime.RealtimeAudioRoutePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BamaVoiceViewModel @Inject constructor(
    application: Application,
    realtimeVoiceEngineFactory: RealtimeVoiceEngineFactory,
    voiceDiagnostics: VoiceDiagnostics
) : AndroidViewModel(application), SharedPreferences.OnSharedPreferenceChangeListener {
    private data class EngineKey(
        val mode: VoiceMode,
        val inputProvider: VoiceInputProvider,
        val outputProvider: VoiceOutputProvider,
        val cloudVoiceEnabled: Boolean,
        val cloudProvider: String,
        val elevenLabsApiKey: String,
        val elevenLabsVoiceId: String,
        val elevenLabsModelId: String,
        val piperEndpoint: String,
        val piperVoiceName: String,
        val fallbackEnabled: Boolean,
        val clearVoiceStyle: Boolean,
        val realtimeVoice: RealtimeVoice,
        val realtimeTurnTaking: RealtimeTurnTaking
    )

    private data class EngineBundle(
        val input: AndroidSpeechRecognizerEngine,
        val output: SpeechOutputEngine,
        val selectedVoiceLabel: String
    )

    private val appContext = application.applicationContext
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engineKey = readEngineKey()
    private var engineBundle = createEngines(engineKey)
    private val realtimeEngine = realtimeVoiceEngineFactory.create()
    private val controller = BamaVoiceSessionController(
        scope = voiceScope,
        initialInputEngine = engineBundle.input,
        initialOutputEngine = engineBundle.output,
        audioSession = AndroidVoiceAudioSession(appContext),
        realtimeEngine = realtimeEngine,
        diagnostics = voiceDiagnostics
    )

    val uiState: StateFlow<VoiceSessionUiState> = controller.uiState
    val finalTranscripts: SharedFlow<VoiceFinalTranscript> = controller.finalTranscripts
    val realtimeTurns: SharedFlow<RealtimeFinalizedTurn> = controller.realtimeTurns

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        voiceScope.launch {
            controller.updateConfiguration(readSessionConfiguration(engineKey, engineBundle.selectedVoiceLabel))
        }
    }

    fun toggleListening() = controller.toggleListening()

    fun startListening() = controller.startListening()

    fun finishListening() = controller.finishListening()

    fun cancelListening() = controller.cancelListening()

    fun recoverFromError() = controller.recoverFromError()

    fun reportPermissionDenied(permanentlyDenied: Boolean) =
        controller.reportPermissionDenied(permanentlyDenied)

    fun markTranscriptHandled(accepted: Boolean) = controller.markTranscriptHandled(accepted)

    fun markTextMessageAccepted() = controller.markTextMessageAccepted()

    fun onAssistantTextChanged(messageId: String, text: String, isStreaming: Boolean) =
        controller.onAssistantTextChanged(messageId, text, isStreaming)

    fun speakMessage(messageId: String, text: String) = controller.speakFullMessage(messageId, text)

    fun previewVoice(text: String) = controller.preview(text)

    fun stopSpeaking() = controller.stopSpeaking(interrupted = true)

    fun stopAll() = controller.stopAll()

    fun startLiveSession(personaName: String) = controller.startLiveSession(personaName)

    fun endLiveSession() = controller.endLiveSession()

    fun toggleLiveMicrophone() = controller.toggleLiveMicrophone()

    fun beginLiveUserTurn() = controller.beginLiveUserTurn()

    fun finishLiveUserTurn() = controller.finishLiveUserTurn()

    fun leaveChatScreen() {
        val currentKey = readEngineKey()
        val freshBundle = createEngines(currentKey)
        engineKey = currentKey
        engineBundle = freshBundle
        voiceScope.launch {
            controller.updateConfiguration(
                newConfiguration = readSessionConfiguration(currentKey, freshBundle.selectedVoiceLabel),
                replacementInputEngine = freshBundle.input,
                replacementOutputEngine = freshBundle.output
            )
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean = engineBundle.input.isAvailable()

    fun refreshConfiguration() {
        val nextKey = readEngineKey()
        val nextConfiguration: VoiceSessionConfiguration
        if (nextKey == engineKey) {
            nextConfiguration = readSessionConfiguration(engineKey, engineBundle.selectedVoiceLabel)
            voiceScope.launch { controller.updateConfiguration(nextConfiguration) }
            return
        }

        val nextBundle = createEngines(nextKey)
        nextConfiguration = readSessionConfiguration(nextKey, nextBundle.selectedVoiceLabel)
        engineKey = nextKey
        engineBundle = nextBundle
        voiceScope.launch {
            controller.updateConfiguration(
                newConfiguration = nextConfiguration,
                replacementInputEngine = nextBundle.input,
                replacementOutputEngine = nextBundle.output
            )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key in VOICE_PREFERENCE_KEYS) refreshConfiguration()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        voiceScope.launch {
            controller.release()
            voiceScope.cancel()
        }
        super.onCleared()
    }

    private fun readEngineKey(): EngineKey {
        val requestedOutput = resolveStoredOutputProvider()
        return EngineKey(
            mode = VoiceMode.fromStorage(prefs.getString(KEY_VOICE_MODE, VoiceMode.UNIVERSAL.storageValue)),
            inputProvider = VoiceInputProvider.fromStorage(
                prefs.getString(KEY_VOICE_INPUT_PROVIDER, VoiceInputProvider.AUTOMATIC.storageValue)
            ),
            outputProvider = requestedOutput,
            cloudVoiceEnabled = prefs.getBoolean(KEY_CLOUD_VOICE_ENABLED, false),
            cloudProvider = prefs.getString(KEY_CLOUD_VOICE_PROVIDER, CloudVoiceManager.Provider.ELEVENLABS.storageValue)
                ?: CloudVoiceManager.Provider.ELEVENLABS.storageValue,
            elevenLabsApiKey = SecureSettingsStore.getString(
                appContext,
                prefs,
                KEY_ELEVENLABS_API_KEY
            ),
            elevenLabsVoiceId = prefs.getString(KEY_ELEVENLABS_VOICE_ID, DEFAULT_ELEVENLABS_VOICE_ID)
                ?: DEFAULT_ELEVENLABS_VOICE_ID,
            elevenLabsModelId = prefs.getString(KEY_ELEVENLABS_MODEL_ID, DEFAULT_ELEVENLABS_MODEL_ID)
                ?: DEFAULT_ELEVENLABS_MODEL_ID,
            piperEndpoint = prefs.getString(KEY_PIPER_ENDPOINT, "").orEmpty(),
            piperVoiceName = prefs.getString(KEY_PIPER_VOICE_NAME, "").orEmpty(),
            fallbackEnabled = prefs.getBoolean(KEY_VOICE_PROVIDER_FALLBACK, true),
            clearVoiceStyle = prefs.getString(KEY_TTS_VOICE_STYLE, SettingsViewModel.TTS_STYLE_NATURAL) ==
                SettingsViewModel.TTS_STYLE_CLEAR,
            realtimeVoice = RealtimeVoice.fromStorage(
                prefs.getString(KEY_REALTIME_VOICE, RealtimeVoice.MARIN.storageValue)
            ),
            realtimeTurnTaking = RealtimeTurnTaking.fromStorage(
                prefs.getString(KEY_REALTIME_TURN_TAKING, RealtimeTurnTaking.SEMANTIC.storageValue)
            )
        )
    }

    private fun readSessionConfiguration(key: EngineKey, selectedVoiceLabel: String): VoiceSessionConfiguration {
        val languageCode = prefs.getString(KEY_LANGUAGE, "de").orEmpty().ifBlank { "de" }
        val languageTag = when (languageCode) {
            "en" -> "en-US"
            "fr" -> "fr-FR"
            "es" -> "es-ES"
            "tr" -> "tr-TR"
            "ar" -> "ar"
            else -> "de-DE"
        }
        return VoiceSessionConfiguration(
            mode = key.mode,
            inputProvider = key.inputProvider,
            outputProvider = key.outputProvider,
            languageTag = languageTag,
            automaticLanguageDetection = prefs.getBoolean(KEY_AUTO_LANGUAGE_DETECTION, true),
            autoSend = prefs.getBoolean(KEY_AUTO_SEND_VOICE, false),
            autoPlayback = prefs.getBoolean(KEY_TTS_ENABLED, false),
            handsFree = prefs.getBoolean(KEY_VOICE_CHAT_MODE, false),
            pushToTalk = prefs.getBoolean(KEY_VOICE_PUSH_TO_TALK, false),
            interruptionEnabled = prefs.getBoolean(KEY_VOICE_INTERRUPTION_ENABLED, true),
            providerFallbackEnabled = key.fallbackEnabled,
            silenceTimeoutMs = prefs.getLong(KEY_VOICE_SILENCE_TIMEOUT_MS, DEFAULT_SILENCE_TIMEOUT_MS),
            speechSpeed = prefs.getFloat(KEY_TTS_SPEED, 1.0f),
            speechPitch = prefs.getFloat(KEY_TTS_PITCH, 1.0f),
            selectedVoiceLabel = selectedVoiceLabel,
            realtimeVoice = key.realtimeVoice,
            realtimeTurnTaking = key.realtimeTurnTaking,
            realtimeNoiseReduction = RealtimeAudioRoutePolicy.noiseReductionMode(appContext)
        )
    }

    private fun createEngines(key: EngineKey): EngineBundle {
        val requireOnDevice = VoiceProviderPolicy.requiresOnDeviceInput(key.mode, key.inputProvider)
        val input = AndroidSpeechRecognizerEngine(appContext, requireOnDeviceEngine = requireOnDevice)
        val androidOutput = AndroidTextToSpeechEngine(appContext)

        val resolvedOutput = resolveOutputEngine(key, androidOutput)
        return EngineBundle(
            input = input,
            output = resolvedOutput.first,
            selectedVoiceLabel = resolvedOutput.second
        )
    }

    private fun resolveOutputEngine(
        key: EngineKey,
        androidOutput: AndroidTextToSpeechEngine
    ): Pair<SpeechOutputEngine, String> {
        if (key.mode == VoiceMode.LOCAL) {
            val localPiper = if (
                VoiceProviderPolicy.resolveLocalOutputProvider(key.outputProvider, key.piperEndpoint) ==
                VoiceOutputProvider.PIPER
            ) {
                CloudVoiceManager.resolvePiperConfig(key.piperEndpoint, key.piperVoiceName)
            } else {
                null
            }
            return if (localPiper != null) {
                CloudSpeechOutputEngine(
                    CloudVoiceManager(appContext),
                    localPiper,
                    resolveVoiceStyle(key)
                ) to key.piperVoiceName.ifBlank { "Piper lokal" }
            } else {
                androidOutput to "Android Standard"
            }
        }

        val requestedProvider = when (key.outputProvider) {
            VoiceOutputProvider.AUTOMATIC -> if (key.cloudVoiceEnabled) {
                when (CloudVoiceManager.Provider.fromStorage(key.cloudProvider)) {
                    CloudVoiceManager.Provider.ELEVENLABS -> VoiceOutputProvider.ELEVENLABS
                    CloudVoiceManager.Provider.PIPER -> VoiceOutputProvider.PIPER
                }
            } else {
                VoiceOutputProvider.ANDROID
            }
            VoiceOutputProvider.OPENAI_LIVE -> VoiceOutputProvider.ANDROID
            else -> key.outputProvider
        }

        val cloudConfig = when (requestedProvider) {
            VoiceOutputProvider.ELEVENLABS -> CloudVoiceManager.resolveElevenLabsConfig(
                key.elevenLabsApiKey,
                key.elevenLabsVoiceId,
                key.elevenLabsModelId
            )
            VoiceOutputProvider.PIPER -> CloudVoiceManager.resolvePiperConfig(
                key.piperEndpoint,
                key.piperVoiceName
            )
            else -> null
        }
        if (cloudConfig == null) {
            if (requestedProvider == VoiceOutputProvider.ANDROID || key.fallbackEnabled) {
                return androidOutput to "Android Standard"
            }
            return UnavailableSpeechOutputEngine(
                requestedProvider,
                "Die gewählte Sprachausgabe ist noch nicht vollständig konfiguriert."
            ) to requestedProvider.displayName
        }

        val cloudOutput = CloudSpeechOutputEngine(
            CloudVoiceManager(appContext),
            cloudConfig,
            resolveVoiceStyle(key)
        )
        val output = if (key.fallbackEnabled) {
            FallbackSpeechOutputEngine(cloudOutput, androidOutput)
        } else {
            cloudOutput
        }
        val label = when (requestedProvider) {
            VoiceOutputProvider.ELEVENLABS -> "ElevenLabs"
            VoiceOutputProvider.PIPER -> key.piperVoiceName.ifBlank { "Piper" }
            else -> requestedProvider.displayName
        }
        return output to label
    }

    private fun resolveStoredOutputProvider(): VoiceOutputProvider {
        val stored = prefs.getString(KEY_VOICE_OUTPUT_PROVIDER, null)
        if (!stored.isNullOrBlank()) return VoiceOutputProvider.fromStorage(stored)
        if (!prefs.getBoolean(KEY_CLOUD_VOICE_ENABLED, false)) return VoiceOutputProvider.AUTOMATIC
        return when (CloudVoiceManager.Provider.fromStorage(prefs.getString(KEY_CLOUD_VOICE_PROVIDER, null))) {
            CloudVoiceManager.Provider.ELEVENLABS -> VoiceOutputProvider.ELEVENLABS
            CloudVoiceManager.Provider.PIPER -> VoiceOutputProvider.PIPER
        }
    }

    private fun resolveVoiceStyle(key: EngineKey): CloudVoiceManager.VoiceStyle =
        if (key.clearVoiceStyle) CloudVoiceManager.VoiceStyle.CLEAR else CloudVoiceManager.VoiceStyle.NATURAL

    companion object {
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val KEY_VOICE_INPUT_PROVIDER = "voice_input_provider"
        private const val KEY_VOICE_OUTPUT_PROVIDER = "voice_output_provider"
        private const val KEY_VOICE_INTERRUPTION_ENABLED = "voice_interruption_enabled"
        private const val KEY_VOICE_PROVIDER_FALLBACK = "voice_provider_fallback_enabled"
        private const val KEY_VOICE_SILENCE_TIMEOUT_MS = "voice_silence_timeout_ms"
        private const val KEY_AUTO_SEND_VOICE = "auto_send_voice"
        private const val KEY_VOICE_CHAT_MODE = "voice_chat_mode"
        private const val KEY_VOICE_PUSH_TO_TALK = "voice_push_to_talk_enabled"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_VOICE_STYLE = "tts_voice_style"
        private const val KEY_CLOUD_VOICE_ENABLED = "cloud_voice_enabled"
        private const val KEY_CLOUD_VOICE_PROVIDER = "cloud_voice_provider"
        private const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
        private const val KEY_ELEVENLABS_VOICE_ID = "elevenlabs_voice_id"
        private const val KEY_ELEVENLABS_MODEL_ID = "elevenlabs_model_id"
        private const val KEY_PIPER_ENDPOINT = "piper_endpoint"
        private const val KEY_PIPER_VOICE_NAME = "piper_voice_name"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_AUTO_LANGUAGE_DETECTION = "auto_language_detection_enabled"
        private const val KEY_REALTIME_VOICE = "voice_realtime_voice"
        private const val KEY_REALTIME_TURN_TAKING = "voice_realtime_turn_taking"
        private const val DEFAULT_ELEVENLABS_VOICE_ID = "JBFqnCBsd6RMkjVDRZzb"
        private const val DEFAULT_ELEVENLABS_MODEL_ID = "eleven_flash_v2_5"
        private const val DEFAULT_SILENCE_TIMEOUT_MS = 1_200L
        private val VOICE_PREFERENCE_KEYS = setOf(
            KEY_VOICE_MODE,
            KEY_VOICE_INPUT_PROVIDER,
            KEY_VOICE_OUTPUT_PROVIDER,
            KEY_VOICE_INTERRUPTION_ENABLED,
            KEY_VOICE_PROVIDER_FALLBACK,
            KEY_VOICE_SILENCE_TIMEOUT_MS,
            KEY_AUTO_SEND_VOICE,
            KEY_VOICE_CHAT_MODE,
            KEY_VOICE_PUSH_TO_TALK,
            KEY_TTS_ENABLED,
            KEY_TTS_SPEED,
            KEY_TTS_PITCH,
            KEY_TTS_VOICE_STYLE,
            KEY_CLOUD_VOICE_ENABLED,
            KEY_CLOUD_VOICE_PROVIDER,
            KEY_ELEVENLABS_VOICE_ID,
            KEY_ELEVENLABS_MODEL_ID,
            KEY_PIPER_ENDPOINT,
            KEY_PIPER_VOICE_NAME,
            KEY_LANGUAGE,
            KEY_AUTO_LANGUAGE_DETECTION,
            KEY_REALTIME_VOICE,
            KEY_REALTIME_TURN_TAKING
        )
    }
}
