package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderModelDefinition
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

data class ChatProviderChoiceUi(
    val provider: ProviderDefinition,
    val models: List<ProviderModelDefinition>,
    val selectable: Boolean,
    val unavailableReason: String?
)

data class ChatProviderSelectionUiState(
    val loading: Boolean = true,
    val persistedSelection: ActiveChatProviderSelection = ActiveChatProviderSelection.Legacy,
    val choices: List<ChatProviderChoiceUi> = emptyList(),
    val summary: String = "Bisherige KI-Einstellungen",
    val warning: String? = null
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
    private val _uiState = MutableStateFlow(ChatProviderSelectionUiState())
    val uiState: StateFlow<ChatProviderSelectionUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<ChatProviderSelectionEffect>(extraBufferCapacity = 2)
    val effects: SharedFlow<ChatProviderSelectionEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeProviders(), selectionStore.selection) { providers, selection -> providers to selection }
                .collect { (providers, selection) ->
                    val choices = providers.filter { it.id.isCustom && !it.builtIn && it.enabled }.map { provider ->
                        val models = repository.getModels(provider.id).filter { it.enabled }
                        val reason = when {
                            models.isEmpty() -> "Kein aktiviertes Modell"
                            provider.authenticationType == ProviderAuthenticationType.BEARER && !provider.hasSecret -> "API-Key fehlt"
                            else -> null
                        }
                        ChatProviderChoiceUi(provider, models, reason == null, reason)
                    }
                    val resolution = resolver.resolve(selection)
                    val summary = when (resolution) {
                        ActiveChatProviderResolution.Legacy -> "Bisherige KI-Einstellungen"
                        is ActiveChatProviderResolution.ResolvedCustomProvider -> "${resolution.definition.displayName} · ${resolution.model.displayName}"
                        is ActiveChatProviderResolution.Invalid -> "Ungültige eigene Anbieterwahl"
                    }
                    _uiState.value = ChatProviderSelectionUiState(
                        loading = false,
                        persistedSelection = selection,
                        choices = choices,
                        summary = summary,
                        warning = (resolution as? ActiveChatProviderResolution.Invalid)?.userMessage
                    )
                }
        }
    }

    fun confirm(selection: ActiveChatProviderSelection) {
        viewModelScope.launch {
            if (selection is ActiveChatProviderSelection.Custom) {
                val choice = _uiState.value.choices.firstOrNull { it.provider.id == selection.providerId }
                if (choice == null || !choice.selectable || choice.models.none { it.modelId == selection.modelId }) {
                    _effects.emit(ChatProviderSelectionEffect.Message("Diese Anbieter- und Modellauswahl ist nicht verfügbar."))
                    return@launch
                }
            }
            selectionStore.save(selection)
            _effects.emit(ChatProviderSelectionEffect.Saved)
        }
    }
}
