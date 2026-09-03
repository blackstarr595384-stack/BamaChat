package com.example.bamachat.data.provider.discovery

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderCapabilities
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ProviderDiscoveryServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var store: DiscoveryProviderStore
    private lateinit var secrets: DiscoverySecretStorage
    private lateinit var repository: ProviderRepository
    private lateinit var service: ProviderDiscoveryService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = DiscoveryProviderStore()
        secrets = DiscoverySecretStorage()
        repository = ProviderRepository(store, secrets)
        service = ProviderDiscoveryService(repository, secrets, OpenAiModelDiscoveryAdapter(), OllamaModelDiscoveryAdapter())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun enabledCustomProviderUsesExactlyOneDiscoveryRequest() = runBlocking {
        val provider = provider()
        repository.createCustomProvider(provider)
        secrets.put(provider.id, generatedSecret())
        server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"model-a\"}]}"))

        val result = service.discover(provider.id)

        assertEquals(listOf("model-a"), result.models.map { it.modelId })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun missingDisabledBuiltInAndMissingSecretAreBlockedBeforeNetwork() = runBlocking {
        val missing = ProviderId.newCustom(UUID.fromString("44444444-4444-4444-4444-444444444444"))
        assertServiceError(ProviderDiscoveryError.PROVIDER_MISSING) { service.discover(missing) }

        val disabled = provider(id = ProviderId.newCustom(), enabled = false)
        repository.createCustomProvider(disabled)
        assertServiceError(ProviderDiscoveryError.PROVIDER_DISABLED) { service.discover(disabled.id) }

        repository.seedBuiltInProviders()
        assertServiceError(ProviderDiscoveryError.BUILT_IN_NOT_SUPPORTED) {
            service.discover(ProviderId(ProviderId.OPENROUTER))
        }

        val noSecret = provider(id = ProviderId.newCustom())
        repository.createCustomProvider(noSecret)
        assertServiceError(ProviderDiscoveryError.SECRET_MISSING) { service.discover(noSecret.id) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun publicOllamaAndUnconfirmedLocalHttpAreBlockedBeforeNetwork() = runBlocking {
        val publicOllama = provider(
            id = ProviderId.newCustom(),
            connectionType = ProviderConnectionType.OLLAMA_LOCAL,
            authenticationType = ProviderAuthenticationType.NONE_LOCAL_ONLY,
            baseUrl = "https://provider.example/",
            localHttpConfirmed = false
        )
        store.providers[publicOllama.id.value] = publicOllama.toTestEntity()
        assertServiceError(ProviderDiscoveryError.UNSAFE_URL) { service.discover(publicOllama.id) }

        val unconfirmedLocal = provider(
            id = ProviderId.newCustom(),
            authenticationType = ProviderAuthenticationType.NONE_LOCAL_ONLY,
            baseUrl = localUrl("/"),
            localHttpConfirmed = false
        )
        store.providers[unconfirmedLocal.id.value] = unconfirmedLocal.toTestEntity()
        assertServiceError(ProviderDiscoveryError.LOCAL_HTTP_CONFIRMATION_REQUIRED) {
            service.discover(unconfirmedLocal.id)
        }
        assertEquals(0, server.requestCount)
    }

    private fun provider(
        id: ProviderId = ProviderId.newCustom(UUID.fromString("55555555-5555-5555-5555-555555555555")),
        enabled: Boolean = true,
        connectionType: ProviderConnectionType = ProviderConnectionType.OPENAI_COMPATIBLE,
        authenticationType: ProviderAuthenticationType = ProviderAuthenticationType.BEARER,
        baseUrl: String = localUrl("/v1/"),
        localHttpConfirmed: Boolean = true
    ) = ProviderDefinition.create(
        id = id,
        displayName = "Discovery-Service-Test",
        connectionType = connectionType,
        baseUrl = baseUrl,
        authenticationType = authenticationType,
        capabilities = ProviderCapabilities(streaming = true, modelDiscovery = true, tools = false, vision = false),
        timeoutMs = 5_000,
        enabled = enabled,
        builtIn = false,
        localHttpConfirmed = localHttpConfirmed
    )

    private fun assertServiceError(expected: ProviderDiscoveryError, block: suspend () -> Unit) {
        val error = assertThrows(ProviderDiscoveryException::class.java) { runBlocking { block() } }
        assertEquals(expected, error.error)
    }

    private fun generatedSecret(): String = CharArray(24) { 'z' }.concatToString()

    private fun localUrl(path: String): String = server.url(path).newBuilder().host("127.0.0.1").build().toString()
}

internal class DiscoverySecretStorage : ProviderSecretStorage {
    private val values = linkedMapOf<ProviderId, String>()
    override fun put(providerId: ProviderId, secret: String) { values[providerId] = secret }
    override fun get(providerId: ProviderId): String? = values[providerId]
    override fun contains(providerId: ProviderId): Boolean = providerId in values
    override fun remove(providerId: ProviderId) { values.remove(providerId) }
    override fun clearCustomSecrets() { values.clear() }
}

internal class DiscoveryProviderStore : ProviderStore {
    val providers = linkedMapOf<String, ProviderEntity>()
    private val models = linkedMapOf<String, MutableList<ProviderModelEntity>>()
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
        if (provider.providerId !in providers) return 0
        providers[provider.providerId] = provider
        publish()
        return 1
    }
    override suspend fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long): Int =
        updateProviderField(providerId) { it.copy(enabled = enabled, updatedAt = updatedAt) }
    override suspend fun setDefaultModel(providerId: String, modelId: String?, updatedAt: Long): Int =
        updateProviderField(providerId) { it.copy(defaultModelId = modelId, updatedAt = updatedAt) }
    override suspend fun setHasSecret(providerId: String, hasSecret: Boolean, updatedAt: Long): Int =
        updateProviderField(providerId) { it.copy(hasSecret = hasSecret, updatedAt = updatedAt) }
    override suspend fun deleteProvider(providerId: String): Int = if (providers.remove(providerId) != null) 1 else 0
    override suspend fun getModels(providerId: String): List<ProviderModelEntity> = models[providerId].orEmpty()
    override suspend fun getModel(providerId: String, modelId: String): ProviderModelEntity? =
        models[providerId].orEmpty().firstOrNull { it.modelId == modelId }
    override suspend fun replaceModels(providerId: String, models: List<ProviderModelEntity>) {
        this.models[providerId] = models.toMutableList()
    }
    override suspend fun insertModel(model: ProviderModelEntity) {
        models.getOrPut(model.providerId) { mutableListOf() }.add(model)
    }
    override suspend fun insertModelsIfAbsent(models: List<ProviderModelEntity>): List<Long> = models.map { model ->
        val current = this.models.getOrPut(model.providerId) { mutableListOf() }
        if (current.any { it.modelId == model.modelId }) -1L else {
            current += model
            current.size.toLong()
        }
    }
    override suspend fun deleteModel(providerId: String, modelId: String): Int =
        if (models[providerId]?.removeIf { it.modelId == modelId } == true) 1 else 0
    override suspend fun seedBuiltIns(providers: List<ProviderEntity>, models: List<ProviderModelEntity>) {
        providers.forEach { this.providers.putIfAbsent(it.providerId, it) }
        publish()
    }

    private fun updateProviderField(providerId: String, transform: (ProviderEntity) -> ProviderEntity): Int {
        val provider = providers[providerId] ?: return 0
        providers[providerId] = transform(provider)
        publish()
        return 1
    }

    private fun publish() {
        providerFlow.value = providers.values.toList()
    }
}

private fun ProviderDefinition.toTestEntity() = ProviderEntity(
    providerId = id.value,
    displayName = displayName,
    connectionType = connectionType.name,
    baseUrl = baseUrl,
    authenticationType = authenticationType.name,
    defaultModelId = defaultModelId,
    streaming = capabilities.streaming,
    modelDiscovery = capabilities.modelDiscovery,
    tools = capabilities.tools,
    vision = capabilities.vision,
    timeoutMs = timeoutMs,
    enabled = enabled,
    builtIn = builtIn,
    localHttpConfirmed = localHttpConfirmed,
    hasSecret = hasSecret,
    createdAt = createdAt,
    updatedAt = updatedAt
)
