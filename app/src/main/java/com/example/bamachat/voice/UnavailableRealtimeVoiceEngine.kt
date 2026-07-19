package com.example.bamachat.voice

class UnavailableRealtimeVoiceEngine : RealtimeVoiceEngine {
    override val isAvailable: Boolean = false

    override suspend fun start(
        request: RealtimeVoiceSessionRequest,
        listener: RealtimeVoiceListener
    ): VoiceOperationResult =
        VoiceOperationResult.Failure(
            VoiceFailure(
                category = VoiceFailureCategory.AUTHENTICATION_REQUIRED,
                userMessage = "Live-Unterhaltung benötigt einen sicheren Backend-Endpunkt für kurzlebige Zugangsdaten."
            )
        )

    override suspend fun mute(muted: Boolean) = Unit
    override suspend fun beginUserTurn() = Unit
    override suspend fun finishUserTurn() = Unit
    override suspend fun interrupt() = Unit
    override suspend fun stop() = Unit
    override suspend fun release() = Unit
}
