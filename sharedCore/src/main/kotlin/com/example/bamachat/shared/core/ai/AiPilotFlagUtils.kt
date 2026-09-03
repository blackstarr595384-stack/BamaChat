package com.example.bamachat.shared.core.ai

object AiPilotFlagUtils {
    const val KEY_SHARED_AI_EXPERIMENTAL = "shared.ai.experimental"
    const val KEY_SHARED_AI_STREAMING_PILOT = "shared.ai.streamingPilot"

    fun isSharedAiPilotEnabled(
        sharedAiExperimental: Boolean,
        developerModeEnabled: Boolean
    ): Boolean = sharedAiExperimental && developerModeEnabled

    fun isStreamingPilotEnabled(
        sharedAiExperimental: Boolean,
        developerModeEnabled: Boolean,
        sharedAiStreamingPilot: Boolean
    ): Boolean = isSharedAiPilotEnabled(
        sharedAiExperimental = sharedAiExperimental,
        developerModeEnabled = developerModeEnabled
    ) && sharedAiStreamingPilot
}
