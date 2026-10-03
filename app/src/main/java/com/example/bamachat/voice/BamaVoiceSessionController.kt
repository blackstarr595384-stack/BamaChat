package com.example.bamachat.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
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
    private val realtimeEngine: RealtimeVoiceEngine = UnavailableRealtimeVoiceEngine(),
    private val diagnostics: VoiceDiagnostics = NoOpVoiceDiagnostics,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class QueuedSpeech(
        val messageId: String,
        val text: String
    )

    private val machine = VoiceSessionMachine()
    private val realtimeTurnsAccumulator = RealtimeTurnAccumulator(nowMillis)
    private val speechBuffer = StreamingSpeechBuffer()
    private val speechQueue = ArrayDeque<QueuedSpeech>()
    private val _uiState = MutableStateFlow(VoiceSessionUiState())
    val uiState: StateFlow<VoiceSessionUiState> = _uiState.asStateFlow()
    private val _finalTranscripts = MutableSharedFlow<VoiceFinalTranscript>(extraBufferCapacity = 4)
    val finalTranscripts: SharedFlow<VoiceFinalTranscript> = _finalTranscripts.asSharedFlow()
    private val _realtimeTurns = MutableSharedFlow<RealtimeFinalizedTurn>(extraBufferCapacity = 8)
    val realtimeTurns: SharedFlow<RealtimeFinalizedTurn> = _realtimeTurns.asSharedFlow()

    private var inputEngine = initialInputEngine
    private var outputEngine = initialOutputEngine
    private var configuration = VoiceSessionConfiguration()
    private var inputOperationJob: Job? = null
    private var finalTranscriptTimeoutJob: Job? = null
    private var speechJob: Job? = null
    private var liveSessionJob: Job? = null
    private var liveSessionTimeoutJob: Job? = null
    private var liveInactivityTimeoutJob: Job? = null
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
    private var liveSessionActive = false
    private var liveMicrophoneMuted = false
    private var liveSessionStartedAtMs: Long? = null
    private var liveSessionDurationLimitSeconds: Long? = null
    private var liveSessionGeneration = 0L
    private var activeRealtimeResponseId: String? = null
    private var realtimeUserTranscript = ""
    private var realtimeAssistantTranscript = ""
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
        val realtimeConfigurationChanged = configuration.mode != newConfiguration.mode ||
            configuration.realtimeVoice != newConfiguration.realtimeVoice ||
            configuration.realtimeTurnTaking != newConfiguration.realtimeTurnTaking ||
            configuration.realtimePersonaName != newConfiguration.realtimePersonaName
        if (providerChanged || (liveSessionActive && realtimeConfigurationChanged)) {
            stopAllInternal(interrupted = false)
        }

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
        if (configuration.mode == VoiceMode.LIVE) {
            if (liveSessionActive) toggleLiveMicrophone() else startLiveSession()
            return
        }
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
        if (configuration.mode == VoiceMode.LIVE) {
            if (liveSessionActive) beginLiveUserTurn() else startLiveSession()
            return
        }
        inputOperationJob?.cancel()
        inputOperationJob = scope.launch {
            startListeningInternal()
        }
    }

    fun finishListening() {
        if (released) return
        if (configuration.mode == VoiceMode.LIVE) {
            finishLiveUserTurn()
            return
        }
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
        if (configuration.mode == VoiceMode.LIVE) {
            scope.launch {
                realtimeEngine.mute(true)
                liveMicrophoneMuted = true
                machine.realtimeListening()
                publishState()
            }
            return
        }
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
        if (configuration.mode == VoiceMode.LIVE) return
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
        if (configuration.mode == VoiceMode.LIVE) return
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
        if (released || text.isBlank() || configuration.mode == VoiceMode.LIVE) return
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
        if (configuration.mode == VoiceMode.LIVE) {
            scope.launch { interruptLiveResponse() }
            return
        }
        scope.launch {
            stopSpeakingInternal(interrupted = interrupted, suppressCurrentMessage = interrupted)
        }
    }

    fun stopAll() {
        if (released) return
        scope.launch { stopAllInternal(interrupted = false) }
    }

    fun startLiveSession(personaName: String = configuration.realtimePersonaName) {
        if (released || liveSessionJob?.isActive == true || liveSessionActive) return
        liveSessionJob = scope.launch { startLiveSessionInternal(personaName) }
    }

    fun endLiveSession() {
        if (released) return
        scope.launch {
            endLiveSessionInternal(showEndedState = true)
            publishState()
        }
    }

    fun toggleLiveMicrophone() {
        if (!liveSessionActive) {
            startLiveSession()
            return
        }
        if (liveMicrophoneMuted) beginLiveUserTurn() else finishLiveUserTurn()
    }

    fun beginLiveUserTurn() {
        if (!liveSessionActive || released) return
        scope.launch {
            if (machine.state == VoiceSessionState.Speaking && configuration.interruptionEnabled) {
                interruptLiveResponse()
            }
            realtimeEngine.beginUserTurn()
            liveMicrophoneMuted = false
            machine.realtimeListening()
            publishState()
        }
    }

    fun finishLiveUserTurn() {
        if (!liveSessionActive || released) return
        scope.launch {
            realtimeEngine.finishUserTurn()
            liveMicrophoneMuted = true
            machine.thinking()
            publishState()
        }
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
        realtimeEngine.release()
        audioSession.deactivate()
        released = true
    }

    private suspend fun startListeningInternal() {
        if (configuration.mode == VoiceMode.LIVE) {
            startLiveSessionInternal()
            return
        }
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
        endLiveSessionInternal(showEndedState = false)
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

    private suspend fun startLiveSessionInternal(
        personaName: String = configuration.realtimePersonaName
    ) {
        if (configuration.mode != VoiceMode.LIVE) return
        if (!realtimeEngine.isAvailable) {
            machine.fail(
                VoiceFailure(
                    VoiceFailureCategory.UNSUPPORTED,
                    "Für Live-Unterhaltung muss zuerst der sichere BamaVoice-Server eingerichtet werden.",
                    recoverable = false
                )
            )
            publishState()
            return
        }
        inputOperationJob?.cancelAndJoin()
        cancelFinalTranscriptTimeout()
        runCatching { inputEngine.cancel() }
        stopSpeakingInternal(interrupted = false, suppressCurrentMessage = false)
        resetRealtimeSessionData()
        machine.connecting()
        publishState()
        val request = RealtimeVoiceSessionRequest(
            provider = "openai",
            model = OPENAI_REALTIME_MODEL,
            voice = configuration.realtimeVoice.storageValue,
            languageTag = configuration.languageTag,
            personaName = personaName.trim().ifBlank { configuration.realtimePersonaName },
            turnTaking = configuration.realtimeTurnTaking,
            noiseReduction = configuration.realtimeNoiseReduction,
            interruptResponse = configuration.interruptionEnabled
        )
        val sessionGeneration = ++liveSessionGeneration
        val result = realtimeEngine.start(
            request,
            RealtimeVoiceListener { event ->
                scope.launch { handleRealtimeEvent(sessionGeneration, event) }
            }
        )
        liveSessionJob = null
        if (result is VoiceOperationResult.Failure) {
            liveSessionActive = false
            machine.fail(result.error)
            publishState()
            diagnostics.event(
                "voice_realtime_start_failed",
                mapOf("category" to result.error.category.name.lowercase())
            )
        }
    }

    private suspend fun handleRealtimeEvent(
        sessionGeneration: Long,
        event: RealtimeVoiceEvent
    ) {
        if (released || configuration.mode != VoiceMode.LIVE || sessionGeneration != liveSessionGeneration) return
        when (event) {
            RealtimeVoiceEvent.Connecting -> machine.connecting()
            RealtimeVoiceEvent.Connected -> {
                liveSessionActive = true
                liveMicrophoneMuted = configuration.realtimeTurnTaking == RealtimeTurnTaking.PUSH_TO_TALK
                machine.realtimeListening()
            }
            is RealtimeVoiceEvent.SessionStarted -> {
                val nowSeconds = nowMillis() / 1_000L
                liveSessionStartedAtMs = nowMillis()
                liveSessionDurationLimitSeconds =
                    (event.sessionExpiresAtEpochSeconds - nowSeconds).coerceAtLeast(1L)
                scheduleLiveSessionTimeout(event.sessionExpiresAtEpochSeconds)
            }
            is RealtimeVoiceEvent.Reconnecting -> {
                liveSessionActive = false
                machine.reconnecting(event.attempt, event.maximumAttempts)
            }
            is RealtimeVoiceEvent.SpeechStarted -> {
                val wasSpeaking = machine.state == VoiceSessionState.Speaking
                realtimeUserTranscript = ""
                if (wasSpeaking && configuration.interruptionEnabled) {
                    val interruptionStartedAt = nowMillis()
                    realtimeEngine.interrupt()
                    activeRealtimeResponseId?.let(realtimeTurnsAccumulator::cancelAssistant)
                    activeRealtimeResponseId = null
                    realtimeAssistantTranscript = ""
                    diagnostics.timing(
                        "voice_realtime_interruption_to_cancel_sent",
                        (nowMillis() - interruptionStartedAt).coerceAtLeast(0L),
                        mapOf("mode" to VoiceMode.LIVE.storageValue)
                    )
                    machine.interrupted()
                }
                machine.realtimeListening()
            }
            RealtimeVoiceEvent.SpeechStopped -> machine.thinking()
            is RealtimeVoiceEvent.UserTranscriptDelta -> {
                realtimeUserTranscript = appendRealtimeTranscript(realtimeUserTranscript, event.delta)
                if (realtimeUserTranscript.isNotBlank()) {
                    machine.realtimeTranscribing(realtimeUserTranscript)
                }
            }
            is RealtimeVoiceEvent.UserTranscriptCompleted -> {
                val transcript = event.transcript.trim()
                realtimeUserTranscript = transcript
                realtimeTurnsAccumulator.finalizeUser(event.itemId, transcript)?.let {
                    _realtimeTurns.emit(it)
                }
                machine.thinking()
            }
            is RealtimeVoiceEvent.ResponseCreated -> {
                activeRealtimeResponseId = event.responseId
                realtimeTurnsAccumulator.beginAssistant(event.responseId)
                realtimeAssistantTranscript = ""
                machine.thinking()
            }
            is RealtimeVoiceEvent.AssistantTranscriptDelta -> {
                if (activeRealtimeResponseId == null) activeRealtimeResponseId = event.responseId
                val updatedTranscript = realtimeTurnsAccumulator.appendAssistant(event.responseId, event.delta)
                if (activeRealtimeResponseId == event.responseId && updatedTranscript != null) {
                    realtimeAssistantTranscript = updatedTranscript
                    machine.speaking()
                }
            }
            is RealtimeVoiceEvent.AssistantTranscriptCompleted -> {
                if (activeRealtimeResponseId == null) activeRealtimeResponseId = event.responseId
                val completedTranscript = realtimeTurnsAccumulator.completeAssistantTranscript(
                    event.responseId,
                    event.transcript
                )
                if (activeRealtimeResponseId == event.responseId && completedTranscript != null) {
                    realtimeAssistantTranscript = completedTranscript
                }
            }
            is RealtimeVoiceEvent.ResponseCompleted -> {
                realtimeTurnsAccumulator.finalizeAssistant(event.responseId)?.let {
                    _realtimeTurns.emit(it)
                }
                if (activeRealtimeResponseId == event.responseId) {
                    activeRealtimeResponseId = null
                    realtimeAssistantTranscript = ""
                    machine.realtimeListening()
                }
            }
            is RealtimeVoiceEvent.ResponseCancelled -> {
                realtimeTurnsAccumulator.cancelAssistant(event.responseId)
                if (activeRealtimeResponseId == event.responseId) {
                    activeRealtimeResponseId = null
                    realtimeAssistantTranscript = ""
                    machine.interrupted()
                    machine.realtimeListening()
                }
            }
            is RealtimeVoiceEvent.Failure -> {
                endLiveSessionInternal(showEndedState = false)
                machine.fail(event.error)
            }
            RealtimeVoiceEvent.Closed -> {
                endLiveSessionInternal(showEndedState = false)
                machine.ended()
            }
        }
        if (event.refreshesLiveInactivityTimeout()) scheduleLiveInactivityTimeout()
        publishState()
    }

    private suspend fun interruptLiveResponse() {
        if (!liveSessionActive) return
        val interruptedResponseId = activeRealtimeResponseId
        realtimeEngine.interrupt()
        interruptedResponseId?.let(realtimeTurnsAccumulator::cancelAssistant)
        activeRealtimeResponseId = null
        realtimeAssistantTranscript = ""
        machine.interrupted()
        publishState()
        machine.realtimeListening()
        publishState()
    }

    private suspend fun endLiveSessionInternal(showEndedState: Boolean) {
        val hadLiveSession = liveSessionActive || activeRealtimeResponseId != null ||
            liveSessionStartedAtMs != null || machine.state == VoiceSessionState.Connecting ||
            machine.state is VoiceSessionState.Reconnecting
        val currentJob = currentCoroutineContext()[Job]
        liveSessionJob?.takeUnless { it === currentJob }?.cancel()
        liveSessionJob = null
        liveSessionGeneration += 1L
        liveSessionTimeoutJob?.takeUnless { it === currentJob }?.cancel()
        liveSessionTimeoutJob = null
        liveInactivityTimeoutJob?.takeUnless { it === currentJob }?.cancel()
        liveInactivityTimeoutJob = null
        if (hadLiveSession) {
            realtimeEngine.stop()
        }
        liveSessionActive = false
        liveMicrophoneMuted = true
        liveSessionStartedAtMs = null
        liveSessionDurationLimitSeconds = null
        activeRealtimeResponseId = null
        realtimeUserTranscript = ""
        realtimeAssistantTranscript = ""
        realtimeTurnsAccumulator.reset()
        if (showEndedState && hadLiveSession) machine.ended()
    }

    private fun scheduleLiveSessionTimeout(expiresAtEpochSeconds: Long) {
        liveSessionTimeoutJob?.cancel()
        val delayMs = (expiresAtEpochSeconds * 1_000L - nowMillis()).coerceAtLeast(1_000L)
        liveSessionTimeoutJob = scope.launch {
            delay(delayMs)
            endLiveSessionInternal(showEndedState = true)
            publishState()
        }
    }

    private fun scheduleLiveInactivityTimeout() {
        if (liveSessionStartedAtMs == null) return
        liveInactivityTimeoutJob?.cancel()
        liveInactivityTimeoutJob = scope.launch {
            delay(LIVE_INACTIVITY_TIMEOUT_MS)
            endLiveSessionInternal(showEndedState = true)
            publishState()
            diagnostics.event(
                "voice_realtime_inactivity_disconnect",
                mapOf("mode" to VoiceMode.LIVE.storageValue)
            )
        }
    }

    private fun RealtimeVoiceEvent.refreshesLiveInactivityTimeout(): Boolean = when (this) {
        RealtimeVoiceEvent.Connecting,
        is RealtimeVoiceEvent.Reconnecting,
        is RealtimeVoiceEvent.Failure,
        RealtimeVoiceEvent.Closed -> false
        else -> true
    }

    private fun resetRealtimeSessionData() {
        realtimeTurnsAccumulator.reset()
        activeRealtimeResponseId = null
        realtimeUserTranscript = ""
        realtimeAssistantTranscript = ""
        liveSessionStartedAtMs = null
        liveSessionDurationLimitSeconds = null
        liveMicrophoneMuted = false
    }

    private fun appendRealtimeTranscript(current: String, delta: String): String {
        if (delta.isBlank()) return current
        return (current + delta).take(MAX_REALTIME_TRANSCRIPT_CHARS)
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
            inputProvider = if (configuration.mode == VoiceMode.LIVE) {
                VoiceInputProvider.OPENAI_TRANSCRIPTION
            } else {
                inputEngine.provider
            },
            outputProvider = if (configuration.mode == VoiceMode.LIVE) {
                VoiceOutputProvider.OPENAI_LIVE
            } else {
                outputEngine.provider
            },
            selectedVoiceLabel = if (configuration.mode == VoiceMode.LIVE) {
                configuration.realtimeVoice.displayName
            } else {
                configuration.selectedVoiceLabel
            },
            connectionLabel = connectionLabel(machine.state),
            privacyLabel = privacyLabel(),
            realtimeProviderLabel = realtimeEngine.providerLabel,
            realtimeTransportStatusLabel = if (liveSessionActive) {
                realtimeEngine.connectedStatusLabel
            } else {
                realtimeEngine.disconnectedStatusLabel
            },
            realtimeAvailable = realtimeEngine.isAvailable,
            liveSessionActive = liveSessionActive,
            microphoneMuted = liveMicrophoneMuted,
            secureConnection = liveSessionActive,
            sessionStartedAtEpochMillis = liveSessionStartedAtMs,
            sessionDurationLimitSeconds = liveSessionDurationLimitSeconds,
            partialTranscript = if (configuration.mode == VoiceMode.LIVE) realtimeUserTranscript else _uiState.value.partialTranscript,
            assistantTranscript = if (configuration.mode == VoiceMode.LIVE) realtimeAssistantTranscript else _uiState.value.assistantTranscript
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
        configuration.mode == VoiceMode.LIVE -> realtimeEngine.privacyLabel
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
        VoiceSessionState.Connecting -> "Sichere Verbindung wird hergestellt …"
        is VoiceSessionState.Reconnecting -> "Verbindung wird wiederhergestellt …"
        VoiceSessionState.Listening -> "Ich höre zu …"
        is VoiceSessionState.Transcribing -> "Transkription läuft"
        VoiceSessionState.Thinking -> "BamaFlow denkt …"
        VoiceSessionState.Speaking -> "BamaFlow spricht"
        VoiceSessionState.Interrupted -> "Sprachausgabe unterbrochen"
        VoiceSessionState.Ended -> "Live-Unterhaltung beendet"
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
        private const val OPENAI_REALTIME_MODEL = "gpt-realtime"
        private const val MAX_REALTIME_TRANSCRIPT_CHARS = 32_000
        private const val LIVE_INACTIVITY_TIMEOUT_MS = 3 * 60 * 1_000L
    }
}
