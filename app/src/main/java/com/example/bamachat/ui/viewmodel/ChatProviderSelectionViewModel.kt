package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.data.provider.chat.ActiveChatProviderResolution
import com.example.bamachat.data.provider.chat.ActiveChatProviderResolver
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelection
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val summary: String = "BamaChat Standard",
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

    private var baselineSelection: ActiveChatProviderSelection? = null
    private var pendingSelection: ActiveChatProviderSelection? = null
    private var selectionByOptionKey: Map<String, ActiveChatProviderSelection> =
        mapOf(LEGACY_OPTION_KEY to ActiveChatProviderSelection.Legacy)

    init {
        viewModelScope.launch {
            combine(repository.observeProviders(), selectionStore.selection) { providers, selection ->
                providers to selection
            }.collect { (providers, persistedSelection) ->
                if (baselineSelection == null) {
                    baselineSelection = persistedSelection
                    pendingSelection = persistedSelection
                }

                val optionSelections = linkedMapOf<String, ActiveChatProviderSelection>(
                    LEGACY_OPTION_KEY to ActiveChatProviderSelection.Legacy
                )
                val choices = providers
                    .filter { it.id.isCustom && !it.builtIn }
                    .mapIndexed { providerIndex, provider ->
                        val activeModels = repository.getModels(provider.id).filter { it.enabled }
                        val unavailableReason = when {
                            !provider.enabled -> "Deaktiviert"
                            activeModels.isEmpty() -> "Keine aktiven Modelle"
                            provider.authenticationType == ProviderAuthenticationType.BEARER &&
                                !provider.hasSecret -> "API-Key fehlt"
                            else -> null
                        }
                        val providerSelectable = unavailableReason == null
                        ChatProviderChoiceUi(
                            displayName = provider.displayName,
                            connectionLabel = when (provider.connectionType) {
                                ProviderConnectionType.OPENAI_COMPATIBLE -> "OpenAI-kompatibel · Externer Anbieter"
                                ProviderConnectionType.OLLAMA_LOCAL -> "Ollama lokal · Lokale Verbindung"
                            },
                            availabilityLabel = unavailableReason,
                            selectable = providerSelectable,
                            models = activeModels.mapIndexed { modelIndex, model ->
                                val optionKey = "custom_${providerIndex}_model_$modelIndex"
                                val selection = ActiveChatProviderSelection.Custom(provider.id, model.modelId)
                                optionSelections[optionKey] = selection
                                ChatProviderModelChoiceUi(
                                    optionKey = optionKey,
                                    displayName = model.displayName,
                                    defaultModel = provider.defaultModelId == model.modelId,
                                    selected = pendingSelection == selection,
                                    enabled = providerSelectable
                                )
                            }
                        )
                    }
                selectionByOptionKey = optionSelections

                val resolution = resolver.resolve(persistedSelection)
                val persistedSummary = when (resolution) {
                    ActiveChatProviderResolution.Legacy -> "BamaChat Standard"
                    is ActiveChatProviderResolution.ResolvedCustomProvider ->
                        "${resolution.definition.displayName} · ${resolution.model.displayName}"
                    is ActiveChatProviderResolution.Invalid -> "Auswahl nicht verfügbar"
                }
                val pendingIsLegacy = pendingSelection === ActiveChatProviderSelection.Legacy
                val pendingAvailable = pendingIsLegacy ||
                    optionSelections.values.any { it == pendingSelection }

                _uiState.value = ChatProviderSelectionUiState(
                    loading = false,
                    legacySelected = pendingIsLegacy,
                    choices = choices,
                    summary = persistedSummary,
                    warning = (resolution as? ActiveChatProviderResolution.Invalid)?.userMessage,
                    invalidCurrentSelection = resolution is ActiveChatProviderResolution.Invalid,
                    canConfirm = pendingAvailable,
                    confirming = _uiState.value.confirming
                )
            }
        }
    }

    fun selectLegacy() {
        if (_uiState.value.confirming) return
        pendingSelection = ActiveChatProviderSelection.Legacy
        updateSelectionState()
    }

    fun selectOption(optionKey: String) {
        if (_uiState.value.confirming) return
        val selection = selectionByOptionKey[optionKey] ?: return
        pendingSelection = selection
        updateSelectionState()
    }

    fun confirm() {
        if (_uiState.value.confirming || !_uiState.value.canConfirm) return
        val selection = pendingSelection ?: return
        _uiState.value = _uiState.value.copy(confirming = true, canConfirm = false)
        viewModelScope.launch {
            if (selectionStore.selection.value != baselineSelection) {
                finishWithMessage("Die Anbieterauswahl hat sich geändert. Öffne die Auswahl erneut.")
                return@launch
            }
            if (selection is ActiveChatProviderSelection.Custom) {
                val resolution = resolver.resolve(selection)
                if (resolution !is ActiveChatProviderResolution.ResolvedCustomProvider) {
                    finishWithMessage("Diese Anbieter- und Modellauswahl ist nicht verfügbar.")
                    return@launch
                }
            }
            if (selectionStore.selection.value != selection) {
                selectionStore.save(selection)
            }
            baselineSelection = selection
            _uiState.value = _uiState.value.copy(confirming = false, canConfirm = true)
            _effects.emit(ChatProviderSelectionEffect.Saved)
        }
    }

    private fun updateSelectionState() {
        val pending = pendingSelection
        _uiState.value = _uiState.value.copy(
            legacySelected = pending === ActiveChatProviderSelection.Legacy,
            choices = _uiState.value.choices.map { choice ->
                choice.copy(
                    models = choice.models.map { model ->
                        model.copy(selected = selectionByOptionKey[model.optionKey] == pending)
                    }
                )
            },
            canConfirm = pending === ActiveChatProviderSelection.Legacy ||
                selectionByOptionKey.values.any { it == pending }
        )
    }

    private suspend fun finishWithMessage(message: String) {
        _uiState.value = _uiState.value.copy(confirming = false, canConfirm = true)
        _effects.emit(ChatProviderSelectionEffect.Message(message))
    }
}
