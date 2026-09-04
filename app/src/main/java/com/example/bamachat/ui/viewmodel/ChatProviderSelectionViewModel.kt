package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.data.provider.chat.ActiveChatProviderResolution
import com.example.bamachat.data.provider.chat.ActiveChatProviderResolver
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelection
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelectionStore
import com.example.bamachat.data.provider.chat.AndroidChatProviderSelectionAdapter
import com.example.bamachat.shared.core.provider.selection.ChatProviderAvailability
import com.example.bamachat.shared.core.provider.selection.ChatProviderConnectionKind
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelection
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionConfirmationIssue
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionEvent
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionReducer
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionState
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionValidationIssue
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionValidity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ChatProviderModelChoiceUi(
    val optionKey: String,
    val displayName: String,
    val defaultModel: Boolean,
    val selected: Boolean,
    val enabled: Boolean
)

data class ChatProviderChoiceUi(
    val displayName: String,
    val connectionLabel: String,
    val availabilityLabel: String?,
    val selectable: Boolean,
    val models: List<ChatProviderModelChoiceUi>
)

data class ChatProviderSelectionUiState(
    val loading: Boolean = true,
    val legacySelected: Boolean = true,
    val choices: List<ChatProviderChoiceUi> = emptyList(),
    val summary: String = "BamaFlow Standard",
    val warning: String? = null,
    val invalidCurrentSelection: Boolean = false,
    val canConfirm: Boolean = false,
    val confirming: Boolean = false
)

sealed interface ChatProviderSelectionEffect {
    data object Saved : ChatProviderSelectionEffect
    data class Message(val text: String) : ChatProviderSelectionEffect
}

@HiltViewModel
class ChatProviderSelectionViewModel @Inject constructor(
    private val repository: ProviderRepository,
    private val selectionStore: ActiveChatProviderSelectionStore,
    private val resolver: ActiveChatProviderResolver
) : ViewModel() {
    companion object {
        internal const val LEGACY_OPTION_KEY = "legacy"
    }

    private val _uiState = MutableStateFlow(ChatProviderSelectionUiState())
    val uiState: StateFlow<ChatProviderSelectionUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<ChatProviderSelectionEffect>(extraBufferCapacity = 2)
    val effects: SharedFlow<ChatProviderSelectionEffect> = _effects.asSharedFlow()

    private val reducer = ChatProviderSelectionReducer()
    private var coreState: ChatProviderSelectionState = reducer.initialState()
    private var coreInitialized = false
    private var selectionByOptionKey: Map<String, ChatProviderSelection> =
        mapOf(LEGACY_OPTION_KEY to ChatProviderSelection.Legacy)
    private var latestChoices: List<ChatProviderChoiceUi> = emptyList()
    private var latestSummary = "BamaFlow Standard"

    init {
        viewModelScope.launch {
            combine(repository.observeProviders(), selectionStore.selection) { providers, selection ->
                providers to selection
            }.collect { (providers, persistedSelection) ->
                val providerModels = providers.map { provider ->
                    provider to repository.getModels(provider.id)
                }
                val catalog = AndroidChatProviderSelectionAdapter.catalog(providerModels)
                val sharedPersistedSelection =
                    AndroidChatProviderSelectionAdapter.toShared(persistedSelection)
                if (!coreInitialized) {
                    coreState = reducer.reduce(
                        coreState,
                        ChatProviderSelectionEvent.Loaded(
                            savedSelection = sharedPersistedSelection,
                            catalog = catalog
                        )
                    )
                    coreInitialized = true
                } else {
                    coreState = reducer.reduce(
                        coreState,
                        ChatProviderSelectionEvent.CatalogUpdated(catalog)
                    )
                    coreState = reducer.reduce(
                        coreState,
                        ChatProviderSelectionEvent.PersistedSelectionChanged(
                            sharedPersistedSelection
                        )
                    )
                }

                val optionSelections = linkedMapOf<String, ChatProviderSelection>(
                    LEGACY_OPTION_KEY to ChatProviderSelection.Legacy
                )
                latestChoices = catalog.providers
                    .mapIndexed { providerIndex, provider ->
                        val unavailableReason = when (provider.availability) {
                            ChatProviderAvailability.AVAILABLE -> null
                            ChatProviderAvailability.DISABLED -> "Deaktiviert"
                            ChatProviderAvailability.NO_ACTIVE_MODELS -> "Keine aktiven Modelle"
                            ChatProviderAvailability.CREDENTIAL_MISSING -> "API-Key fehlt"
                        }
                        val providerSelectable =
                            provider.availability == ChatProviderAvailability.AVAILABLE
                        ChatProviderChoiceUi(
                            displayName = provider.displayName,
                            connectionLabel = when (provider.connectionKind) {
                                ChatProviderConnectionKind.EXTERNAL ->
                                    "OpenAI-kompatibel · Externer Anbieter"
                                ChatProviderConnectionKind.LOCAL ->
                                    "Ollama lokal · Lokale Verbindung"
                            },
                            availabilityLabel = unavailableReason,
                            selectable = providerSelectable,
                            models = provider.models.mapIndexed { modelIndex, model ->
                                val optionKey = "custom_${providerIndex}_model_$modelIndex"
                                val selection = ChatProviderSelection.Custom(
                                    providerId = provider.id,
                                    modelId = model.id
                                )
                                optionSelections[optionKey] = selection
                                ChatProviderModelChoiceUi(
                                    optionKey = optionKey,
                                    displayName = model.displayName,
                                    defaultModel = model.defaultModel,
                                    selected = coreState.pendingSelection == selection,
                                    enabled = providerSelectable
                                )
                            }
                        )
                    }
                selectionByOptionKey = optionSelections

                val resolution = try {
                    resolver.resolve(
                        AndroidChatProviderSelectionAdapter.toAndroid(
                            coreState.savedSelection
                        )
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
                latestSummary = when (resolution) {
                    ActiveChatProviderResolution.Legacy -> "BamaFlow Standard"
                    is ActiveChatProviderResolution.ResolvedCustomProvider ->
                        "${resolution.definition.displayName} · ${resolution.model.displayName}"
                    is ActiveChatProviderResolution.Invalid, null -> "Auswahl nicht verfügbar"
                }
                if (resolution is ActiveChatProviderResolution.Invalid) {
                    coreState = reducer.reduce(
                        coreState,
                        ChatProviderSelectionEvent.SelectionInvalidated(
                            AndroidChatProviderSelectionAdapter.validationIssue(
                                resolution.error
                            )
                        )
                    )
                } else if (resolution == null) {
                    coreState = reducer.reduce(
                        coreState,
                        ChatProviderSelectionEvent.SelectionInvalidated(
                            ChatProviderSelectionValidationIssue.INVALID_SELECTION
                        )
                    )
                }
                updateSelectionState()
            }
        }
    }

    fun selectLegacy() {
        coreState = reducer.reduce(coreState, ChatProviderSelectionEvent.SelectLegacy)
        updateSelectionState()
    }

    fun selectOption(optionKey: String) {
        val selection = selectionByOptionKey[optionKey] ?: return
        val event = when (selection) {
            ChatProviderSelection.Legacy -> ChatProviderSelectionEvent.SelectLegacy
            is ChatProviderSelection.Custom -> ChatProviderSelectionEvent.SelectCustom(
                providerId = selection.providerId,
                modelId = selection.modelId
            )
        }
        coreState = reducer.reduce(coreState, event)
        updateSelectionState()
    }

    fun cancel() {
        coreState = reducer.reduce(coreState, ChatProviderSelectionEvent.Cancel)
        updateSelectionState()
    }

    fun confirm() {
        val before = coreState
        coreState = reducer.reduce(coreState, ChatProviderSelectionEvent.BeginConfirmation)
        if (coreState === before || !coreState.confirmationInProgress) return
        updateSelectionState()
        val expectedSavedSelection = try {
            AndroidChatProviderSelectionAdapter.toAndroid(coreState.savedSelection)
        } catch (_: Exception) {
            if (applyConfirmationFailure(ChatProviderSelectionConfirmationIssue.OPERATION_FAILED)) {
                _effects.tryEmit(
                    ChatProviderSelectionEffect.Message(
                        confirmationIssueMessage(
                            ChatProviderSelectionConfirmationIssue.OPERATION_FAILED
                        )
                    )
                )
            }
            return
        }
        val selection = try {
            AndroidChatProviderSelectionAdapter.toAndroid(coreState.pendingSelection)
        } catch (_: Exception) {
            if (applyConfirmationFailure(ChatProviderSelectionConfirmationIssue.OPERATION_FAILED)) {
                _effects.tryEmit(
                    ChatProviderSelectionEffect.Message(
                        confirmationIssueMessage(
                            ChatProviderSelectionConfirmationIssue.OPERATION_FAILED
                        )
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            try {
                if (selectionStore.selection.value != expectedSavedSelection) {
                    finishWithIssue(ChatProviderSelectionConfirmationIssue.SELECTION_CHANGED)
                    return@launch
                }
                if (selection is ActiveChatProviderSelection.Custom) {
                    val resolution = resolver.resolve(selection)
                    if (resolution !is ActiveChatProviderResolution.ResolvedCustomProvider) {
                        finishWithIssue(
                            ChatProviderSelectionConfirmationIssue.SELECTION_UNAVAILABLE
                        )
                        return@launch
                    }
                }
                if (selectionStore.selection.value != expectedSavedSelection) {
                    finishWithIssue(ChatProviderSelectionConfirmationIssue.SELECTION_CHANGED)
                    return@launch
                }
                if (selectionStore.selection.value != selection) {
                    selectionStore.save(selection)
                }
                if (applyConfirmationSuccess()) {
                    _effects.emit(ChatProviderSelectionEffect.Saved)
                }
            } catch (cancellation: CancellationException) {
                finishWithIssue(ChatProviderSelectionConfirmationIssue.OPERATION_FAILED)
                throw cancellation
            } catch (_: Exception) {
                finishWithIssue(ChatProviderSelectionConfirmationIssue.OPERATION_FAILED)
            } finally {
                if (coreState.confirmationInProgress) {
                    finishWithIssue(ChatProviderSelectionConfirmationIssue.OPERATION_FAILED)
                }
            }
        }
    }

    private fun updateSelectionState() {
        val pending = coreState.pendingSelection
        _uiState.value = ChatProviderSelectionUiState(
            loading = false,
            legacySelected = pending === ChatProviderSelection.Legacy,
            choices = latestChoices.map { choice ->
                choice.copy(
                    models = choice.models.map { model ->
                        model.copy(selected = selectionByOptionKey[model.optionKey] == pending)
                    }
                )
            },
            summary = latestSummary,
            warning = coreState.confirmationError?.let(::confirmationIssueMessage)
                ?: (coreState.savedValidity as? ChatProviderSelectionValidity.Invalid)
                    ?.issue
                    ?.let(::validationIssueMessage),
            invalidCurrentSelection = coreState.invalidCurrentSelection,
            canConfirm = coreState.canConfirm,
            confirming = coreState.confirmationInProgress
        )
    }

    private fun applyConfirmationSuccess(): Boolean {
        if (!coreState.confirmationInProgress) return false
        coreState = reducer.reduce(
            coreState,
            ChatProviderSelectionEvent.ConfirmationSucceeded
        )
        updateSelectionState()
        return true
    }

    private fun applyConfirmationFailure(
        issue: ChatProviderSelectionConfirmationIssue
    ): Boolean {
        if (!coreState.confirmationInProgress) return false
        coreState = reducer.reduce(
            coreState,
            ChatProviderSelectionEvent.ConfirmationFailed(issue)
        )
        updateSelectionState()
        return true
    }

    private suspend fun finishWithIssue(issue: ChatProviderSelectionConfirmationIssue) {
        if (applyConfirmationFailure(issue)) {
            _effects.emit(
                ChatProviderSelectionEffect.Message(confirmationIssueMessage(issue))
            )
        }
    }

    private fun confirmationIssueMessage(
        issue: ChatProviderSelectionConfirmationIssue
    ): String = when (issue) {
        ChatProviderSelectionConfirmationIssue.SELECTION_CHANGED ->
            "Die Anbieterauswahl hat sich geändert. Öffne die Auswahl erneut."
        ChatProviderSelectionConfirmationIssue.SELECTION_UNAVAILABLE ->
            "Diese Anbieter- und Modellauswahl ist nicht verfügbar."
        ChatProviderSelectionConfirmationIssue.OPERATION_FAILED ->
            "Auswahl konnte nicht übernommen werden."
    }

    private fun validationIssueMessage(
        issue: ChatProviderSelectionValidationIssue
    ): String = when (issue) {
        ChatProviderSelectionValidationIssue.BLANK_PROVIDER_ID,
        ChatProviderSelectionValidationIssue.DUPLICATE_PROVIDER_ID,
        ChatProviderSelectionValidationIssue.PROVIDER_NOT_FOUND ->
            "Der ausgewählte Anbieter ist nicht mehr verfügbar."
        ChatProviderSelectionValidationIssue.BLANK_MODEL_ID,
        ChatProviderSelectionValidationIssue.DUPLICATE_MODEL_ID,
        ChatProviderSelectionValidationIssue.MODEL_NOT_FOUND ->
            "Das ausgewählte Modell ist nicht mehr verfügbar."
        ChatProviderSelectionValidationIssue.PROVIDER_DISABLED ->
            "Der ausgewählte Anbieter ist deaktiviert."
        ChatProviderSelectionValidationIssue.MODEL_DISABLED ->
            "Das ausgewählte Modell ist deaktiviert."
        ChatProviderSelectionValidationIssue.CREDENTIAL_MISSING ->
            "Für den ausgewählten Anbieter fehlt die erforderliche Anmeldung."
        ChatProviderSelectionValidationIssue.NO_MODELS_AVAILABLE ->
            "Der ausgewählte Anbieter besitzt keine aktiven Modelle."
        ChatProviderSelectionValidationIssue.UNSAFE_PROVIDER_CONFIGURATION ->
            "Die Anbieteradresse ist aus Sicherheitsgründen nicht erlaubt."
        ChatProviderSelectionValidationIssue.INVALID_SELECTION,
        ChatProviderSelectionValidationIssue.UNSUPPORTED_SELECTION ->
            "Diese Anbieter- und Modellauswahl ist nicht verfügbar."
    }
}
