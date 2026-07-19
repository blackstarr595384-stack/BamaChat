package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.EphemeralVoiceCredential
import com.example.bamachat.voice.VoiceOperationResult

enum class RealtimeConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSED
}

interface RealtimePeerConnectionController {
    suspend fun connect(
        credential: EphemeralVoiceCredential,
        onEvent: (String) -> Unit,
        onConnectionState: (RealtimeConnectionState) -> Unit
    ): VoiceOperationResult

    suspend fun setMicrophoneMuted(muted: Boolean)
    suspend fun setPlaybackMuted(muted: Boolean)
    suspend fun sendClientEvent(event: String): Boolean
    suspend fun close()
}

fun interface RealtimePeerConnectionFactory {
    fun create(): RealtimePeerConnectionController
}
