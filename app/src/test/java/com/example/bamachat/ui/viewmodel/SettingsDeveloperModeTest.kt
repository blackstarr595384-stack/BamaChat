package com.example.bamachat.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDeveloperModeTest {
    @Test
    fun developerModeDefaultIsFalse() {
        assertFalse(SettingsViewModel.resolveDeveloperModePreference(storedValue = null))
    }

    @Test
    fun developerModeUserEnabledStaysTrue() {
        assertTrue(SettingsViewModel.resolveDeveloperModePreference(storedValue = true))
    }

    @Test
    fun developerModeUserDisabledStaysFalse() {
        assertFalse(SettingsViewModel.resolveDeveloperModePreference(storedValue = false))
    }
}
