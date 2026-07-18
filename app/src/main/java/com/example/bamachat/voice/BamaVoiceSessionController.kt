package com.example.bamachat.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class BamaVoiceSessionController(
    private val scope: CoroutineScope,
    initialInputEngine: SpeechToTextEngine,
    initialOutputEngine: SpeechOutputEngine,
    private val audioSession: VoiceAudioSession,
    private val diagnostics: VoiceDiagnostics = NoOpVoiceDiagnostics,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class QueuedSpeech(
        val messageId: String,
        val text: String
    )

    private val machine = VoiceSessionMachine()
    private val speechBuffer = StreamingSpeechBuffer()
    private val speechQueue = ArrayDeque<QueuedSpeech>()
    private val _uiState = MutableStateFlow(VoiceSessionUiState())
    val uiState: StateFlow<VoiceSessionUiState> = _uiState.asStateFlow()
    private val _finalTranscripts = MutableSharedFlow<VoiceFinalTranscript>(extraBufferCapacity = 4)
    val finalTranscripts: SharedFlow<VoiceFinalTranscript> = _finalTranscripts.asSharedFlow()

    private var inputEngine = initialInputEngine
    private var outputEngine = initialOutputEngine
    private var configuration = VoiceSessionConfiguration()
    private var inputOperationJob: Job? = null
    private var finalTranscriptTimeoutJob: Job? = null
    private var speechJob: Job? = null
    private var currentAssistantMessageId: String? = null
    private var suppressedAssistantMessageId: String? = null
    private var assistantStreaming = false
    private var expectAssistantSpeech = false
    private var resumeHandsFreeAfterTurn = false
    private var lastMicActionAtMs: Long? = null
    private var microphoneRequestedAtMs: Long? = null
    private var speechEndedAtMs: Long? = null
    private var thinkingStartedAtMs: Long? = null
    private var firstAiTokenAtMs: Long? = null
    private var firstAudioStartedAtMs: Long? = null
    private var released = false

    init {
        publishState()
    }

    suspend fun updateConfiguration(
        newConfiguration: VoiceSessionConfiguration,
        replacementInputEngine: SpeechToTextEngine? = null,
        replacementOutputEngine: SpeechOutputEngine? = null
    ) {
        if (released) return
        val providerChanged = replacementInputEngine != null || replacementOutputEngine != null
        if (providerChanged) stopAllInternal(interrupted = false)

        replacementInputEngine?.let { replacement ->
            if (replacement !== inputEngine) {
                inputEngine.release()
                inputEngine = replacement
            }
        }
        replacementOutputEngine?.let { replacement ->
            if (replacement !== outputEngine) {
                outputEngine.release()
                outputEngine = replacement
            }
        }
        configuration = newConfiguration
        publishState()
        if (providerChanged) {
            diagnostics.event(
                "voice_provider_changed",
                mapOf(
                    "input" to inputEngine.provider.storageValue,
                    "output" to outputEngine.provider.storageValue,
                    "mode" to configuration.mode.storageValue
                )
            )
        }
    }

    fun toggleListening() {
        if (released || isDebouncedMicAction()) return
        when (machine.state) {
            VoiceSessionState.Preparing,
            VoiceSessionState.Listening,
            is VoiceSessionState.Transcribing -> finishListening()
            else -> {
                inputOperationJob?.cancel()
                inputOperationJob = scope.launch { startListeningInternal() }
            }
        }
    }

    fun startListening() {
        if (released || isDebouncedMicAction()) return
        inputOperationJob?.cancel()
        inputOperationJob = scope.launch {
            startListeningInternal()
        }
    }

    fun finishListening() {
        if (released) return
        inputOperationJob?.cancel()
        inputOperationJob = scope.launch {
            val sessionId = machine.activeSessionId ?: return@launch
            machine.awaitingFinal(sessionId)
            speechEndedAtMs = nowMillis()
            publishState()
            runCatching { inputEngine.finish() }
                .onFailure { fail(temporaryFailure("Spracherkennung konnte nicht beendet werden.")) }
            if (machine.activeSessionId == sessionId) scheduleFinalTranscriptTimeout(sessionId)
        }
    }

    fun cancelListening() {
        if (released) return
        inputOperationJob?.cancel()
        inputOperationJob = scope.launch {
            cancelFinalTranscriptTimeout()
            runCatching { inputEngine.cancel() }
            machine.idle()
            resetInputUi()
            audioSession.deactivate()
            publishState()
            diagnostics.event("voice_input_cancelled", providerAttributes())
        }
    }

    fun recoverFromError() {
        if (machine.state is VoiceSessionState.Error) {
            machine.idle()
            publishState()
        }
    }

    fun reportPermissionDenied(permanentlyDenied: Boolean) {
        scope.launch {
            fail(
                VoiceFailure(
                    VoiceFailureCategory.PERMISSION_DENIED,
                    if (permanentlyDenied) {
                        "Mikrofonzugriff ist dauerhaft deaktiviert. Öffne die App-Einstellungen."
                    } else {
                        "Mikrofonzugriff wurde nicht erlaubt."
                    }
                )
            )
        }
    }

    fun markTranscriptHandled(accepted: Boolean) {
        if (!accepted) {
            machine.idle()
            expectAssistantSpeech = false
            resumeHandsFreeAfterTurn = false
            publishState()
            return
        }
        thinkingStartedAtMs = nowMillis()
        firstAiTokenAtMs = null
        firstAudioStartedAtMs = null
        expectAssistantSpeech = configuration.autoPlayback
        resumeHandsFreeAfterTurn = configuration.handsFree
        machine.thinking()
        publishState()
    }

    fun markTextMessageAccepted() {
        if (!configuration.autoPlayback) return
        thinkingStartedAtMs = nowMillis()
        firstAiTokenAtMs = null
        firstAudioStartedAtMs = null
        expectAssistantSpeech = true
        resumeHandsFreeAfterTurn = false
        machine.thinking()
        publishState()
    }

    fun onAssistantTextChanged(
        messageId: String,
        text: String,
        isStreaming: Boolean
    ) {
        if (released || text.isBlank()) return
        currentAssistantMessageId = messageId
        assistantStreaming = isStreaming
        _uiState.value = _uiState.value.copy(assistantTranscript = text)

        if (thinkingStartedAtMs != null && firstAiTokenAtMs == null) {
            firstAiTokenAtMs = nowMillis()
            diagnostics.timing(
                "voice_transcript_to_first_ai_token",
                (firstAiTokenAtMs!! - thinkingStartedAtMs!!).coerceAtLeast(0L),
                providerAttributes()
            )
        }

        val messageIsSuppressed = suppressedAssistantMessageId == messageId
        if (!expectAssistantSpeech || messageIsSuppressed) {
            if (!isStreaming && !messageIsSuppressed && speechJob == null && machine.state == VoiceSessionState.Thinking) {
                machine.idle()
                publishState()
                maybeResumeHandsFree()
            }
            return
        }

        val chunks = speechBuffer.consume(messageId, text, isFinal = !isStreaming)
        chunks.forEach { chunk -> speechQueue.addLast(QueuedSpeech(messageId, chunk)) }
        if (chunks.isNotEmpty()) startSpeechQueue()

        if (!isStreaming && speechQueue.isEmpty() && speechJob == null) {
            machine.idle()
            expectAssistantSpeech = false
            publishState()
            maybeResumeHandsFree()
        }
    }

    fun speakFullMessage(messageId: String, text: String) {
        if (released) return
        val chunks = VoiceTextProcessor.splitCompleteText(text)
        if (chunks.isEmpty()) return
        scope.launch {
            stopSpeakingInternal(interrupted = false, suppressCurrentMessage = false)
            suppressedAssistantMessageId = null
            currentAssistantMessageId = messageId
            expectAssistantSpeech = false
            chunks.forEach { chunk -> speechQueue.addLast(QueuedSpeech(messageId, chunk)) }
            startSpeechQueue()
        }
    }

    fun preview(text: String) {
        speakFullMessage(PREVIEW_MESSAGE_ID, text)
    }

    fun stopSpeaking(interrupted: Boolean = true) {
        if (released) return
        scope.launch {
            stopSpeakingInternal(interrupted = interrupted, suppressCurrentMessage = interrupted)
        }
    }

    fun stopAll() {
        if (released) return
        scope.launch { stopAllInternal(interrupted = false) }
    }

    fun leaveScreen() {
        if (released) return
        scope.launch { stopAllInternal(interrupted = false) }
    }

    suspend fun release() {
        if (released) return
        stopAllInternal(interrupted = false)
        inputEngine.release()
        outputEngine.release()
        audioSession.deactivate()
        released = true
    }

    private suspend fun startListeningInternal() {
        if (!inputEngine.isAvailable()) {
            fail(
                VoiceFailure(
                    VoiceFailureCategory.UNSUPPORTED,
                    if (configuration.mode == VoiceMode.LOCAL) {
                        "Lokale Spracherkennung ist auf diesem Gerät nicht verfügbar."
                    } else {
                        "Spracherkennung ist auf diesem Gerät nicht verfügbar."
                    }
                )
            )
            return
        }

        if (
            machine.state == VoiceSessionState.Speaking ||
            machine.state == VoiceSessionState.Thinking ||
            speechJob != null ||
            expectAssistantSpeech
        ) {
            stopSpeakingInternal(interrupted = true, suppressCurrentMessage = true)
        }

        val sessionId = machine.beginPreparing() ?: return
        resetInputUi()
        microphoneRequestedAtMs = nowMillis()
        publishState()

        val focusResult = audioSession.activate(VoiceAudioPurpose.LISTENING) {
            scope.launch { handleAudioFocusLoss() }
        }
        if (focusResult is VoiceOperationResult.Failure) {
            fail(focusResult.error)
            return
        }

        val listener = object : SpeechInputListener {
            override fun onReady() {
                scope.launch {
                    if (!machine.listening(sessionId)) return@launch
                    microphoneRequestedAtMs?.let { startedAt ->
                        diagnostics.timing(
                            "voice_microphone_tap_to_listening",
                            (nowMillis() - startedAt).coerceAtLeast(0L),
                            providerAttributes()
                        )
                    }
                    publishState()
                }
            }

            override fun onInputLevel(level: Float) {
                scope.launch {
                    if (machine.activeSessionId == sessionId) {
                        _uiState.value = _uiState.value.copy(inputLevel = level.coerceIn(0f, 1f))
                    }
                }
            }

            override fun onPartialTranscript(text: String) {
                scope.launch {
                    if (machine.partial(sessionId, text)) {
                        _uiState.value = _uiState.value.copy(partialTranscript = text.trim())
                        publishState()
                    }
                }
            }

            override fun onSpeechEnded() {
                scope.launch {
                    if (machine.awaitingFinal(sessionId)) {
                        speechEndedAtMs = nowMillis()
                        publishState()
                        scheduleFinalTranscriptTimeout(sessionId)
                    }
                }
            }

            override fun onFinalTranscript(text: String) {
                scope.launch {
                    val finalTranscript = machine.finalTranscript(sessionId, text) ?: return@launch
                    cancelFinalTranscriptTimeout()
                    speechEndedAtMs?.let { endedAt ->
                        diagnostics.timing(
                            "voice_speech_end_to_final_transcript",
                            (nowMillis() - endedAt).coerceAtLeast(0L),
                            providerAttributes()
                        )
                    }
                    audioSession.deactivate()
                    _uiState.value = _uiState.value.copy(
                        partialTranscript = "",
                        finalTranscript = finalTranscript.text,
                        inputLevel = 0f
                    )
                    publishState()
                    _finalTranscripts.tryEmit(finalTranscript)
                    diagnostics.event("voice_transcription_success", providerAttributes())
                }
            }

            override fun onFailure(error: VoiceFailure) {
                scope.launch {
                    if (machine.activeSessionId == sessionId) fail(error)
                }
            }
        }

        val inputConfig = SpeechInputConfig(
            languageTag = configuration.languageTag,
            automaticLanguageDetection = configuration.automaticLanguageDetection,
            silenceTimeoutMs = configuration.silenceTimeoutMs,
            requireOnDevice = configuration.mode == VoiceMode.LOCAL
        )
        val result = withTimeoutOrNull(INPUT_START_TIMEOUT_MS) {
            inputEngine.startStreaming(inputConfig, listener)
        } ?: VoiceOperationResult.Failure(
            VoiceFailure(VoiceFailureCategory.TIMEOUT, "Spracherkennung hat zu lange zum Starten gebraucht.")
        )
        if (result is VoiceOperationResult.Failure) fail(result.error)
    }

    private fun startSpeechQueue() {
        if (speechJob?.isActive == true || speechQueue.isEmpty()) return
        speechJob = scope.launch {
            val focusResult = audioSession.activate(VoiceAudioPurpose.SPEAKING) {
                scope.launch { handleAudioFocusLoss() }
            }
            if (focusResult is VoiceOperationResult.Failure) {
                fail(focusResult.error)
                speechQueue.clear()
                speechJob = null
                return@launch
            }

            try {
                while (speechQueue.isNotEmpty()) {
                    val queued = speechQueue.removeFirst()
                    if (queued.messageId == suppressedAssistantMessageId) continue
                    _uiState.value = _uiState.value.copy(activeOutputMessageId = queued.messageId)
                    machine.speaking()
                    publishState()
                    val result = try {
                        withTimeout(OUTPUT_CHUNK_TIMEOUT_MS) {
                            outputEngine.speak(
                                SpeechOutputRequest(
                                    text = queued.text,
                                    languageTag = configuration.languageTag,
                                    speed = configuration.speechSpeed,
                                    pitch = configuration.speechPitch
                                ),
                                object : SpeechOutputListener {
                                    override fun onPlaybackStarted() {
                                        if (firstAudioStartedAtMs == null) {
                                            firstAudioStartedAtMs = nowMillis()
                                            firstAiTokenAtMs?.let { firstTokenAt ->
                                                diagnostics.timing(
                                                    "voice_first_ai_token_to_first_audio",
                                                    (firstAudioStartedAtMs!! - firstTokenAt).coerceAtLeast(0L),
                                                    providerAttributes()
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    } catch (_: TimeoutCancellationException) {
                        VoiceOperationResult.Failure(
                            VoiceFailure(VoiceFailureCategory.TIMEOUT, "Sprachausgabe hat zu lange gebraucht.")
                        )
                    }
                    if (result is VoiceOperationResult.Failure) {
                        diagnostics.event(
                            "voice_output_failed",
                            providerAttributes() + ("category" to result.error.category.name.lowercase())
                        )
                        fail(result.error)
                        speechQueue.clear()
                        break
                    }
                    diagnostics.event("voice_output_chunk_success", providerAttributes())
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                audioSession.deactivate()
                speechJob = null
                if (machine.state == VoiceSessionState.Speaking) {
                    if (assistantStreaming && expectAssistantSpeech) machine.thinking() else machine.idle()
                    publishState()
                }
                if (machine.state != VoiceSessionState.Speaking) {
                    _uiState.value = _uiState.value.copy(activeOutputMessageId = null)
                }
                if (!assistantStreaming && speechQueue.isEmpty()) {
                    expectAssistantSpeech = false
                    maybeResumeHandsFree()
                }
            }
        }
    }

    private suspend fun stopSpeakingInternal(
        interrupted: Boolean,
        suppressCurrentMessage: Boolean
    ) {
        val interruptionStartedAt = nowMillis()
        val hadActiveOutput = speechJob != null || speechQueue.isNotEmpty() ||
            machine.state == VoiceSessionState.Speaking || machine.state == VoiceSessionState.Thinking ||
            expectAssistantSpeech
        if (suppressCurrentMessage) suppressedAssistantMessageId = currentAssistantMessageId
        speechQueue.clear()
        val runningJob = speechJob
        speechJob = null
        runningJob?.cancelAndJoin()
        withTimeoutOrNull(OUTPUT_STOP_TIMEOUT_MS) { outputEngine.stop() }
        audioSession.deactivate()
        expectAssistantSpeech = false
        resumeHandsFreeAfterTurn = false
        speechBuffer.reset()
        if (interrupted) machine.interrupted() else machine.idle()
        _uiState.value = _uiState.value.copy(activeOutputMessageId = null)
        publishState()
        if (interrupted && hadActiveOutput) {
            diagnostics.timing(
                "voice_interruption_to_playback_stopped",
                (nowMillis() - interruptionStartedAt).coerceAtLeast(0L),
                providerAttributes()
            )
            diagnostics.event("voice_output_interrupted", providerAttributes())
        }
    }

    private suspend fun stopAllInternal(interrupted: Boolean) {
        inputOperationJob?.cancelAndJoin()
        inputOperationJob = null
        cancelFinalTranscriptTimeout()
        runCatching { inputEngine.cancel() }
        stopSpeakingInternal(interrupted = interrupted, suppressCurrentMessage = false)
        machine.idle()
        resetInputUi()
        currentAssistantMessageId = null
        suppressedAssistantMessageId = null
        assistantStreaming = false
        expectAssistantSpeech = false
        resumeHandsFreeAfterTurn = false
        publishState()
    }

    private suspend fun handleAudioFocusLoss() {
        runCatching { inputEngine.cancel() }
        stopSpeakingInternal(interrupted = true, suppressCurrentMessage = true)
        machine.fail(
            VoiceFailure(
                VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                "Audio wurde von einer anderen App oder einem Anruf übernommen."
            )
        )
        resetInputUi()
        publishState()
        diagnostics.event("voice_audio_focus_lost", providerAttributes())
    }

    private suspend fun fail(error: VoiceFailure) {
        cancelFinalTranscriptTimeout()
        runCatching { inputEngine.cancel() }
        audioSession.deactivate()
        machine.fail(error)
        resetInputUi(clearFinal = false)
        publishState()
        diagnostics.event(
            "voice_session_failed",
            providerAttributes() + ("category" to error.category.name.lowercase())
        )
    }

    private fun maybeResumeHandsFree() {
        if (!resumeHandsFreeAfterTurn || released) return
        resumeHandsFreeAfterTurn = false
        inputOperationJob?.cancel()
        inputOperationJob = scope.launch { startListeningInternal() }
    }

    private fun scheduleFinalTranscriptTimeout(sessionId: Long) {
        finalTranscriptTimeoutJob?.cancel()
        val timeoutMs = (configuration.silenceTimeoutMs + FINAL_RESULT_GRACE_MS)
            .coerceIn(MIN_FINAL_RESULT_TIMEOUT_MS, MAX_FINAL_RESULT_TIMEOUT_MS)
        finalTranscriptTimeoutJob = scope.launch {
            delay(timeoutMs)
            finalTranscriptTimeoutJob = null
            if (machine.activeSessionId == sessionId) {
                fail(
                    VoiceFailure(
                        VoiceFailureCategory.TIMEOUT,
                        "Die Spracherkennung hat kein finales Ergebnis geliefert. Bitte erneut versuchen."
                    )
                )
            }
        }
    }

    private fun cancelFinalTranscriptTimeout() {
        finalTranscriptTimeoutJob?.cancel()
        finalTranscriptTimeoutJob = null
    }

    private fun publishState() {
        _uiState.value = _uiState.value.copy(
            state = machine.state,
            mode = configuration.mode,
            inputProvider = inputEngine.provider,
            outputProvider = outputEngine.provider,
            selectedVoiceLabel = configuration.selectedVoiceLabel,
            connectionLabel = connectionLabel(machine.state),
            privacyLabel = privacyLabel(),
            realtimeAvailable = false
        )
    }

    private fun resetInputUi(clearFinal: Boolean = true) {
        _uiState.value = _uiState.value.copy(
            inputLevel = 0f,
            partialTranscript = "",
            finalTranscript = if (clearFinal) "" else _uiState.value.finalTranscript
        )
    }

    private fun privacyLabel(): String = when {
        configuration.mode == VoiceMode.LOCAL && outputEngine.provider == VoiceOutputProvider.PIPER ->
            "Keine Cloud-Voice-Anfrage; bereinigter Sprachtext geht nur an den privaten Piper-Endpunkt."
        configuration.mode == VoiceMode.LOCAL -> "Voice-Verarbeitung nutzt nur verfügbare On-Device-Komponenten."
        inputEngine.provider == VoiceInputProvider.OPENAI_TRANSCRIPTION -> "Audio wird zur Transkription an OpenAI übertragen."
        outputEngine.provider == VoiceOutputProvider.ELEVENLABS -> "Sprachtext wird zur Ausgabe an ElevenLabs übertragen."
        outputEngine.provider == VoiceOutputProvider.PIPER -> "Sprachtext wird an den konfigurierten Piper-Endpunkt übertragen."
        else -> "Android verarbeitet Spracheingabe und Sprachausgabe; Gerätefunktionen können systemabhängig sein."
    }

    private fun connectionLabel(state: VoiceSessionState): String = when (state) {
        VoiceSessionState.Idle -> "Bereit"
        VoiceSessionState.Preparing -> "Mikrofon wird vorbereitet"
        VoiceSessionState.Listening -> "Ich höre zu …"
        is VoiceSessionState.Transcribing -> "Transkription läuft"
        VoiceSessionState.Thinking -> "BamaChat denkt …"
        VoiceSessionState.Speaking -> "BamaChat spricht"
        VoiceSessionState.Interrupted -> "Sprachausgabe unterbrochen"
        is VoiceSessionState.Error -> "Aktion erforderlich"
    }

    private fun providerAttributes(): Map<String, String> = mapOf(
        "mode" to configuration.mode.storageValue,
        "input" to inputEngine.provider.storageValue,
        "output" to outputEngine.provider.storageValue
    )

    private fun isDebouncedMicAction(): Boolean {
        val now = nowMillis()
        val previousActionAt = lastMicActionAtMs
        if (previousActionAt != null && now - previousActionAt < MIC_DEBOUNCE_MS) return true
        lastMicActionAtMs = now
        return false
    }

    private fun temporaryFailure(message: String) = VoiceFailure(
        VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
        message
    )

    companion object {
        private const val MIC_DEBOUNCE_MS = 280L
        private const val INPUT_START_TIMEOUT_MS = 2_500L
        private const val OUTPUT_CHUNK_TIMEOUT_MS = 60_000L
        private const val OUTPUT_STOP_TIMEOUT_MS = 500L
        private const val FINAL_RESULT_GRACE_MS = 3_000L
        private const val MIN_FINAL_RESULT_TIMEOUT_MS = 4_000L
        private const val MAX_FINAL_RESULT_TIMEOUT_MS = 8_000L
        private const val PREVIEW_MESSAGE_ID = "voice-preview"
    }
}
