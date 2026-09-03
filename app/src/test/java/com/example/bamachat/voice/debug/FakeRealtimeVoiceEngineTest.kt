package com.example.bamachat.voice.debug

import com.example.bamachat.voice.RealtimeTurnTaking
import com.example.bamachat.voice.RealtimeFinalizedTurn
import com.example.bamachat.voice.RealtimeVoiceEvent
import com.example.bamachat.voice.RealtimeVoiceSessionRequest
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRealtimeVoiceEngineTest {
    @Test
    fun finalMessageDiagnosticsDeduplicateByStableMessageId() {
        val repository = DebugVoiceScenarioRepository()
        val first = RealtimeFinalizedTurn("fake-message-1", "Gleicher Testtext", true, 1L)
        val duplicate = first.copy(timestamp = 2L)
        val second = first.copy(messageId = "fake-message-2", timestamp = 3L)

        repository.recordFinalTurn(first)
        repository.recordFinalTurn(duplicate)
        repository.recordFinalTurn(second)

        assertEquals(2, repository.state.value.finalUserMessages.size)
        assertEquals(listOf("fake-message-1", "fake-message-2"), repository.state.value.finalUserMessages.map { it.messageId })
    }

    @Test
    fun successfulScenarioConnectsOnlyAfterHandshakeAndCleansOnce() = runBlocking {
        val fixture = fixture(FakeRealtimeScenario.SUCCESS)

        val result = fixture.engine.start(request(), fixture.events::add)

        assertEquals(VoiceOperationResult.Success, result)
        val history = fixture.repository.state.value.statusHistory
        assertTrue(history.indexOf("PeerConnection bereit") < history.indexOf("DataChannel offen"))
        assertTrue(history.indexOf("DataChannel offen") < history.indexOf("Event: Connected"))
        assertEquals(1, fixture.events.count { it == RealtimeVoiceEvent.Connected })
        assertEquals(1, fixture.repository.state.value.cleanupCount)
        assertEquals(1, fixture.repository.state.value.releaseCount)

        fixture.engine.stop()
        fixture.engine.release()
        assertEquals(1, fixture.repository.state.value.cleanupCount)
        assertEquals(1, fixture.repository.state.value.releaseCount)
        fixture.close()
    }

    @Test
    fun bargeInCancelsOldResponseAndContinuesWithNewTurn() = runBlocking {
        val fixture = fixture(FakeRealtimeScenario.BARGE_IN)
        fixture.engine.start(request(), fixture.events::add)

        assertTrue(fixture.events.any { it is RealtimeVoiceEvent.AssistantTranscriptDelta })
        assertFalse(fixture.events.any { it is RealtimeVoiceEvent.ResponseCompleted })

        fixture.engine.interrupt()

        val cancelled = fixture.events.filterIsInstance<RealtimeVoiceEvent.ResponseCancelled>()
        val completed = fixture.events.filterIsInstance<RealtimeVoiceEvent.ResponseCompleted>()
        assertEquals(1, cancelled.size)
        assertEquals(1, completed.size)
        assertFalse(completed.any { it.responseId == cancelled.single().responseId })
        fixture.close()
    }

    @Test
    fun rapidStartRequestsCreateOneFakeSession() = runBlocking {
        val fixture = fixture(FakeRealtimeScenario.BARGE_IN)

        fixture.engine.start(request(), fixture.events::add)
        fixture.engine.start(request(), fixture.events::add)

        assertEquals(1, fixture.repository.state.value.startCount)
        fixture.close()
    }

    @Test
    fun configuredFailuresRemainRecoverableAndNeverConnect() = runBlocking {
        val scenarios = listOf(
            FakeRealtimeScenario.SDP_ERROR,
            FakeRealtimeScenario.PEER_CONNECTION_ERROR,
            FakeRealtimeScenario.DATA_CHANNEL_ERROR,
            FakeRealtimeScenario.MICROPHONE_ERROR,
            FakeRealtimeScenario.TIMEOUT
        )

        scenarios.forEach { scenario ->
            val fixture = fixture(scenario)
            fixture.engine.start(request(), fixture.events::add)
            val failure = fixture.events.filterIsInstance<RealtimeVoiceEvent.Failure>().single()

            assertTrue(failure.error.recoverable)
            assertFalse(fixture.events.any { it == RealtimeVoiceEvent.Connected })
            fixture.close()
        }
    }

    @Test
    fun credentialFailureReturnsTypedFailureWithoutTransportEvents() = runBlocking {
        val fixture = fixture(FakeRealtimeScenario.CREDENTIAL_ERROR)

        val result = fixture.engine.start(request(), fixture.events::add)

        assertTrue(result is VoiceOperationResult.Failure)
        assertEquals(
            VoiceFailureCategory.AUTHENTICATION_REQUIRED,
            (result as VoiceOperationResult.Failure).error.category
        )
        assertTrue(fixture.events.isEmpty())
        assertEquals(0, fixture.repository.state.value.releaseCount)
        fixture.close()
    }

    @Test
    fun reconnectAttemptsStayBounded() = runBlocking {
        val fixture = fixture(FakeRealtimeScenario.RECONNECT_FAILED)

        fixture.engine.start(request(), fixture.events::add)

        val reconnects = fixture.events.filterIsInstance<RealtimeVoiceEvent.Reconnecting>()
        assertEquals(listOf(1, 2), reconnects.map { it.attempt })
        assertEquals(listOf(2, 2), reconnects.map { it.maximumAttempts })
        assertEquals(1, fixture.events.filterIsInstance<RealtimeVoiceEvent.Failure>().size)
        fixture.close()
    }

    @Test
    fun duplicateScenarioProvidesStableDuplicateIdsForControllerFiltering() = runBlocking {
        val fixture = fixture(FakeRealtimeScenario.DUPLICATE_LATE_EVENTS)

        fixture.engine.start(request(), fixture.events::add)

        val users = fixture.events.filterIsInstance<RealtimeVoiceEvent.UserTranscriptCompleted>()
        val responses = fixture.events.filterIsInstance<RealtimeVoiceEvent.ResponseCompleted>()
        assertEquals(2, users.size)
        assertEquals(1, users.map { it.itemId }.distinct().size)
        assertEquals(2, responses.size)
        assertEquals(1, responses.map { it.responseId }.distinct().size)
        fixture.close()
    }

    private fun fixture(scenario: FakeRealtimeScenario): Fixture {
        val repository = DebugVoiceScenarioRepository().apply {
            selectScenario(scenario)
            selectDelay(FakeRealtimeDelay.IMMEDIATE)
            resetStatus()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = FakeRealtimeVoiceEngine(
            repository = repository,
            nowEpochSeconds = { 1_000L },
            stepDelay = {},
            engineScope = scope
        )
        return Fixture(repository, engine, scope)
    }

    private fun request() = RealtimeVoiceSessionRequest(
        provider = "fake",
        model = "fake-realtime",
        voice = "synthetic",
        languageTag = "de-DE",
        personaName = "Testpersona",
        turnTaking = RealtimeTurnTaking.SEMANTIC
    )

    private class Fixture(
        val repository: DebugVoiceScenarioRepository,
        val engine: FakeRealtimeVoiceEngine,
        private val scope: CoroutineScope
    ) {
        val events = mutableListOf<RealtimeVoiceEvent>()

        suspend fun close() {
            engine.release()
            scope.cancel()
        }
    }
}
