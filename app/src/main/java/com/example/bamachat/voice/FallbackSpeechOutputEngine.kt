package com.example.bamachat.voice

class FallbackSpeechOutputEngine(
    private val primary: SpeechOutputEngine,
    private val fallback: SpeechOutputEngine
) : SpeechOutputEngine {
    override val provider: VoiceOutputProvider = primary.provider

    override suspend fun speak(
        request: SpeechOutputRequest,
        listener: SpeechOutputListener
    ): VoiceOperationResult {
        var primaryPlaybackStarted = false
        val primaryResult = primary.speak(
            request,
            object : SpeechOutputListener {
                override fun onPlaybackStarted() {
                    primaryPlaybackStarted = true
                    listener.onPlaybackStarted()
                }
            }
        )
        if (primaryResult is VoiceOperationResult.Success) return primaryResult
        if (primaryPlaybackStarted) return primaryResult
        return fallback.speak(request, listener)
    }

    override suspend fun stop() {
        primary.stop()
        fallback.stop()
    }

    override suspend fun pause(): VoiceOperationResult {
        val primaryResult = primary.pause()
        return if (primaryResult is VoiceOperationResult.Success) primaryResult else fallback.pause()
    }

    override suspend fun resume(): VoiceOperationResult {
        val primaryResult = primary.resume()
        return if (primaryResult is VoiceOperationResult.Success) primaryResult else fallback.resume()
    }

    override suspend fun release() {
        primary.release()
        fallback.release()
    }
}
