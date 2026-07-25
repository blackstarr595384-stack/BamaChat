package com.example.bamachat.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderCapabilities
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.ProviderModelDefinition
import com.example.bamachat.data.provider.ProviderModelSource
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.data.provider.ProviderSecretStorage
import com.example.bamachat.data.provider.chat.ActiveChatProviderResolver
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelection
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelectionStore
import com.example.bamachat.data.provider.local.ProviderEntity
import com.example.bamachat.data.provider.local.ProviderModelEntity
import com.example.bamachat.data.provider.local.ProviderStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ChatProviderSelectionViewModelTest {
    private lateinit var store: SwitcherProviderStore
    private lateinit var secrets: SwitcherSecretStorage
    private lateinit var repository: ProviderRepository
    private lateinit var selectionStore: ActiveChatProviderSelectionStore
    private lateinit var resolver: ActiveChatProviderResolver

    @Before
    fun setUp() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("chat_provider_switcher_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = SwitcherProviderStore()
        secrets = SwitcherSecretStorage()
        repository = ProviderRepository(store, secrets)
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
    }

    @Test
    fun emptyStateShowsSafeLegacyChoiceWithoutTechnicalData() {
        val viewModel = viewModel()
        waitUntil { !viewModel.uiState.value.loading }

        val state = viewModel.uiState.value
        assertTrue(state.legacySelected)
        assertTrue(state.choices.isEmpty())
        assertEquals("BamaChat Standard", state.summary)
        assertFalse(state.toString().contains("custom:"))
        assertFalse(state.toString().contains("http"))
    }

    @Test
    fun choicesExposeSafeLabelsAndUnavailableStates() = runBlocking {
        addProvider("Lokaler Anbieter", ProviderConnectionType.OLLAMA_LOCAL)
        val external = addProvider(
            "Externer Anbieter",
            ProviderConnectionType.OPENAI_COMPATIBLE,
            ProviderAuthenticationType.BEARER
        )
        repository.saveCustomSecret(external, generatedSecret())
        addProvider("Deaktivierter Anbieter", ProviderConnectionType.OLLAMA_LOCAL, enabled = false)
        addProvider("Anbieter ohne Modelle", ProviderConnectionType.OLLAMA_LOCAL, withModel = false)

        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 4 }
        val choices = viewModel.uiState.value.choices

        assertTrue(choices.any { it.connectionLabel == "Ollama lokal · Lokale Verbindung" })
        assertTrue(choices.any { it.connectionLabel == "OpenAI-kompatibel · Externer Anbieter" })
        assertFalse(choices.first { it.displayName == "Deaktivierter Anbieter" }.selectable)
        assertEquals(
            "Keine aktiven Modelle",
            choices.first { it.displayName == "Anbieter ohne Modelle" }.availabilityLabel
        )
        assertFalse(viewModel.uiState.value.toString().contains("https://"))
        assertFalse(viewModel.uiState.value.toString().contains("custom:"))
    }

    @Test
    fun temporarySelectionPersistsOnlyAfterExplicitConfirmation() = runBlocking {
        addProvider("Lokaler Anbieter", ProviderConnectionType.OLLAMA_LOCAL)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        val optionKey = viewModel.uiState.value.choices.single().models.single().optionKey

        viewModel.selectOption(optionKey)

        assertEquals(ActiveChatProviderSelection.Legacy, selectionStore.selection.value)
        assertTrue(viewModel.uiState.value.choices.single().models.single().selected)

        viewModel.confirm()
        viewModel.confirm()
        waitUntil { selectionStore.selection.value is ActiveChatProviderSelection.Custom }

        assertTrue(selectionStore.selection.value is ActiveChatProviderSelection.Custom)
    }

    @Test
    fun invalidStoredSelectionIsNotSilentlyResetToLegacy() {
        val invalid = ActiveChatProviderSelection.Custom(ProviderId.newCustom(), "missing-model")
        selectionStore.save(invalid)

        val viewModel = viewModel()
        waitUntil { !viewModel.uiState.value.loading }

        assertTrue(viewModel.uiState.value.invalidCurrentSelection)
        assertEquals("Auswahl nicht verfügbar", viewModel.uiState.value.summary)
        assertEquals(invalid, selectionStore.selection.value)

        viewModel.selectLegacy()
        assertEquals(invalid, selectionStore.selection.value)
        viewModel.confirm()
        waitUntil { selectionStore.selection.value == ActiveChatProviderSelection.Legacy }
    }

    @Test
    fun resolverExceptionResetsConfirmingAndDoesNotSave() = runBlocking {
        addProvider("Fehleranbieter", ProviderConnectionType.OLLAMA_LOCAL)
        val preferences = trackingPreferences()
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        viewModel.selectOption(singleOption(viewModel))
        store.getProviderFailure = IllegalStateException("technical detail")

        viewModel.confirm()
        waitUntil { !viewModel.uiState.value.confirming }

        assertEquals(0, preferences.applyCalls)
        assertEquals("Auswahl konnte nicht übernommen werden.", viewModel.uiState.value.warning)
    }

    @Test
    fun storeExceptionResetsConfirmingAndAttemptsOnlyOneSave() = runBlocking {
        addProvider("Speicheranbieter", ProviderConnectionType.OLLAMA_LOCAL)
        val preferences = trackingPreferences(failOnApply = true)
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        viewModel.selectOption(singleOption(viewModel))

        viewModel.confirm()
        waitUntil { !viewModel.uiState.value.confirming }

        assertEquals(1, preferences.applyCalls)
        assertEquals(ActiveChatProviderSelection.Legacy, selectionStore.selection.value)
        assertEquals("Auswahl konnte nicht übernommen werden.", viewModel.uiState.value.warning)
    }

    @Test
    fun cancellationExceptionResetsConfirmingAndIsNotConvertedToTechnicalText() = runBlocking {
        addProvider("Abbruchanbieter", ProviderConnectionType.OLLAMA_LOCAL)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        viewModel.selectOption(singleOption(viewModel))
        store.getProviderFailure = CancellationException("technical cancellation")

        viewModel.confirm()
        waitUntil { !viewModel.uiState.value.confirming }

        assertEquals("Auswahl konnte nicht übernommen werden.", viewModel.uiState.value.warning)
        assertFalse(viewModel.uiState.value.warning.orEmpty().contains("technical"))
        assertEquals(ActiveChatProviderSelection.Legacy, selectionStore.selection.value)
    }

    @Test
    fun cancellationExceptionResetsStateAndIsRethrown() = runBlocking {
        addProvider("Weitergereichter Abbruch", ProviderConnectionType.OLLAMA_LOCAL)
        val preferences = trackingPreferences()
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        viewModel.selectOption(singleOption(viewModel))
        val releaseFailure = CompletableDeferred<Unit>()
        store.getProviderGate = releaseFailure
        store.getProviderFailure = CancellationException("technical cancellation")
        val parentJob = requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
        val existingChildren = parentJob.children.toSet()

        viewModel.confirm()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val confirmationJob = parentJob.children.single { it !in existingChildren }
        val completionCause = CompletableDeferred<Throwable?>()
        confirmationJob.invokeOnCompletion { cause ->
            completionCause.complete(cause)
        }
        releaseFailure.complete(Unit)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(completionCause.await() is CancellationException)
        assertFalse(viewModel.uiState.value.confirming)
        assertEquals(0, preferences.applyCalls)
        assertEquals("Auswahl konnte nicht übernommen werden.", viewModel.uiState.value.warning)
        assertFalse(viewModel.uiState.value.warning.orEmpty().contains("technical"))
    }

    @Test
    fun externalSelectionWithoutLocalChangeUpdatesSavedAndPending() = runBlocking {
        val providerId = addProvider("Extern gewählt", ProviderConnectionType.OLLAMA_LOCAL)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        val selection = ActiveChatProviderSelection.Custom(
            providerId,
            modelIdFor("Extern gewählt")
        )

        selectionStore.save(selection)
        waitUntil { viewModel.uiState.value.choices.single().models.single().selected }

        assertFalse(viewModel.uiState.value.canConfirm)
        assertEquals(selection, selectionStore.selection.value)
    }

    @Test
    fun externalSelectionWithLocalChangePreservesPendingUntilCancel() = runBlocking {
        val first = addProvider("Erste Auswahl", ProviderConnectionType.OLLAMA_LOCAL)
        val second = addProvider("Zweite Auswahl", ProviderConnectionType.OLLAMA_LOCAL)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 2 }
        val firstOption = optionFor(viewModel, "Erste Auswahl")
        viewModel.selectOption(firstOption)
        val external = ActiveChatProviderSelection.Custom(
            second,
            modelIdFor("Zweite Auswahl")
        )

        selectionStore.save(external)
        waitUntil {
            viewModel.uiState.value.choices
                .first { it.displayName == "Erste Auswahl" }
                .models.single().selected
        }

        assertTrue(viewModel.uiState.value.canConfirm)
        viewModel.cancel()
        assertTrue(
            viewModel.uiState.value.choices
                .first { it.displayName == "Zweite Auswahl" }
                .models.single().selected
        )
        assertEquals(external, selectionStore.selection.value)
    }

    @Test
    fun cancelRestoresSavedSelectionWithoutSaveOrResolverCall() = runBlocking {
        addProvider("Lokaler Wechsel", ProviderConnectionType.OLLAMA_LOCAL)
        val preferences = trackingPreferences()
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        viewModel.selectOption(singleOption(viewModel))
        val resolverCalls = store.getProviderCalls

        viewModel.cancel()

        assertTrue(viewModel.uiState.value.legacySelected)
        assertEquals(0, preferences.applyCalls)
        assertEquals(resolverCalls, store.getProviderCalls)
    }

    @Test
    fun fastDoubleTapSavesExactlyOnce() = runBlocking {
        addProvider("Doppeltipp", ProviderConnectionType.OLLAMA_LOCAL)
        val preferences = trackingPreferences()
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        viewModel.selectOption(singleOption(viewModel))

        viewModel.confirm()
        viewModel.confirm()
        waitUntil { selectionStore.selection.value is ActiveChatProviderSelection.Custom }

        assertEquals(1, preferences.applyCalls)
    }

    @Test
    fun selectingAlreadySavedOptionDoesNotSaveOrLoseState() = runBlocking {
        val providerId = addProvider("Gespeichert", ProviderConnectionType.OLLAMA_LOCAL)
        val preferences = trackingPreferences()
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        val saved = ActiveChatProviderSelection.Custom(providerId, modelIdFor("Gespeichert"))
        selectionStore.save(saved)
        preferences.applyCalls = 0
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }

        viewModel.selectOption(singleOption(viewModel))
        viewModel.confirm()

        assertEquals(0, preferences.applyCalls)
        assertFalse(viewModel.uiState.value.canConfirm)
        assertTrue(viewModel.uiState.value.choices.single().models.single().selected)
    }

    @Test
    fun modelDeletedImmediatelyBeforeSavePreventsPersistence() = runBlocking {
        val providerId = addProvider("Gelöschtes Modell", ProviderConnectionType.OLLAMA_LOCAL)
        val preferences = trackingPreferences()
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        viewModel.selectOption(singleOption(viewModel))
        store.deleteModel(providerId.value, modelIdFor("Gelöschtes Modell"))

        viewModel.confirm()
        waitUntil { !viewModel.uiState.value.confirming }

        assertEquals(0, preferences.applyCalls)
        assertEquals(
            "Diese Anbieter- und Modellauswahl ist nicht verfügbar.",
            viewModel.uiState.value.warning
        )
    }

    @Test
    fun providerDeletedImmediatelyBeforeSaveDoesNotPersist() = runBlocking {
        addProvider("Gelöschter Anbieter", ProviderConnectionType.OLLAMA_LOCAL)
        val preferences = trackingPreferences()
        selectionStore = ActiveChatProviderSelectionStore(preferences)
        resolver = ActiveChatProviderResolver(selectionStore, repository, secrets)
        val viewModel = viewModel()
        waitUntil { viewModel.uiState.value.choices.size == 1 }
        viewModel.selectOption(singleOption(viewModel))
        store.deleteProviderOnGet = true

        viewModel.confirm()
        waitUntil { !viewModel.uiState.value.confirming }

        assertEquals(0, preferences.applyCalls)
        assertEquals(ActiveChatProviderSelection.Legacy, selectionStore.selection.value)
        assertEquals(
            "Diese Anbieter- und Modellauswahl ist nicht verfügbar.",
            viewModel.uiState.value.warning
        )
        assertFalse(viewModel.uiState.value.warning.orEmpty().contains("technical"))
    }

    private fun viewModel() = ChatProviderSelectionViewModel(repository, selectionStore, resolver)

    private fun singleOption(viewModel: ChatProviderSelectionViewModel): String =
        viewModel.uiState.value.choices.single().models.single().optionKey

    private fun optionFor(
        viewModel: ChatProviderSelectionViewModel,
        providerName: String
    ): String = viewModel.uiState.value.choices
        .first { it.displayName == providerName }
        .models
        .single()
        .optionKey

    private fun modelIdFor(name: String): String = "model-${name.length}"

    private fun trackingPreferences(
        failOnApply: Boolean = false
    ): TrackingSharedPreferences {
        val delegate = RuntimeEnvironment.getApplication()
            .getSharedPreferences("chat_provider_tracking_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        return TrackingSharedPreferences(delegate, failOnApply)
    }

    private suspend fun addProvider(
        name: String,
        connectionType: ProviderConnectionType,
        authenticationType: ProviderAuthenticationType = ProviderAuthenticationType.NONE_LOCAL_ONLY,
        enabled: Boolean = true,
        withModel: Boolean = true
    ): ProviderId {
        val id = ProviderId.newCustom(UUID.randomUUID())
        repository.createCustomProvider(
            ProviderDefinition.create(
                id = id,
                displayName = name,
                connectionType = connectionType,
                baseUrl = if (connectionType == ProviderConnectionType.OLLAMA_LOCAL) {
                    "http://127.0.0.1:11434"
                } else {
                    "https://example.invalid/v1"
                },
                authenticationType = authenticationType,
                capabilities = ProviderCapabilities(true, false, false, false),
                enabled = enabled,
                localHttpConfirmed = connectionType == ProviderConnectionType.OLLAMA_LOCAL
            )
        )
        if (withModel) {
            repository.addManualModel(
                id,
                ProviderModelDefinition.create(
                    providerId = id,
                    modelId = modelIdFor(name),
                    displayName = "$name Modell",
                    source = ProviderModelSource.MANUAL
                )
            )
        }
        return id
    }

    private fun generatedSecret(): String = CharArray(24) { 'q' }.concatToString()

    private fun waitUntil(condition: () -> Boolean) {
        val mainLooper = shadowOf(android.os.Looper.getMainLooper())
        mainLooper.idle()
        if (!condition()) {
            mainLooper.runToEndOfTasks()
        }
        assertTrue("Bedingung wurde nicht erfüllt.", condition())
    }
}

private class SwitcherSecretStorage : ProviderSecretStorage {
    private val values = mutableMapOf<ProviderId, String>()
    override fun put(providerId: ProviderId, secret: String) {
        values[providerId] = secret
    }
    override fun get(providerId: ProviderId): String? = values[providerId]
    override fun contains(providerId: ProviderId): Boolean = providerId in values
    override fun remove(providerId: ProviderId) {
        values.remove(providerId)
    }
    override fun clearCustomSecrets() {
        values.clear()
    }
}

private class SwitcherProviderStore : ProviderStore {
    private val providers = linkedMapOf<String, ProviderEntity>()
    private val models = linkedMapOf<String, MutableList<ProviderModelEntity>>()
    private val providerFlow = MutableStateFlow<List<ProviderEntity>>(emptyList())
    var getProviderFailure: Throwable? = null
    var getProviderGate: CompletableDeferred<Unit>? = null
    var deleteProviderOnGet: Boolean = false
    var getProviderCalls: Int = 0

    override fun observeProviders(): Flow<List<ProviderEntity>> = providerFlow
    override fun observeEnabledProviders(): Flow<List<ProviderEntity>> =
        MutableStateFlow(providers.values.filter { it.enabled })
    override suspend fun getProvider(providerId: String): ProviderEntity? {
        getProviderCalls += 1
        getProviderGate?.await()
        getProviderFailure?.let { throw it }
        if (deleteProviderOnGet) {
            deleteProviderOnGet = false
            providers.remove(providerId)
            models.remove(providerId)
            publish()
            return null
        }
        return providers[providerId]
    }
    override suspend fun insertProvider(provider: ProviderEntity) {
        providers[provider.providerId] = provider
        publish()
    }
    override suspend fun updateProvider(provider: ProviderEntity): Int {
        if (provider.providerId !in providers) return 0
        providers[provider.providerId] = provider
        publish()
        return 1
    }
    override suspend fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long): Int {
        val provider = providers[providerId] ?: return 0
        providers[providerId] = provider.copy(enabled = enabled, updatedAt = updatedAt)
        publish()
        return 1
    }
    override suspend fun setDefaultModel(providerId: String, modelId: String?, updatedAt: Long): Int {
        val provider = providers[providerId] ?: return 0
        providers[providerId] = provider.copy(defaultModelId = modelId, updatedAt = updatedAt)
        publish()
        return 1
    }
    override suspend fun setHasSecret(providerId: String, hasSecret: Boolean, updatedAt: Long): Int {
        val provider = providers[providerId] ?: return 0
        providers[providerId] = provider.copy(hasSecret = hasSecret, updatedAt = updatedAt)
        publish()
        return 1
    }
    override suspend fun deleteProvider(providerId: String): Int {
        val removed = providers.remove(providerId) ?: return 0
        models.remove(removed.providerId)
        publish()
        return 1
    }
    override suspend fun getModels(providerId: String): List<ProviderModelEntity> =
        models[providerId].orEmpty()
    override suspend fun getModel(providerId: String, modelId: String): ProviderModelEntity? =
        models[providerId].orEmpty().firstOrNull { it.modelId == modelId }
    override suspend fun replaceModels(providerId: String, models: List<ProviderModelEntity>) {
        this.models[providerId] = models.toMutableList()
    }
    override suspend fun insertModel(model: ProviderModelEntity) {
        models.getOrPut(model.providerId) { mutableListOf() }.add(model)
    }
    override suspend fun insertModelsIfAbsent(models: List<ProviderModelEntity>): List<Long> =
        models.map { model ->
            val providerModels = this.models.getOrPut(model.providerId) { mutableListOf() }
            if (providerModels.any { it.modelId == model.modelId }) {
                -1L
            } else {
                providerModels += model
                providerModels.size.toLong()
            }
        }
    override suspend fun deleteModel(providerId: String, modelId: String): Int =
        if (models[providerId]?.removeIf { it.modelId == modelId } == true) 1 else 0
    override suspend fun seedBuiltIns(
        providers: List<ProviderEntity>,
        models: List<ProviderModelEntity>
    ) {
        providers.forEach { this.providers.putIfAbsent(it.providerId, it) }
        models.forEach { model ->
            val providerModels = this.models.getOrPut(model.providerId) { mutableListOf() }
            if (providerModels.none { it.modelId == model.modelId }) providerModels += model
        }
        publish()
    }

    private fun publish() {
        providerFlow.value = providers.values.toList()
    }
}

private class TrackingSharedPreferences(
    private val delegate: SharedPreferences,
    private val failOnApply: Boolean
) : SharedPreferences by delegate {
    var applyCalls: Int = 0

    override fun edit(): SharedPreferences.Editor {
        val editor = delegate.edit()
        return object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                editor.putString(key, value)
                return this
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?
            ): SharedPreferences.Editor {
                editor.putStringSet(key, values)
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                editor.putInt(key, value)
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                editor.putLong(key, value)
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                editor.putFloat(key, value)
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                editor.putBoolean(key, value)
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                editor.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                editor.clear()
                return this
            }

            override fun commit(): Boolean = editor.commit()

            override fun apply() {
                applyCalls += 1
                if (failOnApply) throw IllegalStateException("simulated write failure")
                editor.apply()
            }
        }
    }
}
