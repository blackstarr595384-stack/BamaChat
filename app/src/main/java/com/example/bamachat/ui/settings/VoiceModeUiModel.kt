package com.example.bamachat.ui.settings

import com.example.bamachat.voice.VoiceMode

internal enum class VoiceModeFamily {
    Smart,
    DirectLive,
    Local
}

internal data class VoiceModeUiModel(
    val family: VoiceModeFamily,
    val title: String,
    val badge: String,
    val description: String,
    val hints: List<String>
)

internal data class VoiceSettingsVisibility(
    val showSmartOptions: Boolean,
    val showDirectLiveOptions: Boolean,
    val showLocalOptions: Boolean
)

internal object VoiceModeUiPolicy {
    val options: List<VoiceModeUiModel> = listOf(
        VoiceModeUiModel(
            family = VoiceModeFamily.Smart,
            title = "Smart Voice",
            badge = "Empfohlen",
            description = "Spracheingabe mit deinem gewählten Chat-Modell und natürlicher Sprachausgabe.",
            hints = listOf("Internet je nach Anbieter", "Flexible Kosten")
        ),
        VoiceModeUiModel(
            family = VoiceModeFamily.DirectLive,
            title = "Direct Live",
            badge = "Experimentell",
            description = "Direkte Live-Unterhaltung mit besonders geringer Verzögerung.",
            hints = listOf("Cloud-Audio", "Mögliche Zusatzkosten")
        ),
        VoiceModeUiModel(
            family = VoiceModeFamily.Local,
            title = "Lokal",
            badge = "Datenschutzfreundlich",
            description = "Geräteerkennung und lokale Sprachausgabe ohne BamaVoice-Cloud.",
            hints = listOf("Privat", "Geräteabhängig", "Eingeschränkt offline")
        )
    )

    fun familyFor(mode: VoiceMode): VoiceModeFamily = when (mode) {
        VoiceMode.AUTOMATIC,
        VoiceMode.UNIVERSAL -> VoiceModeFamily.Smart
        VoiceMode.LIVE -> VoiceModeFamily.DirectLive
        VoiceMode.LOCAL -> VoiceModeFamily.Local
    }

    fun smartStatus(mode: VoiceMode): String? = when (mode) {
        VoiceMode.AUTOMATIC -> "Automatische Auswahl"
        VoiceMode.UNIVERSAL -> "Standard-Chat"
        VoiceMode.LIVE,
        VoiceMode.LOCAL -> null
    }

    fun overviewSummary(mode: VoiceMode): String = when (mode) {
        VoiceMode.AUTOMATIC -> "Smart Voice · automatische Auswahl"
        VoiceMode.UNIVERSAL -> "Smart Voice · Standard-Chat"
        VoiceMode.LIVE -> "Direct Live · experimentell"
        VoiceMode.LOCAL -> "Lokal · datenschutzfreundlich"
    }

    fun selectionTarget(family: VoiceModeFamily, currentMode: VoiceMode): VoiceMode = when (family) {
        VoiceModeFamily.Smart -> when (currentMode) {
            VoiceMode.AUTOMATIC,
            VoiceMode.UNIVERSAL -> currentMode
            VoiceMode.LIVE,
            VoiceMode.LOCAL -> VoiceMode.UNIVERSAL
        }
        VoiceModeFamily.DirectLive -> VoiceMode.LIVE
        VoiceModeFamily.Local -> VoiceMode.LOCAL
    }

    fun visibility(mode: VoiceMode): VoiceSettingsVisibility = when (familyFor(mode)) {
        VoiceModeFamily.Smart -> VoiceSettingsVisibility(
            showSmartOptions = true,
            showDirectLiveOptions = false,
            showLocalOptions = false
        )
        VoiceModeFamily.DirectLive -> VoiceSettingsVisibility(
            showSmartOptions = false,
            showDirectLiveOptions = true,
            showLocalOptions = false
        )
        VoiceModeFamily.Local -> VoiceSettingsVisibility(
            showSmartOptions = false,
            showDirectLiveOptions = false,
            showLocalOptions = true
        )
    }
}
