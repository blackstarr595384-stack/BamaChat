package com.example.bamachat.shared.core.provider.selection

fun interface ChatProviderCatalog {
    fun snapshot(): ChatProviderCatalogSnapshot
}

interface ChatProviderSelectionPersistence {
    fun loadSelection(): ChatProviderSelection
    fun saveSelection(selection: ChatProviderSelection): ChatProviderSelectionSaveResult
}

fun interface ChatProviderSelectionValidator {
    fun validate(
        selection: ChatProviderSelection,
        catalog: ChatProviderCatalogSnapshot
    ): ChatProviderSelectionValidity
}

object CatalogChatProviderSelectionValidator : ChatProviderSelectionValidator {
    override fun validate(
        selection: ChatProviderSelection,
        catalog: ChatProviderCatalogSnapshot
    ): ChatProviderSelectionValidity = when (selection) {
        ChatProviderSelection.Legacy -> ChatProviderSelectionValidity.Valid
        is ChatProviderSelection.Custom -> validateCustom(selection, catalog)
    }

    private fun validateCustom(
        selection: ChatProviderSelection.Custom,
        catalog: ChatProviderCatalogSnapshot
    ): ChatProviderSelectionValidity {
        if (selection.providerId.isBlank()) {
            return invalid(ChatProviderSelectionValidationIssue.BLANK_PROVIDER_ID)
        }
        if (selection.modelId.isBlank()) {
            return invalid(ChatProviderSelectionValidationIssue.BLANK_MODEL_ID)
        }
        val providers = catalog.providers.filter { it.id == selection.providerId }
        if (providers.isEmpty()) {
            return invalid(ChatProviderSelectionValidationIssue.PROVIDER_NOT_FOUND)
        }
        if (providers.size != 1) {
            return invalid(ChatProviderSelectionValidationIssue.DUPLICATE_PROVIDER_ID)
        }
        val provider = providers.single()
        if (provider.availability != ChatProviderAvailability.AVAILABLE) {
            return invalid(
                when (provider.availability) {
                    ChatProviderAvailability.DISABLED ->
                        ChatProviderSelectionValidationIssue.PROVIDER_DISABLED
                    ChatProviderAvailability.NO_ACTIVE_MODELS ->
                        ChatProviderSelectionValidationIssue.NO_MODELS_AVAILABLE
                    ChatProviderAvailability.CREDENTIAL_MISSING ->
                        ChatProviderSelectionValidationIssue.CREDENTIAL_MISSING
                    ChatProviderAvailability.AVAILABLE -> error("Unreachable")
                }
            )
        }
        val models = provider.models.filter { it.id == selection.modelId }
        if (models.isEmpty()) {
            return invalid(ChatProviderSelectionValidationIssue.MODEL_NOT_FOUND)
        }
        if (models.size != 1) {
            return invalid(ChatProviderSelectionValidationIssue.DUPLICATE_MODEL_ID)
        }
        if (!models.single().enabled) {
            return invalid(ChatProviderSelectionValidationIssue.MODEL_DISABLED)
        }
        return ChatProviderSelectionValidity.Valid
    }

    private fun invalid(issue: ChatProviderSelectionValidationIssue) =
        ChatProviderSelectionValidity.Invalid(issue)
}
