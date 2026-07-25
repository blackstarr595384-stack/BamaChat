package com.example.bamachat.shared.core.provider.selection

sealed interface ChatProviderSelection {
    data object Legacy : ChatProviderSelection

    data class Custom(
        val providerId: String,
        val modelId: String
    ) : ChatProviderSelection {
        override fun toString(): String = "Custom(provider=redacted, model=redacted)"
    }
}

enum class ChatProviderConnectionKind {
    EXTERNAL,
    LOCAL
}

enum class ChatProviderAvailability {
    AVAILABLE,
    DISABLED,
    NO_ACTIVE_MODELS,
    CREDENTIAL_MISSING
}

data class ChatProviderModel(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val defaultModel: Boolean = false
)

data class ChatProvider(
    val id: String,
    val displayName: String,
    val connectionKind: ChatProviderConnectionKind,
    val availability: ChatProviderAvailability,
    val models: List<ChatProviderModel>
)

data class ChatProviderCatalogSnapshot(
    val providers: List<ChatProvider>
) {
    companion object {
        val Empty = ChatProviderCatalogSnapshot(emptyList())
    }
}

fun ChatProviderCatalogSnapshot.defensiveCopy(): ChatProviderCatalogSnapshot =
    ChatProviderCatalogSnapshot(
        providers = providers.map { provider ->
            provider.copy(models = provider.models.toList())
        }
    )

sealed interface ChatProviderSelectionValidity {
    data object Valid : ChatProviderSelectionValidity
    data class Invalid(
        val issue: ChatProviderSelectionValidationIssue
    ) : ChatProviderSelectionValidity
}

enum class ChatProviderSelectionValidationIssue {
    BLANK_PROVIDER_ID,
    BLANK_MODEL_ID,
    DUPLICATE_PROVIDER_ID,
    DUPLICATE_MODEL_ID,
    PROVIDER_NOT_FOUND,
    MODEL_NOT_FOUND,
    PROVIDER_DISABLED,
    MODEL_DISABLED,
    CREDENTIAL_MISSING,
    NO_MODELS_AVAILABLE,
    UNSAFE_PROVIDER_CONFIGURATION,
    INVALID_SELECTION,
    UNSUPPORTED_SELECTION
}

enum class ChatProviderSelectionConfirmationIssue {
    SELECTION_CHANGED,
    SELECTION_UNAVAILABLE,
    OPERATION_FAILED
}

sealed interface ChatProviderSelectionSaveResult {
    data object Saved : ChatProviderSelectionSaveResult
    data object Unchanged : ChatProviderSelectionSaveResult
    data class Rejected(
        val issue: ChatProviderSelectionValidationIssue
    ) : ChatProviderSelectionSaveResult
}

data class ChatProviderSelectionState(
    val savedSelection: ChatProviderSelection,
    val pendingSelection: ChatProviderSelection,
    val catalog: ChatProviderCatalogSnapshot,
    val savedValidity: ChatProviderSelectionValidity,
    val pendingValidity: ChatProviderSelectionValidity,
    val confirmationInProgress: Boolean = false,
    val confirmationError: ChatProviderSelectionConfirmationIssue? = null
) {
    val hasChanges: Boolean
        get() = pendingSelection != savedSelection

    val canConfirm: Boolean
        get() = !confirmationInProgress &&
            hasChanges &&
            pendingValidity === ChatProviderSelectionValidity.Valid

    val invalidCurrentSelection: Boolean
        get() = savedValidity is ChatProviderSelectionValidity.Invalid
}
