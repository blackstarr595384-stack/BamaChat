package com.example.bamachat.data.provider.discovery

import androidx.lifecycle.SavedStateHandle
import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderCapabilities
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.ProviderRepository
import com.example.bamachat.ui.viewmodel.ProviderDiscoveryUiStatus
import com.example.bamachat.ui.viewmodel.ProviderEditorViewModel
import java.util.UUID
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ProviderEditorDiscoveryViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ProviderRepository
    private lateinit var service: ProviderDiscoveryService
    private lateinit var provider: ProviderDefinition

    @Before
    fun setUp() = runBlocking {
        server = MockWebServer()
        server.start()
        val store = DiscoveryProviderStore()
        val secrets = DiscoverySecretStorage()
        repository = ProviderRepository(store, secrets)
        service = ProviderDiscoveryService(repository, secrets, OpenAiModelDiscoveryAdapter(), OllamaModelDiscoveryAdapter())
        provider = ProviderDefinition.create(
            id = ProviderId.newCustom(UUID.fromString("66666666-6666-6666-6666-666666666666")),
            displayName = "ViewModel-Test",
            connectionType = ProviderConnectionType.OPENAI_COMPATIBLE,
            baseUrl = server.url("/v1/").newBuilder().host("127.0.0.1").build().toString(),
            authenticationType = ProviderAuthenticationType.NONE_LOCAL_ONLY,
            capabilities = ProviderCapabilities(streaming = true, modelDiscovery = true, tools = false, vision = false),
            timeoutMs = 5_000,
            localHttpConfirmed = true
        )
        repository.createCustomProvider(provider)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun openingEditorMakesNoRequestAndDoubleTapStartsExactlyOne() {
        server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"model-a\"}]}"))
        val viewModel = viewModel(provider.id)
        waitUntil { !viewModel.uiState.value.loading }

        assertEquals(0, server.requestCount)
        viewModel.fetchModels()
        viewModel.fetchModels()
        waitUntil { viewModel.uiState.value.discoveryStatus == ProviderDiscoveryUiStatus.MODELS_FOUND }

        assertEquals(1, server.requestCount)
        assertTrue(runBlocking { repository.getModels(provider.id) }.isEmpty())
        viewModel.cancelDiscovery()
    }

    @Test
    fun discoveredModelIsPersistedOnlyAfterExplicitSelectionAndImport() {
        server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"model-a\"}]}"))
        val viewModel = viewModel(provider.id)
        waitUntil { !viewModel.uiState.value.loading }

        viewModel.fetchModels()
        waitUntil { viewModel.uiState.value.discoveryModels.isNotEmpty() }
        assertTrue(runBlocking { repository.getModels(provider.id) }.isEmpty())

        viewModel.toggleDiscoveredModel("model-a")
        viewModel.importSelectedModels()
        waitUntil { runBlocking { repository.getModels(provider.id) }.any { it.modelId == "model-a" } }

        assertEquals(1, server.requestCount)
        assertEquals(listOf("model-a"), runBlocking { repository.getModels(provider.id) }.map { it.modelId })
        assertEquals("1 Modell importiert.", viewModel.uiState.value.discoveryMessage)
    }

    @Test
    fun unsavedProviderTestActionMakesNoRequest() {
        val viewModel = ProviderEditorViewModel(repository, service, SavedStateHandle())
        waitUntil { !viewModel.uiState.value.loading }

        viewModel.testConnection()

        assertEquals(0, server.requestCount)
        assertEquals("Speichere den Anbieter zuerst.", viewModel.uiState.value.discoveryMessage)
    }

    @Test
    fun connectionSuccessUsesFocusedSessionMessage() {
        server.enqueue(MockResponse().setBody("{\"data\":[]}"))
        val viewModel = viewModel(provider.id)
        waitUntil { !viewModel.uiState.value.loading }

        viewModel.testConnection()
        waitUntil { viewModel.uiState.value.discoveryStatus == ProviderDiscoveryUiStatus.SUCCESS }

        assertEquals("Verbindung erfolgreich.", viewModel.uiState.value.discoveryMessage)
        assertEquals(1, server.requestCount)
    }

    private fun viewModel(providerId: ProviderId) = ProviderEditorViewModel(
        repository,
        service,
        SavedStateHandle(mapOf("providerId" to providerId.value))
    )

    private fun waitUntil(condition: () -> Boolean) {
        repeat(150) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("Bedingung wurde nicht rechtzeitig erfüllt.")
    }
}
