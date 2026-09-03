package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.EphemeralVoiceCredential
import com.example.bamachat.voice.RealtimeEphemeralCredentialProvider
import com.example.bamachat.voice.RealtimeTurnTaking
import com.example.bamachat.voice.RealtimeVoiceEvent
import com.example.bamachat.voice.RealtimeVoiceListener
import com.example.bamachat.voice.RealtimeVoiceSessionRequest
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiRealtimeVoiceEngineTest {
    @Test
    fun repeatedStopAndReleaseCleanUpPeerAndLeaseAtMostOnce() = runBlocking {
        val credentials = FakeCredentialProvider()
        val peer = FakePeerConnection()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = engine(credentials, FakePeerFactory(listOf(peer)), scope)
        val events = mutableListOf<RealtimeVoiceEvent>()

        val result = engine.start(request(), RealtimeVoiceListener(events::add))
        engine.stop()
        engine.stop()
        engine.release()

        assertEquals(VoiceOperationResult.Success, result)
        assertEquals(1, credentials.requestCalls)
        assertEquals(1, peer.closeCalls)
        assertEquals(listOf(LEASE_ID), credentials.releasedLeases)
        assertTrue(events.contains(RealtimeVoiceEvent.Connecting))
        assertTrue(events.contains(RealtimeVoiceEvent.Connected))
        assertTrue(events.any { it is RealtimeVoiceEvent.SessionStarted })
        scope.cancel()
    }

    @Test
    fun connectingStateDoesNotCloseOrReconnectTheActiveSession() = runBlocking {
        val credentials = FakeCredentialProvider()
        val peer = FakePeerConnection()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = engine(credentials, FakePeerFactory(listOf(peer)), scope)
        val events = mutableListOf<RealtimeVoiceEvent>()
        engine.start(request(), RealtimeVoiceListener(events::add))

        peer.emitState(RealtimeConnectionState.CONNECTING)

        assertEquals(1, credentials.requestCalls)
        assertEquals(0, peer.closeCalls)
        assertTrue(events.last() is RealtimeVoiceEvent.Connecting)
        assertFalse(events.any { it is RealtimeVoiceEvent.Failure })
        engine.release()
        scope.cancel()
    }

    @Test
    fun expiredCredentialNeverCreatesPeerConnection() = runBlocking {
        val credentials = FakeCredentialProvider(expiresAt = NOW_SECONDS + 4)
        val peers = FakePeerFactory(emptyList())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = engine(credentials, peers, scope)

        val result = engine.start(request(), RealtimeVoiceListener { })

        assertTrue(result is VoiceOperationResult.Failure)
        assertEquals(
            VoiceFailureCategory.AUTHENTICATION_REQUIRED,
            (result as VoiceOperationResult.Failure).error.category
        )
        assertEquals(0, peers.createdCount)
        assertEquals(listOf(LEASE_ID), credentials.releasedLeases)
        engine.release()
        scope.cancel()
    }

    @Test
    fun interruptionCancelsAndClearsRemoteAudioWithoutStartingAnotherProvider() = runBlocking {
        val credentials = FakeCredentialProvider()
        val peer = FakePeerConnection()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = engine(credentials, FakePeerFactory(listOf(peer)), scope)
        engine.start(request(), RealtimeVoiceListener { })

        engine.interrupt()

        assertTrue(peer.playbackMuted)
        assertTrue(peer.sentEvents.any { it.contains("response.cancel") })
        assertTrue(peer.sentEvents.any { it.contains("output_audio_buffer.clear") })
        assertFalse(peer.sentEvents.any { it.contains("response.create") })
        peer.emitRawEvent("""{"type":"response.created","response":{"id":"response-next"}}""")
        assertFalse(peer.playbackMuted)
        engine.release()
        scope.cancel()
    }

    @Test
    fun reconnectCreatesFreshCredentialsAndStopsAfterTwoAttempts() = runBlocking {
        val credentials = FakeCredentialProvider()
        val initialPeer = FakePeerConnection()
        val reconnectOne = FakePeerConnection(
            connectResult = VoiceOperationResult.Failure(temporaryFailure())
        )
        val reconnectTwo = FakePeerConnection(
            connectResult = VoiceOperationResult.Failure(temporaryFailure())
        )
        val peers = FakePeerFactory(listOf(initialPeer, reconnectOne, reconnectTwo))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = engine(credentials, peers, scope)
        val events = mutableListOf<RealtimeVoiceEvent>()
        engine.start(request(), RealtimeVoiceListener(events::add))

        initialPeer.emitState(RealtimeConnectionState.DISCONNECTED)

        assertEquals(3, credentials.requestCalls)
        assertEquals(3, peers.createdCount)
        assertEquals(listOf(1, 2), events.filterIsInstance<RealtimeVoiceEvent.Reconnecting>().map { it.attempt })
        assertTrue(events.last() is RealtimeVoiceEvent.Failure)
        assertEquals(3, credentials.releasedLeases.size)
        engine.release()
        scope.cancel()
    }

    @Test
    fun stopCancelsReconnectBackoffBeforeWaitingForLifecycleMutex() = runBlocking {
        val credentials = FakeCredentialProvider()
        val initialPeer = FakePeerConnection()
        val peers = FakePeerFactory(listOf(initialPeer))
        val reconnectStarted = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = OpenAiRealtimeVoiceEngine(
            credentialProvider = credentials,
            peerConnectionFactory = peers,
            nowEpochSeconds = { NOW_SECONDS },
            reconnectDelay = {
                reconnectStarted.complete(Unit)
                awaitCancellation()
            },
            engineScope = scope
        )
        engine.start(request(), RealtimeVoiceListener { })
        initialPeer.emitState(RealtimeConnectionState.DISCONNECTED)
        reconnectStarted.await()

        withTimeout(1_000L) { engine.stop() }

        assertTrue(initialPeer.closeCalls >= 1)
        assertEquals(listOf(LEASE_ID), credentials.releasedLeases)
        engine.release()
        scope.cancel()
    }

    @Test
    fun remotePeerCloseMapsToLiveSessionClosedEvent() = runBlocking {
        val credentials = FakeCredentialProvider()
        val peer = FakePeerConnection()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = engine(credentials, FakePeerFactory(listOf(peer)), scope)
        val events = mutableListOf<RealtimeVoiceEvent>()
        engine.start(request(), RealtimeVoiceListener(events::add))

        peer.emitState(RealtimeConnectionState.CLOSED)

        assertTrue(events.last() is RealtimeVoiceEvent.Closed)
        engine.release()
        scope.cancel()
    }

    private fun engine(
        credentials: FakeCredentialProvider,
        peers: FakePeerFactory,
        scope: CoroutineScope
    ) = OpenAiRealtimeVoiceEngine(
        credentialProvider = credentials,
        peerConnectionFactory = peers,
        nowEpochSeconds = { NOW_SECONDS },
        reconnectDelay = { },
        engineScope = scope
    )

    private fun request() = RealtimeVoiceSessionRequest(
        provider = "openai",
        model = "gpt-realtime",
        voice = "marin",
        languageTag = "de-DE",
        personaName = "BamaChat",
        turnTaking = RealtimeTurnTaking.SEMANTIC
    )

    private fun temporaryFailure() = VoiceFailure(
        VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
        "Temporärer Testfehler"
    )

    private class FakeCredentialProvider(
        private val expiresAt: Long = NOW_SECONDS + 90
    ) : RealtimeEphemeralCredentialProvider {
        override val isConfigured = true
        var requestCalls = 0
        val releasedLeases = mutableListOf<String>()

        override suspend fun requestCredential(
            request: RealtimeVoiceSessionRequest
        ): Result<EphemeralVoiceCredential> {
            requestCalls++
            return Result.success(
                EphemeralVoiceCredential(
                    value = "short-lived-$requestCalls",
                    expiresAtEpochSeconds = expiresAt,
                    model = request.model,
                    voice = request.voice,
                    leaseId = LEASE_ID,
                    sessionExpiresAtEpochSeconds = NOW_SECONDS + 900
                )
            )
        }

        override suspend fun releaseCredential(leaseId: String) {
            releasedLeases += leaseId
        }
    }

    private class FakePeerFactory(
        peers: List<FakePeerConnection>
    ) : RealtimePeerConnectionFactory {
        private val queue = ArrayDeque(peers)
        var createdCount = 0

        override fun create(): RealtimePeerConnectionController {
            createdCount++
            return queue.removeFirst()
        }
    }

    private class FakePeerConnection(
        private val connectResult: VoiceOperationResult = VoiceOperationResult.Success
    ) : RealtimePeerConnectionController {
        val sentEvents = mutableListOf<String>()
        var closeCalls = 0
        var playbackMuted = false
        private var stateListener: ((RealtimeConnectionState) -> Unit)? = null
        private var eventListener: ((String) -> Unit)? = null

        override suspend fun connect(
            credential: EphemeralVoiceCredential,
            onEvent: (String) -> Unit,
            onConnectionState: (RealtimeConnectionState) -> Unit
        ): VoiceOperationResult {
            stateListener = onConnectionState
            eventListener = onEvent
            onConnectionState(RealtimeConnectionState.CONNECTING)
            if (connectResult == VoiceOperationResult.Success) {
                onConnectionState(RealtimeConnectionState.CONNECTED)
            }
            return connectResult
        }

        override suspend fun setMicrophoneMuted(muted: Boolean) = Unit

        override suspend fun setPlaybackMuted(muted: Boolean) {
            playbackMuted = muted
        }

        override suspend fun sendClientEvent(event: String): Boolean {
            sentEvents += event
            return true
        }

        override suspend fun close() {
            closeCalls++
        }

        fun emitState(state: RealtimeConnectionState) {
            stateListener?.invoke(state)
        }

        fun emitRawEvent(event: String) {
            eventListener?.invoke(event)
        }
    }

    companion object {
        private const val NOW_SECONDS = 1_000L
        private const val LEASE_ID = "lease-1234567890123456"
    }
}
