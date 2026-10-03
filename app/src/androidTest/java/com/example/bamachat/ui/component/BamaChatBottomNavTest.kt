package com.example.bamachat.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.bamachat.ui.theme.BamaChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BamaChatBottomNavTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsDestinationUsesFullAccessibleNameAndRemainsSelectable() {
        var selectedRoute: String? = null
        composeRule.setContent {
            BamaChatTheme {
                BamaChatBottomNav(
                    currentRoute = "settings",
                    designPreset = "standard",
                    onNavigate = { selectedRoute = it }
                )
            }
        }

        composeRule.onNodeWithTag("bottom_nav_settings", useUnmergedTree = true)
            .assertIsSelected()
            .assertHasClickAction()
            .performClick()
        assertEquals("settings", selectedRoute)

        composeRule.onNodeWithText("Einst.", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(
            "Einstellungen",
            useUnmergedTree = true
        ).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription(
            "Einst.",
            useUnmergedTree = true
        ).assertCountEquals(0)
    }
}
