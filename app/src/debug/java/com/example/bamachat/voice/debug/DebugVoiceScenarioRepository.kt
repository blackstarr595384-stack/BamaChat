package com.example.bamachat.voice.debug

import com.example.bamachat.voice.RealtimeFinalizedTurn
import com.example.bamachat.voice.RealtimeVoiceEvent
import com.example.bamachat.voice.VoiceSessionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DebugVoiceLabState(
    val fakeEnabled: Boolean = true,
    val selectedScenario: FakeRealtimeScenario = FakeRealtimeScenario.SUCCESS,
    val selectedDelay: FakeRealtimeDelay = FakeRealtimeDelay.SHORT,
    val statusHistory: List<String> = listOf("Testlabor bereit"),
    val eventCount: Int = 0,
    val startCount: Int = 0,
    val cleanupCount: Int = 0,
    val releaseCount: Int = 0,
    val finalUserMessages: List<DebugVoiceFinalMessage> = emptyList(),
    val finalAssistantMessages: List<DebugVoiceFinalMessage> = emptyList()
)

data class DebugVoiceFinalMessage(
    val messageId: String,
    val text: String
)

internal sealed interface DebugVoiceCommand {
    data object NetworkFailure : DebugVoiceCommand
}

@Singleton
class DebugVoiceScenarioRepository @Inject constructor() {
    private val _state = MutableStateFlow(DebugVoiceLabState())
    val state: StateFlow<DebugVoiceLabState> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<DebugVoiceCommand>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val commands: SharedFlow<DebugVoiceCommand> = _commands.asSharedFlow()

    private val cleanedSessions = linkedSetOf<Long>()
    private val releasedSessions = linkedSetOf<Long>()

    fun setFakeEnabled(enabled: Boolean) {
        _state.update { it.copy(fakeEnabled = enabled) }
        recordStatus(if (enabled) "Lokale Fake-Realtime aktiviert" else "Lokale Fake-Realtime deaktiviert")
    }

    fun selectScenario(scenario: FakeRealtimeScenario) {
        _state.update { it.copy(selectedScenario = scenario) }
        recordStatus("Szenario ausgewählt: ${scenario.displayName}")
    }

    fun selectDelay(delay: FakeRealtimeDelay) {
        _state.update { it.copy(selectedDelay = delay) }
        recordStatus("Verzögerung ausgewählt: ${delay.displayName}")
    }

    fun prepareScenarioStart() {
        recordStatus("Szenario wird lokal gestartet")
    }

    fun triggerNetworkFailure() {
        recordStatus("Netzwerkfehler manuell ausgelöst")
        _commands.tryEmit(DebugVoiceCommand.NetworkFailure)
    }

    fun recordControllerState(state: VoiceSessionState) {
        recordStatus("UI-Status: ${state.safeLabel()}")
    }

    fun recordFinalTurn(turn: RealtimeFinalizedTurn) {
        val messageId = turn.messageId.trim().take(MAX_MESSAGE_ID_CHARS)
        val text = turn.text.trim().take(MAX_DISPLAY_TEXT_CHARS)
        if (messageId.isBlank() || text.isBlank()) return
        val message = DebugVoiceFinalMessage(messageId, text)
        _state.update { current ->
            if (turn.isUser) {
                if (current.finalUserMessages.any { it.messageId == messageId }) current else current.copy(
                    finalUserMessages = (current.finalUserMessages + message).takeLast(MAX_FINAL_MESSAGES)
                )
            } else {
                if (current.finalAssistantMessages.any { it.messageId == messageId }) current else current.copy(
                    finalAssistantMessages = (current.finalAssistantMessages + message).takeLast(MAX_FINAL_MESSAGES)
                )
            }
        }
    }

    fun resetStatus() {
        cleanedSessions.clear()
        releasedSessions.clear()
        _state.update {
            DebugVoiceLabState(
                fakeEnabled = it.fakeEnabled,
                selectedScenario = it.selectedScenario,
                selectedDelay = it.selectedDelay
            )
        }
    }

    internal fun recordStart() {
        _state.update { it.copy(startCount = it.startCount + 1) }
    }

    internal fun recordEvent(event: RealtimeVoiceEvent) {
        _state.update { it.copy(eventCount = it.eventCount + 1) }
        recordStatus("Event: ${event.safeLabel()}")
    }

    internal fun recordTransportStatus(label: String) {
        recordStatus(label.take(MAX_STATUS_CHARS))
    }

    internal fun recordCleanup(sessionId: Long) {
        if (!cleanedSessions.add(sessionId)) return
        _state.update { it.copy(cleanupCount = it.cleanupCount + 1) }
        recordStatus("Session-Cleanup abgeschlossen")
    }

    internal fun recordRelease(sessionId: Long) {
        if (!releasedSessions.add(sessionId)) return
        _state.update { it.copy(releaseCount = it.releaseCount + 1) }
        recordStatus("Fake-Lease freigegeben")
    }

    private fun recordStatus(label: String) {
        val cleanLabel = label.trim().take(MAX_STATUS_CHARS)
        if (cleanLabel.isBlank()) return
        _state.update { current ->
            if (current.statusHistory.lastOrNull() == cleanLabel) current else current.copy(
                statusHistory = (current.statusHistory + cleanLabel).takeLast(MAX_HISTORY_ENTRIES)
            )
        }
    }

    private fun VoiceSessionState.safeLabel(): String = when (this) {
        VoiceSessionState.Idle -> "Idle"
        VoiceSessionState.Preparing -> "Preparing"
        VoiceSessionState.Connecting -> "Connecting"
        is VoiceSessionState.Reconnecting -> "Reconnecting $attempt/$maximumAttempts"
        VoiceSessionState.Listening -> "Listening"
        is VoiceSessionState.Transcribing -> "Transcribing"
        VoiceSessionState.Thinking -> "Thinking"
        VoiceSessionState.Speaking -> "Speaking"
        VoiceSessionState.Interrupted -> "Interrupted"
        VoiceSessionState.Ended -> "Ended"
        is VoiceSessionState.Error -> "Recoverable Error"
    }

    private fun RealtimeVoiceEvent.safeLabel(): String = when (this) {
        RealtimeVoiceEvent.Connecting -> "Connecting"
        RealtimeVoiceEvent.Connected -> "Connected"
        is RealtimeVoiceEvent.SessionStarted -> "SessionStarted"
        is RealtimeVoiceEvent.Reconnecting -> "Reconnecting"
        is RealtimeVoiceEvent.SpeechStarted -> "SpeechStarted"
        RealtimeVoiceEvent.SpeechStopped -> "SpeechStopped"
        is RealtimeVoiceEvent.UserTranscriptDelta -> "UserTranscriptDelta"
        is RealtimeVoiceEvent.UserTranscriptCompleted -> "UserTranscriptCompleted"
        is RealtimeVoiceEvent.ResponseCreated -> "ResponseCreated"
        is RealtimeVoiceEvent.AssistantTranscriptDelta -> "AssistantTranscriptDelta"
        is RealtimeVoiceEvent.AssistantTranscriptCompleted -> "AssistantTranscriptCompleted"
        is RealtimeVoiceEvent.ResponseCompleted -> "ResponseCompleted"
        is RealtimeVoiceEvent.ResponseCancelled -> "ResponseCancelled"
        is RealtimeVoiceEvent.Failure -> "Failure:${error.category.name}"
        RealtimeVoiceEvent.Closed -> "Closed"
    }

    companion object {
        private const val MAX_HISTORY_ENTRIES = 100
        private const val MAX_FINAL_MESSAGES = 10
        private const val MAX_DISPLAY_TEXT_CHARS = 500
        private const val MAX_MESSAGE_ID_CHARS = 120
        private const val MAX_STATUS_CHARS = 120
    }
}
