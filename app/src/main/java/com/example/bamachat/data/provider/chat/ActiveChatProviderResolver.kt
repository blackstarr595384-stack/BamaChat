package com.example.bamachat.data.provider.chat

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderModelDefinition
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.data.provider.ProviderSecretStorage
import com.example.bamachat.data.provider.ProviderUrlPolicy
import com.example.bamachat.data.provider.ProviderUrlValidationResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

enum class ActiveChatProviderResolutionError {
    PROVIDER_MISSING,
    PROVIDER_DISABLED,
    BUILT_IN_NOT_ALLOWED,
    MODEL_MISSING,
    MODEL_DISABLED,
    SECRET_MISSING,
    UNSAFE_URL
}

sealed interface ActiveChatProviderResolution {
    data object Legacy : ActiveChatProviderResolution
    data class ResolvedCustomProvider(
        val definition: ProviderDefinition,
        val model: ProviderModelDefinition,
        val normalizedBaseUrl: String
    ) : ActiveChatProviderResolution
    data class Invalid(
        val error: ActiveChatProviderResolutionError,
        val userMessage: String
    ) : ActiveChatProviderResolution
}

@Singleton
class ActiveChatProviderResolver @Inject constructor(
    private val store: ActiveChatProviderSelectionStore,
    private val repository: ProviderRepository,
    private val secretStorage: ProviderSecretStorage
) {
    suspend fun resolve(selection: ActiveChatProviderSelection = store.selection.value): ActiveChatProviderResolution {
        if (selection === ActiveChatProviderSelection.Legacy) return ActiveChatProviderResolution.Legacy
        selection as ActiveChatProviderSelection.Custom
        val provider = repository.getProvider(selection.providerId)
            ?: return invalid(ActiveChatProviderResolutionError.PROVIDER_MISSING, "Der ausgewählte Anbieter wurde gelöscht.")
        if (provider.builtIn || !provider.id.isCustom) {
            return invalid(ActiveChatProviderResolutionError.BUILT_IN_NOT_ALLOWED, "Integrierte Anbieter verwenden weiterhin die bisherigen KI-Einstellungen.")
        }
        if (!provider.enabled) {
            return invalid(ActiveChatProviderResolutionError.PROVIDER_DISABLED, "Der ausgewählte Anbieter ist deaktiviert.")
        }
        val model = repository.getModels(provider.id).firstOrNull { it.modelId == selection.modelId }
            ?: return invalid(ActiveChatProviderResolutionError.MODEL_MISSING, "Das ausgewählte Modell ist nicht mehr vorhanden.")
        if (!model.enabled) {
            return invalid(ActiveChatProviderResolutionError.MODEL_DISABLED, "Das ausgewählte Modell ist deaktiviert.")
        }
        if (provider.authenticationType == ProviderAuthenticationType.BEARER && !secretStorage.contains(provider.id)) {
            return invalid(ActiveChatProviderResolutionError.SECRET_MISSING, "Für den ausgewählten Anbieter fehlt ein API-Key.")
        }
        val url = ProviderUrlPolicy.validate(provider.baseUrl, provider.localHttpConfirmed)
        if (url !is ProviderUrlValidationResult.Valid) {
            return invalid(ActiveChatProviderResolutionError.UNSAFE_URL, "Die Adresse des ausgewählten Anbieters ist nicht sicher konfiguriert.")
        }
        return ActiveChatProviderResolution.ResolvedCustomProvider(provider, model, url.normalizedUrl)
    }

    fun observeResolution(): Flow<ActiveChatProviderResolution> = combine(
        store.selection,
        repository.observeProviders()
    ) { selection, _ -> selection }.let { selections ->
        flow { selections.collect { emit(resolve(it)) } }
    }

    private fun invalid(error: ActiveChatProviderResolutionError, message: String) =
        ActiveChatProviderResolution.Invalid(error, "$message Wähle unter KI und Modelle eine gültige Chat-Konfiguration.")
}
