package com.example.bamachat.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsVoiceDefaultsTest {
    @Test
    fun autoSendDefaultsToOffWithoutStoredPreference() {
        assertFalse(SettingsViewModel.resolveAutoSendVoicePreference(null))
    }

    @Test
    fun existingAutoSendChoiceIsPreserved() {
        assertTrue(SettingsViewModel.resolveAutoSendVoicePreference(true))
        assertFalse(SettingsViewModel.resolveAutoSendVoicePreference(false))
    }
}
