package com.example.bamachat.voice.debug

import com.example.bamachat.voice.SpeechOutputEngine
import com.example.bamachat.voice.SpeechOutputListener
import com.example.bamachat.voice.SpeechOutputRequest
import com.example.bamachat.voice.VoiceAudioPurpose
import com.example.bamachat.voice.VoiceAudioSession
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalAudioHardwareTestController(
    private val recognizer: LocalHardwareSpeechRecognizer,
    private val speechOutput: SpeechOutputEngine,
    private val audioSession: VoiceAudioSession,
    private val scope: CoroutineScope
) {
    private val operationMutex = Mutex()
    private val _uiState = MutableStateFlow(LocalAudioHardwareUiState())
    val uiState: StateFlow<LocalAudioHardwareUiState> = _uiState.asStateFlow()

    private var recognitionGeneration = 0L
    private var outputGeneration = 0L
    private var recognitionActive = false
    private var outputJob: Job? = null
    private var permissionGrantedForConversation = false
    private var lastFinalGeneration = -1L
    private var lastHandsFreeRestartGeneration = -1L
    private var lifecycleStopped = false
    private var released = false

    suspend fun startMicrophone(permissionGranted: Boolean) = operationMutex.withLock {
        if (released) return@withLock
        lifecycleStopped = false
        _uiState.update { it.copy(conversationActive = false) }
        permissionGrantedForConversation = false
        stopOutputLocked(updateStatus = false)
        startRecognitionLocked(permissionGranted)
    }

    suspend fun stopMicrophone() = operationMutex.withLock {
        if (released || !recognitionActive) return@withLock
        appendDiagnostic("Mikrofon: Stop angefordert")
        recognizer.stop()
    }

    suspend fun startSpeechTest() = operationMutex.withLock {
        if (released) return@withLock
        lifecycleStopped = false
        _uiState.update { it.copy(conversationActive = false) }
        permissionGrantedForConversation = false
        cancelRecognitionLocked(markEnded = true)
        launchSpeechLocked(TEST_SPEECH_TEXT, resumeListening = false)
    }

    suspend fun stopSpeechOutput() = operationMutex.withLock {
        if (released) return@withLock
        stopOutputLocked(updateStatus = true)
    }

    suspend fun startLocalConversation(permissionGranted: Boolean) = operationMutex.withLock {
        if (released) return@withLock
        lifecycleStopped = false
        _uiState.update {
            it.copy(
                conversationActive = true,
                partialTranscript = "",
                finalTranscript = "",
                localResponse = ""
            )
        }
        permissionGrantedForConversation = permissionGranted
        stopOutputLocked(updateStatus = false)
        startRecognitionLocked(permissionGranted)
    }

    suspend fun interruptAndListen(permissionGranted: Boolean) = operationMutex.withLock {
        if (released) return@withLock
        lifecycleStopped = false
        _uiState.update { it.copy(conversationActive = true) }
        permissionGrantedForConversation = permissionGranted
        stopOutputLocked(updateStatus = true)
        startRecognitionLocked(permissionGranted)
        appendDiagnostic("Lokales Gespräch: Ausgabe unterbrochen")
    }

    suspend fun endConversation() = operationMutex.withLock {
        if (released) return@withLock
        permissionGrantedForConversation = false
        _uiState.update { it.copy(conversationActive = false) }
        cancelRecognitionLocked(markEnded = true)
        stopOutputLocked(updateStatus = true)
        appendDiagnostic("Lokales Gespräch: beendet")
    }

    fun setHandsFree(enabled: Boolean) {
        if (released) return
        _uiState.update { it.copy(handsFree = enabled) }
        appendDiagnostic(if (enabled) "Freisprechen: aktiv" else "Freisprechen: inaktiv")
    }

    fun setSpeechSpeed(speed: Float) {
        if (released) return
        _uiState.update { it.copy(speechSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)) }
    }

    fun setSpeechPitch(pitch: Float) {
        if (released) return
        _uiState.update { it.copy(speechPitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)) }
    }

    fun reportPermissionDenied() {
        if (released) return
        applyFailure(permissionFailure())
    }

    fun clearDiagnostics() {
        if (released) return
        _uiState.update {
            it.copy(
                diagnostics = emptyList(),
                lastErrorCategory = LocalAudioErrorCategory.NONE,
                errorMessage = null
            )
        }
    }

    suspend fun stopForLifecycle() = operationMutex.withLock {
        if (released || lifecycleStopped) return@withLock
        lifecycleStopped = true
        permissionGrantedForConversation = false
        _uiState.update { it.copy(conversationActive = false) }
        cancelRecognitionLocked(markEnded = true)
        stopOutputLocked(updateStatus = true)
        appendDiagnostic("Lifecycle: Audio-Hardwaretest gestoppt")
    }

    suspend fun release() = operationMutex.withLock {
        if (released) return@withLock
        released = true
        lifecycleStopped = true
        permissionGrantedForConversation = false
        recognitionGeneration += 1
        outputGeneration += 1
        recognitionActive = false
        outputJob?.cancel()
        outputJob = null
        runCatching { recognizer.cancel() }
        runCatching { speechOutput.stop() }
        runCatching { audioSession.deactivate() }
        runCatching { recognizer.release() }
        runCatching { speechOutput.release() }
    }

    private suspend fun startRecognitionLocked(permissionGranted: Boolean) {
        if (!permissionGranted) {
            _uiState.update { it.copy(conversationActive = false) }
            permissionGrantedForConversation = false
            applyFailure(permissionFailure())
            return
        }
        if (!recognizer.isAvailable()) {
            applyFailure(
                LocalAudioFailure(
                    LocalAudioErrorCategory.RECOGNIZER_UNAVAILABLE,
                    "Android-Spracherkennung ist auf diesem Gerät nicht verfügbar."
                )
            )
            return
        }

        if (recognitionActive) {
            appendDiagnostic("Mikrofon: bereits aktiv")
            return
        }

        val generation = ++recognitionGeneration
        lastFinalGeneration = -1L
        recognitionActive = true
        _uiState.update {
            it.copy(
                microphoneStatus = LocalMicrophoneStatus.PREPARING,
                partialTranscript = "",
                lastErrorCategory = LocalAudioErrorCategory.NONE,
                errorMessage = null
            )
        }
        appendDiagnostic("Mikrofon: Start angefordert")

        val audioResult = audioSession.activate(VoiceAudioPurpose.LISTENING) {
            scope.launch { stopForLifecycle() }
        }
        if (audioResult is VoiceOperationResult.Failure) {
            recognitionActive = false
            applyFailure(
                LocalAudioFailure(
                    LocalAudioErrorCategory.AUDIO_FOCUS,
                    "Der Mikrofon-Audiomodus konnte nicht reserviert werden."
                )
            )
            return
        }

        val result = recognizer.start(listenerFor(generation))
        if (result is LocalAudioOperationResult.Failure) {
            recognitionActive = false
            audioSession.deactivate()
            applyFailure(result.error)
        }
    }

    private fun listenerFor(generation: Long) = object : LocalHardwareRecognitionListener {
        override fun onReady() {
            if (!isCurrentRecognition(generation)) return
            _uiState.update { it.copy(microphoneStatus = LocalMicrophoneStatus.LISTENING) }
            appendDiagnostic("Mikrofon: bereit")
        }

        override fun onPartialTranscript(text: String) {
            if (!isCurrentRecognition(generation)) return
            val cleanText = normalizeTranscript(text)
            if (cleanText.isBlank()) return
            _uiState.update {
                it.copy(
                    microphoneStatus = LocalMicrophoneStatus.PARTIAL_TRANSCRIPT,
                    partialTranscript = cleanText
                )
            }
        }

        override fun onFinalTranscript(text: String) {
            if (!isCurrentRecognition(generation) || lastFinalGeneration == generation) return
            val cleanText = normalizeTranscript(text)
            if (cleanText.isBlank()) {
                onFailure(
                    LocalAudioFailure(
                        LocalAudioErrorCategory.NO_SPEECH,
                        "Es wurde kein verständlicher Spracheingang erkannt."
                    )
                )
                return
            }
            lastFinalGeneration = generation
            recognitionActive = false
            _uiState.update {
                it.copy(
                    microphoneStatus = LocalMicrophoneStatus.FINAL_TRANSCRIPT,
                    partialTranscript = "",
                    finalTranscript = cleanText,
                    lastErrorCategory = LocalAudioErrorCategory.NONE,
                    errorMessage = null
                )
            }
            appendDiagnostic("Mikrofon: finales Ergebnis übernommen")
            scope.launch {
                audioSession.deactivate()
                if (_uiState.value.conversationActive && generation == recognitionGeneration) {
                    val response = buildLocalTestResponse(cleanText)
                    _uiState.update { it.copy(localResponse = response) }
                    operationMutex.withLock {
                        if (!released && _uiState.value.conversationActive) {
                            launchSpeechLocked(response, resumeListening = true)
                        }
                    }
                }
            }
        }

        override fun onFailure(error: LocalAudioFailure) {
            if (!isCurrentRecognition(generation)) return
            recognitionActive = false
            applyFailure(error)
            scope.launch { audioSession.deactivate() }
        }
    }

    private suspend fun launchSpeechLocked(text: String, resumeListening: Boolean) {
        stopOutputLocked(updateStatus = false)
        val generation = ++outputGeneration
        _uiState.update {
            it.copy(
                outputStatus = LocalSpeechOutputStatus.PREPARING,
                lastErrorCategory = LocalAudioErrorCategory.NONE,
                errorMessage = null
            )
        }
        appendDiagnostic("Sprachausgabe: wird vorbereitet")
        val request = SpeechOutputRequest(
            text = text,
            languageTag = LANGUAGE_TAG,
            speed = _uiState.value.speechSpeed,
            pitch = _uiState.value.speechPitch
        )
        outputJob = scope.launch {
            val focusResult = audioSession.activate(VoiceAudioPurpose.SPEAKING) {
                scope.launch { stopSpeechOutput() }
            }
            if (generation != outputGeneration) return@launch
            if (focusResult is VoiceOperationResult.Failure) {
                applyFailure(
                    LocalAudioFailure(
                        LocalAudioErrorCategory.AUDIO_FOCUS,
                        "Audio wird gerade von einer anderen App verwendet."
                    ),
                    outputFailure = true
                )
                return@launch
            }

            val result = runCatching {
                speechOutput.speak(
                    request,
                    object : SpeechOutputListener {
                        override fun onPlaybackStarted() {
                            if (generation != outputGeneration) return
                            _uiState.update { it.copy(outputStatus = LocalSpeechOutputStatus.SPEAKING) }
                            appendDiagnostic("Sprachausgabe: gestartet")
                        }
                    }
                )
            }.getOrElse {
                VoiceOperationResult.Failure(
                    VoiceFailure(
                        VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                        "Die lokale Android-Sprachausgabe ist fehlgeschlagen."
                    )
                )
            }
            if (generation != outputGeneration) return@launch
            audioSession.deactivate()
            outputJob = null
            when (result) {
                VoiceOperationResult.Success -> {
                    _uiState.update { it.copy(outputStatus = LocalSpeechOutputStatus.FINISHED) }
                    appendDiagnostic("Sprachausgabe: fertig")
                    restartHandsFreeOnce(generation, resumeListening)
                }
                is VoiceOperationResult.Failure -> applyOutputFailure(result)
            }
        }
    }

    private suspend fun restartHandsFreeOnce(generation: Long, resumeListening: Boolean) {
        if (!resumeListening || generation != outputGeneration) return
        if (!_uiState.value.handsFree || !_uiState.value.conversationActive) return
        if (!permissionGrantedForConversation || lastHandsFreeRestartGeneration == generation) return
        lastHandsFreeRestartGeneration = generation
        operationMutex.withLock {
            if (!released && _uiState.value.conversationActive && generation == outputGeneration) {
                startRecognitionLocked(permissionGranted = true)
            }
        }
    }

    private suspend fun cancelRecognitionLocked(markEnded: Boolean) {
        if (!recognitionActive && !markEnded) return
        recognitionGeneration += 1
        recognitionActive = false
        runCatching { recognizer.cancel() }
        runCatching { audioSession.deactivate() }
        if (markEnded) {
            _uiState.update { it.copy(microphoneStatus = LocalMicrophoneStatus.ENDED, partialTranscript = "") }
        }
    }

    private suspend fun stopOutputLocked(updateStatus: Boolean) {
        outputGeneration += 1
        outputJob?.cancel()
        outputJob = null
        runCatching { speechOutput.stop() }
        runCatching { audioSession.deactivate() }
        if (updateStatus) {
            _uiState.update { it.copy(outputStatus = LocalSpeechOutputStatus.STOPPED) }
            appendDiagnostic("Sprachausgabe: gestoppt")
        }
    }

    private fun applyOutputFailure(result: VoiceOperationResult.Failure) {
        val category = when (result.error.category) {
            VoiceFailureCategory.CANCELLED -> LocalAudioErrorCategory.CANCELLED
            VoiceFailureCategory.TIMEOUT -> LocalAudioErrorCategory.TIMEOUT
            else -> LocalAudioErrorCategory.SPEECH_OUTPUT
        }
        applyFailure(
            LocalAudioFailure(category, "Die lokale Android-Sprachausgabe ist fehlgeschlagen."),
            outputFailure = true
        )
    }

    private fun applyFailure(error: LocalAudioFailure, outputFailure: Boolean = false) {
        _uiState.update {
            it.copy(
                microphoneStatus = if (outputFailure) it.microphoneStatus else LocalMicrophoneStatus.ERROR,
                outputStatus = if (outputFailure) LocalSpeechOutputStatus.ERROR else it.outputStatus,
                lastErrorCategory = error.category,
                errorMessage = error.userMessage
            )
        }
        appendDiagnostic("Fehlerkategorie: ${error.category.name}")
    }

    private fun isCurrentRecognition(generation: Long): Boolean =
        !released && recognitionActive && generation == recognitionGeneration

    private fun appendDiagnostic(message: String) {
        val safeMessage = message.trim().take(MAX_DIAGNOSTIC_CHARS)
        if (safeMessage.isBlank()) return
        _uiState.update { current ->
            if (current.diagnostics.lastOrNull() == safeMessage) current else current.copy(
                diagnostics = (current.diagnostics + safeMessage).takeLast(MAX_DIAGNOSTIC_ENTRIES)
            )
        }
    }

    companion object {
        const val TEST_SPEECH_TEXT = "Dies ist ein lokaler BamaVoice-Audiotest."
        private const val LANGUAGE_TAG = "de-DE"
        private const val MAX_TRANSCRIPT_CHARS = 500
        private const val MAX_DIAGNOSTIC_CHARS = 100
        private const val MAX_DIAGNOSTIC_ENTRIES = 40
        private const val MIN_SPEED = 0.5f
        private const val MAX_SPEED = 2f
        private const val MIN_PITCH = 0.8f
        private const val MAX_PITCH = 1.2f

        fun buildLocalTestResponse(transcript: String): String {
            val cleanTranscript = normalizeTranscript(transcript)
                .trimEnd('.', '!', '?')
            return "Ich habe verstanden: $cleanTranscript. Dies ist eine lokale Testantwort."
        }

        private fun normalizeTranscript(text: String): String = text
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_TRANSCRIPT_CHARS)

        private fun permissionFailure() = LocalAudioFailure(
            LocalAudioErrorCategory.PERMISSION_MISSING,
            "Mikrofonzugriff ist nicht erlaubt. Bitte erteile die Berechtigung über die sichtbare Startaktion."
        )
    }
}
