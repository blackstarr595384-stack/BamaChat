package com.example.bamachat.voice.debug

import com.example.bamachat.voice.RealtimeVoiceEvent
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory

enum class FakeRealtimeScenario(
    val displayName: String,
    internal val startFailure: VoiceFailure? = null
) {
    SUCCESS("Erfolgreicher Normalablauf"),
    BARGE_IN("Barge-in"),
    RECONNECT_SUCCESS("Reconnect erfolgreich"),
    RECONNECT_FAILED("Reconnect endgültig fehlgeschlagen"),
    CREDENTIAL_ERROR(
        "Backend-/Credential-Fehler",
        VoiceFailure(
            VoiceFailureCategory.AUTHENTICATION_REQUIRED,
            "Die simulierte Live-Berechtigung konnte nicht erstellt werden."
        )
    ),
    SDP_ERROR("SDP-Fehler"),
    PEER_CONNECTION_ERROR("PeerConnection-Fehler"),
    DATA_CHANNEL_ERROR("DataChannel öffnet nicht"),
    MICROPHONE_ERROR("Mikrofonfehler"),
    NETWORK_FAILURE("Netzwerkabbruch"),
    TIMEOUT("Timeout"),
    DUPLICATE_LATE_EVENTS("Doppelte und verspätete Events");

    internal fun steps(sessionExpiresAtEpochSeconds: Long): List<FakeRealtimeScenarioStep> = when (this) {
        SUCCESS -> successfulTurn(sessionExpiresAtEpochSeconds)
        BARGE_IN -> bargeInTurn(sessionExpiresAtEpochSeconds)
        RECONNECT_SUCCESS -> reconnectSuccess(sessionExpiresAtEpochSeconds)
        RECONNECT_FAILED -> reconnectFailure(sessionExpiresAtEpochSeconds)
        CREDENTIAL_ERROR -> emptyList()
        SDP_ERROR -> connectionFailure("SDP-Austausch fehlgeschlagen", VoiceFailureCategory.TEMPORARY_SERVICE_ERROR)
        PEER_CONNECTION_ERROR -> connectionFailure(
            "PeerConnection konnte nicht erstellt werden",
            VoiceFailureCategory.TEMPORARY_SERVICE_ERROR
        )
        DATA_CHANNEL_ERROR -> connectionFailure(
            "DataChannel wurde nicht geöffnet",
            VoiceFailureCategory.TIMEOUT
        )
        MICROPHONE_ERROR -> connectionFailure(
            "Mikrofon konnte nicht aktiviert werden",
            VoiceFailureCategory.PERMISSION_DENIED
        )
        NETWORK_FAILURE -> networkFailure(sessionExpiresAtEpochSeconds)
        TIMEOUT -> connectionFailure("Verbindungsaufbau hat das Zeitlimit erreicht", VoiceFailureCategory.TIMEOUT)
        DUPLICATE_LATE_EVENTS -> duplicateLateEvents(sessionExpiresAtEpochSeconds)
    }

    private fun successfulTurn(expiresAt: Long) = handshake(expiresAt) + listOf(
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.SpeechStarted(USER_ITEM_1)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.UserTranscriptDelta(USER_ITEM_1, USER_PARTIAL_1)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.UserTranscriptCompleted(USER_ITEM_1, USER_FINAL_1)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.SpeechStopped),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.ResponseCreated(RESPONSE_1)),
        FakeRealtimeScenarioStep.Emit(
            RealtimeVoiceEvent.AssistantTranscriptDelta(RESPONSE_1, ASSISTANT_ITEM_1, ASSISTANT_PARTIAL_1)
        ),
        FakeRealtimeScenarioStep.Emit(
            RealtimeVoiceEvent.AssistantTranscriptCompleted(RESPONSE_1, ASSISTANT_ITEM_1, ASSISTANT_FINAL_1)
        ),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.ResponseCompleted(RESPONSE_1)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Closed)
    )

    private fun bargeInTurn(expiresAt: Long) = handshake(expiresAt) + listOf(
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.ResponseCreated(RESPONSE_1)),
        FakeRealtimeScenarioStep.Emit(
            RealtimeVoiceEvent.AssistantTranscriptDelta(RESPONSE_1, ASSISTANT_ITEM_1, INTERRUPTED_PARTIAL)
        ),
        FakeRealtimeScenarioStep.AwaitInterruption,
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.SpeechStarted(USER_ITEM_2)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.UserTranscriptDelta(USER_ITEM_2, USER_PARTIAL_2)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.UserTranscriptCompleted(USER_ITEM_2, USER_FINAL_2)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.SpeechStopped),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.ResponseCreated(RESPONSE_2)),
        FakeRealtimeScenarioStep.Emit(
            RealtimeVoiceEvent.AssistantTranscriptCompleted(RESPONSE_2, ASSISTANT_ITEM_2, ASSISTANT_FINAL_2)
        ),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.ResponseCompleted(RESPONSE_2)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Closed)
    )

    private fun reconnectSuccess(expiresAt: Long) = handshake(expiresAt) + listOf(
        FakeRealtimeScenarioStep.Record("Transport getrennt"),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Reconnecting(1, MAX_RECONNECT_ATTEMPTS)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Connecting),
        FakeRealtimeScenarioStep.Record("PeerConnection erneut bereit"),
        FakeRealtimeScenarioStep.Record("DataChannel erneut offen"),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Connected),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Closed)
    )

    private fun reconnectFailure(expiresAt: Long) = handshake(expiresAt) + listOf(
        FakeRealtimeScenarioStep.Record("Transport getrennt"),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Reconnecting(1, MAX_RECONNECT_ATTEMPTS)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Reconnecting(2, MAX_RECONNECT_ATTEMPTS)),
        FakeRealtimeScenarioStep.Emit(
            RealtimeVoiceEvent.Failure(
                VoiceFailure(
                    VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                    "Die simulierte Verbindung konnte nicht wiederhergestellt werden."
                )
            )
        )
    )

    private fun networkFailure(expiresAt: Long) = handshake(expiresAt) + listOf(
        FakeRealtimeScenarioStep.Record("Simulierter Netzwerkabbruch"),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Reconnecting(1, MAX_RECONNECT_ATTEMPTS)),
        FakeRealtimeScenarioStep.Emit(
            RealtimeVoiceEvent.Failure(
                VoiceFailure(VoiceFailureCategory.OFFLINE, "Die simulierte Netzwerkverbindung wurde getrennt.")
            )
        )
    )

    private fun duplicateLateEvents(expiresAt: Long) = handshake(expiresAt) + listOf(
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.UserTranscriptDelta(USER_ITEM_1, USER_PARTIAL_1)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.UserTranscriptCompleted(USER_ITEM_1, USER_FINAL_1)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.UserTranscriptCompleted(USER_ITEM_1, USER_FINAL_1)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.ResponseCreated(RESPONSE_1)),
        FakeRealtimeScenarioStep.Emit(
            RealtimeVoiceEvent.AssistantTranscriptCompleted(RESPONSE_1, ASSISTANT_ITEM_1, ASSISTANT_FINAL_1)
        ),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.ResponseCompleted(RESPONSE_1)),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.ResponseCompleted(RESPONSE_1)),
        FakeRealtimeScenarioStep.Record("Verspätetes Event verworfen"),
        FakeRealtimeScenarioStep.Emit(
            RealtimeVoiceEvent.AssistantTranscriptDelta(RESPONSE_1, ASSISTANT_ITEM_1, LATE_PARTIAL)
        ),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Closed)
    )

    private fun handshake(expiresAt: Long) = listOf(
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Connecting),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.SessionStarted(expiresAt)),
        FakeRealtimeScenarioStep.Record("PeerConnection bereit"),
        FakeRealtimeScenarioStep.Record("DataChannel offen"),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Connected)
    )

    private fun connectionFailure(message: String, category: VoiceFailureCategory) = listOf(
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Connecting),
        FakeRealtimeScenarioStep.Emit(RealtimeVoiceEvent.Failure(VoiceFailure(category, message)))
    )

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 2
        private const val USER_ITEM_1 = "fake-user-001"
        private const val USER_ITEM_2 = "fake-user-002"
        private const val RESPONSE_1 = "fake-response-001"
        private const val RESPONSE_2 = "fake-response-002"
        private const val ASSISTANT_ITEM_1 = "fake-assistant-001"
        private const val ASSISTANT_ITEM_2 = "fake-assistant-002"
        private const val USER_PARTIAL_1 = "Wie funktioniert die lokale Simu"
        private const val USER_FINAL_1 = "Wie funktioniert die lokale Simulation?"
        private const val USER_PARTIAL_2 = "Bitte antworte jetzt kür"
        private const val USER_FINAL_2 = "Bitte antworte jetzt kürzer."
        private const val ASSISTANT_PARTIAL_1 = "Die lokale Simulation läuft vollständig ohne Netzwerk."
        private const val ASSISTANT_FINAL_1 = "Die lokale Simulation prüft den Voice-Ablauf vollständig ohne Netzwerkzugriff."
        private const val INTERRUPTED_PARTIAL = "Diese simulierte Antwort wird absichtlich unterbrochen."
        private const val ASSISTANT_FINAL_2 = "Die unterbrochene Antwort wurde verworfen und durch diese neue Antwort ersetzt."
        private const val LATE_PARTIAL = "Dieses verspätete Fragment darf nicht gespeichert werden."
    }
}

sealed interface FakeRealtimeScenarioStep {
    data class Emit(val event: RealtimeVoiceEvent) : FakeRealtimeScenarioStep
    data class Record(val label: String) : FakeRealtimeScenarioStep
    data object AwaitInterruption : FakeRealtimeScenarioStep
}

enum class FakeRealtimeDelay(
    val displayName: String,
    val delayMs: Long
) {
    IMMEDIATE("Sofort", 0L),
    SHORT("250 ms", 250L),
    NORMAL("1 s", 1_000L),
    SLOW("Langsam", 2_000L)
}
