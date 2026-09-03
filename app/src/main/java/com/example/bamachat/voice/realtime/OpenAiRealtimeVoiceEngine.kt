package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.EphemeralVoiceCredential
import com.example.bamachat.voice.NoOpVoiceDiagnostics
import com.example.bamachat.voice.RealtimeEphemeralCredentialProvider
import com.example.bamachat.voice.RealtimeTurnTaking
import com.example.bamachat.voice.RealtimeVoiceEngine
import com.example.bamachat.voice.RealtimeVoiceEvent
import com.example.bamachat.voice.RealtimeVoiceListener
import com.example.bamachat.voice.RealtimeVoiceSessionRequest
import com.example.bamachat.voice.VoiceDiagnostics
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class OpenAiRealtimeVoiceEngine(
    private val credentialProvider: RealtimeEphemeralCredentialProvider,
    private val peerConnectionFactory: RealtimePeerConnectionFactory,
    private val diagnostics: VoiceDiagnostics = NoOpVoiceDiagnostics,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val reconnectDelay: suspend (Long) -> Unit = { delay(it) },
    private val engineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : RealtimeVoiceEngine {
    override val isAvailable: Boolean
        get() = credentialProvider.isConfigured

    private val lifecycleMutex = Mutex()
    private var activePeer: RealtimePeerConnectionController? = null
    private var activeCredential: EphemeralVoiceCredential? = null
    private var activeRequest: RealtimeVoiceSessionRequest? = null
    private var activeListener: RealtimeVoiceListener? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var hasConnected = false
    private var stopping = false

    override suspend fun start(
        request: RealtimeVoiceSessionRequest,
        listener: RealtimeVoiceListener
    ): VoiceOperationResult {
        stopping = true
        cancelReconnect()
        return lifecycleMutex.withLock {
            stopInternal(clearListener = true)
            if (!isAvailable) {
                return@withLock VoiceOperationResult.Failure(
                    VoiceFailure(
                        VoiceFailureCategory.UNSUPPORTED,
                        "Für Live-Unterhaltung muss zuerst der sichere BamaVoice-Server eingerichtet werden.",
                        recoverable = false
                    )
                )
            }
            stopping = false
            activeRequest = request
            activeListener = listener
            reconnectAttempt = 0
            hasConnected = false
            listener.onEvent(RealtimeVoiceEvent.Connecting)
            connectOnce(request, listener)
        }
    }

    override suspend fun mute(muted: Boolean) {
        activePeer?.setMicrophoneMuted(muted)
    }

    override suspend fun beginUserTurn() {
        val request = activeRequest ?: return
        val peer = activePeer ?: return
        if (request.turnTaking == RealtimeTurnTaking.PUSH_TO_TALK && request.interruptResponse) {
            peer.setPlaybackMuted(true)
            peer.sendClientEvent(clientEvent("input_audio_buffer.clear"))
            peer.sendClientEvent(clientEvent("response.cancel"))
            peer.sendClientEvent(clientEvent("output_audio_buffer.clear"))
        }
        peer.setMicrophoneMuted(false)
    }

    override suspend fun finishUserTurn() {
        val request = activeRequest ?: return
        val peer = activePeer ?: return
        if (request.turnTaking == RealtimeTurnTaking.PUSH_TO_TALK) {
            peer.setMicrophoneMuted(true)
            peer.sendClientEvent(clientEvent("input_audio_buffer.commit"))
            peer.sendClientEvent(clientEvent("response.create"))
        } else {
            peer.setMicrophoneMuted(true)
        }
    }

    override suspend fun interrupt() {
        activePeer?.let { peer ->
            peer.setPlaybackMuted(true)
            peer.sendClientEvent(clientEvent("response.cancel"))
            peer.sendClientEvent(clientEvent("output_audio_buffer.clear"))
        }
    }

    override suspend fun stop() {
        stopping = true
        cancelReconnect()
        lifecycleMutex.withLock {
            stopInternal(clearListener = true)
        }
    }

    override suspend fun release() {
        stopping = true
        cancelReconnect()
        lifecycleMutex.withLock {
            stopInternal(clearListener = true)
        }
        engineScope.cancel()
    }

    private suspend fun connectOnce(
        request: RealtimeVoiceSessionRequest,
        listener: RealtimeVoiceListener
    ): VoiceOperationResult {
        val credential = credentialProvider.requestCredential(request).getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            return VoiceOperationResult.Failure(
                (throwable as? RealtimeVoiceException)?.failure ?: VoiceFailure(
                    VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                    "Die sichere Live-Verbindung konnte nicht vorbereitet werden."
                )
            )
        }
        if (credential.expiresAtEpochSeconds <= nowEpochSeconds() + MIN_CREDENTIAL_LIFETIME_SECONDS) {
            credentialProvider.releaseCredential(credential.leaseId)
            return VoiceOperationResult.Failure(
                VoiceFailure(
                    VoiceFailureCategory.AUTHENTICATION_REQUIRED,
                    "Die kurzlebige Live-Berechtigung ist bereits abgelaufen. Bitte starte erneut."
                )
            )
        }

        activeCredential = credential
        val peer = peerConnectionFactory.create()
        activePeer = peer
        val result = peer.connect(
            credential = credential,
            onEvent = { rawEvent ->
                RealtimeEventMapper.map(rawEvent)?.let { event ->
                    if (event is RealtimeVoiceEvent.ResponseCreated) {
                        engineScope.launch {
                            if (activePeer === peer) peer.setPlaybackMuted(false)
                        }
                    }
                    listener.onEvent(event)
                }
            },
            onConnectionState = { state -> handleConnectionState(peer, state) }
        )
        if (result is VoiceOperationResult.Failure) {
            activePeer = null
            peer.close()
            credentialProvider.releaseCredential(credential.leaseId)
            activeCredential = null
            diagnostics.event(
                "voice_realtime_connection_failed",
                mapOf("category" to result.error.category.name.lowercase())
            )
            return result
        }
        if (request.turnTaking == RealtimeTurnTaking.PUSH_TO_TALK) {
            peer.setMicrophoneMuted(true)
        }
        listener.onEvent(RealtimeVoiceEvent.SessionStarted(credential.sessionExpiresAtEpochSeconds))
        diagnostics.event("voice_realtime_connected", mapOf("model" to request.model, "voice" to request.voice))
        return VoiceOperationResult.Success
    }

    private fun handleConnectionState(
        sourcePeer: RealtimePeerConnectionController,
        state: RealtimeConnectionState
    ) {
        if (sourcePeer !== activePeer) return
        val listener = activeListener ?: return
        when (state) {
            RealtimeConnectionState.CONNECTING -> listener.onEvent(RealtimeVoiceEvent.Connecting)
            RealtimeConnectionState.CONNECTED -> {
                reconnectAttempt = 0
                hasConnected = true
                listener.onEvent(RealtimeVoiceEvent.Connected)
            }
            RealtimeConnectionState.DISCONNECTED,
            RealtimeConnectionState.FAILED -> if (hasConnected) scheduleReconnect()
            RealtimeConnectionState.CLOSED -> listener.onEvent(RealtimeVoiceEvent.Closed)
        }
    }

    private fun scheduleReconnect() {
        if (stopping || reconnectJob?.isActive == true) return
        val request = activeRequest ?: return
        val listener = activeListener ?: return
        reconnectJob = engineScope.launch {
            lifecycleMutex.withLock {
                if (stopping || activeRequest == null) return@withLock
                var lastFailure: VoiceFailure? = null
                while (reconnectAttempt < MAX_RECONNECT_ATTEMPTS && !stopping) {
                    reconnectAttempt += 1
                    listener.onEvent(
                        RealtimeVoiceEvent.Reconnecting(reconnectAttempt, MAX_RECONNECT_ATTEMPTS)
                    )
                    disconnectCurrentPeer()
                    reconnectDelay(RECONNECT_BACKOFF_MS * reconnectAttempt)
                    val result = connectOnce(request, listener)
                    if (result is VoiceOperationResult.Success) return@withLock
                    lastFailure = (result as VoiceOperationResult.Failure).error
                }
                listener.onEvent(
                    RealtimeVoiceEvent.Failure(
                        lastFailure ?: VoiceFailure(
                            VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                            "Die Live-Verbindung konnte nicht wiederhergestellt werden."
                        )
                    )
                )
            }
        }
    }

    private suspend fun stopInternal(clearListener: Boolean) {
        stopping = true
        cancelReconnect()
        disconnectCurrentPeer()
        activeRequest = null
        reconnectAttempt = 0
        hasConnected = false
        if (clearListener) activeListener = null
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private suspend fun disconnectCurrentPeer() {
        val peer = activePeer
        activePeer = null
        peer?.close()
        val credential = activeCredential
        activeCredential = null
        credential?.leaseId?.let { credentialProvider.releaseCredential(it) }
    }

    private fun clientEvent(type: String): String = JSONObject()
        .put("event_id", "bamachat_${System.nanoTime()}")
        .put("type", type)
        .toString()

    companion object {
        private const val MIN_CREDENTIAL_LIFETIME_SECONDS = 5L
        private const val MAX_RECONNECT_ATTEMPTS = 2
        private const val RECONNECT_BACKOFF_MS = 500L
    }
}
