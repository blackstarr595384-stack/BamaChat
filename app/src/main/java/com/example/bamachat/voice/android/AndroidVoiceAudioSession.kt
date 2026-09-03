package com.example.bamachat.voice.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.example.bamachat.voice.VoiceAudioPurpose
import com.example.bamachat.voice.VoiceAudioSession
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidVoiceAudioSession(context: Context) : VoiceAudioSession {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var previousMode: Int? = null
    private var focusLossCallback: (() -> Unit)? = null

    override suspend fun activate(
        purpose: VoiceAudioPurpose,
        onFocusLost: () -> Unit
    ): VoiceOperationResult = withContext(Dispatchers.Main.immediate) {
        deactivateInternal()
        focusLossCallback = onFocusLost
        if (previousMode == null) previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (purpose == VoiceAudioPurpose.LISTENING) {
            return@withContext VoiceOperationResult.Success
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> focusLossCallback?.invoke()
                }
            }
            .build()
        focusRequest = request
        if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            VoiceOperationResult.Success
        } else {
            deactivateInternal()
            VoiceOperationResult.Failure(
                VoiceFailure(
                    VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                    "Audio wird gerade von einer anderen App verwendet."
                )
            )
        }
    }

    override suspend fun deactivate() = withContext(Dispatchers.Main.immediate) {
        deactivateInternal()
    }

    private fun deactivateInternal() {
        focusRequest?.let { request -> runCatching { audioManager.abandonAudioFocusRequest(request) } }
        focusRequest = null
        focusLossCallback = null
        previousMode?.let { mode -> runCatching { audioManager.mode = mode } }
        previousMode = null
    }
}
