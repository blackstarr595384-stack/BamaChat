package com.example.bamachat.ui.settings

import com.example.bamachat.voice.VoiceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModeUiPolicyTest {
    @Test
    fun allStoredModesMapWithoutChangingTheirIdentity() {
        assertEquals(VoiceModeFamily.Smart, VoiceModeUiPolicy.familyFor(VoiceMode.AUTOMATIC))
        assertEquals(VoiceModeFamily.Smart, VoiceModeUiPolicy.familyFor(VoiceMode.UNIVERSAL))
        assertEquals(VoiceModeFamily.DirectLive, VoiceModeUiPolicy.familyFor(VoiceMode.LIVE))
        assertEquals(VoiceModeFamily.Local, VoiceModeUiPolicy.familyFor(VoiceMode.LOCAL))
        VoiceMode.entries.forEach { mode ->
            assertEquals(mode, VoiceMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun renderingAutomaticAndUniversalDoesNotRequestMigration() {
        assertEquals(
            VoiceMode.AUTOMATIC,
            VoiceModeUiPolicy.selectionTarget(VoiceModeFamily.Smart, VoiceMode.AUTOMATIC)
        )
        assertEquals(
            VoiceMode.UNIVERSAL,
            VoiceModeUiPolicy.selectionTarget(VoiceModeFamily.Smart, VoiceMode.UNIVERSAL)
        )
        assertEquals("Automatische Auswahl", VoiceModeUiPolicy.smartStatus(VoiceMode.AUTOMATIC))
        assertEquals("Standard-Chat", VoiceModeUiPolicy.smartStatus(VoiceMode.UNIVERSAL))
        assertEquals(
            "Smart Voice · automatische Auswahl",
            VoiceModeUiPolicy.overviewSummary(VoiceMode.AUTOMATIC)
        )
    }

    @Test
    fun explicitSmartSelectionFromLiveOrLocalUsesStableUniversalMode() {
        assertEquals(
            VoiceMode.UNIVERSAL,
            VoiceModeUiPolicy.selectionTarget(VoiceModeFamily.Smart, VoiceMode.LIVE)
        )
        assertEquals(
            VoiceMode.UNIVERSAL,
            VoiceModeUiPolicy.selectionTarget(VoiceModeFamily.Smart, VoiceMode.LOCAL)
        )
    }

    @Test
    fun directLiveAndLocalSelectionsKeepTheirStoredEnumValues() {
        assertEquals(
            VoiceMode.LIVE,
            VoiceModeUiPolicy.selectionTarget(VoiceModeFamily.DirectLive, VoiceMode.AUTOMATIC)
        )
        assertEquals(
            VoiceMode.LOCAL,
            VoiceModeUiPolicy.selectionTarget(VoiceModeFamily.Local, VoiceMode.UNIVERSAL)
        )
    }

    @Test
    fun eachModeExposesExactlyItsRelevantOptionGroup() {
        val smart = VoiceModeUiPolicy.visibility(VoiceMode.AUTOMATIC)
        val live = VoiceModeUiPolicy.visibility(VoiceMode.LIVE)
        val local = VoiceModeUiPolicy.visibility(VoiceMode.LOCAL)

        assertTrue(smart.showSmartOptions)
        assertFalse(smart.showDirectLiveOptions)
        assertFalse(smart.showLocalOptions)

        assertFalse(live.showSmartOptions)
        assertTrue(live.showDirectLiveOptions)
        assertFalse(live.showLocalOptions)

        assertFalse(local.showSmartOptions)
        assertFalse(local.showDirectLiveOptions)
        assertTrue(local.showLocalOptions)
    }

    @Test
    fun directLiveOptionIsExplicitlyExperimental() {
        val directLive = VoiceModeUiPolicy.options.single { it.family == VoiceModeFamily.DirectLive }

        assertEquals("Direct Live", directLive.title)
        assertEquals("Experimentell", directLive.badge)
    }
}
