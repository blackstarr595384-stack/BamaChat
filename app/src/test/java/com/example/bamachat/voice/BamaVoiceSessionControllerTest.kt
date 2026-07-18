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

    private fun fixture(
        input: FakeInput = FakeInput(),
        output: FakeOutput = FakeOutput(),
        clock: TestClock = TestClock()
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val audio = FakeAudioSession()
        val controller = BamaVoiceSessionController(
            scope = scope,
            initialInputEngine = input,
            initialOutputEngine = output,
            audioSession = audio,
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
}
