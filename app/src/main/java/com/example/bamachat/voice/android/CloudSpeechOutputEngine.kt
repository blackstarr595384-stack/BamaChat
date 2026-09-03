package com.example.bamachat.voice.android

import com.example.bamachat.util.CloudVoiceManager
import com.example.bamachat.voice.SpeechOutputEngine
import com.example.bamachat.voice.SpeechOutputListener
import com.example.bamachat.voice.SpeechOutputRequest
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import com.example.bamachat.voice.VoiceOutputProvider

class CloudSpeechOutputEngine(
    private val manager: CloudVoiceManager,
    private val config: CloudVoiceManager.CloudVoiceConfig,
    private val voiceStyle: CloudVoiceManager.VoiceStyle
) : SpeechOutputEngine {
    override val provider: VoiceOutputProvider = when (config.provider) {
        CloudVoiceManager.Provider.ELEVENLABS -> VoiceOutputProvider.ELEVENLABS
        CloudVoiceManager.Provider.PIPER -> VoiceOutputProvider.PIPER
    }

    override suspend fun speak(
        request: SpeechOutputRequest,
        listener: SpeechOutputListener
    ): VoiceOperationResult {
        val successful = manager.speak(
            text = request.text,
            config = config,
            voiceStyle = voiceStyle,
            onPlaybackStarted = listener::onPlaybackStarted
        )
        return if (successful) {
            VoiceOperationResult.Success
        } else {
            VoiceOperationResult.Failure(
                VoiceFailure(
                    manager.lastErrorCategory().toVoiceFailureCategory(),
                    manager.lastErrorMessage() ?: "Cloud-Sprachausgabe ist momentan nicht verfügbar."
                )
            )
        }
    }

    override suspend fun stop() = manager.stop()

    override suspend fun pause(): VoiceOperationResult = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.UNSUPPORTED, "Pausieren wird von dieser Sprachausgabe nicht unterstützt.")
    )

    override suspend fun resume(): VoiceOperationResult = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.UNSUPPORTED, "Fortsetzen wird von dieser Sprachausgabe nicht unterstützt.")
    )

    override suspend fun release() {
        manager.stop()
        manager.release()
    }

    private fun CloudVoiceManager.FailureCategory?.toVoiceFailureCategory(): VoiceFailureCategory = when (this) {
        CloudVoiceManager.FailureCategory.OFFLINE -> VoiceFailureCategory.OFFLINE
        CloudVoiceManager.FailureCategory.AUTHENTICATION_REQUIRED -> VoiceFailureCategory.AUTHENTICATION_REQUIRED
        CloudVoiceManager.FailureCategory.RATE_LIMITED -> VoiceFailureCategory.RATE_LIMITED
        CloudVoiceManager.FailureCategory.UNSUPPORTED -> VoiceFailureCategory.UNSUPPORTED
        CloudVoiceManager.FailureCategory.TIMEOUT -> VoiceFailureCategory.TIMEOUT
        CloudVoiceManager.FailureCategory.TEMPORARY_SERVICE_ERROR,
        null -> VoiceFailureCategory.TEMPORARY_SERVICE_ERROR
    }
}
