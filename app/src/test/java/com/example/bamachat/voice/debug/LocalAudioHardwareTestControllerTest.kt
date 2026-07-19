package com.example.bamachat.voice.debug

import com.example.bamachat.voice.SpeechOutputEngine
import com.example.bamachat.voice.SpeechOutputListener
import com.example.bamachat.voice.SpeechOutputRequest
import com.example.bamachat.voice.VoiceAudioPurpose
import com.example.bamachat.voice.VoiceAudioSession
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import com.example.bamachat.voice.VoiceOutputProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioHardwareTestControllerTest {
    @Test
    fun hardwareTestHasNoRealtimeOrNetworkDependencyAndDoesNotStartAutomatically() = runBlocking {
        val fixture = fixture()

        val dependencyNames = LocalAudioHardwareTestController::class.java.declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.map(Class<*>::getName) }
        assertFalse(dependencyNames.any { name ->
            name.contains("Realtime", ignoreCase = true) ||
                name.contains("Firebase", ignoreCase = true) ||
                name.contains("OpenAI", ignoreCase = true) ||
                name.contains("OkHttp", ignoreCase = true)
        })
        assertEquals(0, fixture.recognizer.startCalls)
        assertEquals(0, fixture.output.speakCalls)
        fixture.close()
    }

    @Test
    fun missingPermissionDoesNotStartRecognizer() = runBlocking {
        val fixture = fixture()

        fixture.controller.startMicrophone(permissionGranted = false)

        assertEquals(0, fixture.recognizer.startCalls)
        assertEquals(LocalMicrophoneStatus.ERROR, fixture.controller.uiState.value.microphoneStatus)
        assertEquals(
            LocalAudioErrorCategory.PERMISSION_MISSING,
            fixture.controller.uiState.value.lastErrorCategory
        )
        fixture.close()
    }

    @Test
    fun partialTranscriptIsUiOnlyAndNeverTriggersOutput() = runBlocking {
        val fixture = fixture()

        fixture.controller.startMicrophone(permissionGranted = true)
        fixture.recognizer.partial("Nur ein synthetisches Teilergebnis")

        val state = fixture.controller.uiState.value
        assertEquals("Nur ein synthetisches Teilergebnis", state.partialTranscript)
        assertTrue(state.finalTranscript.isEmpty())
        assertEquals(0, fixture.output.speakCalls)
        fixture.close()
    }

    @Test
    fun finalTranscriptIsAcceptedExactlyOnceWithoutLeavingLocalState() = runBlocking {
        val fixture = fixture()
        fixture.controller.startMicrophone(permissionGranted = true)
        val listener = fixture.recognizer.currentListener()

        listener.onFinalTranscript("Synthetisches Endergebnis")
        listener.onFinalTranscript("Verspätetes Duplikat")

        assertEquals("Synthetisches Endergebnis", fixture.controller.uiState.value.finalTranscript)
        assertEquals(0, fixture.output.speakCalls)
        fixture.close()
    }

    @Test
    fun localConversationBuildsFixedResponseAndUsesOnlyLocalOutput() = runBlocking {
        val fixture = fixture()

        fixture.controller.startLocalConversation(permissionGranted = true)
        fixture.recognizer.final("Lokaler Test")

        assertEquals(
            "Ich habe verstanden: Lokaler Test. Dies ist eine lokale Testantwort.",
            fixture.controller.uiState.value.localResponse
        )
        assertEquals(1, fixture.output.speakCalls)
        assertEquals(fixture.controller.uiState.value.localResponse, fixture.output.requests.single().text)
        fixture.close()
    }

    @Test
    fun speechTestUsesConfiguredLocalSpeedAndPitch() = runBlocking {
        val fixture = fixture()
        fixture.controller.setSpeechSpeed(1.4f)
        fixture.controller.setSpeechPitch(0.9f)

        fixture.controller.startSpeechTest()

        val request = fixture.output.requests.single()
        assertEquals(LocalAudioHardwareTestController.TEST_SPEECH_TEXT, request.text)
        assertEquals(1.4f, request.speed)
        assertEquals(0.9f, request.pitch)
        assertEquals("de-DE", request.languageTag)
        fixture.close()
    }

    @Test
    fun interruptionStopsTtsAndStaleOutputCannotResume() = runBlocking {
        val output = FakeOutput(blockPlayback = true)
        val fixture = fixture(output = output)
        fixture.controller.setHandsFree(true)
        fixture.controller.startLocalConversation(permissionGranted = true)
        fixture.recognizer.final("Bitte unterbrechen")
        assertEquals(LocalSpeechOutputStatus.SPEAKING, fixture.controller.uiState.value.outputStatus)

        fixture.controller.interruptAndListen(permissionGranted = true)
        output.finishBlockedPlayback()

        assertTrue(output.stopCalls >= 1)
        assertEquals(2, fixture.recognizer.startCalls)
        assertEquals(LocalSpeechOutputStatus.STOPPED, fixture.controller.uiState.value.outputStatus)
        fixture.close()
    }

    @Test
    fun handsFreeRestartsListeningAtMostOnceAfterSuccessfulOutput() = runBlocking {
        val fixture = fixture()
        fixture.controller.setHandsFree(true)
        fixture.controller.startLocalConversation(permissionGranted = true)
        val firstListener = fixture.recognizer.currentListener()

        firstListener.onFinalTranscript("Einmal fortsetzen")
        firstListener.onFinalTranscript("Nicht doppelt fortsetzen")

        assertEquals(1, fixture.output.speakCalls)
        assertEquals(2, fixture.recognizer.startCalls)
        fixture.close()
    }

    @Test
    fun lifecycleStopAndReleaseAreIdempotent() = runBlocking {
        val fixture = fixture(output = FakeOutput(blockPlayback = true))
        fixture.controller.startLocalConversation(permissionGranted = true)
        fixture.recognizer.final("Lifecycle-Test")

        fixture.controller.stopForLifecycle()
        val cancelsAfterFirstStop = fixture.recognizer.cancelCalls
        val outputStopsAfterFirstStop = fixture.output.stopCalls
        fixture.controller.stopForLifecycle()

        assertEquals(cancelsAfterFirstStop, fixture.recognizer.cancelCalls)
        assertEquals(outputStopsAfterFirstStop, fixture.output.stopCalls)
        fixture.controller.release()
        fixture.controller.release()
        assertEquals(1, fixture.recognizer.releaseCalls)
        assertEquals(1, fixture.output.releaseCalls)
        fixture.scope.cancel()
    }

    @Test
    fun diagnosticsNeverContainPartialFinalOrResponseText() = runBlocking {
        val fixture = fixture()
        val transcript = "VERTRAULICHER SYNTHETISCHER TESTTEXT"

        fixture.controller.startLocalConversation(permissionGranted = true)
        fixture.recognizer.partial(transcript)
        fixture.recognizer.final(transcript)

        val diagnostics = fixture.controller.uiState.value.diagnostics.joinToString(" ")
        assertFalse(diagnostics.contains(transcript))
        assertFalse(diagnostics.contains("Ich habe verstanden"))
        fixture.close()
    }

    @Test
    fun fixedResponseNormalizesWhitespaceAndTerminalPunctuation() {
        assertEquals(
            "Ich habe verstanden: Ein lokaler Test. Dies ist eine lokale Testantwort.",
            LocalAudioHardwareTestController.buildLocalTestResponse("  Ein   lokaler Test!  ")
        )
    }

    private fun fixture(
        recognizer: FakeRecognizer = FakeRecognizer(),
        output: FakeOutput = FakeOutput()
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val audioSession = FakeAudioSession()
        return Fixture(
            controller = LocalAudioHardwareTestController(recognizer, output, audioSession, scope),
            recognizer = recognizer,
            output = output,
            audioSession = audioSession,
            scope = scope
        )
    }

    private class Fixture(
        val controller: LocalAudioHardwareTestController,
        val recognizer: FakeRecognizer,
        val output: FakeOutput,
        val audioSession: FakeAudioSession,
        val scope: CoroutineScope
    ) {
        suspend fun close() {
            controller.release()
            scope.cancel()
        }
    }

    private class FakeRecognizer(
        private val available: Boolean = true
    ) : LocalHardwareSpeechRecognizer {
        var startCalls = 0
        var stopCalls = 0
        var cancelCalls = 0
        var releaseCalls = 0
        private var listener: LocalHardwareRecognitionListener? = null

        override fun isAvailable(): Boolean = available

        override suspend fun start(listener: LocalHardwareRecognitionListener): LocalAudioOperationResult {
            startCalls += 1
            this.listener = listener
            listener.onReady()
            return LocalAudioOperationResult.Success
        }

        override suspend fun stop() {
            stopCalls += 1
        }

        override suspend fun cancel() {
            cancelCalls += 1
            listener = null
        }

        override suspend fun release() {
            releaseCalls += 1
            listener = null
        }

        fun currentListener(): LocalHardwareRecognitionListener = checkNotNull(listener)

        fun partial(text: String) = currentListener().onPartialTranscript(text)

        fun final(text: String) = currentListener().onFinalTranscript(text)
    }

    private class FakeOutput(
        private val blockPlayback: Boolean = false
    ) : SpeechOutputEngine {
        override val provider: VoiceOutputProvider = VoiceOutputProvider.ANDROID
        val requests = mutableListOf<SpeechOutputRequest>()
        var speakCalls = 0
        var stopCalls = 0
        var releaseCalls = 0
        private var blockedResult: CompletableDeferred<VoiceOperationResult>? = null

        override suspend fun speak(
            request: SpeechOutputRequest,
            listener: SpeechOutputListener
        ): VoiceOperationResult {
            speakCalls += 1
            requests += request
            listener.onPlaybackStarted()
            return if (blockPlayback) {
                CompletableDeferred<VoiceOperationResult>().also { blockedResult = it }.await()
            } else {
                VoiceOperationResult.Success
            }
        }

        override suspend fun stop() {
            stopCalls += 1
            blockedResult?.complete(cancelledResult())
            blockedResult = null
        }

        override suspend fun pause(): VoiceOperationResult = VoiceOperationResult.Success

        override suspend fun resume(): VoiceOperationResult = VoiceOperationResult.Success

        override suspend fun release() {
            releaseCalls += 1
            blockedResult?.complete(cancelledResult())
            blockedResult = null
        }

        fun finishBlockedPlayback() {
            blockedResult?.complete(VoiceOperationResult.Success)
            blockedResult = null
        }

        private fun cancelledResult() = VoiceOperationResult.Failure(
            VoiceFailure(VoiceFailureCategory.CANCELLED, "Synthetisch gestoppt")
        )
    }

    private class FakeAudioSession : VoiceAudioSession {
        var activateCalls = 0
        var deactivateCalls = 0

        override suspend fun activate(
            purpose: VoiceAudioPurpose,
            onFocusLost: () -> Unit
        ): VoiceOperationResult {
            activateCalls += 1
            return VoiceOperationResult.Success
        }

        override suspend fun deactivate() {
            deactivateCalls += 1
        }
    }
}
