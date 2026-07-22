package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderCapabilities
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.ProviderModelDefinition
import com.example.bamachat.data.provider.ProviderModelSource
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.data.provider.ProviderUrlPolicy
import com.example.bamachat.data.provider.ProviderUrlValidationResult
import com.example.bamachat.data.provider.discovery.DiscoveredProviderModel
import com.example.bamachat.data.provider.discovery.ProviderDiscoveryException
import com.example.bamachat.data.provider.discovery.ProviderDiscoveryService
import com.example.bamachat.ui.provider.ProviderDiscoveryPresentation
import com.example.bamachat.ui.provider.toProviderUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class ProviderDiscoveryUiStatus {
    NOT_TESTED,
    TESTING,
    FETCHING_MODELS,
    SUCCESS,
    MODELS_FOUND,
    NO_MODELS,
    CANCELLED,
    ERROR
}

data class ProviderEditorUiState(
    val loading: Boolean = true,
    val id: ProviderId = ProviderId.newCustom(),
    val existing: Boolean = false,
    val builtIn: Boolean = false,
    val displayName: String = "",
    val connectionType: ProviderConnectionType = ProviderConnectionType.OPENAI_COMPATIBLE,
    val baseUrl: String = "https://",
    val authenticationType: ProviderAuthenticationType = ProviderAuthenticationType.BEARER,
    val timeoutSeconds: String = "30",
    val streaming: Boolean = true,
    val modelDiscovery: Boolean = false,
    val tools: Boolean = false,
    val vision: Boolean = false,
    val enabled: Boolean = true,
    val localHttpConfirmed: Boolean = false,
    val hasSecret: Boolean = false,
    val removeStoredSecret: Boolean = false,
    val models: List<ProviderModelDefinition> = emptyList(),
    val defaultModelId: String? = null,
    val errorMessage: String? = null,
    val saving: Boolean = false,
    val discoveryStatus: ProviderDiscoveryUiStatus = ProviderDiscoveryUiStatus.NOT_TESTED,
    val discoveryMessage: String? = null,
    val discoveryModels: List<DiscoveredProviderModel> = emptyList(),
    val selectedDiscoveredModelIds: Set<String> = emptySet(),
    val discoveryTruncated: Boolean = false,
    val importingModels: Boolean = false
)

sealed interface ProviderEditorEffect {
    data object Saved : ProviderEditorEffect
    data class Message(val text: String) : ProviderEditorEffect
    data object ConfirmLocalHttp : ProviderEditorEffect
}

@HiltViewModel
class ProviderEditorViewModel @Inject constructor(
    private val repository: ProviderRepository,
    private val discoveryService: ProviderDiscoveryService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val requestedProviderId = savedStateHandle.get<String>("providerId")
        ?.let { runCatching { ProviderId(it) }.getOrNull() }
    private val _uiState = MutableStateFlow(ProviderEditorUiState(id = requestedProviderId ?: ProviderId.newCustom()))
    val uiState: StateFlow<ProviderEditorUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<ProviderEditorEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<ProviderEditorEffect> = _effects.asSharedFlow()
    private var pendingSecret: String? = null
    private var discoveryJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                val provider = requestedProviderId?.let { repository.getProvider(it) }
                val models = provider?.let { repository.getModels(it.id) }.orEmpty()
                if (requestedProviderId != null && provider == null) {
                    throw IllegalStateException("Provider unavailable")
                }
                provider to models
            }.onSuccess { (provider, models) ->
                _uiState.value = if (provider == null) _uiState.value.copy(loading = false) else {
                ProviderEditorUiState(
                    loading = false,
                    id = provider.id,
                    existing = true,
                    builtIn = provider.builtIn,
                    displayName = provider.displayName,
                    connectionType = provider.connectionType,
                    baseUrl = provider.baseUrl,
                    authenticationType = provider.authenticationType,
                    timeoutSeconds = (provider.timeoutMs / 1_000L).toString(),
                    streaming = provider.capabilities.streaming,
                    modelDiscovery = provider.capabilities.modelDiscovery,
                    tools = provider.capabilities.tools,
                    vision = provider.capabilities.vision,
                    enabled = provider.enabled,
                    localHttpConfirmed = provider.localHttpConfirmed,
                    hasSecret = provider.hasSecret,
                    models = models,
                    defaultModelId = provider.defaultModelId
                )
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(loading = false, errorMessage = error.toProviderUserMessage())
            }
        }
    }

    fun update(transform: (ProviderEditorUiState) -> ProviderEditorUiState) {
        _uiState.value = transform(_uiState.value).copy(errorMessage = null)
    }

    fun addModel(modelId: String, displayName: String): Boolean {
        val cleanId = modelId.trim()
        if (cleanId.isEmpty() || _uiState.value.models.any { it.modelId == cleanId }) {
            _uiState.value = _uiState.value.copy(errorMessage = if (cleanId.isEmpty()) "Die Modell-ID darf nicht leer sein." else "Diese Modell-ID ist bereits vorhanden.")
            return false
        }
        val model = ProviderModelDefinition.create(
            providerId = _uiState.value.id,
            modelId = cleanId,
            displayName = displayName.trim().ifBlank { cleanId },
            source = ProviderModelSource.MANUAL
        )
        _uiState.value = _uiState.value.copy(models = _uiState.value.models + model, errorMessage = null)
        return true
    }

    fun removeModel(modelId: String) {
        update { state -> state.copy(models = state.models.filterNot { it.modelId == modelId }, defaultModelId = state.defaultModelId.takeUnless { it == modelId }) }
    }

    fun save(secretInput: String) {
        val state = _uiState.value
        val validation = ProviderUrlPolicy.validate(state.baseUrl, state.localHttpConfirmed)
        when (validation) {
            is ProviderUrlValidationResult.Invalid -> _uiState.value = state.copy(errorMessage = validation.message)
            is ProviderUrlValidationResult.RequiresLocalHttpConfirmation -> {
                pendingSecret = secretInput
                _effects.tryEmit(ProviderEditorEffect.ConfirmLocalHttp)
            }
            is ProviderUrlValidationResult.Valid -> persist(secretInput, validation.normalizedUrl)
        }
    }

    fun confirmLocalHttp() {
        val secret = pendingSecret.orEmpty()
        pendingSecret = null
        update { it.copy(localHttpConfirmed = true) }
        val validation = ProviderUrlPolicy.validate(_uiState.value.baseUrl, true)
        if (validation is ProviderUrlValidationResult.Valid) persist(secret, validation.normalizedUrl)
    }

    fun cancelLocalHttpConfirmation() {
        pendingSecret = null
    }

    fun testConnection() = startDiscovery(showModels = false)

    fun fetchModels() = startDiscovery(showModels = true)

    fun cancelDiscovery() {
        val job = discoveryJob ?: return
        if (job.isActive) {
            _uiState.value = _uiState.value.copy(
                discoveryStatus = ProviderDiscoveryUiStatus.CANCELLED,
                discoveryMessage = ProviderDiscoveryPresentation.errorMessage(
                    com.example.bamachat.data.provider.discovery.ProviderDiscoveryError.CANCELLED
                )
            )
            job.cancel()
        }
        discoveryJob = null
    }

    fun toggleDiscoveredModel(modelId: String) {
        update { state ->
            val selected = state.selectedDiscoveredModelIds.toMutableSet()
            if (!selected.add(modelId)) selected.remove(modelId)
            state.copy(selectedDiscoveredModelIds = selected)
        }
    }

    fun selectAllDiscoveredModels() {
        update { state ->
            val existingIds = state.models.map { it.modelId }.toSet()
            state.copy(selectedDiscoveredModelIds = state.discoveryModels.map { it.modelId }.filterNot(existingIds::contains).toSet())
        }
    }

    fun clearDiscoveredModelSelection() {
        update { it.copy(selectedDiscoveredModelIds = emptySet()) }
    }

    fun dismissDiscoveredModels() {
        update {
            it.copy(
                discoveryModels = emptyList(),
                selectedDiscoveredModelIds = emptySet(),
                discoveryTruncated = false
            )
        }
    }

    fun importSelectedModels() {
        val state = _uiState.value
        if (!state.existing || state.builtIn || state.importingModels || state.selectedDiscoveredModelIds.isEmpty()) return
        _uiState.value = state.copy(importingModels = true)
        viewModelScope.launch {
            runCatching {
                val selectedModels = state.discoveryModels
                    .filter { it.modelId in state.selectedDiscoveredModelIds }
                    .map { discovered ->
                        ProviderModelDefinition.create(
                            providerId = state.id,
                            modelId = discovered.modelId,
                            displayName = discovered.modelId,
                            source = ProviderModelSource.DISCOVERED
                        )
                    }
                val result = repository.importDiscoveredModels(state.id, selectedModels)
                result to repository.getModels(state.id)
            }.onSuccess { (result, models) ->
                _uiState.value = _uiState.value.copy(
                    models = models,
                    discoveryModels = emptyList(),
                    selectedDiscoveredModelIds = emptySet(),
                    importingModels = false,
                    discoveryMessage = if (result.importedCount > 0) {
                        if (result.importedCount == 1) "1 Modell importiert." else "${result.importedCount} Modelle importiert."
                    } else {
                        "Keine neuen Modelle importiert."
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(importingModels = false)
                _effects.emit(ProviderEditorEffect.Message(error.toProviderUserMessage()))
            }
        }
    }

    private fun startDiscovery(showModels: Boolean) {
        val state = _uiState.value
        if (discoveryJob?.isActive == true) return
        if (!state.existing) {
            _uiState.value = state.copy(discoveryMessage = "Speichere den Anbieter zuerst.")
            return
        }
        if (state.builtIn) return
        _uiState.value = state.copy(
            discoveryStatus = if (showModels) {
                ProviderDiscoveryUiStatus.FETCHING_MODELS
            } else {
                ProviderDiscoveryUiStatus.TESTING
            },
            discoveryMessage = if (showModels) "Modelle werden abgerufen …" else "Verbindung wird geprüft …",
            discoveryModels = emptyList(),
            selectedDiscoveredModelIds = emptySet(),
            discoveryTruncated = false
        )
        val launchedJob = viewModelScope.launch {
            try {
                val result = discoveryService.discover(state.id)
                _uiState.value = if (showModels) {
                    if (result.models.isEmpty()) {
                        _uiState.value.copy(
                            discoveryStatus = ProviderDiscoveryUiStatus.NO_MODELS,
                            discoveryMessage = "Der Anbieter hat keine importierbaren Modelle zurückgegeben."
                        )
                    } else {
                        _uiState.value.copy(
                            discoveryStatus = ProviderDiscoveryUiStatus.MODELS_FOUND,
                            discoveryMessage = "${result.models.size} Modelle gefunden.",
                            discoveryModels = result.models,
                            selectedDiscoveredModelIds = emptySet(),
                            discoveryTruncated = result.truncated
                        )
                    }
                } else {
                    _uiState.value.copy(
                        discoveryStatus = ProviderDiscoveryUiStatus.SUCCESS,
                        discoveryMessage = "Verbindung erfolgreich."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProviderDiscoveryException) {
                _uiState.value = _uiState.value.copy(
                    discoveryStatus = ProviderDiscoveryUiStatus.ERROR,
                    discoveryMessage = ProviderDiscoveryPresentation.errorMessage(error.error)
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    discoveryStatus = ProviderDiscoveryUiStatus.ERROR,
                    discoveryMessage = "Die Verbindung konnte nicht geprüft werden."
                )
            } finally {
                if (discoveryJob === coroutineContext[Job]) {
                    discoveryJob = null
                }
            }
        }
        discoveryJob = launchedJob
    }

    private fun persist(secretInput: String, normalizedUrl: String) {
        val state = _uiState.value
        if (state.builtIn) {
            viewModelScope.launch {
                runCatching {
                    repository.setEnabled(state.id, state.enabled)
                    applyDefaultModel(state.id, state.defaultModelId)
                }.onSuccess { _effects.emit(ProviderEditorEffect.Saved) }
                    .onFailure { _effects.emit(ProviderEditorEffect.Message(it.toProviderUserMessage())) }
            }
            return
        }
        val name = state.displayName.trim()
        val timeout = state.timeoutSeconds.toLongOrNull()?.times(1_000L)
        if (name.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Bitte gib einen Anbieternamen ein.")
            return
        }
        if (timeout == null || timeout !in ProviderDefinition.MIN_TIMEOUT_MS..ProviderDefinition.MAX_TIMEOUT_MS) {
            _uiState.value = state.copy(errorMessage = "Das Zeitlimit muss zwischen 5 und 120 Sekunden liegen.")
            return
        }
        if (state.defaultModelId != null && state.models.none { it.modelId == state.defaultModelId && it.enabled }) {
            _uiState.value = state.copy(errorMessage = "Das Standardmodell muss vorhanden und aktiviert sein.")
            return
        }
        val definition = ProviderDefinition.create(
            id = state.id,
            displayName = name,
            connectionType = state.connectionType,
            baseUrl = normalizedUrl,
            authenticationType = state.authenticationType,
            defaultModelId = if (state.existing) repositoryDefaultPlaceholder(state) else null,
            capabilities = ProviderCapabilities(state.streaming, state.modelDiscovery, state.tools, state.vision),
            timeoutMs = timeout,
            enabled = state.enabled,
            builtIn = false,
            localHttpConfirmed = state.localHttpConfirmed,
            hasSecret = state.hasSecret
        )
        _uiState.value = state.copy(saving = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val existing = if (state.existing) repository.getProvider(state.id) else null
                val persistedDefinition = definition.copy(defaultModelId = existing?.defaultModelId)
                if (existing == null) repository.createCustomProvider(persistedDefinition) else repository.updateCustomProvider(persistedDefinition)
                repository.replaceModels(state.id, state.models)
                applyDefaultModel(state.id, state.defaultModelId)
                when {
                    state.authenticationType == ProviderAuthenticationType.NONE_LOCAL_ONLY && state.hasSecret -> repository.removeCustomSecret(state.id)
                    state.removeStoredSecret -> repository.removeCustomSecret(state.id)
                    secretInput.isNotBlank() -> repository.saveCustomSecret(state.id, secretInput)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(saving = false)
                _effects.emit(ProviderEditorEffect.Saved)
            }.onFailure {
                _uiState.value = _uiState.value.copy(saving = false)
                _effects.emit(ProviderEditorEffect.Message(it.toProviderUserMessage()))
            }
        }
    }

    private suspend fun applyDefaultModel(providerId: ProviderId, modelId: String?) {
        if (modelId == null) repository.clearDefaultModel(providerId) else repository.setDefaultModel(providerId, modelId)
    }

    private fun repositoryDefaultPlaceholder(state: ProviderEditorUiState): String? = state.defaultModelId

    override fun onCleared() {
        discoveryJob?.cancel()
        discoveryJob = null
        super.onCleared()
    }
}
