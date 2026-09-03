package com.example.bamachat.ui.voice

import com.example.bamachat.voice.VoiceSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRuntimePresentationTest {
    @Test
    fun debugRuntimeUsesExplicitSimulationPresentation() {
        val presentation = VoiceRuntimePresentation.resolve()

        assertTrue(presentation.isSimulation)
        assertEquals("Debug-Simulation", presentation.modeLabel)
        assertEquals("Debug-Simulation", presentation.panelBadge)
        assertEquals("Keine echte OpenAI-Verbindung", presentation.connectionNotice)
        assertEquals(
            "Das Mikrofon wird in dieser Simulation nicht verwendet.",
            presentation.microphoneNotice
        )
        assertEquals("Simulation starten", presentation.startActionLabel)
        assertFalse(presentation.requiresMicrophonePermission)
        assertFalse(presentation.persistsPrivacyConfirmation)
        assertFalse(presentation.showRealtimeVoice)
    }

    @Test
    fun debugRuntimeMapsSessionStatesToSimulationLanguage() {
        val presentation = VoiceRuntimePresentation.resolve()

        assertEquals("Debug-Simulation bereit", presentation.statusText(VoiceSessionState.Idle))
        assertEquals("Simuliertes Zuhören", presentation.statusText(VoiceSessionState.Listening))
        assertEquals("Simulierte Antwort", presentation.statusText(VoiceSessionState.Speaking))
        assertEquals("Debug-Simulation beendet", presentation.statusText(VoiceSessionState.Ended))
    }

    @Test
    fun directRuntimeKeepsConciseRealConsentAndMicrophoneGate() {
        val presentation = DirectLiveRuntimePresentation.model

        assertFalse(presentation.isSimulation)
        assertEquals("Direct Live starten?", presentation.startDialogTitle)
        assertEquals(
            "Direct Live verwendet dein Mikrofon und überträgt Sprache an OpenAI.",
            presentation.startDialogIntro
        )
        assertEquals("Direct Live starten", presentation.startActionLabel)
        assertTrue(presentation.requiresMicrophonePermission)
        assertTrue(presentation.persistsPrivacyConfirmation)
        assertTrue(presentation.showRealtimeVoice)
        assertNull(presentation.panelBadge)
        assertNull(presentation.statusText(VoiceSessionState.Listening))
    }
}
