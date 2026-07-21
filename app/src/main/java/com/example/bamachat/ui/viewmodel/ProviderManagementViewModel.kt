package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.ProviderModelDefinition
import com.example.bamachat.data.provider.ProviderModelSource
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.ui.provider.toProviderUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ProviderListItemUi(
    val definition: ProviderDefinition,
    val modelCount: Int
)

data class ProviderManagementUiState(
    val loading: Boolean = true,
    val providers: List<ProviderListItemUi> = emptyList(),
    val errorMessage: String? = null
)

sealed interface ProviderManagementEffect {
    data class Message(val text: String) : ProviderManagementEffect
    data class OpenProvider(val providerId: ProviderId) : ProviderManagementEffect
}

@HiltViewModel
class ProviderManagementViewModel @Inject constructor(
    private val repository: ProviderRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProviderManagementUiState())
    val uiState: StateFlow<ProviderManagementUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ProviderManagementEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<ProviderManagementEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeProviders()
                .catch { error -> _uiState.value = ProviderManagementUiState(loading = false, errorMessage = error.toProviderUserMessage()) }
                .collectLatest { providers ->
                    val items = providers.map { provider ->
                        ProviderListItemUi(provider, repository.getModels(provider.id).size)
                    }
                    _uiState.value = ProviderManagementUiState(loading = false, providers = items)
                }
        }
    }

    fun setEnabled(providerId: ProviderId, enabled: Boolean) = runAction {
        repository.setEnabled(providerId, enabled)
    }

    fun delete(providerId: ProviderId) = runAction(successMessage = "Anbieter wurde gelöscht.") {
        repository.deleteCustomProvider(providerId)
    }

    fun duplicate(providerId: ProviderId) = runAction {
        val source = repository.getProvider(providerId)
            ?: error("Provider unavailable")
        require(source.id.isCustom && !source.builtIn)
        val sourceModels = repository.getModels(source.id)
        val newId = ProviderId.newCustom()
        val copy = source.copy(
            id = newId,
            displayName = "${source.displayName} Kopie".take(80),
            defaultModelId = null,
            enabled = false,
            builtIn = false,
            hasSecret = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        repository.createCustomProvider(copy)
        val copiedModels = sourceModels.map { model ->
            ProviderModelDefinition.create(
                providerId = newId,
                modelId = model.modelId,
                displayName = model.displayName,
                source = ProviderModelSource.MANUAL,
                enabled = model.enabled
            )
        }
        repository.replaceModels(newId, copiedModels)
        source.defaultModelId?.takeIf { id -> copiedModels.any { it.modelId == id && it.enabled } }?.let {
            repository.setDefaultModel(newId, it)
        }
        _effects.emit(ProviderManagementEffect.OpenProvider(newId))
    }

    private fun runAction(successMessage: String? = null, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { successMessage?.let { _effects.emit(ProviderManagementEffect.Message(it)) } }
                .onFailure { _effects.emit(ProviderManagementEffect.Message(it.toProviderUserMessage())) }
        }
    }
}
