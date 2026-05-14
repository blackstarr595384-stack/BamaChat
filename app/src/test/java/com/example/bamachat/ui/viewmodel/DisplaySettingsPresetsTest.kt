package com.example.bamachat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplaySettingsPresetsTest {

    @Test
    fun normalizeFallsBackToStandard() {
        assertEquals(DisplaySettingsPresets.STANDARD, DisplaySettingsPresets.normalize(null))
        assertEquals(DisplaySettingsPresets.STANDARD, DisplaySettingsPresets.normalize("   "))
        assertEquals(DisplaySettingsPresets.STANDARD, DisplaySettingsPresets.normalize("unknown"))
    }

    @Test
    fun compactPresetUsesSmallerLayoutValues() {
        val compact = DisplaySettingsPresets.tuningFor(DisplaySettingsPresets.COMPACT)
        val standard = DisplaySettingsPresets.tuningFor(DisplaySettingsPresets.STANDARD)
        assertTrue(compact.compactChatHeader)
        assertTrue(compact.cornerRoundnessScale < standard.cornerRoundnessScale)
        assertTrue(compact.fontSizeSp < standard.fontSizeSp)
    }
}
