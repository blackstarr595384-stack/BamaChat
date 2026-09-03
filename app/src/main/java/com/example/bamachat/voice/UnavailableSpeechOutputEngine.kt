package com.example.bamachat.voice

class UnavailableSpeechOutputEngine(
    override val provider: VoiceOutputProvider,
    private val userMessage: String
) : SpeechOutputEngine {
    override suspend fun speak(
        request: SpeechOutputRequest,
        listener: SpeechOutputListener
    ): VoiceOperationResult = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.UNSUPPORTED, userMessage)
    )

    override suspend fun stop() = Unit

    override suspend fun pause(): VoiceOperationResult = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.UNSUPPORTED, userMessage)
    )

    override suspend fun resume(): VoiceOperationResult = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.UNSUPPORTED, userMessage)
    )

    override suspend fun release() = Unit
}
