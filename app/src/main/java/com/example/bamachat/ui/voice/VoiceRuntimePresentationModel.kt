package com.example.bamachat.ui.voice

import com.example.bamachat.voice.VoiceSessionState

internal data class VoiceRuntimeStatusLabels(
    val ready: String,
    val connecting: String,
    val reconnecting: String,
    val listening: String,
    val responding: String,
    val interrupted: String,
    val ended: String
)

internal data class VoiceRuntimePresentationModel(
    val isSimulation: Boolean,
    val modeLabel: String,
    val panelBadge: String? = null,
    val connectionNotice: String? = null,
    val microphoneNotice: String? = null,
    val inputProviderLabel: String? = null,
    val outputProviderLabel: String? = null,
    val startDialogTitle: String,
    val startDialogIntro: String,
    val startDialogHighlights: List<String>,
    val startActionLabel: String,
    val cancelActionLabel: String,
    val requiresMicrophonePermission: Boolean,
    val persistsPrivacyConfirmation: Boolean,
    val showRealtimeVoice: Boolean,
    val showServerDurationPolicy: Boolean,
    val unavailableNotice: String,
    val startInputActionDescription: String? = null,
    val stopInputActionDescription: String? = null,
    val statusLabels: VoiceRuntimeStatusLabels? = null
) {
    fun statusText(state: VoiceSessionState): String? {
        val labels = statusLabels ?: return null
        return when (state) {
            VoiceSessionState.Idle -> labels.ready
            VoiceSessionState.Preparing,
            VoiceSessionState.Connecting -> labels.connecting
            is VoiceSessionState.Reconnecting -> labels.reconnecting
            VoiceSessionState.Listening,
            is VoiceSessionState.Transcribing -> labels.listening
            VoiceSessionState.Thinking,
            VoiceSessionState.Speaking -> labels.responding
            VoiceSessionState.Interrupted -> labels.interrupted
            VoiceSessionState.Ended -> labels.ended
            is VoiceSessionState.Error -> null
        }
    }
}

internal object DirectLiveRuntimePresentation {
    val model = VoiceRuntimePresentationModel(
        isSimulation = false,
        modeLabel = "Direct Live",
        startDialogTitle = "Direct Live starten?",
        startDialogIntro = "Direct Live verwendet dein Mikrofon und überträgt Sprache an OpenAI.",
        startDialogHighlights = listOf(
            "Besonders schnelle Unterhaltung",
            "Internetverbindung erforderlich",
            "Cloud-Audio und mögliche Zusatzkosten",
            "Du kannst das Gespräch jederzeit beenden"
        ),
        startActionLabel = "Direct Live starten",
        cancelActionLabel = "Abbrechen",
        requiresMicrophonePermission = true,
        persistsPrivacyConfirmation = true,
        showRealtimeVoice = true,
        showServerDurationPolicy = true,
        unavailableNotice = "Für Live-Unterhaltung muss zuerst der sichere BamaVoice-Server eingerichtet werden."
    )
}
