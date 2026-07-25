package com.example.bamachat.shared.core.provider.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProviderSelectionReducerTest {
    private val reducer = ChatProviderSelectionReducer()

    @Test
    fun legacySavedRemainsValidAndUnchanged() {
        val state = loaded(ChatProviderSelection.Legacy)

        assertEquals(ChatProviderSelection.Legacy, state.savedSelection)
        assertEquals(ChatProviderSelection.Legacy, state.pendingSelection)
        assertFalse(state.hasChanges)
        assertFalse(state.canConfirm)
    }

    @Test
    fun validCustomSelectionIsLoaded() {
        val selection = custom()
        val state = loaded(selection)

        assertEquals(selection, state.savedSelection)
        assertSame(ChatProviderSelectionValidity.Valid, state.savedValidity)
    }

    @Test
    fun deletedProviderMakesSavedSelectionInvalid() {
        val state = reducer.reduce(
            reducer.initialState(),
            ChatProviderSelectionEvent.Loaded(
                custom(),
                ChatProviderCatalogSnapshot.Empty
            )
        )

        assertTrue(state.invalidCurrentSelection)
        assertEquals(custom(), state.savedSelection)
    }

    @Test
    fun deletedModelMakesSavedSelectionInvalid() {
        val state = loaded(custom(), catalog(models = emptyList()))

        assertTrue(state.invalidCurrentSelection)
    }

    @Test
    fun disabledProviderMakesSavedSelectionInvalid() {
        val state = loaded(
            custom(),
            catalog(availability = ChatProviderAvailability.DISABLED)
        )

        assertTrue(state.invalidCurrentSelection)
    }

    @Test
    fun temporarySelectionDoesNotChangeSavedSelection() {
        val state = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )

        assertEquals(ChatProviderSelection.Legacy, state.savedSelection)
        assertEquals(custom(), state.pendingSelection)
    }

    @Test
    fun cancelRestoresSavedSelection() {
        val changed = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )
        val cancelled = reducer.reduce(changed, ChatProviderSelectionEvent.Cancel)

        assertEquals(cancelled.savedSelection, cancelled.pendingSelection)
        assertFalse(cancelled.hasChanges)
    }

    @Test
    fun sameSelectionIsNoOp() {
        val state = loaded(custom())
        val result = reducer.reduce(
            state,
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )
        val confirming = reducer.reduce(result, ChatProviderSelectionEvent.BeginConfirmation)

        assertFalse(result.hasChanges)
        assertSame(result, confirming)
    }

    @Test
    fun confirmationStartsOnlyForValidChangedSelection() {
        val changed = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )
        val confirming = reducer.reduce(changed, ChatProviderSelectionEvent.BeginConfirmation)

        assertTrue(confirming.confirmationInProgress)
        assertFalse(confirming.canConfirm)
    }

    @Test
    fun secondConfirmationIsBlocked() {
        val changed = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )
        val confirming = reducer.reduce(changed, ChatProviderSelectionEvent.BeginConfirmation)

        assertSame(
            confirming,
            reducer.reduce(confirming, ChatProviderSelectionEvent.BeginConfirmation)
        )
    }

    @Test
    fun confirmationFailureIsVisibleAndSafe() {
        val confirming = reducer.reduce(
            reducer.reduce(
                loaded(ChatProviderSelection.Legacy),
                ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
            ),
            ChatProviderSelectionEvent.BeginConfirmation
        )
        val failed = reducer.reduce(
            confirming,
            ChatProviderSelectionEvent.ConfirmationFailed(
                ChatProviderSelectionConfirmationIssue.SELECTION_UNAVAILABLE
            )
        )

        assertEquals(
            ChatProviderSelectionConfirmationIssue.SELECTION_UNAVAILABLE,
            failed.confirmationError
        )
        assertFalse(failed.confirmationInProgress)
        assertEquals(ChatProviderSelection.Legacy, failed.savedSelection)
    }

    @Test
    fun invalidSelectionNeverFallsBackToLegacy() {
        val selection = custom()
        val state = loaded(selection, ChatProviderCatalogSnapshot.Empty)

        assertEquals(selection, state.savedSelection)
        assertEquals(selection, state.pendingSelection)
    }

    @Test
    fun catalogUpdateCanInvalidateSelection() {
        val state = loaded(custom())
        val updated = reducer.reduce(
            state,
            ChatProviderSelectionEvent.CatalogUpdated(ChatProviderCatalogSnapshot.Empty)
        )

        assertTrue(updated.invalidCurrentSelection)
        assertEquals(custom(), updated.savedSelection)
    }

    @Test
    fun sharedDtosContainNoSecretOrUrlFields() {
        val fieldNames = listOf(
            ChatProvider::class,
            ChatProviderModel::class,
            ChatProviderSelectionState::class
        ).flatMap { type -> type.java.declaredFields.map { it.name.lowercase() } }

        assertFalse(fieldNames.any { "secret" in it })
        assertFalse(fieldNames.any { "apikey" in it })
        assertFalse(fieldNames.any { "url" in it })
        assertFalse(fieldNames.any { "authorization" in it })
    }

    @Test
    fun transitionsAreDeterministic() {
        val initial = loaded(ChatProviderSelection.Legacy)
        val event = ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)

        assertEquals(
            reducer.reduce(initial, event),
            reducer.reduce(initial, event)
        )
    }

    @Test
    fun persistedSelectionChangedUpdatesSavedAndPendingWhenNoLocalChanges() {
        val updated = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.PersistedSelectionChanged(custom())
        )

        assertEquals(custom(), updated.savedSelection)
        assertEquals(custom(), updated.pendingSelection)
        assertFalse(updated.hasChanges)
    }

    @Test
    fun persistedSelectionChangedPreservesPendingWhenUserHasChanges() {
        val pending = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )
        val external = ChatProviderSelection.Custom(PROVIDER_ID, SECOND_MODEL_ID)
        val updated = reducer.reduce(
            pending,
            ChatProviderSelectionEvent.PersistedSelectionChanged(external)
        )

        assertEquals(external, updated.savedSelection)
        assertEquals(custom(), updated.pendingSelection)
        assertTrue(updated.hasChanges)
    }

    @Test
    fun sameSelectionReturnsStrictlyUnchangedState() {
        val state = loaded(custom())

        val updated = reducer.reduce(
            state,
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )

        assertSame(state, updated)
    }

    @Test
    fun sameSelectionPreservesConfirmationError() {
        val changed = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )
        val confirming = reducer.reduce(changed, ChatProviderSelectionEvent.BeginConfirmation)
        val failed = reducer.reduce(
            confirming,
            ChatProviderSelectionEvent.ConfirmationFailed(
                ChatProviderSelectionConfirmationIssue.OPERATION_FAILED
            )
        )

        val updated = reducer.reduce(
            failed,
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )

        assertSame(failed, updated)
        assertEquals(
            ChatProviderSelectionConfirmationIssue.OPERATION_FAILED,
            updated.confirmationError
        )
        assertTrue(updated.hasChanges)
    }

    @Test
    fun blankProviderIdIsInvalid() {
        val state = loaded(ChatProviderSelection.Custom(" ", MODEL_ID))

        assertEquals(
            ChatProviderSelectionValidationIssue.BLANK_PROVIDER_ID,
            invalidIssue(state.savedValidity)
        )
    }

    @Test
    fun blankModelIdIsInvalid() {
        val state = loaded(ChatProviderSelection.Custom(PROVIDER_ID, " "))

        assertEquals(
            ChatProviderSelectionValidationIssue.BLANK_MODEL_ID,
            invalidIssue(state.savedValidity)
        )
    }

    @Test
    fun duplicateProviderIdsAreInvalid() {
        val duplicated = ChatProviderCatalogSnapshot(
            providers = listOf(provider(), provider())
        )
        val state = loaded(custom(), duplicated)

        assertEquals(
            ChatProviderSelectionValidationIssue.DUPLICATE_PROVIDER_ID,
            invalidIssue(state.savedValidity)
        )
    }

    @Test
    fun duplicateModelIdsAreInvalid() {
        val duplicated = catalog(
            models = listOf(
                ChatProviderModel(MODEL_ID, "Erstes Modell", enabled = true),
                ChatProviderModel(MODEL_ID, "Zweites Modell", enabled = true)
            )
        )
        val state = loaded(custom(), duplicated)

        assertEquals(
            ChatProviderSelectionValidationIssue.DUPLICATE_MODEL_ID,
            invalidIssue(state.savedValidity)
        )
    }

    @Test
    fun typedValidationIssueContainsNoFreeTechnicalMessage() {
        val invalid = CatalogChatProviderSelectionValidator.validate(
            ChatProviderSelection.Custom(" ", MODEL_ID),
            catalog()
        )

        assertEquals(
            ChatProviderSelectionValidationIssue.BLANK_PROVIDER_ID,
            invalidIssue(invalid)
        )
        assertFalse(
            ChatProviderSelectionValidity.Invalid::class.java.declaredFields.any {
                it.type == String::class.java
            }
        )
    }

    @Test
    fun catalogUpdateRevalidatesAfterPersistedSelectionChange() {
        val missing = reducer.reduce(
            loaded(ChatProviderSelection.Legacy, ChatProviderCatalogSnapshot.Empty),
            ChatProviderSelectionEvent.PersistedSelectionChanged(custom())
        )
        val updated = reducer.reduce(
            missing,
            ChatProviderSelectionEvent.CatalogUpdated(catalog())
        )

        assertTrue(missing.invalidCurrentSelection)
        assertSame(ChatProviderSelectionValidity.Valid, updated.savedValidity)
        assertEquals(custom(), updated.savedSelection)
    }

    @Test
    fun cancelAfterExternalSelectionChangeRestoresCurrentSavedSelection() {
        val local = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )
        val external = ChatProviderSelection.Custom(PROVIDER_ID, SECOND_MODEL_ID)
        val synchronized = reducer.reduce(
            local,
            ChatProviderSelectionEvent.PersistedSelectionChanged(external)
        )
        val cancelled = reducer.reduce(synchronized, ChatProviderSelectionEvent.Cancel)

        assertEquals(external, cancelled.savedSelection)
        assertEquals(external, cancelled.pendingSelection)
        assertFalse(cancelled.hasChanges)
    }

    @Test
    fun confirmingNeverChangesDuringPersistedSelectionSync() {
        val changed = reducer.reduce(
            loaded(ChatProviderSelection.Legacy),
            ChatProviderSelectionEvent.SelectCustom(PROVIDER_ID, MODEL_ID)
        )
        val confirming = reducer.reduce(changed, ChatProviderSelectionEvent.BeginConfirmation)
        val external = ChatProviderSelection.Custom(PROVIDER_ID, SECOND_MODEL_ID)
        val synchronized = reducer.reduce(
            confirming,
            ChatProviderSelectionEvent.PersistedSelectionChanged(external)
        )

        assertTrue(synchronized.confirmationInProgress)
        assertEquals(external, synchronized.savedSelection)
        assertEquals(custom(), synchronized.pendingSelection)
    }

    @Test
    fun mutatingOriginalProviderListDoesNotChangeReducerState() {
        val providers = mutableListOf(provider())
        val state = loaded(custom(), ChatProviderCatalogSnapshot(providers))

        providers.clear()

        assertEquals(1, state.catalog.providers.size)
        assertEquals(PROVIDER_ID, state.catalog.providers.single().id)
        assertSame(ChatProviderSelectionValidity.Valid, state.savedValidity)
        assertEquals(custom(), state.savedSelection)
    }

    @Test
    fun mutatingOriginalModelListDoesNotChangeReducerState() {
        val models = mutableListOf(
            ChatProviderModel(MODEL_ID, "Sicheres Modell", enabled = true)
        )
        val state = loaded(custom(), catalog(models = models))

        models.clear()

        assertEquals(1, state.catalog.providers.single().models.size)
        assertEquals(MODEL_ID, state.catalog.providers.single().models.single().id)
        assertSame(ChatProviderSelectionValidity.Valid, state.savedValidity)
        assertEquals(custom(), state.savedSelection)
    }

    @Test
    fun catalogEventsStoreIndependentSnapshots() {
        val loadedProviders = mutableListOf(provider())
        val loaded = loaded(
            ChatProviderSelection.Legacy,
            ChatProviderCatalogSnapshot(loadedProviders)
        )
        loadedProviders.clear()

        val updatedModels = mutableListOf(
            ChatProviderModel(MODEL_ID, "Sicheres Modell", enabled = true)
        )
        val updated = reducer.reduce(
            loaded,
            ChatProviderSelectionEvent.CatalogUpdated(catalog(models = updatedModels))
        )
        updatedModels.clear()

        val synchronized = reducer.reduce(
            updated,
            ChatProviderSelectionEvent.PersistedSelectionChanged(custom())
        )

        assertEquals(1, loaded.catalog.providers.size)
        assertEquals(1, updated.catalog.providers.single().models.size)
        assertEquals(1, synchronized.catalog.providers.single().models.size)
        assertSame(ChatProviderSelectionValidity.Valid, synchronized.savedValidity)
        assertEquals(custom(), synchronized.savedSelection)
    }

    private fun loaded(
        selection: ChatProviderSelection,
        catalog: ChatProviderCatalogSnapshot = catalog()
    ): ChatProviderSelectionState = reducer.reduce(
        reducer.initialState(),
        ChatProviderSelectionEvent.Loaded(selection, catalog)
    )

    private fun custom() = ChatProviderSelection.Custom(PROVIDER_ID, MODEL_ID)

    private fun invalidIssue(
        validity: ChatProviderSelectionValidity
    ): ChatProviderSelectionValidationIssue =
        (validity as ChatProviderSelectionValidity.Invalid).issue

    private fun catalog(
        availability: ChatProviderAvailability = ChatProviderAvailability.AVAILABLE,
        models: List<ChatProviderModel> = listOf(
            ChatProviderModel(MODEL_ID, "Sicheres Modell", enabled = true)
        )
    ) = ChatProviderCatalogSnapshot(providers = listOf(provider(availability, models)))

    private fun provider(
        availability: ChatProviderAvailability = ChatProviderAvailability.AVAILABLE,
        models: List<ChatProviderModel> = listOf(
            ChatProviderModel(MODEL_ID, "Sicheres Modell", enabled = true),
            ChatProviderModel(SECOND_MODEL_ID, "Zweites Modell", enabled = true)
        )
    ) = ChatProvider(
        id = PROVIDER_ID,
        displayName = "Sicherer Anbieter",
        connectionKind = ChatProviderConnectionKind.EXTERNAL,
        availability = availability,
        models = models
    )

    private companion object {
        const val PROVIDER_ID = "provider:test"
        const val MODEL_ID = "model-test"
        const val SECOND_MODEL_ID = "model-second"
    }
}
