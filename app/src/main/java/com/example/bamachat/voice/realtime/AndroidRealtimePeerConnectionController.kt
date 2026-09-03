package com.example.bamachat.voice.realtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import com.example.bamachat.voice.AppVoiceDiagnostics
import com.example.bamachat.voice.EphemeralVoiceCredential
import com.example.bamachat.voice.VoiceDiagnostics
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class AndroidRealtimePeerConnectionController(
    context: Context,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val diagnostics: VoiceDiagnostics = AppVoiceDiagnostics
) : RealtimePeerConnectionController {
    private val appContext = context.applicationContext
    private val audioFocusController = RealtimeAudioFocusController(appContext)
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val sdpExchange = OpenAiRealtimeSdpExchange(httpClient, diagnostics)
    @Volatile
    private var closed = false

    override suspend fun connect(
        credential: EphemeralVoiceCredential,
        onEvent: (String) -> Unit,
        onConnectionState: (RealtimeConnectionState) -> Unit
    ): VoiceOperationResult = withContext(dispatcher) {
        closeInternal()
        closed = false
        onConnectionState(RealtimeConnectionState.CONNECTING)
        if (!audioFocusController.acquire {
                peerConnection?.setAudioPlayout(false)
                peerConnection?.setAudioRecording(false)
                onConnectionState(RealtimeConnectionState.FAILED)
            }
        ) {
            return@withContext failure("Audio konnte nicht für Live-Unterhaltung reserviert werden.")
        }

        val ready = CompletableDeferred<Unit>()
        val readinessGate = RealtimeConnectionReadinessGate {
            if (!closed && !ready.isCompleted) {
                onConnectionState(RealtimeConnectionState.CONNECTED)
                ready.complete(Unit)
            }
        }

        try {
            WebRtcRuntime.ensureInitialized(appContext)
            val adm = JavaAudioDeviceModule.builder(appContext)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setUseHardwareAcousticEchoCanceler(
                    JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
                )
                .setUseHardwareNoiseSuppressor(
                    JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()
                )
                .setUseLowLatency(true)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .createAudioDeviceModule()
            audioDeviceModule = adm
            val factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .createPeerConnectionFactory()
            peerConnectionFactory = factory

            val source = factory.createAudioSource(audioConstraints())
            audioSource = source
            val track = factory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, source)
            localAudioTrack = track

            val observer = object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
                override fun onIceCandidate(candidate: IceCandidate) = Unit
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                override fun onAddStream(stream: MediaStream) = Unit
                override fun onRemoveStream(stream: MediaStream) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onDataChannel(channel: DataChannel) {
                    if (dataChannel == null) registerDataChannel(channel, onEvent) {
                        readinessGate.markDataChannelOpen()
                    }
                }

                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                    (receiver.track() as? AudioTrack)?.apply {
                        setEnabled(true)
                        setVolume(1.0)
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            readinessGate.markPeerConnected()
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED ->
                            onConnectionState(RealtimeConnectionState.DISCONNECTED)
                        PeerConnection.PeerConnectionState.FAILED -> {
                            if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException("peer_failed"))
                            onConnectionState(RealtimeConnectionState.FAILED)
                        }
                        PeerConnection.PeerConnectionState.CLOSED ->
                            onConnectionState(RealtimeConnectionState.CLOSED)
                        else -> Unit
                    }
                }
            }

            val rtcConfiguration = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                enableDscp = true
            }
            val peer = factory.createPeerConnection(rtcConfiguration, observer)
                ?: throw IllegalStateException("peer_creation_failed")
            peerConnection = peer
            peer.addTrack(track, listOf(LOCAL_STREAM_ID))
            val channel = peer.createDataChannel(DATA_CHANNEL_LABEL, DataChannel.Init())
                ?: throw IllegalStateException("data_channel_failed")
            registerDataChannel(channel, onEvent) {
                readinessGate.markDataChannelOpen()
            }
            performRealtimeSdpHandshake(
                peer = object : RealtimeSdpHandshakePeer {
                    override suspend fun createOffer(): RealtimeSdpDescription {
                        val offer = peer.createOfferSuspend(audioOfferConstraints())
                        return RealtimeSdpDescription(
                            RealtimeSdpDescriptionType.OFFER,
                            offer.description
                        )
                    }

                    override suspend fun setLocalDescription(
                        description: RealtimeSdpDescription
                    ) {
                        peer.setLocalDescriptionSuspend(description.toWebRtcDescription())
                    }

                    override suspend fun setRemoteDescription(
                        description: RealtimeSdpDescription
                    ) {
                        peer.setRemoteDescriptionSuspend(description.toWebRtcDescription())
                    }
                },
                exchange = sdpExchange,
                clientSecret = credential.value
            )
            val connected = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                runCatching { ready.await() }.isSuccess
            } == true
            if (!connected) throw IllegalStateException("connection_timeout")
            VoiceOperationResult.Success
        } catch (cancellation: CancellationException) {
            closeInternal()
            throw cancellation
        } catch (_: Exception) {
            closeInternal()
            onConnectionState(RealtimeConnectionState.FAILED)
            failure("Die direkte Live-Audioverbindung konnte nicht hergestellt werden.")
        }
    }

    override suspend fun setMicrophoneMuted(muted: Boolean) = withContext(dispatcher) {
        localAudioTrack?.setEnabled(!muted)
        audioDeviceModule?.setMicrophoneMute(muted)
        Unit
    }

    override suspend fun setPlaybackMuted(muted: Boolean) = withContext(dispatcher) {
        audioDeviceModule?.setSpeakerMute(muted)
        peerConnection?.setAudioPlayout(!muted)
        Unit
    }

    override suspend fun sendClientEvent(event: String): Boolean = withContext(dispatcher) {
        if (event.length > MAX_CLIENT_EVENT_CHARS) return@withContext false
        val channel = dataChannel ?: return@withContext false
        if (channel.state() != DataChannel.State.OPEN) return@withContext false
        val bytes = event.toByteArray(StandardCharsets.UTF_8)
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    override suspend fun close() = withContext(dispatcher) {
        closeInternal()
    }

    private fun registerDataChannel(
        channel: DataChannel,
        onEvent: (String) -> Unit,
        onOpen: () -> Unit
    ) {
        dataChannel?.takeIf { it !== channel }?.runCatchingDispose()
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                if (channel.state() == DataChannel.State.OPEN) onOpen()
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val data = buffer.data.slice()
                if (data.remaining() > MAX_SERVER_EVENT_BYTES) return
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                onEvent(String(bytes, StandardCharsets.UTF_8))
            }
        })
        if (channel.state() == DataChannel.State.OPEN) onOpen()
    }

    @Synchronized
    private fun closeInternal() {
        if (closed) return
        closed = true
        dataChannel?.runCatchingDispose()
        dataChannel = null
        peerConnection?.runCatching {
            close()
            dispose()
        }
        peerConnection = null
        localAudioTrack?.runCatching { dispose() }
        localAudioTrack = null
        audioSource?.runCatching { dispose() }
        audioSource = null
        peerConnectionFactory?.runCatching { dispose() }
        peerConnectionFactory = null
        audioDeviceModule?.runCatching { release() }
        audioDeviceModule = null
        audioFocusController.release()
    }

    private fun DataChannel.runCatchingDispose() {
        runCatching { unregisterObserver() }
        runCatching { close() }
        runCatching { dispose() }
    }

    private fun failure(message: String) = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.TEMPORARY_SERVICE_ERROR, message)
    )

    private fun RealtimeSdpDescription.toWebRtcDescription(): SessionDescription =
        SessionDescription(
            when (type) {
                RealtimeSdpDescriptionType.OFFER -> SessionDescription.Type.OFFER
                RealtimeSdpDescriptionType.ANSWER -> SessionDescription.Type.ANSWER
            },
            sdp
        )

    private suspend fun PeerConnection.createOfferSuspend(
        constraints: MediaConstraints
    ): SessionDescription = suspendCancellableCoroutine { continuation ->
        createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                if (continuation.isActive) continuation.resume(description)
            }

            override fun onCreateFailure(error: String) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(IllegalStateException("offer_failed")))
                }
            }
        }, constraints)
    }

    private suspend fun PeerConnection.setLocalDescriptionSuspend(
        description: SessionDescription
    ) = setDescriptionSuspend { observer -> setLocalDescription(observer, description) }

    private suspend fun PeerConnection.setRemoteDescriptionSuspend(
        description: SessionDescription
    ) = setDescriptionSuspend { observer -> setRemoteDescription(observer, description) }

    private suspend fun setDescriptionSuspend(
        setter: (SdpObserver) -> Unit
    ): Unit = suspendCancellableCoroutine { continuation ->
        setter(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onSetFailure(error: String) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(IllegalStateException("sdp_set_failed")))
                }
            }
        })
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    companion object {
        private const val LOCAL_STREAM_ID = "bamachat-live-stream"
        private const val LOCAL_AUDIO_TRACK_ID = "bamachat-live-microphone"
        private const val DATA_CHANNEL_LABEL = "oai-events"
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val MAX_SERVER_EVENT_BYTES = 256 * 1024
        private const val MAX_CLIENT_EVENT_CHARS = 8_192
        private fun audioConstraints() = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        private fun audioOfferConstraints() = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

object RealtimeAudioRoutePolicy {
    fun noiseReductionMode(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "near_field"
        val activeDevice = runCatching { audioManager.communicationDevice }.getOrNull()
        return if (activeDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "far_field" else "near_field"
    }
}

private object WebRtcRuntime {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            initialized = true
        }
    }
}

private class RealtimeAudioFocusController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var previousMode: Int? = null

    fun acquire(onFocusLost: () -> Unit): Boolean {
        release()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    onFocusLost()
                }
            }
            .build()
        val result = audioManager.requestAudioFocus(request)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focusRequest = request
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        return true
    }

    fun release() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        previousMode?.let { audioManager.mode = it }
        previousMode = null
    }
}
