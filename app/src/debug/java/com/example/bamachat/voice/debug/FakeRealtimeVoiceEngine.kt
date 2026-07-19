package com.example.bamachat.voice.debug

import com.example.bamachat.voice.RealtimeVoiceEngine
import com.example.bamachat.voice.RealtimeVoiceEvent
import com.example.bamachat.voice.RealtimeVoiceListener
import com.example.bamachat.voice.RealtimeVoiceSessionRequest
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeRealtimeVoiceEngine(
    private val repository: DebugVoiceScenarioRepository,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val stepDelay: suspend (Long) -> Unit = { delay(it) },
    private val engineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : RealtimeVoiceEngine {
    override val isAvailable: Boolean
        get() = repository.state.value.fakeEnabled
    override val providerLabel: String = "Lokale BamaVoice-Simulation"
    override val privacyLabel: String = "Lokale Debug-Simulation: Es werden keine Audio- oder Providerdaten übertragen."
    override val connectedStatusLabel: String = "Lokale Simulation aktiv"
    override val disconnectedStatusLabel: String = "Lokale Simulation bereit"

    private val lifecycleMutex = Mutex()
    private var listener: RealtimeVoiceListener? = null
    private var scenarioJob: Job? = null
    private var networkJob: Job? = null
    private var activeSessionId: Long? = null
    private var activeResponseId: String? = null
    private var awaitingInterruption: CompletableDeferred<Unit>? = null
    private var nextSessionId = 1L
    private var released = false

    init {
        engineScope.launch {
            repository.commands.collect { command ->
                when (command) {
                    DebugVoiceCommand.NetworkFailure -> triggerNetworkFailure()
                }
            }
        }
    }

    override suspend fun start(
        request: RealtimeVoiceSessionRequest,
        listener: RealtimeVoiceListener
    ): VoiceOperationResult = lifecycleMutex.withLock {
        if (released) return@withLock failure("Die lokale Voice-Simulation wurde bereits freigegeben.")
        if (!isAvailable) {
            return@withLock VoiceOperationResult.Failure(
                VoiceFailure(
                    VoiceFailureCategory.UNSUPPORTED,
                    "Die lokale Voice-Simulation ist deaktiviert."
                )
            )
        }
        if (activeSessionId != null) return@withLock VoiceOperationResult.Success

        val scenario = repository.state.value.selectedScenario
        repository.recordStart()
        scenario.startFailure?.let { error ->
            repository.recordTransportStatus("Simulierter Startfehler: ${error.category.name}")
            return@withLock VoiceOperationResult.Failure(error)
        }

        val sessionId = nextSessionId++
        val delayMs = repository.state.value.selectedDelay.delayMs
        activeSessionId = sessionId
        this.listener = listener
        repository.recordTransportStatus("Fake-Session $sessionId vorbereitet")
        scenarioJob = engineScope.launch {
            runScenario(
                sessionId = sessionId,
                scenario = scenario,
                delayMs = delayMs,
                sessionExpiresAtEpochSeconds = nowEpochSeconds() + FAKE_SESSION_DURATION_SECONDS
            )
        }
        VoiceOperationResult.Success
    }

    override suspend fun mute(muted: Boolean) {
        repository.recordTransportStatus(if (muted) "Fake-Mikrofon stumm" else "Fake-Mikrofon aktiv")
    }

    override suspend fun beginUserTurn() {
        emit(RealtimeVoiceEvent.SpeechStarted(MANUAL_USER_ITEM_ID))
    }

    override suspend fun finishUserTurn() {
        emit(RealtimeVoiceEvent.SpeechStopped)
    }

    override suspend fun interrupt() {
        val responseId = activeResponseId
        if (responseId != null) emit(RealtimeVoiceEvent.ResponseCancelled(responseId))
        repository.recordTransportStatus("Simulierte Ausgabe unterbrochen")
        awaitingInterruption?.complete(Unit)
    }

    override suspend fun stop() {
        lifecycleMutex.withLock { stopCurrentSession() }
    }

    override suspend fun release() {
        lifecycleMutex.withLock {
            if (released) return@withLock
            stopCurrentSession()
            released = true
        }
        engineScope.cancel()
    }

    private suspend fun runScenario(
        sessionId: Long,
        scenario: FakeRealtimeScenario,
        delayMs: Long,
        sessionExpiresAtEpochSeconds: Long
    ) {
        for (step in scenario.steps(sessionExpiresAtEpochSeconds)) {
            if (activeSessionId != sessionId) return
            if (delayMs > 0L) stepDelay(delayMs)
            when (step) {
                is FakeRealtimeScenarioStep.Emit -> emit(step.event)
                is FakeRealtimeScenarioStep.Record -> repository.recordTransportStatus(step.label)
                FakeRealtimeScenarioStep.AwaitInterruption -> {
                    repository.recordTransportStatus("Warte auf Barge-in")
                    val interruption = CompletableDeferred<Unit>()
                    awaitingInterruption = interruption
                    interruption.await()
                    awaitingInterruption = null
                }
            }
        }
        if (activeSessionId == sessionId) finishSession(sessionId)
    }

    private fun emit(event: RealtimeVoiceEvent) {
        when (event) {
            is RealtimeVoiceEvent.ResponseCreated -> activeResponseId = event.responseId
            is RealtimeVoiceEvent.ResponseCompleted,
            is RealtimeVoiceEvent.ResponseCancelled -> activeResponseId = null
            else -> Unit
        }
        repository.recordEvent(event)
        listener?.onEvent(event)
    }

    private fun triggerNetworkFailure() {
        val sessionId = activeSessionId ?: return
        scenarioJob?.cancel()
        scenarioJob = null
        networkJob?.cancel()
        networkJob = engineScope.launch {
            repository.recordTransportStatus("Transport getrennt")
            emit(RealtimeVoiceEvent.Reconnecting(1, MAX_RECONNECT_ATTEMPTS))
            val delayMs = repository.state.value.selectedDelay.delayMs
            if (delayMs > 0L) stepDelay(delayMs)
            emit(
                RealtimeVoiceEvent.Failure(
                    VoiceFailure(VoiceFailureCategory.OFFLINE, "Der simulierte Netzwerkpfad wurde getrennt.")
                )
            )
            finishSession(sessionId)
        }
    }

    private fun stopCurrentSession() {
        scenarioJob?.cancel()
        scenarioJob = null
        networkJob?.cancel()
        networkJob = null
        awaitingInterruption?.cancel()
        awaitingInterruption = null
        activeSessionId?.let(::finishSession)
    }

    private fun finishSession(sessionId: Long) {
        repository.recordCleanup(sessionId)
        repository.recordRelease(sessionId)
        if (activeSessionId == sessionId) {
            activeSessionId = null
            activeResponseId = null
            listener = null
        }
    }

    private fun failure(message: String) = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.CANCELLED, message, recoverable = true)
    )

    companion object {
        private const val FAKE_SESSION_DURATION_SECONDS = 15 * 60L
        private const val MAX_RECONNECT_ATTEMPTS = 2
        private const val MANUAL_USER_ITEM_ID = "fake-manual-user"
    }
}
