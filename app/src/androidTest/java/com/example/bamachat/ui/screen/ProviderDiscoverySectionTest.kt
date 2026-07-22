package com.example.bamachat.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.bamachat.ui.theme.BamaChatTheme
import com.example.bamachat.ui.viewmodel.ProviderDiscoveryUiStatus
import com.example.bamachat.ui.viewmodel.ProviderEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProviderDiscoverySectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unsavedProviderShowsNoExecutableNetworkActions() {
        composeRule.setContent {
            BamaChatTheme {
                ProviderDiscoverySection(
                    state = ProviderEditorUiState(loading = false, existing = false),
                    onTestConnection = {},
                    onFetchModels = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("Speichere den Anbieter zuerst.").assertIsDisplayed()
        composeRule.onNodeWithTag("provider_test_connection").assertIsNotEnabled()
        composeRule.onNodeWithTag("provider_fetch_models").assertIsNotEnabled()
    }

    @Test
    fun savedProviderActionsRequireExplicitTap() {
        var testClicks = 0
        var fetchClicks = 0
        composeRule.setContent {
            BamaChatTheme {
                ProviderDiscoverySection(
                    state = ProviderEditorUiState(loading = false, existing = true, enabled = true),
                    onTestConnection = { testClicks++ },
                    onFetchModels = { fetchClicks++ },
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("provider_test_connection").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("provider_fetch_models").assertIsEnabled().performClick()
        assertEquals(1, testClicks)
        assertEquals(1, fetchClicks)
    }

    @Test
    fun loadingStateShowsCancelAndDisablesDuplicateRequests() {
        composeRule.setContent {
            BamaChatTheme {
                ProviderDiscoverySection(
                    state = ProviderEditorUiState(
                        loading = false,
                        existing = true,
                        discoveryStatus = ProviderDiscoveryUiStatus.CHECKING
                    ),
                    onTestConnection = {},
                    onFetchModels = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("provider_test_connection").assertIsNotEnabled()
        composeRule.onNodeWithTag("provider_fetch_models").assertIsNotEnabled()
        composeRule.onNodeWithTag("provider_cancel_discovery").assertIsDisplayed()
        composeRule.onNodeWithText("Verbindung wird geprüft …").assertIsDisplayed()
    }
}
