package com.example.bamachat.desktop

import com.example.bamachat.shared.core.provider.selection.ChatProvider
import com.example.bamachat.shared.core.provider.selection.ChatProviderAvailability
import com.example.bamachat.shared.core.provider.selection.ChatProviderCatalog
import com.example.bamachat.shared.core.provider.selection.ChatProviderCatalogSnapshot
import com.example.bamachat.shared.core.provider.selection.ChatProviderConnectionKind
import com.example.bamachat.shared.core.provider.selection.ChatProviderModel
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelection
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionPersistence
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionSaveResult
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionValidationIssue
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionValidator
import com.example.bamachat.shared.core.provider.selection.ChatProviderSelectionValidity
import com.example.bamachat.shared.core.provider.selection.CatalogChatProviderSelectionValidator
import com.example.bamachat.shared.core.provider.selection.defensiveCopy

internal object DesktopChatProviderSelectionAdapter {
    const val OPENROUTER_PROVIDER_ID = "desktop:openrouter"
    const val OLLAMA_PROVIDER_ID = "desktop:ollama"

    fun selection(settings: DesktopUserSettings): ChatProviderSelection.Custom = when (
        settings.provider
    ) {
        DesktopProvider.OPENROUTER -> ChatProviderSelection.Custom(
            providerId = OPENROUTER_PROVIDER_ID,
            modelId = settings.openRouterModel
        )
        DesktopProvider.OLLAMA -> ChatProviderSelection.Custom(
            providerId = OLLAMA_PROVIDER_ID,
            modelId = settings.ollamaModel
        )
    }

    fun catalog(settings: DesktopUserSettings): ChatProviderCatalogSnapshot =
        ChatProviderCatalogSnapshot(
            providers = listOf(
                provider(
                    id = OPENROUTER_PROVIDER_ID,
                    displayName = "OpenRouter",
                    connectionKind = ChatProviderConnectionKind.EXTERNAL,
                    modelId = settings.openRouterModel,
                    available = settings.openRouterApiKey.isNotBlank()
                ),
                provider(
                    id = OLLAMA_PROVIDER_ID,
                    displayName = "Ollama",
                    connectionKind = ChatProviderConnectionKind.LOCAL,
                    modelId = settings.ollamaModel,
                    available = true
                )
            )
        )

    fun applyValidatedSelection(
        settings: DesktopUserSettings,
        selection: ChatProviderSelection.Custom
    ): DesktopUserSettings = when (selection.providerId) {
        OPENROUTER_PROVIDER_ID -> settings.copy(
                provider = DesktopProvider.OPENROUTER,
                openRouterModel = selection.modelId
            )
        OLLAMA_PROVIDER_ID -> settings.copy(
                provider = DesktopProvider.OLLAMA,
                ollamaModel = selection.modelId
            )
        else -> settings
    }

    private fun provider(
        id: String,
        displayName: String,
        connectionKind: ChatProviderConnectionKind,
        modelId: String,
        available: Boolean
    ) = ChatProvider(
        id = id,
        displayName = displayName,
        connectionKind = connectionKind,
        availability = when {
            modelId.isBlank() -> ChatProviderAvailability.NO_ACTIVE_MODELS
            !available -> ChatProviderAvailability.CREDENTIAL_MISSING
            else -> ChatProviderAvailability.AVAILABLE
        },
        models = modelId.takeIf { it.isNotBlank() }?.let {
            listOf(ChatProviderModel(it, it, enabled = true, defaultModel = true))
        }.orEmpty()
    )
}

internal class DesktopChatProviderCatalog(
    private val loadSettings: () -> DesktopUserSettings = DesktopSettingsStore::load
) : ChatProviderCatalog {
    override fun snapshot(): ChatProviderCatalogSnapshot =
        DesktopChatProviderSelectionAdapter.catalog(loadSettings())
}

internal class DesktopChatProviderSelectionPersistence(
    private val loadSettings: () -> DesktopUserSettings = DesktopSettingsStore::load,
    private val saveSettings: (DesktopUserSettings) -> Unit = DesktopSettingsStore::save,
    private val catalog: ChatProviderCatalog? = null,
    private val validator: ChatProviderSelectionValidator =
        CatalogChatProviderSelectionValidator
) : ChatProviderSelectionPersistence {
    override fun loadSelection(): ChatProviderSelection =
        DesktopChatProviderSelectionAdapter.selection(loadSettings())

    override fun saveSelection(
        selection: ChatProviderSelection
    ): ChatProviderSelectionSaveResult {
        val current = loadSettings()
        val catalogSnapshot = (
            catalog?.snapshot()
                ?: DesktopChatProviderSelectionAdapter.catalog(current)
            ).defensiveCopy()
        if (selection !is ChatProviderSelection.Custom) {
            return ChatProviderSelectionSaveResult.Rejected(
                ChatProviderSelectionValidationIssue.UNSUPPORTED_SELECTION
            )
        }
        if (selection.providerId.isBlank()) {
            return ChatProviderSelectionSaveResult.Rejected(
                ChatProviderSelectionValidationIssue.BLANK_PROVIDER_ID
            )
        }
        if (
            selection.providerId != DesktopChatProviderSelectionAdapter.OPENROUTER_PROVIDER_ID &&
            selection.providerId != DesktopChatProviderSelectionAdapter.OLLAMA_PROVIDER_ID
        ) {
            return ChatProviderSelectionSaveResult.Rejected(
                ChatProviderSelectionValidationIssue.PROVIDER_NOT_FOUND
            )
        }
        val validity = validator.validate(selection, catalogSnapshot)
        if (validity is ChatProviderSelectionValidity.Invalid) {
            return ChatProviderSelectionSaveResult.Rejected(validity.issue)
        }
        val updated = DesktopChatProviderSelectionAdapter.applyValidatedSelection(
            current,
            selection
        )
        if (updated == current) return ChatProviderSelectionSaveResult.Unchanged
        saveSettings(updated)
        return ChatProviderSelectionSaveResult.Saved
    }
}
