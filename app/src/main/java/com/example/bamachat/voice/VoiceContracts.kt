package com.example.bamachat.voice

enum class VoiceMode(
    val storageValue: String,
    val displayName: String
) {
    AUTOMATIC("automatic", "Automatisch empfohlen"),
    LIVE("live", "Live-Unterhaltung"),
    UNIVERSAL("universal", "Spracheingabe mit normalem Chat"),
    LOCAL("local", "Nur lokal");

    companion object {
        fun fromStorage(value: String?): VoiceMode =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: UNIVERSAL
    }
}

enum class VoiceInputProvider(
    val storageValue: String,
    val displayName: String
) {
    AUTOMATIC("automatic", "Automatisch"),
    OPENAI_TRANSCRIPTION("openai_transcription", "OpenAI Transkription"),
    ANDROID("android", "Android");

    companion object {
        fun fromStorage(value: String?): VoiceInputProvider =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: AUTOMATIC
    }
}

enum class VoiceOutputProvider(
    val storageValue: String,
    val displayName: String
) {
    AUTOMATIC("automatic", "Automatisch"),
    OPENAI_LIVE("openai_live", "OpenAI Live"),
    ELEVENLABS("elevenlabs", "ElevenLabs"),
    PIPER("piper", "Piper"),
    ANDROID("android", "Android");

    companion object {
        fun fromStorage(value: String?): VoiceOutputProvider =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: AUTOMATIC
    }
}

sealed interface VoiceSessionState {
    data object Idle : VoiceSessionState
    data object Preparing : VoiceSessionState
    data object Connecting : VoiceSessionState
    data class Reconnecting(val attempt: Int, val maximumAttempts: Int) : VoiceSessionState
    data object Listening : VoiceSessionState
    data class Transcribing(val partialText: String) : VoiceSessionState
    data object Thinking : VoiceSessionState
    data object Speaking : VoiceSessionState
    data object Interrupted : VoiceSessionState
    data object Ended : VoiceSessionState
    data class Error(
        val userMessage: String,
        val recoverable: Boolean = true
    ) : VoiceSessionState
}

enum class RealtimeVoice(
    val storageValue: String,
    val displayName: String
) {
    MARIN("marin", "Marin"),
    CEDAR("cedar", "Cedar");

    companion object {
        fun fromStorage(value: String?): RealtimeVoice =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: MARIN
    }
}

enum class RealtimeTurnTaking(
    val storageValue: String,
    val displayName: String
) {
    SEMANTIC("semantic", "Natürlich / Semantic"),
    FAST("fast", "Schnell"),
    PUSH_TO_TALK("push_to_talk", "Push-to-talk");

    companion object {
        fun fromStorage(value: String?): RealtimeTurnTaking =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: SEMANTIC
    }
}

enum class VoiceFailureCategory {
    OFFLINE,
    AUTHENTICATION_REQUIRED,
    PERMISSION_DENIED,
    RATE_LIMITED,
    UNSUPPORTED,
    TEMPORARY_SERVICE_ERROR,
    TIMEOUT,
    CANCELLED
}

data class VoiceFailure(
    val category: VoiceFailureCategory,
    val userMessage: String,
    val recoverable: Boolean = true
)

sealed interface VoiceOperationResult {
    data object Success : VoiceOperationResult
    data class Failure(val error: VoiceFailure) : VoiceOperationResult
}

data class SpeechInputConfig(
    val languageTag: String,
    val automaticLanguageDetection: Boolean,
    val silenceTimeoutMs: Long,
    val requireOnDevice: Boolean
)

data class SpeechOutputRequest(
    val text: String,
    val languageTag: String,
    val speed: Float,
    val pitch: Float
)

interface SpeechInputListener {
    fun onReady()
    fun onInputLevel(level: Float)
    fun onPartialTranscript(text: String)
    fun onSpeechEnded()
    fun onFinalTranscript(text: String)
    fun onFailure(error: VoiceFailure)
}

interface SpeechOutputListener {
    fun onPlaybackStarted()
}

interface SpeechToTextEngine {
    val provider: VoiceInputProvider
    fun isAvailable(): Boolean
    suspend fun startStreaming(config: SpeechInputConfig, listener: SpeechInputListener): VoiceOperationResult
    suspend fun finish()
    suspend fun cancel()
    suspend fun release()
}

interface SpeechOutputEngine {
    val provider: VoiceOutputProvider
    suspend fun speak(request: SpeechOutputRequest, listener: SpeechOutputListener): VoiceOperationResult
    suspend fun stop()
    suspend fun pause(): VoiceOperationResult
    suspend fun resume(): VoiceOperationResult
    suspend fun release()
}

enum class VoiceAudioPurpose {
    LISTENING,
    SPEAKING
}

interface VoiceAudioSession {
    suspend fun activate(purpose: VoiceAudioPurpose, onFocusLost: () -> Unit): VoiceOperationResult
    suspend fun deactivate()
}

data class VoiceSessionConfiguration(
    val mode: VoiceMode = VoiceMode.UNIVERSAL,
    val inputProvider: VoiceInputProvider = VoiceInputProvider.AUTOMATIC,
    val outputProvider: VoiceOutputProvider = VoiceOutputProvider.AUTOMATIC,
    val languageTag: String = "de-DE",
    val automaticLanguageDetection: Boolean = true,
    val autoSend: Boolean = false,
    val autoPlayback: Boolean = false,
    val handsFree: Boolean = false,
    val pushToTalk: Boolean = false,
    val interruptionEnabled: Boolean = true,
    val providerFallbackEnabled: Boolean = true,
    val silenceTimeoutMs: Long = 1_200L,
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val selectedVoiceLabel: String = "Android Standard",
    val realtimeVoice: RealtimeVoice = RealtimeVoice.MARIN,
    val realtimeTurnTaking: RealtimeTurnTaking = RealtimeTurnTaking.SEMANTIC,
    val realtimePersonaName: String = "BamaChat",
    val realtimeNoiseReduction: String = "near_field"
)

data class VoiceFinalTranscript(
    val sessionId: Long,
    val text: String
)

data class VoiceSessionUiState(
    val state: VoiceSessionState = VoiceSessionState.Idle,
    val inputLevel: Float = 0f,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val assistantTranscript: String = "",
    val activeOutputMessageId: String? = null,
    val mode: VoiceMode = VoiceMode.UNIVERSAL,
    val inputProvider: VoiceInputProvider = VoiceInputProvider.AUTOMATIC,
    val outputProvider: VoiceOutputProvider = VoiceOutputProvider.AUTOMATIC,
    val selectedVoiceLabel: String = "Android Standard",
    val connectionLabel: String = "Bereit",
    val privacyLabel: String = "Android-Spracherkennung und lokale Geräteausgabe",
    val realtimeProviderLabel: String = "OpenAI Realtime",
    val realtimeTransportStatusLabel: String = "Noch nicht verbunden · OpenAI Realtime",
    val realtimeAvailable: Boolean = false,
    val liveSessionActive: Boolean = false,
    val microphoneMuted: Boolean = false,
    val secureConnection: Boolean = false,
    val sessionStartedAtEpochMillis: Long? = null,
    val sessionDurationLimitSeconds: Long? = null
)

interface VoiceDiagnostics {
    fun event(name: String, attributes: Map<String, String> = emptyMap())
    fun timing(name: String, durationMs: Long, attributes: Map<String, String> = emptyMap())
}

object NoOpVoiceDiagnostics : VoiceDiagnostics {
    override fun event(name: String, attributes: Map<String, String>) = Unit
    override fun timing(name: String, durationMs: Long, attributes: Map<String, String>) = Unit
}

data class RealtimeVoiceSessionRequest(
    val provider: String,
    val model: String,
    val voice: String,
    val languageTag: String,
    val personaName: String = "BamaChat",
    val turnTaking: RealtimeTurnTaking = RealtimeTurnTaking.SEMANTIC,
    val noiseReduction: String = "near_field",
    val interruptResponse: Boolean = true
)

data class EphemeralVoiceCredential(
    val value: String,
    val expiresAtEpochSeconds: Long,
    val model: String,
    val voice: String,
    val leaseId: String,
    val sessionExpiresAtEpochSeconds: Long
)

interface RealtimeEphemeralCredentialProvider {
    val isConfigured: Boolean
    suspend fun requestCredential(request: RealtimeVoiceSessionRequest): Result<EphemeralVoiceCredential>
    suspend fun releaseCredential(leaseId: String)
}

sealed interface RealtimeVoiceEvent {
    data object Connecting : RealtimeVoiceEvent
    data object Connected : RealtimeVoiceEvent
    data class SessionStarted(val sessionExpiresAtEpochSeconds: Long) : RealtimeVoiceEvent
    data class Reconnecting(val attempt: Int, val maximumAttempts: Int) : RealtimeVoiceEvent
    data class SpeechStarted(val itemId: String?) : RealtimeVoiceEvent
    data object SpeechStopped : RealtimeVoiceEvent
    data class UserTranscriptDelta(val itemId: String?, val delta: String) : RealtimeVoiceEvent
    data class UserTranscriptCompleted(val itemId: String, val transcript: String) : RealtimeVoiceEvent
    data class ResponseCreated(val responseId: String) : RealtimeVoiceEvent
    data class AssistantTranscriptDelta(
        val responseId: String,
        val itemId: String?,
        val delta: String
    ) : RealtimeVoiceEvent
    data class AssistantTranscriptCompleted(
        val responseId: String,
        val itemId: String?,
        val transcript: String
    ) : RealtimeVoiceEvent
    data class ResponseCompleted(val responseId: String) : RealtimeVoiceEvent
    data class ResponseCancelled(val responseId: String) : RealtimeVoiceEvent
    data class Failure(val error: VoiceFailure) : RealtimeVoiceEvent
    data object Closed : RealtimeVoiceEvent
}

fun interface RealtimeVoiceListener {
    fun onEvent(event: RealtimeVoiceEvent)
}

data class RealtimeFinalizedTurn(
    val messageId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long
)

interface RealtimeVoiceEngine {
    val isAvailable: Boolean
    val providerLabel: String
        get() = "OpenAI Realtime"
    val privacyLabel: String
        get() = "Live-Audio wird zur Verarbeitung an OpenAI übertragen. Der dauerhafte Provider-Schlüssel ist nicht in der App gespeichert."
    val connectedStatusLabel: String
        get() = "Sichere Verbindung · OpenAI Realtime"
    val disconnectedStatusLabel: String
        get() = "Noch nicht verbunden · OpenAI Realtime"
    suspend fun start(
        request: RealtimeVoiceSessionRequest,
        listener: RealtimeVoiceListener
    ): VoiceOperationResult
    suspend fun mute(muted: Boolean)
    suspend fun beginUserTurn()
    suspend fun finishUserTurn()
    suspend fun interrupt()
    suspend fun stop()
    suspend fun release()
}

object VoiceProviderCatalog {
    const val OPENAI_REALTIME_RECOMMENDATION = "Empfohlen für natürliche Live-Unterhaltung"
    const val OPENAI_TRANSCRIBE_QUALITY = "Beste Erkennungsqualität"
    const val OPENAI_TRANSCRIBE_BALANCED = "Schnell und günstiger"
    const val ELEVENLABS_FLASH_RECOMMENDATION = "Empfohlen für schnelle Sprachausgabe"
    const val ELEVENLABS_MULTILINGUAL_RECOMMENDATION = "Empfohlen für hochwertige längere Antworten"
    const val PIPER_RECOMMENDATION = "Lokal und privat"
    const val ANDROID_RECOMMENDATION = "Einfacher Geräte-Fallback"
}
