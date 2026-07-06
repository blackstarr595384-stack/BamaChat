package com.example.bamachat.data

object SharedAiPilotDebugToggle {
    const val ACTION_SET_ENABLED = "com.example.bamachat.debug.SET_SHARED_AI_PILOT"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_STREAMING_ENABLED = "streaming"

    fun resolveRequestedEnabled(hasEnabledExtra: Boolean, enabled: Boolean): Boolean? {
        return if (hasEnabledExtra) enabled else null
    }

    fun resolveRequestedStreamingEnabled(
        hasStreamingEnabledExtra: Boolean,
        streamingEnabled: Boolean
    ): Boolean? {
        return if (hasStreamingEnabledExtra) streamingEnabled else null
    }
}
