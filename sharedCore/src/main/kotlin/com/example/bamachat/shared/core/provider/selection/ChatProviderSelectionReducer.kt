package com.example.bamachat.shared.core.provider.selection

sealed interface ChatProviderSelectionEvent {
    data class Loaded(
        val savedSelection: ChatProviderSelection,
        val catalog: ChatProviderCatalogSnapshot
    ) : ChatProviderSelectionEvent

    data object SelectLegacy : ChatProviderSelectionEvent

    data class SelectCustom(
        val providerId: String,
        val modelId: String
    ) : ChatProviderSelectionEvent

    data class PersistedSelectionChanged(
        val selection: ChatProviderSelection
    ) : ChatProviderSelectionEvent

    data object Cancel : ChatProviderSelectionEvent
    data object BeginConfirmation : ChatProviderSelectionEvent
    data object ConfirmationSucceeded : ChatProviderSelectionEvent
    data class ConfirmationFailed(
        val issue: ChatProviderSelectionConfirmationIssue
    ) : ChatProviderSelectionEvent
    data class SelectionInvalidated(
        val issue: ChatProviderSelectionValidationIssue
    ) : ChatProviderSelectionEvent
    data class CatalogUpdated(val catalog: ChatProviderCatalogSnapshot) : ChatProviderSelectionEvent
}

class ChatProviderSelectionReducer(
    private val validator: ChatProviderSelectionValidator =
        CatalogChatProviderSelectionValidator
) {
    fun initialState(): ChatProviderSelectionState = stateFor(
        saved = ChatProviderSelection.Legacy,
        pending = ChatProviderSelection.Legacy,
        catalog = ChatProviderCatalogSnapshot.Empty
    )

    fun reduce(
        state: ChatProviderSelectionState,
        event: ChatProviderSelectionEvent
    ): ChatProviderSelectionState = when (event) {
        is ChatProviderSelectionEvent.Loaded -> stateFor(
            saved = event.savedSelection,
            pending = event.savedSelection,
            catalog = event.catalog
        )
        ChatProviderSelectionEvent.SelectLegacy ->
            select(state, ChatProviderSelection.Legacy)
        is ChatProviderSelectionEvent.SelectCustom ->
            select(state, ChatProviderSelection.Custom(event.providerId, event.modelId))
        is ChatProviderSelectionEvent.PersistedSelectionChanged ->
            persistedSelectionChanged(state, event.selection)
        ChatProviderSelectionEvent.Cancel ->
            if (state.confirmationInProgress) {
                state
            } else {
                stateFor(
                    saved = state.savedSelection,
                    pending = state.savedSelection,
                    catalog = state.catalog
                )
            }
        ChatProviderSelectionEvent.BeginConfirmation ->
            if (state.canConfirm) {
                state.copy(confirmationInProgress = true, confirmationError = null)
            } else {
                state
            }
        ChatProviderSelectionEvent.ConfirmationSucceeded ->
            if (state.confirmationInProgress) {
                stateFor(
                    saved = state.pendingSelection,
                    pending = state.pendingSelection,
                    catalog = state.catalog
                )
            } else {
                state
            }
        is ChatProviderSelectionEvent.ConfirmationFailed ->
            if (state.confirmationInProgress) {
                state.copy(
                    confirmationInProgress = false,
                    confirmationError = event.issue
                )
            } else {
                state
            }
        is ChatProviderSelectionEvent.SelectionInvalidated -> state.copy(
            savedValidity = ChatProviderSelectionValidity.Invalid(event.issue)
        )
        is ChatProviderSelectionEvent.CatalogUpdated -> stateFor(
            saved = state.savedSelection,
            pending = state.pendingSelection,
            catalog = event.catalog,
            confirmationInProgress = state.confirmationInProgress,
            confirmationError = state.confirmationError
        )
    }

    private fun select(
        state: ChatProviderSelectionState,
        selection: ChatProviderSelection
    ): ChatProviderSelectionState {
        if (state.confirmationInProgress) return state
        if (selection == state.pendingSelection) return state
        return state.copy(
            pendingSelection = selection,
            pendingValidity = validator.validate(selection, state.catalog),
            confirmationError = null
        )
    }

    private fun persistedSelectionChanged(
        state: ChatProviderSelectionState,
        selection: ChatProviderSelection
    ): ChatProviderSelectionState {
        if (selection == state.savedSelection) return state
        val hadLocalChanges = state.pendingSelection != state.savedSelection
        return stateFor(
            saved = selection,
            pending = if (hadLocalChanges) state.pendingSelection else selection,
            catalog = state.catalog,
            confirmationInProgress = state.confirmationInProgress,
            confirmationError = state.confirmationError
        )
    }

    private fun stateFor(
        saved: ChatProviderSelection,
        pending: ChatProviderSelection,
        catalog: ChatProviderCatalogSnapshot,
        confirmationInProgress: Boolean = false,
        confirmationError: ChatProviderSelectionConfirmationIssue? = null
    ): ChatProviderSelectionState {
        val stableCatalog = catalog.defensiveCopy()
        return ChatProviderSelectionState(
            savedSelection = saved,
            pendingSelection = pending,
            catalog = stableCatalog,
            savedValidity = validator.validate(saved, stableCatalog),
            pendingValidity = validator.validate(pending, stableCatalog),
            confirmationInProgress = confirmationInProgress,
            confirmationError = confirmationError
        )
    }
}
