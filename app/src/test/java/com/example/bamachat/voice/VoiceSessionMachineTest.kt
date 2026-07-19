package com.example.bamachat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionMachineTest {
    @Test
    fun completeConversationTransitionsRemainDeterministic() {
        val machine = VoiceSessionMachine()
        val sessionId = requireNotNull(machine.beginPreparing())

        assertTrue(machine.listening(sessionId))
        assertEquals(VoiceSessionState.Listening, machine.state)
        assertTrue(machine.partial(sessionId, "Hallo BamaChat"))
        assertEquals(VoiceSessionState.Transcribing("Hallo BamaChat"), machine.state)

        val final = machine.finalTranscript(sessionId, " Hallo BamaChat ")
        assertEquals("Hallo BamaChat", final?.text)
        machine.thinking()
        assertEquals(VoiceSessionState.Thinking, machine.state)
        machine.speaking()
        assertEquals(VoiceSessionState.Speaking, machine.state)
    }

    @Test
    fun speakingCanBeInterruptedAndListeningCanRestart() {
        val machine = VoiceSessionMachine()
        machine.speaking()
        machine.interrupted()
        assertEquals(VoiceSessionState.Interrupted, machine.state)

        val sessionId = requireNotNull(machine.beginPreparing())
        assertTrue(machine.listening(sessionId))
        assertEquals(VoiceSessionState.Listening, machine.state)
    }

    @Test
    fun finalTranscriptIsDeliveredExactlyOnce() {
        val machine = VoiceSessionMachine()
        val sessionId = requireNotNull(machine.beginPreparing())
        machine.listening(sessionId)

        assertEquals("Ein Ergebnis", machine.finalTranscript(sessionId, "Ein Ergebnis")?.text)
        assertNull(machine.finalTranscript(sessionId, "Doppeltes Ergebnis"))
    }

    @Test
    fun emptyFinalTranscriptIsNotDelivered() {
        val machine = VoiceSessionMachine()
        val sessionId = requireNotNull(machine.beginPreparing())
        machine.listening(sessionId)

        assertNull(machine.finalTranscript(sessionId, "   "))
        assertEquals(VoiceSessionState.Idle, machine.state)
    }

    @Test
    fun errorIsRecoverableThroughIdle() {
        val machine = VoiceSessionMachine()
        machine.fail(VoiceFailure(VoiceFailureCategory.OFFLINE, "Offline"))

        assertEquals(VoiceSessionState.Error("Offline", true), machine.state)
        machine.idle()
        assertEquals(VoiceSessionState.Idle, machine.state)
    }

    @Test
    fun realtimeConnectionStatesRemainInTheAuthoritativeMachine() {
        val machine = VoiceSessionMachine()

        machine.connecting()
        assertEquals(VoiceSessionState.Connecting, machine.state)
        machine.reconnecting(attempt = 1, maximumAttempts = 2)
        assertEquals(VoiceSessionState.Reconnecting(1, 2), machine.state)
        machine.realtimeListening()
        assertEquals(VoiceSessionState.Listening, machine.state)
        machine.realtimeTranscribing(" Teil ")
        assertEquals(VoiceSessionState.Transcribing("Teil"), machine.state)
        machine.ended()
        assertEquals(VoiceSessionState.Ended, machine.state)
    }
}
