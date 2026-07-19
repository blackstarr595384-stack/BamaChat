package com.example.bamachat.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BamaVoiceSessionControllerTest {
    @Test
    fun rapidMicrophoneTapsCreateOneRecognizerSession() = runBlocking {
        val clock = TestClock()
        val fixture = fixture(clock = clock)

        fixture.controller.startListening()
        clock.advance(100L)
        fixture.controller.startListening()

        assertEquals(1, fixture.input.startCalls)
        assertEquals(VoiceSessionState.Listening, fixture.controller.uiState.value.state)
        fixture.release()
    }

    @Test
    fun partialTranscriptNeverEmitsFinalMessage() = runBlocking {
        val fixture = fixture()
        val finals = mutableListOf<VoiceFinalTranscript>()
        val collector = fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.finalTranscripts.collect(finals::add)
        }

        fixture.controller.startListening()
        fixture.input.partial("Noch nicht fertig")

        assertTrue(finals.isEmpty())
        assertEquals(VoiceSessionState.Transcribing("Noch nicht fertig"), fixture.controller.uiState.value.state)
        collector.cancel()
        fixture.release()
    }

    @Test
    fun finalTranscriptEmitsExactlyOnceAndEmptyTextNeverEmits() = runBlocking {
        val fixture = fixture()
        val finals = mutableListOf<VoiceFinalTranscript>()
        val collector = fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.finalTranscripts.collect(finals::add)
        }

        fixture.controller.startListening()
        fixture.input.final("Finaler Text")
        fixture.input.final("Doppelter Text")
        fixture.advanceMicClock()
        fixture.controller.startListening()
        fixture.input.final("   ")

        assertEquals(listOf("Finaler Text"), finals.map { it.text })
        collector.cancel()
        fixture.release()
    }

    @Test
    fun stopCancelsActiveAndQueuedSpeech() = runBlocking {
        val output = FakeOutput(blockPlayback = true)
        val fixture = fixture(output = output)

        fixture.controller.speakFullMessage("assistant-1", "Erster Satz. Zweiter Satz.")
        val stopsBeforeExplicitAction = output.stopCalls
        fixture.controller.stopSpeaking()

        assertTrue(output.stopCalls > stopsBeforeExplicitAction)
        assertEquals(VoiceSessionState.Interrupted, fixture.controller.uiState.value.state)
        fixture.release()
    }

    @Test
    fun providerReplacementReleasesOldEngines() = runBlocking {
        val fixture = fixture()
        val replacementInput = FakeInput()
        val replacementOutput = FakeOutput()

        fixture.controller.updateConfiguration(
            VoiceSessionConfiguration(),
            replacementInputEngine = replacementInput,
            replacementOutputEngine = replacementOutput
        )

        assertEquals(1, fixture.input.releaseCalls)
        assertEquals(1, fixture.output.releaseCalls)
        fixture.controller.release()
        fixture.scope.cancel()
    }

    @Test
    fun unavailableRecognizerProducesRecoverableErrorWithoutCrash() = runBlocking {
        val fixture = fixture(input = FakeInput(available = false))

        fixture.controller.startListening()

        assertTrue(fixture.controller.uiState.value.state is VoiceSessionState.Error)
        fixture.release()
    }

    @Test
    fun explicitBargeInSuppressesRemainingOldAssistantText() = runBlocking {
        val output = FakeOutput(blockPlayback = true)
        val fixture = fixture(output = output)
        fixture.controller.updateConfiguration(
            VoiceSessionConfiguration(autoPlayback = true, interruptionEnabled = true)
        )
        fixture.controller.markTextMessageAccepted()
        fixture.controller.onAssistantTextChanged("assistant-1", "Erster Satz.", isStreaming = true)
        assertEquals(1, output.spokenChunks.size)

        fixture.controller.startListening()
        fixture.controller.onAssistantTextChanged(
            "assistant-1",
            "Erster Satz. Dieser Rest darf nicht wiedergegeben werden.",
            isStreaming = false
        )

        assertEquals(1, output.spokenChunks.size)
        assertEquals(VoiceSessionState.Listening, fixture.controller.uiState.value.state)
        fixture.release()
    }

    @Test
    fun outputFailureDoesNotPreventLaterSpeechInput() = runBlocking {
        val output = FakeOutput(
            result = VoiceOperationResult.Failure(
                VoiceFailure(VoiceFailureCategory.OFFLINE, "Sprachausgabe offline")
            )
        )
        val fixture = fixture(output = output)

        fixture.controller.speakFullMessage("assistant-1", "Antwort")
        assertTrue(fixture.controller.uiState.value.state is VoiceSessionState.Error)
        fixture.controller.recoverFromError()
        fixture.controller.startListening()

        assertEquals(VoiceSessionState.Listening, fixture.controller.uiState.value.state)
        fixture.release()
    }

    @Test
    fun fallbackDoesNotReplayChunkAfterPrimaryPlaybackStarted() = runBlocking {
        val primary = FakeOutput(
            result = VoiceOperationResult.Failure(
                VoiceFailure(VoiceFailureCategory.TEMPORARY_SERVICE_ERROR, "Abbruch")
            ),
            reportPlaybackStarted = true
        )
        val fallback = FakeOutput()
        val engine = FallbackSpeechOutputEngine(primary, fallback)

        engine.speak(
            SpeechOutputRequest("Nur einmal", "de-DE", 1f, 1f),
            object : SpeechOutputListener {
                override fun onPlaybackStarted() = Unit
            }
        )

        assertEquals(1, primary.spokenChunks.size)
        assertTrue(fallback.spokenChunks.isEmpty())
    }

    @Test
    fun liveFinalTranscriptsPersistExactlyOnceAndPartialsRemainUiOnly() = runBlocking {
        val realtime = FakeRealtimeEngine()
        val fixture = fixture(realtime = realtime)
        val turns = mutableListOf<RealtimeFinalizedTurn>()
        val collector = fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.realtimeTurns.collect(turns::add)
        }
        fixture.controller.updateConfiguration(
            VoiceSessionConfiguration(mode = VoiceMode.LIVE)
        )

        fixture.controller.startLiveSession("Entwickler")
        realtime.emit(RealtimeVoiceEvent.UserTranscriptDelta("user-1", "Teil"))
        realtime.emit(RealtimeVoiceEvent.AssistantTranscriptDelta("response-1", "assistant-1", "Zwischenstand"))
        assertTrue(turns.isEmpty())

        realtime.emit(RealtimeVoiceEvent.UserTranscriptCompleted("user-1", "Finale Frage"))
        realtime.emit(RealtimeVoiceEvent.UserTranscriptCompleted("user-1", "Finale Frage"))
        realtime.emit(RealtimeVoiceEvent.ResponseCreated("response-1"))
        realtime.emit(
            RealtimeVoiceEvent.AssistantTranscriptCompleted(
                "response-1",
                "assistant-1",
                "Finale Antwort"
            )
        )
        realtime.emit(RealtimeVoiceEvent.ResponseCompleted("response-1"))
        realtime.emit(RealtimeVoiceEvent.ResponseCompleted("response-1"))

        assertEquals(listOf(true, false), turns.map { it.isUser })
        assertEquals(listOf("rt-user-user-1", "rt-assistant-response-1"), turns.map { it.messageId })
        assertEquals("Entwickler", realtime.lastRequest?.personaName)
        assertTrue(fixture.output.spokenChunks.isEmpty())
        collector.cancel()
        fixture.release()
    }

    @Test
    fun cancelledRealtimeResponseIsNeverPersistedOrReplayed() = runBlocking {
        val realtime = FakeRealtimeEngine()
        val fixture = fixture(realtime = realtime)
        val turns = mutableListOf<RealtimeFinalizedTurn>()
        val collector = fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.realtimeTurns.collect(turns::add)
        }
        fixture.controller.updateConfiguration(VoiceSessionConfiguration(mode = VoiceMode.LIVE))
        fixture.controller.startLiveSession("BamaChat")

        realtime.emit(RealtimeVoiceEvent.ResponseCreated("cancelled-response"))
        realtime.emit(
            RealtimeVoiceEvent.AssistantTranscriptDelta(
                "cancelled-response",
                "assistant-2",
                "Nicht vollständig gehört"
            )
        )
        realtime.emit(RealtimeVoiceEvent.ResponseCancelled("cancelled-response"))
        realtime.emit(RealtimeVoiceEvent.ResponseCompleted("cancelled-response"))

        assertFalse(turns.any { !it.isUser })
        assertTrue(fixture.output.spokenChunks.isEmpty())
        collector.cancel()
        fixture.release()
    }

    @Test
    fun lateCancelledResponseCannotClearOrPersistTheNextResponse() = runBlocking {
        val realtime = FakeRealtimeEngine()
        val fixture = fixture(realtime = realtime)
        val turns = mutableListOf<RealtimeFinalizedTurn>()
        val collector = fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.realtimeTurns.collect(turns::add)
        }
        fixture.controller.updateConfiguration(VoiceSessionConfiguration(mode = VoiceMode.LIVE))
        fixture.controller.startLiveSession("BamaChat")

        realtime.emit(RealtimeVoiceEvent.ResponseCreated("old-response"))
        realtime.emit(RealtimeVoiceEvent.AssistantTranscriptDelta("old-response", null, "Alt"))
        realtime.emit(RealtimeVoiceEvent.ResponseCancelled("old-response"))
        realtime.emit(RealtimeVoiceEvent.ResponseCreated("new-response"))
        realtime.emit(RealtimeVoiceEvent.AssistantTranscriptDelta("new-response", null, "Neu"))
        realtime.emit(RealtimeVoiceEvent.ResponseCompleted("old-response"))

        assertEquals("Neu", fixture.controller.uiState.value.assistantTranscript)
        assertEquals(VoiceSessionState.Speaking, fixture.controller.uiState.value.state)

        realtime.emit(
            RealtimeVoiceEvent.AssistantTranscriptCompleted(
                "new-response",
                null,
                "Neue Antwort"
            )
        )
        realtime.emit(RealtimeVoiceEvent.ResponseCompleted("new-response"))

        assertEquals(listOf("rt-assistant-new-response"), turns.filter { !it.isUser }.map { it.messageId })
        collector.cancel()
        fixture.release()
    }

    @Test
    fun speechBargeInDiscardsLateCompletionFromInterruptedResponse() = runBlocking {
        val realtime = FakeRealtimeEngine()
        val fixture = fixture(realtime = realtime)
        val turns = mutableListOf<RealtimeFinalizedTurn>()
        val collector = fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.realtimeTurns.collect(turns::add)
        }
        fixture.controller.updateConfiguration(VoiceSessionConfiguration(mode = VoiceMode.LIVE))
        fixture.controller.startLiveSession("BamaChat")
        realtime.emit(RealtimeVoiceEvent.ResponseCreated("interrupted-response"))
        realtime.emit(
            RealtimeVoiceEvent.AssistantTranscriptDelta(
                "interrupted-response",
                null,
                "Nur teilweise gehört"
            )
        )

        realtime.emit(RealtimeVoiceEvent.SpeechStarted("new-user-item"))
        realtime.emit(RealtimeVoiceEvent.ResponseCompleted("interrupted-response"))

        assertEquals(1, realtime.interruptCalls)
        assertFalse(turns.any { !it.isUser })
        collector.cancel()
        fixture.release()
    }

    @Test
    fun eventsArrivingAfterExplicitSessionEndAreIgnored() = runBlocking {
        val realtime = FakeRealtimeEngine()
        val fixture = fixture(realtime = realtime)
        val turns = mutableListOf<RealtimeFinalizedTurn>()
        val collector = fixture.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.realtimeTurns.collect(turns::add)
        }
        fixture.controller.updateConfiguration(VoiceSessionConfiguration(mode = VoiceMode.LIVE))
        fixture.controller.startLiveSession("BamaChat")

        fixture.controller.endLiveSession()
        realtime.emit(RealtimeVoiceEvent.UserTranscriptCompleted("late-user", "Zu spät"))
        realtime.emit(RealtimeVoiceEvent.ResponseCreated("late-response"))
        realtime.emit(
            RealtimeVoiceEvent.AssistantTranscriptCompleted(
                "late-response",
                null,
                "Ebenfalls zu spät"
            )
        )
        realtime.emit(RealtimeVoiceEvent.ResponseCompleted("late-response"))

        assertTrue(turns.isEmpty())
        assertEquals(VoiceSessionState.Ended, fixture.controller.uiState.value.state)
        collector.cancel()
        fixture.release()
    }

    @Test
    fun leavingChatStopsTheActiveRealtimeSession() = runBlocking {
        val realtime = FakeRealtimeEngine()
        val fixture = fixture(realtime = realtime)
        fixture.controller.updateConfiguration(VoiceSessionConfiguration(mode = VoiceMode.LIVE))
        fixture.controller.startLiveSession("BamaChat")

        fixture.controller.leaveScreen()

        assertEquals(1, realtime.stopCalls)
        assertEquals(VoiceSessionState.Idle, fixture.controller.uiState.value.state)
        fixture.release()
    }

    @Test
    fun realtimeFailureStopsTransportAndLeavesRecoverableTextChatState() = runBlocking {
        val realtime = FakeRealtimeEngine()
        val fixture = fixture(realtime = realtime)
        fixture.controller.updateConfiguration(VoiceSessionConfiguration(mode = VoiceMode.LIVE))
        fixture.controller.startLiveSession("BamaChat")

        realtime.emit(
            RealtimeVoiceEvent.Failure(
                VoiceFailure(
                    VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                    "Live vorübergehend nicht verfügbar"
                )
            )
        )

        assertEquals(1, realtime.stopCalls)
        assertEquals(
            VoiceSessionState.Error("Live vorübergehend nicht verfügbar", true),
            fixture.controller.uiState.value.state
        )
        fixture.release()
    }

    private fun fixture(
        input: FakeInput = FakeInput(),
        output: FakeOutput = FakeOutput(),
        realtime: FakeRealtimeEngine = FakeRealtimeEngine(isAvailable = false),
        clock: TestClock = TestClock()
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val audio = FakeAudioSession()
        val controller = BamaVoiceSessionController(
            scope = scope,
            initialInputEngine = input,
            initialOutputEngine = output,
            audioSession = audio,
            realtimeEngine = realtime,
            nowMillis = clock::now
        )
        return Fixture(controller, input, output, scope, clock)
    }

    private class Fixture(
        val controller: BamaVoiceSessionController,
        val input: FakeInput,
        val output: FakeOutput,
        val scope: CoroutineScope,
        private val clock: TestClock
    ) {
        fun advanceMicClock() {
            clock.advance(1_000L)
        }

        suspend fun release() {
            controller.release()
            scope.cancel()
        }
    }

    private class TestClock(private var value: Long = 1_000L) {
        fun now(): Long = value

        fun advance(durationMs: Long) {
            value += durationMs
        }
    }

    private class FakeInput(
        private val available: Boolean = true
    ) : SpeechToTextEngine {
        override val provider = VoiceInputProvider.ANDROID
        var startCalls = 0
        var releaseCalls = 0
        private var listener: SpeechInputListener? = null

        override fun isAvailable(): Boolean = available

        override suspend fun startStreaming(
            config: SpeechInputConfig,
            listener: SpeechInputListener
        ): VoiceOperationResult {
            startCalls++
            this.listener = listener
            listener.onReady()
            return VoiceOperationResult.Success
        }

        override suspend fun finish() = Unit

        override suspend fun cancel() = Unit

        override suspend fun release() {
            releaseCalls++
            listener = null
        }

        fun partial(text: String) = listener?.onPartialTranscript(text)

        fun final(text: String) = listener?.onFinalTranscript(text)
    }

    private class FakeOutput(
        private val result: VoiceOperationResult = VoiceOperationResult.Success,
        private val blockPlayback: Boolean = false,
        private val reportPlaybackStarted: Boolean = true
    ) : SpeechOutputEngine {
        override val provider = VoiceOutputProvider.ANDROID
        val spokenChunks = mutableListOf<String>()
        var stopCalls = 0
        var releaseCalls = 0
        private var pending = CompletableDeferred<Unit>()

        override suspend fun speak(
            request: SpeechOutputRequest,
            listener: SpeechOutputListener
        ): VoiceOperationResult {
            spokenChunks += request.text
            if (reportPlaybackStarted) listener.onPlaybackStarted()
            if (blockPlayback) {
                try {
                    awaitCancellation()
                } finally {
                    pending.complete(Unit)
                }
            }
            return result
        }

        override suspend fun stop() {
            stopCalls++
            pending.complete(Unit)
            pending = CompletableDeferred()
        }

        override suspend fun pause(): VoiceOperationResult = VoiceOperationResult.Success

        override suspend fun resume(): VoiceOperationResult = VoiceOperationResult.Success

        override suspend fun release() {
            releaseCalls++
            stop()
        }
    }

    private class FakeAudioSession : VoiceAudioSession {
        override suspend fun activate(
            purpose: VoiceAudioPurpose,
            onFocusLost: () -> Unit
        ): VoiceOperationResult = VoiceOperationResult.Success

        override suspend fun deactivate() = Unit
    }

    private class FakeRealtimeEngine(
        override val isAvailable: Boolean = true
    ) : RealtimeVoiceEngine {
        var startCalls = 0
        var stopCalls = 0
        var releaseCalls = 0
        var interruptCalls = 0
        var lastRequest: RealtimeVoiceSessionRequest? = null
        private var listener: RealtimeVoiceListener? = null

        override suspend fun start(
            request: RealtimeVoiceSessionRequest,
            listener: RealtimeVoiceListener
        ): VoiceOperationResult {
            startCalls++
            lastRequest = request
            this.listener = listener
            listener.onEvent(RealtimeVoiceEvent.Connected)
            listener.onEvent(RealtimeVoiceEvent.SessionStarted(3_600L))
            return VoiceOperationResult.Success
        }

        override suspend fun mute(muted: Boolean) = Unit
        override suspend fun beginUserTurn() = Unit
        override suspend fun finishUserTurn() = Unit

        override suspend fun interrupt() {
            interruptCalls++
        }

        override suspend fun stop() {
            stopCalls++
        }

        override suspend fun release() {
            releaseCalls++
        }

        fun emit(event: RealtimeVoiceEvent) {
            listener?.onEvent(event)
        }
    }
}
