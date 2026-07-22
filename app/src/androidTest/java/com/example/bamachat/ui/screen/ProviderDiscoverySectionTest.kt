package com.example.bamachat.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.ProviderModelDefinition
import com.example.bamachat.data.provider.ProviderModelSource
import com.example.bamachat.data.provider.discovery.DiscoveredProviderModel
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
    fun connectionTestShowsProgressAndDisablesDuplicateRequests() {
        composeRule.setContent {
            BamaChatTheme {
                ProviderDiscoverySection(
                    state = ProviderEditorUiState(
                        loading = false,
                        existing = true,
                        discoveryStatus = ProviderDiscoveryUiStatus.TESTING
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

    @Test
    fun modelFetchShowsDistinctProgressMessage() {
        composeRule.setContent {
            BamaChatTheme {
                ProviderDiscoverySection(
                    state = ProviderEditorUiState(
                        loading = false,
                        existing = true,
                        discoveryStatus = ProviderDiscoveryUiStatus.FETCHING_MODELS
                    ),
                    onTestConnection = {},
                    onFetchModels = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("provider_discovery_progress").assertIsDisplayed()
        composeRule.onNodeWithText("Modelle werden abgerufen …").assertIsDisplayed()
        composeRule.onNodeWithTag("provider_cancel_discovery").assertIsDisplayed()
    }

    @Test
    fun importDialogStartsEmptyAndExcludesExistingModels() {
        val providerId = ProviderId.newCustom()
        val existingModel = ProviderModelDefinition.create(
            providerId = providerId,
            modelId = "existing-model",
            displayName = "Existing",
            source = ProviderModelSource.MANUAL
        )
        var selectAllClicks = 0
        composeRule.setContent {
            BamaChatTheme {
                ProviderModelImportDialog(
                    state = ProviderEditorUiState(
                        loading = false,
                        id = providerId,
                        models = listOf(existingModel),
                        discoveryModels = listOf(
                            DiscoveredProviderModel("existing-model"),
                            DiscoveredProviderModel("new-model")
                        )
                    ),
                    onDismiss = {},
                    onSelectAll = { selectAllClicks++ },
                    onClearSelection = {},
                    onToggleModel = {},
                    onImport = {}
                )
            }
        }

        composeRule.onNodeWithTag("provider_model_import_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("0 von 2 ausgewählt").assertIsDisplayed()
        composeRule.onNodeWithTag("provider_models_import").assertIsNotEnabled()
        composeRule.onNodeWithTag("provider_model_already_exists").assertIsDisplayed()
        composeRule.onNodeWithTag("provider_models_select_all").performClick()
        assertEquals(1, selectAllClicks)
    }
}
