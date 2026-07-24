package com.example.bamachat.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.bamachat.ui.theme.BamaChatTheme
import com.example.bamachat.ui.viewmodel.ChatProviderChoiceUi
import com.example.bamachat.ui.viewmodel.ChatProviderModelChoiceUi
import com.example.bamachat.ui.viewmodel.ChatProviderRuntimeStatus
import com.example.bamachat.ui.viewmodel.ChatProviderSelectionUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatProviderSwitcherTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatProviderStatusIsVisibleAndClickable() {
        var clicks = 0
        composeRule.setContent {
            BamaChatTheme {
                ChatProviderStatusChip(
                    status = ChatProviderRuntimeStatus(
                        providerName = "Sehr langer eigener Anbietername",
                        modelName = "Sehr langes Modell mit vielen Zeichen",
                        badge = "Eigener Anbieter",
                        customSelection = true
                    ),
                    cornerRadius = 16.dp,
                    chipAlpha = 0.2f,
                    onClick = { clicks++ }
                )
            }
        }

        composeRule.onNodeWithTag("chat_provider_status").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun emptySelectionShowsLegacyAndManageAction() {
        var cancelled = 0
        composeRule.setContent {
            BamaChatTheme {
                ChatProviderSelectionContent(
                    state = ChatProviderSelectionUiState(
                        loading = false,
                        legacySelected = true,
                        canConfirm = true
                    ),
                    onBack = { cancelled++ },
                    onSelectLegacy = {},
                    onSelectOption = {},
                    onConfirm = {},
                    onManageProviders = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_provider_legacy_option").assertIsDisplayed()
        composeRule.onNodeWithText("Du hast noch keine eigenen Anbieter eingerichtet.").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_provider_manage").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_chat_provider_selection").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun customModelsAndUnavailableProvidersAreRepresentedSafely() {
        val choices = listOf(
            ChatProviderChoiceUi(
                displayName = "Lokaler Anbieter",
                connectionLabel = "Ollama lokal · Lokale Verbindung",
                availabilityLabel = null,
                selectable = true,
                models = listOf(
                    ChatProviderModelChoiceUi(
                        optionKey = "custom_0_model_0",
                        displayName = "Lokales Modell",
                        defaultModel = true,
                        selected = true,
                        enabled = true
                    )
                )
            ),
            ChatProviderChoiceUi(
                displayName = "Deaktivierter Anbieter",
                connectionLabel = "OpenAI-kompatibel · Externer Anbieter",
                availabilityLabel = "Deaktiviert",
                selectable = false,
                models = emptyList()
            ),
            ChatProviderChoiceUi(
                displayName = "Anbieter ohne Modelle",
                connectionLabel = "Ollama lokal · Lokale Verbindung",
                availabilityLabel = "Keine aktiven Modelle",
                selectable = false,
                models = emptyList()
            )
        )
        composeRule.setContent {
            BamaChatTheme {
                ChatProviderSelectionContent(
                    state = ChatProviderSelectionUiState(
                        loading = false,
                        legacySelected = false,
                        choices = choices,
                        canConfirm = true
                    ),
                    onBack = {},
                    onSelectLegacy = {},
                    onSelectOption = {},
                    onConfirm = {},
                    onManageProviders = {}
                )
            }
        }

        composeRule.onNodeWithText("Lokaler Anbieter").assertIsDisplayed()
        composeRule.onNodeWithText("Lokales Modell").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_provider_disabled").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_provider_no_models").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_chat_provider_selection").assertIsEnabled()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("https" + "://example.invalid").fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("custom" + ":technical-id").fetchSemanticsNodes().size
        )
    }

    @Test
    fun invalidSelectionAndIncompleteChoiceDisableConfirmation() {
        composeRule.setContent {
            BamaChatTheme {
                ChatProviderSelectionContent(
                    state = ChatProviderSelectionUiState(
                        loading = false,
                        legacySelected = false,
                        warning = "Das ausgewählte Modell ist nicht mehr vorhanden.",
                        invalidCurrentSelection = true,
                        canConfirm = false
                    ),
                    onBack = {},
                    onSelectLegacy = {},
                    onSelectOption = {},
                    onConfirm = {},
                    onManageProviders = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_provider_selection_unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Auswahl nicht verfügbar.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("chat_provider_switch_to_legacy").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_chat_provider_selection").assertIsNotEnabled()
    }
}
