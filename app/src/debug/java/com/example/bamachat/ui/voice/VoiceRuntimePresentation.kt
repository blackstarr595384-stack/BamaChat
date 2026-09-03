package com.example.bamachat.ui.voice

internal object VoiceRuntimePresentation {
    fun resolve(): VoiceRuntimePresentationModel = simulation

    private val simulation = VoiceRuntimePresentationModel(
        isSimulation = true,
        modeLabel = "Debug-Simulation",
        panelBadge = "Debug-Simulation",
        connectionNotice = "Keine echte OpenAI-Verbindung",
        microphoneNotice = "Das Mikrofon wird in dieser Simulation nicht verwendet.",
        inputProviderLabel = "Eingabe: Simulation",
        outputProviderLabel = "Ausgabe: Simulation",
        startDialogTitle = "Debug-Simulation starten?",
        startDialogIntro = "Dieser Test verwendet keine echte OpenAI-Verbindung und kein echtes Mikrofon.",
        startDialogHighlights = emptyList(),
        startActionLabel = "Simulation starten",
        cancelActionLabel = "Abbrechen",
        requiresMicrophonePermission = false,
        persistsPrivacyConfirmation = false,
        showRealtimeVoice = false,
        showServerDurationPolicy = false,
        unavailableNotice = "Die Debug-Simulation ist im BamaVoice Testlabor deaktiviert.",
        startInputActionDescription = "Simuliertes Zuhören starten",
        stopInputActionDescription = "Simuliertes Zuhören beenden",
        statusLabels = VoiceRuntimeStatusLabels(
            ready = "Debug-Simulation bereit",
            connecting = "Debug-Simulation wird vorbereitet …",
            reconnecting = "Simulation wird wiederhergestellt …",
            listening = "Simuliertes Zuhören",
            responding = "Simulierte Antwort",
            interrupted = "Simulation unterbrochen",
            ended = "Debug-Simulation beendet"
        )
    )
}
