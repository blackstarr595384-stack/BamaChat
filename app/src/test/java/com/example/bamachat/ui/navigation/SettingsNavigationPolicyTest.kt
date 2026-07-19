package com.example.bamachat.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsNavigationPolicyTest {
    @Test
    fun chatAndBottomNavigationShareTheOverviewDestination() {
        assertEquals("settings", SettingsNavigationRoutes.OVERVIEW)
        assertEquals(
            SettingsOverviewNavigationAction.Navigate,
            SettingsNavigationPolicy.overviewAction("chat")
        )
        assertEquals(
            SettingsOverviewNavigationAction.Navigate,
            SettingsNavigationPolicy.overviewAction("home_hub")
        )
    }

    @Test
    fun repeatedOverviewNavigationDoesNotStackAnotherDestination() {
        assertEquals(
            SettingsOverviewNavigationAction.None,
            SettingsNavigationPolicy.overviewAction(SettingsNavigationRoutes.OVERVIEW)
        )
        assertEquals(
            SettingsOverviewNavigationAction.None,
            SettingsNavigationPolicy.overviewAction(SettingsNavigationRoutes.LEGACY_SECTION_PATTERN)
        )
    }

    @Test
    fun settingsSubpageReturnsToExistingOverview() {
        assertEquals(
            SettingsOverviewNavigationAction.PopToOverview,
            SettingsNavigationPolicy.overviewAction(SettingsNavigationRoutes.VOICE_AUDIO)
        )
    }

    @Test
    fun navigationPolicyHasNoPreferenceOrVoiceDependency() {
        val action = SettingsNavigationPolicy.overviewAction("profile")

        assertEquals(SettingsOverviewNavigationAction.Navigate, action)
    }

    @Test
    fun everySettingsDestinationExitsBeforeTopLevelChatNavigation() {
        listOf(
            SettingsNavigationRoutes.OVERVIEW,
            SettingsNavigationRoutes.VOICE_AUDIO,
            SettingsNavigationRoutes.LEGACY_SECTION_PATTERN
        ).forEach { route ->
            assertEquals(true, SettingsNavigationPolicy.shouldExitSettingsFlow(route, "chat"))
        }
    }

    @Test
    fun settingsNavigationDoesNotExitItsOwnFlow() {
        assertEquals(
            false,
            SettingsNavigationPolicy.shouldExitSettingsFlow(
                SettingsNavigationRoutes.VOICE_AUDIO,
                SettingsNavigationRoutes.OVERVIEW
            )
        )
    }
}
