package com.example.bamachat.desktop

import com.example.bamachat.shared.core.provider.selection.ChatProvider
import com.example.bamachat.shared.core.provider.selection.ChatProviderAvailability
import com.example.bamachat.shared.core.provider.selection.ChatProviderCatalog
import com.example.bamachat.shared.core.provider.selection.ChatProviderCatalogSnapshot
import com.example.bamachat.shared.core.provider.selection.ChatProviderConnectionKind
import com.example.bamachat.shared.core.provider.selection.ChatProviderModel
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelection
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionSaveResult
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionValidationIssue
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionValidator
import com.example.bamachat.shared.core.provider.selection.CatalogChatProviderSelectionValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopChatProviderSelectionAdapterTest {
    @Test
    fun openRouterReadsAndWritesThroughExistingSettings() {
        var stored = DesktopUserSettings(
            provider = DesktopProvider.OPENROUTER,
            openRouterApiKey = "not-exposed",
            openRouterModel = "old-openrouter-model"
        )
        var writes = 0
        val persistence = persistence(
            load = { stored },
            save = {
                stored = it
                writes += 1
            },
            catalog = catalog(openRouterModels = listOf("new-openrouter-model"))
        )

        assertEquals(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OPENROUTER_PROVIDER_ID,
                "old-openrouter-model"
            ),
            persistence.loadSelection()
        )
        assertEquals(
            ChatProviderSelectionSaveResult.Saved,
            persistence.saveSelection(
                ChatProviderSelection.Custom(
                    DesktopChatProviderSelectionAdapter.OPENROUTER_PROVIDER_ID,
                    "new-openrouter-model"
                )
            )
        )
        assertEquals(DesktopProvider.OPENROUTER, stored.provider)
        assertEquals("new-openrouter-model", stored.openRouterModel)
        assertEquals(1, writes)
    }

    @Test
    fun ollamaReadsAndWritesThroughExistingSettings() {
        var stored = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            ollamaModel = "old-ollama-model"
        )
        val persistence = persistence(
            load = { stored },
            save = { stored = it },
            catalog = catalog(ollamaModels = listOf("new-ollama-model"))
        )

        assertEquals(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "old-ollama-model"
            ),
            persistence.loadSelection()
        )
        assertEquals(
            ChatProviderSelectionSaveResult.Saved,
            persistence.saveSelection(
                ChatProviderSelection.Custom(
                    DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                    "new-ollama-model"
                )
            )
        )
        assertEquals(DesktopProvider.OLLAMA, stored.provider)
        assertEquals("new-ollama-model", stored.ollamaModel)
    }

    @Test
    fun legacySelectionIsRejectedWithoutOverwrite() {
        val initial = DesktopUserSettings()
        var stored = initial
        val persistence = persistence({ stored }, { stored = it }, catalog())

        val result = persistence.saveSelection(ChatProviderSelection.Legacy)

        assertEquals(
            ChatProviderSelectionSaveResult.Rejected(
                ChatProviderSelectionValidationIssue.UNSUPPORTED_SELECTION
            ),
            result
        )
        assertEquals(initial, stored)
    }

    @Test
    fun blankOpenRouterModelIsRejected() {
        assertRejectedWithoutWrite(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OPENROUTER_PROVIDER_ID,
                " "
            ),
            ChatProviderSelectionValidationIssue.BLANK_MODEL_ID
        )
    }

    @Test
    fun blankOllamaModelIsRejected() {
        assertRejectedWithoutWrite(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                ""
            ),
            ChatProviderSelectionValidationIssue.BLANK_MODEL_ID
        )
    }

    @Test
    fun unknownModelIsRejected() {
        assertRejectedWithoutWrite(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OPENROUTER_PROVIDER_ID,
                "unknown-model"
            ),
            ChatProviderSelectionValidationIssue.MODEL_NOT_FOUND
        )
    }

    @Test
    fun unknownProviderIsRejectedWithoutOverwrite() {
        assertRejectedWithoutWrite(
            ChatProviderSelection.Custom("unknown-provider", "unknown-model"),
            ChatProviderSelectionValidationIssue.PROVIDER_NOT_FOUND
        )
    }

    @Test
    fun blankProviderIsRejectedWithTypedIssue() {
        assertRejectedWithoutWrite(
            ChatProviderSelection.Custom(" ", "model"),
            ChatProviderSelectionValidationIssue.BLANK_PROVIDER_ID
        )
    }

    @Test
    fun invalidSelectionLeavesAllExistingSettingsUnchanged() {
        val initial = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            openRouterApiKey = "preserved",
            ollamaBaseUrl = "http://127.0.0.1:11434",
            authEmail = "person@example.invalid"
        )
        var stored = initial
        var writes = 0
        val persistence = persistence(
            load = { stored },
            save = {
                stored = it
                writes += 1
            },
            catalog = catalog()
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "missing"
            )
        )

        assertEquals(
            ChatProviderSelectionSaveResult.Rejected(
                ChatProviderSelectionValidationIssue.MODEL_NOT_FOUND
            ),
            result
        )
        assertEquals(initial, stored)
        assertEquals(0, writes)
    }

    @Test
    fun rejectedSaveResultUsesTypedSafeIssue() {
        val persistence = persistence(
            load = { DesktopUserSettings() },
            save = {},
            catalog = catalog()
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OPENROUTER_PROVIDER_ID,
                "missing"
            )
        )

        assertEquals(
            ChatProviderSelectionValidationIssue.MODEL_NOT_FOUND,
            (result as ChatProviderSelectionSaveResult.Rejected).issue
        )
        assertFalse(result.toString().contains("http"))
    }

    @Test
    fun sharedDtosContainNeitherApiKeyNorBaseUrl() {
        val settings = DesktopUserSettings(
            provider = DesktopProvider.OPENROUTER,
            openRouterApiKey = "not-exposed",
            openRouterModel = "safe-model",
            ollamaBaseUrl = "http://127.0.0.1:11434"
        )

        val snapshot = DesktopChatProviderSelectionAdapter.catalog(settings)
        val visible = snapshot.toString()

        assertFalse(visible.contains(settings.openRouterApiKey))
        assertFalse(visible.contains(settings.ollamaBaseUrl))
    }

    @Test
    fun unchangedSelectionUsesNoSecondPersistenceAndNoWrite() {
        val settings = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            ollamaModel = "ollama-model"
        )
        var writes = 0
        val persistence = persistence(
            load = { settings },
            save = { writes += 1 },
            catalog = catalog(ollamaModels = listOf("ollama-model"))
        )

        val result = persistence.saveSelection(persistence.loadSelection())

        assertEquals(ChatProviderSelectionSaveResult.Unchanged, result)
        assertEquals(0, writes)
    }

    @Test
    fun validConfirmedSelectionWritesExactlyOnce() {
        var stored = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            ollamaModel = "old-model"
        )
        var writes = 0
        val persistence = persistence(
            load = { stored },
            save = {
                stored = it
                writes += 1
            },
            catalog = catalog(ollamaModels = listOf("new-model"))
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "new-model"
            )
        )

        assertEquals(ChatProviderSelectionSaveResult.Saved, result)
        assertEquals(1, writes)
        assertEquals("new-model", stored.ollamaModel)
    }

    @Test
    fun saveUsesExactlyOneCatalogSnapshot() {
        var snapshots = 0
        val settings = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            ollamaModel = "old-model"
        )
        val persistence = DesktopChatProviderSelectionPersistence(
            loadSettings = { settings },
            saveSettings = {},
            catalog = ChatProviderCatalog {
                snapshots += 1
                catalog(ollamaModels = listOf("new-model"))
            }
        )

        persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "new-model"
            )
        )

        assertEquals(1, snapshots)
    }

    @Test
    fun saveUsesExactlyOneSettingsSnapshot() {
        var reads = 0
        val settings = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            ollamaModel = "old-model"
        )
        val persistence = DesktopChatProviderSelectionPersistence(
            loadSettings = {
                reads += 1
                settings
            },
            saveSettings = {},
            catalog = ChatProviderCatalog {
                catalog(ollamaModels = listOf("new-model"))
            }
        )

        persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "new-model"
            )
        )

        assertEquals(1, reads)
    }

    @Test
    fun rejectedSelectionPerformsNoWrite() {
        var writes = 0
        val persistence = DesktopChatProviderSelectionPersistence(
            loadSettings = { DesktopUserSettings() },
            saveSettings = { writes += 1 },
            catalog = ChatProviderCatalog { catalog() }
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom("unknown-provider", "unknown-model")
        )

        assertEquals(
            ChatProviderSelectionSaveResult.Rejected(
                ChatProviderSelectionValidationIssue.PROVIDER_NOT_FOUND
            ),
            result
        )
        assertEquals(0, writes)
    }

    @Test
    fun unchangedSelectionPerformsNoWrite() {
        val settings = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            ollamaModel = "ollama-model"
        )
        var writes = 0
        val persistence = persistence(
            load = { settings },
            save = { writes += 1 },
            catalog = catalog(ollamaModels = listOf("ollama-model"))
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "ollama-model"
            )
        )

        assertEquals(ChatProviderSelectionSaveResult.Unchanged, result)
        assertEquals(0, writes)
    }

    @Test
    fun validSelectionPerformsExactlyOneWrite() {
        var writes = 0
        val persistence = persistence(
            load = {
                DesktopUserSettings(
                    provider = DesktopProvider.OLLAMA,
                    ollamaModel = "old-model"
                )
            },
            save = { writes += 1 },
            catalog = catalog(ollamaModels = listOf("new-model"))
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "new-model"
            )
        )

        assertEquals(ChatProviderSelectionSaveResult.Saved, result)
        assertEquals(1, writes)
    }

    @Test
    fun settingsUsedForUpdateAreTheSameSnapshotThatWasReadForTheOperation() {
        val first = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            ollamaModel = "old-model",
            authEmail = "first@example.invalid"
        )
        val second = first.copy(authEmail = "second@example.invalid")
        var reads = 0
        var written: DesktopUserSettings? = null
        val persistence = DesktopChatProviderSelectionPersistence(
            loadSettings = {
                reads += 1
                if (reads == 1) first else second
            },
            saveSettings = { written = it },
            catalog = ChatProviderCatalog {
                catalog(ollamaModels = listOf("new-model"))
            }
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "new-model"
            )
        )

        assertEquals(ChatProviderSelectionSaveResult.Saved, result)
        assertEquals(1, reads)
        assertEquals("first@example.invalid", written?.authEmail)
        assertEquals("new-model", written?.ollamaModel)
    }

    @Test
    fun catalogMutationAfterSnapshotDoesNotChangeCurrentSaveDecision() {
        val models = mutableListOf(
            ChatProviderModel("new-model", "New model", enabled = true)
        )
        val mutableCatalog = ChatProviderCatalogSnapshot(
            providers = listOf(
                ChatProvider(
                    id = DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                    displayName = "Ollama",
                    connectionKind = ChatProviderConnectionKind.LOCAL,
                    availability = ChatProviderAvailability.AVAILABLE,
                    models = models
                )
            )
        )
        val mutatingValidator = ChatProviderSelectionValidator { selection, snapshot ->
            models.clear()
            CatalogChatProviderSelectionValidator.validate(selection, snapshot)
        }
        var writes = 0
        val persistence = DesktopChatProviderSelectionPersistence(
            loadSettings = {
                DesktopUserSettings(
                    provider = DesktopProvider.OLLAMA,
                    ollamaModel = "old-model"
                )
            },
            saveSettings = { writes += 1 },
            catalog = ChatProviderCatalog { mutableCatalog },
            validator = mutatingValidator
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                "new-model"
            )
        )

        assertEquals(ChatProviderSelectionSaveResult.Saved, result)
        assertEquals(1, writes)
    }

    @Test
    fun settingsProviderReturningDifferentValuesOnRepeatedReadsIsOnlyReadOnce() {
        val first = DesktopUserSettings(
            provider = DesktopProvider.OPENROUTER,
            openRouterApiKey = "fixture-key",
            openRouterModel = "old-model"
        )
        val second = DesktopUserSettings(
            provider = DesktopProvider.OLLAMA,
            ollamaModel = "unrelated-model"
        )
        var reads = 0
        var written: DesktopUserSettings? = null
        val persistence = DesktopChatProviderSelectionPersistence(
            loadSettings = {
                reads += 1
                if (reads == 1) first else second
            },
            saveSettings = { written = it },
            catalog = ChatProviderCatalog {
                catalog(openRouterModels = listOf("new-model"))
            }
        )

        val result = persistence.saveSelection(
            ChatProviderSelection.Custom(
                DesktopChatProviderSelectionAdapter.OPENROUTER_PROVIDER_ID,
                "new-model"
            )
        )

        assertEquals(ChatProviderSelectionSaveResult.Saved, result)
        assertEquals(1, reads)
        assertEquals(DesktopProvider.OPENROUTER, written?.provider)
        assertEquals("new-model", written?.openRouterModel)
        assertEquals("fixture-key", written?.openRouterApiKey)
    }

    private fun assertRejectedWithoutWrite(
        selection: ChatProviderSelection,
        issue: ChatProviderSelectionValidationIssue
    ) {
        val initial = DesktopUserSettings()
        var stored = initial
        var writes = 0
        val persistence = persistence(
            load = { stored },
            save = {
                stored = it
                writes += 1
            },
            catalog = catalog()
        )

        assertEquals(
            ChatProviderSelectionSaveResult.Rejected(issue),
            persistence.saveSelection(selection)
        )
        assertEquals(initial, stored)
        assertEquals(0, writes)
    }

    private fun persistence(
        load: () -> DesktopUserSettings,
        save: (DesktopUserSettings) -> Unit,
        catalog: ChatProviderCatalogSnapshot
    ) = DesktopChatProviderSelectionPersistence(
        loadSettings = load,
        saveSettings = save,
        catalog = ChatProviderCatalog { catalog }
    )

    private fun catalog(
        openRouterModels: List<String> = listOf("openrouter-model"),
        ollamaModels: List<String> = listOf("ollama-model")
    ) = ChatProviderCatalogSnapshot(
        providers = listOf(
            provider(
                DesktopChatProviderSelectionAdapter.OPENROUTER_PROVIDER_ID,
                ChatProviderConnectionKind.EXTERNAL,
                openRouterModels
            ),
            provider(
                DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID,
                ChatProviderConnectionKind.LOCAL,
                ollamaModels
            )
        )
    )

    private fun provider(
        id: String,
        connectionKind: ChatProviderConnectionKind,
        models: List<String>
    ) = ChatProvider(
        id = id,
        displayName = id,
        connectionKind = connectionKind,
        availability = ChatProviderAvailability.AVAILABLE,
        models = models.map {
            ChatProviderModel(
                id = it,
                displayName = it,
                enabled = true
            )
        }
    )
}
