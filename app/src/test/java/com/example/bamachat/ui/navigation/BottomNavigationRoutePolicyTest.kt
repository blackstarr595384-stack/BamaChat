package com.example.bamachat.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationRoutePolicyTest {
    @Test
    fun allFiveTopLevelRoutesRemainVisibleAndSelectable() {
        val routes = listOf("home_hub", "chat", "mini_apps", "profile", "settings")

        routes.forEach { route ->
            val state = BottomNavigationRoutePolicy.resolve(
                previousState = BottomNavigationUiState(),
                destinationHierarchyRoutes = listOf(route)
            )

            assertTrue(route, state.visible)
            assertEquals(route, state.selectedRoute)
        }
    }

    @Test
    fun hubToChatChangesSelectionWithoutHidingContainer() {
        val hubState = BottomNavigationRoutePolicy.resolve(
            previousState = BottomNavigationUiState(),
            destinationHierarchyRoutes = listOf("home_hub")
        )
        val chatState = BottomNavigationRoutePolicy.resolve(
            previousState = hubState,
            destinationHierarchyRoutes = listOf("chat")
        )

        assertTrue(hubState.visible)
        assertTrue(chatState.visible)
        assertEquals("home_hub", hubState.selectedRoute)
        assertEquals("chat", chatState.selectedRoute)
    }

    @Test
    fun temporaryNullRoutePreservesVisibleChatState() {
        val previousState = BottomNavigationUiState(visible = true, selectedRoute = "chat")

        val state = BottomNavigationRoutePolicy.resolve(previousState, emptyList())

        assertEquals(previousState, state)
    }

    @Test
    fun welcomeAndAuthExplicitlyHideBottomNavigation() {
        listOf("welcome", "auth").forEach { route ->
            val state = BottomNavigationRoutePolicy.resolve(
                previousState = BottomNavigationUiState(true, "home_hub"),
                destinationHierarchyRoutes = listOf(route)
            )

            assertFalse(route, state.visible)
        }
    }

    @Test
    fun unknownNestedRoutePreservesPreviousTopLevelState() {
        val previousState = BottomNavigationUiState(visible = true, selectedRoute = "home_hub")

        val state = BottomNavigationRoutePolicy.resolve(
            previousState = previousState,
            destinationHierarchyRoutes = listOf("temporary_transition_destination")
        )

        assertEquals(previousState, state)
    }

    @Test
    fun destinationHierarchyRecognizesTopLevelParent() {
        val state = BottomNavigationRoutePolicy.resolve(
            previousState = BottomNavigationUiState(),
            destinationHierarchyRoutes = listOf("chat/details", "chat")
        )

        assertTrue(state.visible)
        assertEquals("chat", state.selectedRoute)
    }

    @Test
    fun settingsArgumentsNormalizeToSettingsSelection() {
        val state = BottomNavigationRoutePolicy.resolve(
            previousState = BottomNavigationUiState(),
            destinationHierarchyRoutes = listOf("settings?section={section}")
        )

        assertTrue(state.visible)
        assertEquals("settings", state.selectedRoute)
    }

    @Test
    fun settingsVoiceSubpageKeepsSettingsSelectedAndVisible() {
        val state = BottomNavigationRoutePolicy.resolve(
            previousState = BottomNavigationUiState(visible = true, selectedRoute = "settings"),
            destinationHierarchyRoutes = listOf("settings/voice-audio")
        )

        assertTrue(state.visible)
        assertEquals("settings", state.selectedRoute)
    }
}
