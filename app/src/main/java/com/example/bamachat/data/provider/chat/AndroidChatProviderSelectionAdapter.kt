package com.example.bamachat.data.provider.chat

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderModelDefinition
import com.example.bamachat.shared.core.provider.selection.ChatProvider
import com.example.bamachat.shared.core.provider.selection.ChatProviderAvailability
import com.example.bamachat.shared.core.provider.selection.ChatProviderCatalogSnapshot
import com.example.bamachat.shared.core.provider.selection.ChatProviderConnectionKind
import com.example.bamachat.shared.core.provider.selection.ChatProviderModel
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelection
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionValidationIssue

internal object AndroidChatProviderSelectionAdapter {
    fun toShared(selection: ActiveChatProviderSelection): ChatProviderSelection = when (selection) {
        ActiveChatProviderSelection.Legacy -> ChatProviderSelection.Legacy
        is ActiveChatProviderSelection.Custom -> ChatProviderSelection.Custom(
            providerId = selection.providerId.value,
            modelId = selection.modelId
        )
    }

    fun toAndroid(selection: ChatProviderSelection): ActiveChatProviderSelection = when (selection) {
        ChatProviderSelection.Legacy -> ActiveChatProviderSelection.Legacy
        is ChatProviderSelection.Custom -> ActiveChatProviderSelection.Custom(
            providerId = com.example.bamachat.data.provider.ProviderId(selection.providerId),
            modelId = selection.modelId
        )
    }

    fun validationIssue(
        error: ActiveChatProviderResolutionError
    ): ChatProviderSelectionValidationIssue = when (error) {
        ActiveChatProviderResolutionError.PROVIDER_MISSING ->
            ChatProviderSelectionValidationIssue.PROVIDER_NOT_FOUND
        ActiveChatProviderResolutionError.PROVIDER_DISABLED ->
            ChatProviderSelectionValidationIssue.PROVIDER_DISABLED
        ActiveChatProviderResolutionError.BUILT_IN_NOT_ALLOWED ->
            ChatProviderSelectionValidationIssue.UNSUPPORTED_SELECTION
        ActiveChatProviderResolutionError.MODEL_MISSING ->
            ChatProviderSelectionValidationIssue.MODEL_NOT_FOUND
        ActiveChatProviderResolutionError.MODEL_DISABLED ->
            ChatProviderSelectionValidationIssue.MODEL_DISABLED
        ActiveChatProviderResolutionError.SECRET_MISSING ->
            ChatProviderSelectionValidationIssue.CREDENTIAL_MISSING
        ActiveChatProviderResolutionError.UNSAFE_URL ->
            ChatProviderSelectionValidationIssue.UNSAFE_PROVIDER_CONFIGURATION
    }

    fun catalog(
        providers: List<Pair<ProviderDefinition, List<ProviderModelDefinition>>>
    ): ChatProviderCatalogSnapshot = ChatProviderCatalogSnapshot(
        providers = providers
            .filter { (provider, _) -> provider.id.isCustom && !provider.builtIn }
            .map { (provider, models) ->
                val activeModels = models.filter { it.enabled }
                ChatProvider(
                    id = provider.id.value,
                    displayName = provider.displayName,
                    connectionKind = when (provider.connectionType) {
                        ProviderConnectionType.OPENAI_COMPATIBLE ->
                            ChatProviderConnectionKind.EXTERNAL
                        ProviderConnectionType.OLLAMA_LOCAL ->
                            ChatProviderConnectionKind.LOCAL
                    },
                    availability = when {
                        !provider.enabled -> ChatProviderAvailability.DISABLED
                        activeModels.isEmpty() -> ChatProviderAvailability.NO_ACTIVE_MODELS
                        provider.authenticationType == ProviderAuthenticationType.BEARER &&
                            !provider.hasSecret -> ChatProviderAvailability.CREDENTIAL_MISSING
                        else -> ChatProviderAvailability.AVAILABLE
                    },
                    models = activeModels.map { model ->
                        ChatProviderModel(
                            id = model.modelId,
                            displayName = model.displayName,
                            enabled = model.enabled,
                            defaultModel = provider.defaultModelId == model.modelId
                        )
                    }
                )
            }
    )
}
