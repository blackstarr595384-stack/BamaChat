package com.example.bamachat.data.provider.chat

import android.content.Context
import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderCapabilities
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.ProviderModelDefinition
import com.example.bamachat.data.provider.ProviderModelSource
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.data.provider.ProviderSecretStorage
import com.example.bamachat.data.provider.local.ProviderEntity
import com.example.bamachat.data.provider.local.ProviderModelEntity
import com.example.bamachat.data.provider.local.ProviderStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActiveChatProviderResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var fixture: ResolverFixture

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("resolver_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val store = ResolverProviderStore()
        val secrets = ResolverSecretStorage()
        val repository = ProviderRepository(store, secrets)
        val selectionStore = ActiveChatProviderSelectionStore(preferences)
        fixture = ResolverFixture(store, secrets, repository, selectionStore, ActiveChatProviderResolver(selectionStore, repository, secrets))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validCustomProviderResolvesAndNoneLocalNeedsNoSecret() = runBlocking {
        val selection = addProvider(authentication = ProviderAuthenticationType.NONE_LOCAL_ONLY)

        val resolution = fixture.resolver.resolve(selection)

        assertTrue(resolution is ActiveChatProviderResolution.ResolvedCustomProvider)
    }

    @Test
    fun missingSecretDisabledProviderAndDisabledModelAreRejected() = runBlocking {
        val missingSecret = addProvider(authentication = ProviderAuthenticationType.BEARER)
        assertInvalid(missingSecret, ActiveChatProviderResolutionError.SECRET_MISSING)

        val disabledProvider = addProvider(enabled = false)
        assertInvalid(disabledProvider, ActiveChatProviderResolutionError.PROVIDER_DISABLED)

        val disabledModel = addProvider(modelEnabled = false)
        assertInvalid(disabledModel, ActiveChatProviderResolutionError.MODEL_DISABLED)
    }

    @Test
    fun deletedProviderAndMissingModelAreRejectedWithoutNetwork() = runBlocking {
        val deleted = ActiveChatProviderSelection.Custom(ProviderId.newCustom(), "missing")
        assertInvalid(deleted, ActiveChatProviderResolutionError.PROVIDER_MISSING)

        val selection = addProvider()
        fixture.store.models[selection.providerId.value]?.clear()
        assertInvalid(selection, ActiveChatProviderResolutionError.MODEL_MISSING)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun customAuthenticationFailureMakesExactlyOneRequest() = runBlocking {
        val selection = addProvider(authentication = ProviderAuthenticationType.BEARER)
        fixture.secrets.put(selection.providerId, generatedSecret())
        server.enqueue(MockResponse().setResponseCode(401))
        val engine = ProviderChatExecutionEngine(
            fixture.resolver,
            fixture.secrets,
            OpenAiCompatibleChatAdapter(),
            OllamaLocalChatAdapter()
        )

        val error = runCatching {
            engine.execute(
                ProviderChatRequest(selection, listOf(ProviderChatMessage("user", "Test")))
            ) { }
        }.exceptionOrNull() as ProviderChatException

        assertEquals(ProviderChatError.AUTHENTICATION_FAILED, error.error)
        assertEquals(1, server.requestCount)
    }

    private suspend fun addProvider(
        authentication: ProviderAuthenticationType = ProviderAuthenticationType.NONE_LOCAL_ONLY,
        enabled: Boolean = true,
        modelEnabled: Boolean = true
    ): ActiveChatProviderSelection.Custom {
        val id = ProviderId.newCustom(UUID.randomUUID())
        val provider = ProviderDefinition.create(
            id = id,
            displayName = "Lokaler Testanbieter",
            connectionType = if (authentication == ProviderAuthenticationType.NONE_LOCAL_ONLY) {
                ProviderConnectionType.OLLAMA_LOCAL
            } else {
                ProviderConnectionType.OPENAI_COMPATIBLE
            },
            baseUrl = localServerUrl(),
            authenticationType = authentication,
            capabilities = ProviderCapabilities(false, false, false, false),
            timeoutMs = 5_000,
            enabled = enabled,
            localHttpConfirmed = true
        )
        fixture.repository.createCustomProvider(provider)
        fixture.repository.addManualModel(
            id,
            ProviderModelDefinition.create(id, "model-a", source = ProviderModelSource.MANUAL, enabled = modelEnabled)
        )
        return ActiveChatProviderSelection.Custom(id, "model-a")
    }

    private suspend fun assertInvalid(
        selection: ActiveChatProviderSelection.Custom,
        expected: ActiveChatProviderResolutionError
    ) {
        val resolution = fixture.resolver.resolve(selection) as ActiveChatProviderResolution.Invalid
        assertEquals(expected, resolution.error)
    }

    private fun generatedSecret(): String = CharArray(24) { 'z' }.concatToString()

    private fun localServerUrl(): String = server.url("/").newBuilder()
        .host("127.0.0.1")
        .build()
        .toString()
}

private data class ResolverFixture(
    val store: ResolverProviderStore,
    val secrets: ResolverSecretStorage,
    val repository: ProviderRepository,
    val selectionStore: ActiveChatProviderSelectionStore,
    val resolver: ActiveChatProviderResolver
)

private class ResolverSecretStorage : ProviderSecretStorage {
    private val values = mutableMapOf<ProviderId, String>()
    override fun put(providerId: ProviderId, secret: String) { values[providerId] = secret }
    override fun get(providerId: ProviderId): String? = values[providerId]
    override fun contains(providerId: ProviderId): Boolean = providerId in values
    override fun remove(providerId: ProviderId) { values.remove(providerId) }
    override fun clearCustomSecrets() { values.clear() }
}

private class ResolverProviderStore : ProviderStore {
    val providers = linkedMapOf<String, ProviderEntity>()
    val models = linkedMapOf<String, MutableList<ProviderModelEntity>>()
    private val flow = MutableStateFlow<List<ProviderEntity>>(emptyList())

    override fun observeProviders(): Flow<List<ProviderEntity>> = flow
    override fun observeEnabledProviders(): Flow<List<ProviderEntity>> = flow
    override suspend fun getProvider(providerId: String) = providers[providerId]
    override suspend fun insertProvider(provider: ProviderEntity) { providers[provider.providerId] = provider; publish() }
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
    override suspend fun deleteProvider(providerId: String): Int = if (providers.remove(providerId) != null) 1 else 0
    override suspend fun getModels(providerId: String) = models[providerId].orEmpty()
    override suspend fun getModel(providerId: String, modelId: String) = models[providerId].orEmpty().firstOrNull { it.modelId == modelId }
    override suspend fun replaceModels(providerId: String, models: List<ProviderModelEntity>) { this.models[providerId] = models.toMutableList() }
    override suspend fun insertModel(model: ProviderModelEntity) { models.getOrPut(model.providerId) { mutableListOf() }.add(model) }
    override suspend fun insertModelsIfAbsent(models: List<ProviderModelEntity>): List<Long> = models.map { model ->
        val providerModels = this.models.getOrPut(model.providerId) { mutableListOf() }
        if (providerModels.any { it.modelId == model.modelId }) -1L else {
            providerModels += model
            providerModels.size.toLong()
        }
    }
    override suspend fun deleteModel(providerId: String, modelId: String): Int = if (models[providerId]?.removeIf { it.modelId == modelId } == true) 1 else 0
    override suspend fun seedBuiltIns(providers: List<ProviderEntity>, models: List<ProviderModelEntity>) {
        providers.forEach { this.providers.putIfAbsent(it.providerId, it) }
        models.forEach { model -> this.models.getOrPut(model.providerId) { mutableListOf() }.add(model) }
        publish()
    }
    private fun publish() { flow.value = providers.values.toList() }
}
