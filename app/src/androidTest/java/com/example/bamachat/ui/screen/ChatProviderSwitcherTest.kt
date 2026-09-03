package com.example.bamachat.ui.screen

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.data.provider.ProviderSecretStorage
import com.example.bamachat.data.provider.chat.ActiveChatProviderResolver
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelection
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelectionStore
import com.example.bamachat.data.provider.local.ProviderEntity
import com.example.bamachat.data.provider.local.ProviderModelEntity
import com.example.bamachat.data.provider.local.ProviderStore
import com.example.bamachat.shared.core.provider.selection.ChatProvider
import com.example.bamachat.shared.core.provider.selection.ChatProviderAvailability
import com.example.bamachat.shared.core.provider.selection.ChatProviderCatalogSnapshot
import com.example.bamachat.shared.core.provider.selection.ChatProviderConnectionKind
import com.example.bamachat.shared.core.provider.selection.ChatProviderModel
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelection
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionEvent
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionReducer
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionState
import com.example.bamachat.ui.theme.BamaChatTheme
import com.example.bamachat.ui.viewmodel.ChatProviderChoiceUi
import com.example.bamachat.ui.viewmodel.ChatProviderModelChoiceUi
import com.example.bamachat.ui.viewmodel.ChatProviderRuntimeStatus
import com.example.bamachat.ui.viewmodel.ChatProviderSelectionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ChatProviderSwitcherTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
    fun topBarBackUsesTheSharedCancelNavigationCallback() {
        val fixture = viewModelFixture()
        val events = mutableListOf<String>()
        composeRule.setContent {
            BamaChatTheme {
                ChatProviderSelectionScreen(
                    onBack = {
                        val state = coreState(fixture.viewModel)
                        events += if (state.pendingSelection == state.savedSelection) {
                            "navigate-after-cancel"
                        } else {
                            "navigate-before-cancel"
                        }
                    },
                    onManageProviders = {},
                    viewModel = fixture.viewModel
                )
            }
        }
        composeRule.waitUntil { !fixture.viewModel.uiState.value.loading }
        composeRule.runOnUiThread {
            setCoreState(fixture.viewModel, changedCoreState(), updateUi = true)
        }

        composeRule.onNodeWithContentDescription("Zurück").performClick()

        assertEquals(listOf("navigate-after-cancel"), events)
        assertFalse(coreState(fixture.viewModel).hasChanges)
        assertEquals(
            ActiveChatProviderSelection.Legacy,
            fixture.selectionStore.selection.value
        )
    }

    @Test
    fun visibleCancelUsesTheSharedCancelNavigationCallback() {
        val fixture = viewModelFixture()
        val events = mutableListOf<String>()
        composeRule.setContent {
            BamaChatTheme {
                ChatProviderSelectionScreen(
                    onBack = {
                        val state = coreState(fixture.viewModel)
                        events += if (state.pendingSelection == state.savedSelection) {
                            "navigate-after-cancel"
                        } else {
                            "navigate-before-cancel"
                        }
                    },
                    onManageProviders = {},
                    viewModel = fixture.viewModel
                )
            }
        }
        composeRule.waitUntil { !fixture.viewModel.uiState.value.loading }
        composeRule.runOnUiThread {
            setCoreState(fixture.viewModel, changedCoreState(), updateUi = true)
        }

        composeRule.onNodeWithTag("cancel_chat_provider_selection").performClick()

        assertEquals(listOf("navigate-after-cancel"), events)
        assertFalse(coreState(fixture.viewModel).hasChanges)
        assertEquals(
            ActiveChatProviderSelection.Legacy,
            fixture.selectionStore.selection.value
        )
    }

    @Test
    fun systemBackCancelsPendingBeforeNavigatingExactlyOnce() {
        val fixture = viewModelFixture()
        val events = mutableListOf<String>()
        var parentBackCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                BackHandler { parentBackCalls += 1 }
                ChatProviderSelectionScreen(
                    onBack = {
                        val state = coreState(fixture.viewModel)
                        events += if (state.pendingSelection == state.savedSelection) {
                            "navigate-after-cancel"
                        } else {
                            "navigate-before-cancel"
                        }
                    },
                    onManageProviders = {},
                    viewModel = fixture.viewModel
                )
            }
        }
        composeRule.waitUntil { !fixture.viewModel.uiState.value.loading }
        composeRule.runOnUiThread {
            setCoreState(fixture.viewModel, changedCoreState())
        }

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(listOf("navigate-after-cancel"), events)
        assertEquals(0, parentBackCalls)
        assertEquals(
            ActiveChatProviderSelection.Legacy,
            fixture.selectionStore.selection.value
        )
        assertFalse(coreState(fixture.viewModel).hasChanges)
    }

    @Test
    fun systemBackDoesNotCancelOrNavigateWhileConfirmationIsRunning() {
        val fixture = viewModelFixture()
        var navigationCalls = 0
        var parentBackCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                BackHandler { parentBackCalls += 1 }
                ChatProviderSelectionScreen(
                    onBack = { navigationCalls += 1 },
                    onManageProviders = {},
                    viewModel = fixture.viewModel
                )
            }
        }
        composeRule.waitUntil { !fixture.viewModel.uiState.value.loading }
        val confirming = ChatProviderSelectionReducer().reduce(
            changedCoreState(),
            ChatProviderSelectionEvent.BeginConfirmation
        )
        composeRule.runOnUiThread {
            setCoreState(fixture.viewModel, confirming, updateUi = true)
        }
        composeRule.waitUntil { fixture.viewModel.uiState.value.confirming }

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(0, navigationCalls)
        assertEquals(0, parentBackCalls)
        assertTrue(coreState(fixture.viewModel).confirmationInProgress)
        assertEquals(
            ActiveChatProviderSelection.Legacy,
            fixture.selectionStore.selection.value
        )
    }

    @Test
    fun toolbarAndVisibleCancelDoNothingWhileConfirmationIsRunning() {
        val fixture = viewModelFixture()
        var navigationCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                ChatProviderSelectionScreen(
                    onBack = { navigationCalls += 1 },
                    onManageProviders = {},
                    viewModel = fixture.viewModel
                )
            }
        }
        composeRule.waitUntil { !fixture.viewModel.uiState.value.loading }
        val confirming = ChatProviderSelectionReducer().reduce(
            changedCoreState(),
            ChatProviderSelectionEvent.BeginConfirmation
        )
        composeRule.runOnUiThread {
            setCoreState(fixture.viewModel, confirming, updateUi = true)
        }
        composeRule.waitUntil { fixture.viewModel.uiState.value.confirming }

        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithTag("cancel_chat_provider_selection").assertIsNotEnabled()

        assertEquals(0, navigationCalls)
        assertTrue(coreState(fixture.viewModel).confirmationInProgress)
        assertTrue(coreState(fixture.viewModel).hasChanges)
        assertEquals(
            ActiveChatProviderSelection.Legacy,
            fixture.selectionStore.selection.value
        )
    }

    @Test
    fun blockedBackThenSuccessNavigatesExactlyOnce() {
        val coordinator = ChatProviderSelectionBackCoordinator()
        var cancelCalls = 0
        var navigationCalls = 0

        coordinator.requestBack(
            confirmationInProgress = true,
            cancel = { cancelCalls += 1 },
            navigate = { navigationCalls += 1 }
        )
        coordinator.navigateAfterSuccess { navigationCalls += 1 }
        coordinator.navigateAfterSuccess { navigationCalls += 1 }

        assertEquals(0, cancelCalls)
        assertEquals(1, navigationCalls)
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
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("confirm_chat_provider_selection"))
        composeRule.onNodeWithTag("confirm_chat_provider_selection")
            .assertIsDisplayed()
            .assertIsEnabled()
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

    private fun viewModelFixture(): ViewModelFixture {
        val providerStore = EmptyProviderStore()
        val secretStorage = EmptySecretStorage()
        val repository = ProviderRepository(providerStore, secretStorage)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "chat_provider_back_handler_test",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val selectionStore = ActiveChatProviderSelectionStore(preferences)
        val resolver = ActiveChatProviderResolver(selectionStore, repository, secretStorage)
        return ViewModelFixture(
            viewModel = com.example.bamachat.ui.viewmodel.ChatProviderSelectionViewModel(
                repository,
                selectionStore,
                resolver
            ),
            selectionStore = selectionStore
        )
    }

    private fun changedCoreState(): ChatProviderSelectionState {
        val reducer = ChatProviderSelectionReducer()
        val catalog = ChatProviderCatalogSnapshot(
            providers = listOf(
                ChatProvider(
                    id = TEST_PROVIDER_ID,
                    displayName = "Testanbieter",
                    connectionKind = ChatProviderConnectionKind.LOCAL,
                    availability = ChatProviderAvailability.AVAILABLE,
                    models = listOf(
                        ChatProviderModel(
                            id = TEST_MODEL_ID,
                            displayName = "Testmodell",
                            enabled = true
                        )
                    )
                )
            )
        )
        val loaded = reducer.reduce(
            reducer.initialState(),
            ChatProviderSelectionEvent.Loaded(ChatProviderSelection.Legacy, catalog)
        )
        return reducer.reduce(
            loaded,
            ChatProviderSelectionEvent.SelectCustom(TEST_PROVIDER_ID, TEST_MODEL_ID)
        )
    }

    private fun setCoreState(
        viewModel: com.example.bamachat.ui.viewmodel.ChatProviderSelectionViewModel,
        state: ChatProviderSelectionState,
        updateUi: Boolean = false
    ) {
        val field = viewModel.javaClass.getDeclaredField("coreState")
        field.isAccessible = true
        field.set(viewModel, state)
        if (updateUi) {
            val method = viewModel.javaClass.getDeclaredMethod("updateSelectionState")
            method.isAccessible = true
            method.invoke(viewModel)
        }
    }

    private fun coreState(
        viewModel: com.example.bamachat.ui.viewmodel.ChatProviderSelectionViewModel
    ): ChatProviderSelectionState {
        val field = viewModel.javaClass.getDeclaredField("coreState")
        field.isAccessible = true
        return field.get(viewModel) as ChatProviderSelectionState
    }

    private data class ViewModelFixture(
        val viewModel: com.example.bamachat.ui.viewmodel.ChatProviderSelectionViewModel,
        val selectionStore: ActiveChatProviderSelectionStore
    )

    private companion object {
        const val TEST_PROVIDER_ID = "custom:back-handler"
        const val TEST_MODEL_ID = "back-handler-model"
    }
}

private class EmptySecretStorage : ProviderSecretStorage {
    override fun put(providerId: ProviderId, secret: String) = Unit
    override fun get(providerId: ProviderId): String? = null
    override fun contains(providerId: ProviderId): Boolean = false
    override fun remove(providerId: ProviderId) = Unit
    override fun clearCustomSecrets() = Unit
}

private class EmptyProviderStore : ProviderStore {
    private val providers = MutableStateFlow<List<ProviderEntity>>(emptyList())

    override fun observeProviders(): Flow<List<ProviderEntity>> = providers
    override fun observeEnabledProviders(): Flow<List<ProviderEntity>> = providers
    override suspend fun getProvider(providerId: String): ProviderEntity? = null
    override suspend fun insertProvider(provider: ProviderEntity) = Unit
    override suspend fun updateProvider(provider: ProviderEntity): Int = 0
    override suspend fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long): Int = 0
    override suspend fun setDefaultModel(
        providerId: String,
        modelId: String?,
        updatedAt: Long
    ): Int = 0
    override suspend fun setHasSecret(
        providerId: String,
        hasSecret: Boolean,
        updatedAt: Long
    ): Int = 0
    override suspend fun deleteProvider(providerId: String): Int = 0
    override suspend fun getModels(providerId: String): List<ProviderModelEntity> = emptyList()
    override suspend fun getModel(
        providerId: String,
        modelId: String
    ): ProviderModelEntity? = null
    override suspend fun replaceModels(
        providerId: String,
        models: List<ProviderModelEntity>
    ) = Unit
    override suspend fun insertModel(model: ProviderModelEntity) = Unit
    override suspend fun insertModelsIfAbsent(
        models: List<ProviderModelEntity>
    ): List<Long> = emptyList()
    override suspend fun deleteModel(providerId: String, modelId: String): Int = 0
    override suspend fun seedBuiltIns(
        providers: List<ProviderEntity>,
        models: List<ProviderModelEntity>
    ) = Unit
}
