package com.example.bamachat.voice

class VoiceSessionMachine {
    var state: VoiceSessionState = VoiceSessionState.Idle
        private set

    var activeSessionId: Long? = null
        private set

    private var nextSessionId = 1L
    private var lastDeliveredFinalSessionId: Long? = null

    fun beginPreparing(): Long? {
        if (state is VoiceSessionState.Preparing || state is VoiceSessionState.Listening || state is VoiceSessionState.Transcribing) {
            return null
        }
        val sessionId = nextSessionId++
        activeSessionId = sessionId
        state = VoiceSessionState.Preparing
        return sessionId
    }

    fun listening(sessionId: Long): Boolean {
        if (activeSessionId != sessionId) return false
        state = VoiceSessionState.Listening
        return true
    }

    fun connecting() {
        activeSessionId = null
        state = VoiceSessionState.Connecting
    }

    fun reconnecting(attempt: Int, maximumAttempts: Int) {
        activeSessionId = null
        state = VoiceSessionState.Reconnecting(attempt, maximumAttempts)
    }

    fun realtimeListening() {
        activeSessionId = null
        state = VoiceSessionState.Listening
    }

    fun realtimeTranscribing(text: String) {
        activeSessionId = null
        state = VoiceSessionState.Transcribing(text.trim())
    }

    fun partial(sessionId: Long, text: String): Boolean {
        if (activeSessionId != sessionId) return false
        val cleanText = text.trim()
        if (cleanText.isBlank()) return false
        state = VoiceSessionState.Transcribing(cleanText)
        return true
    }

    fun awaitingFinal(sessionId: Long): Boolean {
        if (activeSessionId != sessionId) return false
        val currentPartial = (state as? VoiceSessionState.Transcribing)?.partialText.orEmpty()
        state = VoiceSessionState.Transcribing(currentPartial)
        return true
    }

    fun finalTranscript(sessionId: Long, text: String): VoiceFinalTranscript? {
        if (activeSessionId != sessionId || lastDeliveredFinalSessionId == sessionId) return null
        lastDeliveredFinalSessionId = sessionId
        activeSessionId = null
        val cleanText = text.trim()
        state = VoiceSessionState.Idle
        return cleanText.takeIf { it.isNotBlank() }?.let { VoiceFinalTranscript(sessionId, it) }
    }

    fun thinking() {
        activeSessionId = null
        state = VoiceSessionState.Thinking
    }

    fun speaking() {
        activeSessionId = null
        state = VoiceSessionState.Speaking
    }

    fun interrupted() {
        activeSessionId = null
        state = VoiceSessionState.Interrupted
    }

    fun ended() {
        activeSessionId = null
        state = VoiceSessionState.Ended
    }

    fun fail(error: VoiceFailure) {
        activeSessionId = null
        state = VoiceSessionState.Error(error.userMessage, error.recoverable)
    }

    fun idle() {
        activeSessionId = null
        state = VoiceSessionState.Idle
    }
}
