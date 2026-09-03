package com.example.bamachat.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationPolicyTest {
    @Test
    fun chatProviderSelectionIsFullscreenSettingsSubpage() {
        assertTrue(SettingsNavigationPolicy.isFullscreenSubpage(SettingsNavigationRoutes.CHAT_PROVIDER))
        assertTrue(SettingsNavigationPolicy.isSettingsFlowRoute(SettingsNavigationRoutes.CHAT_PROVIDER))
    }
    @Test
    fun onlyProviderManagementSubpagesUseFullscreenSettingsPresentation() {
        assertFalse(SettingsNavigationPolicy.isFullscreenSubpage(SettingsNavigationRoutes.OVERVIEW))
        assertFalse(SettingsNavigationPolicy.isFullscreenSubpage(SettingsNavigationRoutes.LEGACY_SECTION_PATTERN))
        assertFalse(SettingsNavigationPolicy.isFullscreenSubpage(SettingsNavigationRoutes.VOICE_AUDIO))
        assertTrue(SettingsNavigationPolicy.isFullscreenSubpage(SettingsNavigationRoutes.AI_MODELS))
        assertTrue(SettingsNavigationPolicy.isFullscreenSubpage(SettingsNavigationRoutes.PROVIDERS))
        assertTrue(SettingsNavigationPolicy.isFullscreenSubpage(SettingsNavigationRoutes.PROVIDER_ADD))
        assertTrue(SettingsNavigationPolicy.isFullscreenSubpage(SettingsNavigationRoutes.PROVIDER_EDIT_PATTERN))
        assertTrue(SettingsNavigationPolicy.isFullscreenSubpage("settings/ai-models/providers/custom:11111111-2222-3333-4444-555555555555"))
    }
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
        listOf(
            SettingsNavigationRoutes.VOICE_AUDIO,
            SettingsNavigationRoutes.AI_MODELS,
            SettingsNavigationRoutes.PROVIDERS,
            SettingsNavigationRoutes.PROVIDER_ADD,
            SettingsNavigationRoutes.PROVIDER_EDIT_PATTERN
        ).forEach { route ->
            assertEquals(SettingsOverviewNavigationAction.PopToOverview, SettingsNavigationPolicy.overviewAction(route))
        }
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
            SettingsNavigationRoutes.AI_MODELS,
            SettingsNavigationRoutes.PROVIDERS,
            SettingsNavigationRoutes.PROVIDER_ADD,
            SettingsNavigationRoutes.PROVIDER_EDIT_PATTERN,
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
