package com.example.bamachat.ui.screen

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bamachat.MainActivity
import com.example.bamachat.ui.viewmodel.BamaVoiceViewModel
import com.example.bamachat.voice.VoiceSessionState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatAndSettingsNavigationRemainsStableAcrossAllEntrypoints() {
        enterApp()
        openBottomDestination("bottom_nav_chat", "chat_screen")
        assertBottomDestinationSelected("bottom_nav_chat")
        assertVoiceIdle()

        openBottomDestination("bottom_nav_settings", "settings_overview_screen")
        assertBottomDestinationSelected("bottom_nav_settings")
        openBottomDestination("bottom_nav_chat", "chat_screen")
        assertBottomDestinationSelected("bottom_nav_chat")

        openSettingsFromChatMenu()
        openBottomDestination("bottom_nav_chat", "chat_screen")
        assertBottomDestinationSelected("bottom_nav_chat")

        openVoiceAudioSettings()
        openBottomDestination("bottom_nav_chat", "chat_screen")
        assertBottomDestinationSelected("bottom_nav_chat")

        openVoiceAudioSettings()
        pressBack()
        waitForTag("settings_overview_screen")
        pressBack()
        waitForTag("chat_screen")

        repeat(5) {
            openBottomDestination("bottom_nav_settings", "settings_overview_screen")
            assertBottomDestinationSelected("bottom_nav_settings")
            openBottomDestination("bottom_nav_chat", "chat_screen")
            assertBottomDestinationSelected("bottom_nav_chat")
        }

        assertVoiceIdle()
    }

    private fun enterApp() {
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            hasText("Überspringen") ||
                hasText("Akzeptieren & Fortfahren") ||
                hasWelcomeEntry() ||
                hasTag("bottom_nav_hub")
        }
        if (hasText("Überspringen")) {
            composeRule.onNodeWithText("Überspringen", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                hasText("Akzeptieren & Fortfahren") || hasWelcomeEntry()
            }
        }
        if (hasText("Akzeptieren & Fortfahren")) {
            val agreements = composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            agreements[0].performClick()
            agreements[1].performClick()
            composeRule.onNodeWithText("Akzeptieren & Fortfahren", useUnmergedTree = true)
                .performClick()
            composeRule.waitUntil(timeoutMillis = 10_000L) { hasWelcomeEntry() }
        }
        val welcomeEntry = listOf("Zum BamaHub", "Als Gast starten", "Als Gast fortfahren")
            .firstOrNull(::hasText)
        if (welcomeEntry != null) {
            composeRule.onNodeWithText(welcomeEntry, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
        }
        waitForTag("bottom_nav_hub")
    }

    private fun openSettingsFromChatMenu() {
        composeRule.onNodeWithTag("chat_more_button", useUnmergedTree = true).performClick()
        waitForTag("chat_settings_button")
        composeRule.onNodeWithTag("chat_settings_button", useUnmergedTree = true).performClick()
        waitForTag("settings_overview_screen")
        assertBottomDestinationSelected("bottom_nav_settings")
    }

    private fun openVoiceAudioSettings() {
        openBottomDestination("bottom_nav_settings", "settings_overview_screen")
        waitForTag("settings_overview_list")
        composeRule.onNodeWithTag("settings_overview_list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("settings_category_voice"))
        composeRule.onNodeWithTag("settings_category_voice", useUnmergedTree = true)
            .performClick()
        waitForTag("voice_audio_settings_screen")
        assertBottomDestinationSelected("bottom_nav_settings")
    }

    private fun openBottomDestination(tag: String, destinationTag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        waitForTag(destinationTag)
    }

    private fun assertBottomDestinationSelected(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsSelected()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNode(hasTestTag(tag), useUnmergedTree = true)
    }

    private fun hasTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun hasText(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun hasWelcomeEntry(): Boolean =
        listOf("Zum BamaHub", "Als Gast starten", "Als Gast fortfahren").any(::hasText)

    private fun assertVoiceIdle() {
        var voiceState: VoiceSessionState? = null
        composeRule.activityRule.scenario.onActivity { activity ->
            voiceState = ViewModelProvider(activity)[BamaVoiceViewModel::class.java]
                .uiState
                .value
                .state
        }
        assertTrue("Settings-Navigation darf keine Voice-Sitzung starten", voiceState is VoiceSessionState.Idle)
    }
}
