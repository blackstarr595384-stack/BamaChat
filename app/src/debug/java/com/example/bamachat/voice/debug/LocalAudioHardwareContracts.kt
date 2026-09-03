package com.example.bamachat.voice.debug

enum class LocalMicrophoneStatus(val displayName: String) {
    READY("Bereit"),
    PREPARING("Mikrofon wird vorbereitet"),
    LISTENING("Zuhören"),
    PARTIAL_TRANSCRIPT("Teiltranskript"),
    FINAL_TRANSCRIPT("Finales Transkript"),
    ERROR("Fehler"),
    ENDED("Beendet")
}

enum class LocalSpeechOutputStatus(val displayName: String) {
    READY("Bereit"),
    PREPARING("Wird vorbereitet"),
    SPEAKING("Spricht"),
    STOPPED("Gestoppt"),
    FINISHED("Fertig"),
    ERROR("Fehler")
}

enum class LocalAudioErrorCategory(val displayName: String) {
    NONE("Keine"),
    PERMISSION_MISSING("Mikrofonberechtigung fehlt"),
    RECOGNIZER_UNAVAILABLE("Spracherkennung nicht verfügbar"),
    NO_SPEECH("Kein Spracheingang"),
    NETWORK("System-Spracherkennung ohne Verbindung"),
    TIMEOUT("Zeitüberschreitung"),
    CANCELLED("Vom Benutzer beendet"),
    AUDIO_FOCUS("Audiofokus nicht verfügbar"),
    SPEECH_OUTPUT("Lokale Sprachausgabe fehlgeschlagen"),
    TEMPORARY("Vorübergehender Gerätefehler")
}

data class LocalAudioFailure(
    val category: LocalAudioErrorCategory,
    val userMessage: String
)

sealed interface LocalAudioOperationResult {
    data object Success : LocalAudioOperationResult
    data class Failure(val error: LocalAudioFailure) : LocalAudioOperationResult
}

interface LocalHardwareRecognitionListener {
    fun onReady()
    fun onPartialTranscript(text: String)
    fun onFinalTranscript(text: String)
    fun onFailure(error: LocalAudioFailure)
}

interface LocalHardwareSpeechRecognizer {
    fun isAvailable(): Boolean
    suspend fun start(listener: LocalHardwareRecognitionListener): LocalAudioOperationResult
    suspend fun stop()
    suspend fun cancel()
    suspend fun release()
}

data class LocalAudioHardwareUiState(
    val microphoneStatus: LocalMicrophoneStatus = LocalMicrophoneStatus.READY,
    val outputStatus: LocalSpeechOutputStatus = LocalSpeechOutputStatus.READY,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val localResponse: String = "",
    val handsFree: Boolean = false,
    val conversationActive: Boolean = false,
    val speechSpeed: Float = 1f,
    val speechPitch: Float = 1f,
    val lastErrorCategory: LocalAudioErrorCategory = LocalAudioErrorCategory.NONE,
    val errorMessage: String? = null,
    val diagnostics: List<String> = emptyList()
)
