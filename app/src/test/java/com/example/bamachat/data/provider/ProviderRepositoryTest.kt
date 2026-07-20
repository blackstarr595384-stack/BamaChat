package com.example.bamachat.data.provider

import com.example.bamachat.data.provider.local.ProviderEntity
import com.example.bamachat.data.provider.local.ProviderModelEntity
import com.example.bamachat.data.provider.local.ProviderStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRepositoryTest {
    @Test
    fun builtInSeedIsIdempotentAndCreatesNoDuplicates() = runBlocking {
        val fixture = fixture()

        fixture.repository.seedBuiltInProviders()
        fixture.repository.seedBuiltInProviders()

        val providers = fixture.repository.observeProviders().first()
        assertEquals(6, providers.size)
        assertEquals(6, providers.map { it.id }.distinct().size)
        assertTrue(providers.all { it.builtIn })
        assertTrue(fixture.store.models.values.flatten().distinctBy { it.providerId to it.modelId }.size == fixture.store.models.values.flatten().size)
    }

    @Test
    fun customProviderCanBeCreatedUpdatedDisabledAndRead() = runBlocking {
        val fixture = fixture()
        val original = customDefinition(baseUrl = "https://provider.example/v1")

        fixture.repository.createCustomProvider(original)
        val created = fixture.repository.getProvider(original.id)!!
        assertEquals("https://provider.example/v1/", created.baseUrl)

        fixture.repository.updateCustomProvider(created.copy(displayName = "Aktualisierter Anbieter"))
        fixture.repository.setEnabled(original.id, false)

        val updated = fixture.repository.getProvider(original.id)!!
        assertEquals("Aktualisierter Anbieter", updated.displayName)
        assertFalse(updated.enabled)
        assertFalse(fixture.repository.observeEnabledProviders().first().any { it.id == original.id })
    }

    @Test
    fun updateCannotMoveExistingProviderToAnotherId() = runBlocking {
        val fixture = fixture()
        val original = customDefinition()
        fixture.repository.createCustomProvider(original)

        val exception = assertThrows(ProviderRepositoryException::class.java) {
            runBlocking { fixture.repository.updateCustomProvider(original.copy(id = ProviderId.newCustom())) }
        }

        assertEquals(ProviderRepositoryError.PROVIDER_NOT_FOUND, exception.error)
        assertEquals(original.id, fixture.repository.getProvider(original.id)?.id)
    }

    @Test
    fun builtInCannotBeDeletedButCustomDeletionRemovesModelsAndSecret() = runBlocking {
        val fixture = fixture()
        fixture.repository.seedBuiltInProviders()
        val builtInError = assertThrows(ProviderRepositoryException::class.java) {
            runBlocking { fixture.repository.deleteCustomProvider(ProviderId(ProviderId.OPENROUTER)) }
        }
        assertEquals(ProviderRepositoryError.BUILT_IN_DELETE_FORBIDDEN, builtInError.error)

        val custom = customDefinition()
        fixture.repository.createCustomProvider(custom)
        fixture.repository.saveCustomSecret(custom.id, generatedSecret())
        assertTrue(fixture.repository.getProvider(custom.id)?.hasSecret == true)
        fixture.repository.addManualModel(custom.id, manualModel(custom.id, "model-a"))

        fixture.repository.deleteCustomProvider(custom.id)

        assertNull(fixture.repository.getProvider(custom.id))
        assertFalse(fixture.secretStorage.contains(custom.id))
        assertTrue(fixture.store.models[custom.id.value].isNullOrEmpty())
    }

    @Test
    fun defaultModelMustExistBeEnabledAndBelongToProvider() = runBlocking {
        val fixture = fixture()
        val provider = customDefinition()
        fixture.repository.createCustomProvider(provider)
        fixture.repository.replaceModels(
            provider.id,
            listOf(manualModel(provider.id, "enabled"), manualModel(provider.id, "disabled", enabled = false))
        )

        fixture.repository.setDefaultModel(provider.id, "enabled")
        assertEquals("enabled", fixture.repository.getProvider(provider.id)?.defaultModelId)

        val disabledError = assertThrows(ProviderRepositoryException::class.java) {
            runBlocking { fixture.repository.setDefaultModel(provider.id, "disabled") }
        }
        assertEquals(ProviderRepositoryError.MODEL_DISABLED, disabledError.error)

        val unknownError = assertThrows(ProviderRepositoryException::class.java) {
            runBlocking { fixture.repository.setDefaultModel(provider.id, "unknown") }
        }
        assertEquals(ProviderRepositoryError.MODEL_NOT_FOUND, unknownError.error)

        val otherProviderModel = manualModel(ProviderId.newCustom(), "foreign")
        val mismatch = assertThrows(ProviderRepositoryException::class.java) {
            runBlocking { fixture.repository.addManualModel(provider.id, otherProviderModel) }
        }
        assertEquals(ProviderRepositoryError.MODEL_PROVIDER_MISMATCH, mismatch.error)
    }

    @Test
    fun replaceModelsClearsInvalidDefaultAndManualModelCanBeDeleted() = runBlocking {
        val fixture = fixture()
        val provider = customDefinition()
        fixture.repository.createCustomProvider(provider)
        fixture.repository.addManualModel(provider.id, manualModel(provider.id, "first"))
        fixture.repository.setDefaultModel(provider.id, "first")

        fixture.repository.replaceModels(provider.id, listOf(manualModel(provider.id, "second")))
        assertNull(fixture.repository.getProvider(provider.id)?.defaultModelId)
        assertEquals(listOf("second"), fixture.repository.getModels(provider.id).map { it.modelId })

        fixture.repository.deleteModel(provider.id, "second")
        assertTrue(fixture.repository.getModels(provider.id).isEmpty())
    }

    @Test
    fun repositoryAndUrlPolicyHaveNoNetworkOrLegacyPreferenceDependency() {
        val constructorTypes = ProviderRepository::class.java.declaredConstructors.flatMap { it.parameterTypes.toList() }
        assertTrue(constructorTypes.none { type ->
            type.name.contains("okhttp") || type.name.contains("retrofit") || type.name.contains("SharedPreferences")
        })
        val legacyPreferences = linkedMapOf(
            "ai_provider" to "OpenRouter",
            "multi_provider" to "true",
            "openrouter_model" to "legacy-model",
            "opencode_endpoint" to "legacy-endpoint",
            "opencode_model" to "legacy-opencode",
            "ollama_url" to "legacy-local"
        )
        val snapshot = legacyPreferences.toMap()
        fixture()
        assertEquals(snapshot, legacyPreferences)
    }

    @Test
    fun unauthenticatedAndOllamaConnectionsCannotTargetPublicHosts() {
        val fixture = fixture()
        val publicNoAuth = customDefinition().copy(authenticationType = ProviderAuthenticationType.NONE_LOCAL_ONLY)
        val noAuthError = assertThrows(ProviderRepositoryException::class.java) {
            runBlocking { fixture.repository.createCustomProvider(publicNoAuth) }
        }
        assertEquals(ProviderRepositoryError.INVALID_DEFINITION, noAuthError.error)

        val publicOllama = customDefinition().copy(connectionType = ProviderConnectionType.OLLAMA_LOCAL)
        val ollamaError = assertThrows(ProviderRepositoryException::class.java) {
            runBlocking { fixture.repository.createCustomProvider(publicOllama) }
        }
        assertEquals(ProviderRepositoryError.INVALID_DEFINITION, ollamaError.error)
    }

    private fun fixture(): RepositoryFixture {
        val store = FakeProviderStore()
        val secrets = FakeProviderSecretStorage()
        return RepositoryFixture(store, secrets, ProviderRepository(store, secrets))
    }

    private fun manualModel(providerId: ProviderId, id: String, enabled: Boolean = true) =
        ProviderModelDefinition.create(providerId, id, source = ProviderModelSource.MANUAL, enabled = enabled)

    private fun generatedSecret(): String = CharArray(24) { 's' }.concatToString()
}

private data class RepositoryFixture(
    val store: FakeProviderStore,
    val secretStorage: FakeProviderSecretStorage,
    val repository: ProviderRepository
)

private class FakeProviderSecretStorage : ProviderSecretStorage {
    private val values = linkedMapOf<ProviderId, String>()
    override fun put(providerId: ProviderId, secret: String) { values[providerId] = secret }
    override fun get(providerId: ProviderId): String? = values[providerId]
    override fun contains(providerId: ProviderId): Boolean = values.containsKey(providerId)
    override fun remove(providerId: ProviderId) { values.remove(providerId) }
    override fun clearCustomSecrets() { values.clear() }
}

private class FakeProviderStore : ProviderStore {
    val providers = linkedMapOf<String, ProviderEntity>()
    val models = linkedMapOf<String, MutableList<ProviderModelEntity>>()
    private val providerFlow = MutableStateFlow<List<ProviderEntity>>(emptyList())

    override fun observeProviders(): Flow<List<ProviderEntity>> = providerFlow
    override fun observeEnabledProviders(): Flow<List<ProviderEntity>> =
        MutableStateFlow(providers.values.filter { it.enabled })
    override suspend fun getProvider(providerId: String): ProviderEntity? = providers[providerId]
    override suspend fun insertProvider(provider: ProviderEntity) {
        check(providers.putIfAbsent(provider.providerId, provider) == null)
        publish()
    }
    override suspend fun updateProvider(provider: ProviderEntity): Int {
        if (!providers.containsKey(provider.providerId)) return 0
        providers[provider.providerId] = provider
        publish()
        return 1
    }
    override suspend fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long): Int {
        val current = providers[providerId] ?: return 0
        providers[providerId] = current.copy(enabled = enabled, updatedAt = updatedAt)
        publish()
        return 1
    }
    override suspend fun setDefaultModel(providerId: String, modelId: String?, updatedAt: Long): Int {
        val current = providers[providerId] ?: return 0
        providers[providerId] = current.copy(defaultModelId = modelId, updatedAt = updatedAt)
        publish()
        return 1
    }
    override suspend fun setHasSecret(providerId: String, hasSecret: Boolean, updatedAt: Long): Int {
        val current = providers[providerId] ?: return 0
        providers[providerId] = current.copy(hasSecret = hasSecret, updatedAt = updatedAt)
        publish()
        return 1
    }
    override suspend fun deleteProvider(providerId: String): Int {
        val removed = providers.remove(providerId) ?: return 0
        models.remove(removed.providerId)
        publish()
        return 1
    }
    override suspend fun getModels(providerId: String): List<ProviderModelEntity> = models[providerId].orEmpty().toList()
    override suspend fun getModel(providerId: String, modelId: String): ProviderModelEntity? =
        models[providerId].orEmpty().firstOrNull { it.modelId == modelId }
    override suspend fun replaceModels(providerId: String, models: List<ProviderModelEntity>) {
        this.models[providerId] = models.toMutableList()
        val provider = providers[providerId]
        if (provider?.defaultModelId != null && models.none { it.modelId == provider.defaultModelId && it.enabled }) {
            providers[providerId] = provider.copy(defaultModelId = null)
            publish()
        }
    }
    override suspend fun insertModel(model: ProviderModelEntity) {
        val providerModels = models.getOrPut(model.providerId) { mutableListOf() }
        check(providerModels.none { it.modelId == model.modelId })
        providerModels += model
    }
    override suspend fun deleteModel(providerId: String, modelId: String): Int {
        val removed = models[providerId]?.removeIf { it.modelId == modelId } == true
        return if (removed) 1 else 0
    }
    override suspend fun seedBuiltIns(providers: List<ProviderEntity>, models: List<ProviderModelEntity>) {
        providers.forEach { this.providers.putIfAbsent(it.providerId, it) }
        models.forEach { model ->
            val providerModels = this.models.getOrPut(model.providerId) { mutableListOf() }
            if (providerModels.none { it.modelId == model.modelId }) providerModels += model
        }
        publish()
    }
    private fun publish() { providerFlow.value = providers.values.toList() }
}
