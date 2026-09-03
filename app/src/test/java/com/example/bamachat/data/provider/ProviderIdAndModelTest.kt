package com.example.bamachat.data.provider

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderIdAndModelTest {
    @Test
    fun acceptsAllowlistedBuiltInAndCanonicalCustomIds() {
        assertEquals(ProviderId.OPENROUTER, ProviderId(ProviderId.OPENROUTER).value)
        val uuid = UUID.randomUUID()
        assertEquals("custom:$uuid", ProviderId.newCustom(uuid).value)
    }

    @Test
    fun rejectsEmptyUnknownBuiltInInvalidUuidAndEmbeddedUrl() {
        assertIdError(ProviderIdValidationError.EMPTY, "")
        assertIdError(ProviderIdValidationError.UNKNOWN_BUILT_IN, "builtin:unknown")
        assertIdError(ProviderIdValidationError.INVALID_CUSTOM_UUID, "custom:not-a-uuid")
        assertIdError(ProviderIdValidationError.UNSUPPORTED_FORMAT, "https://example.invalid")
    }

    @Test
    fun customIdToStringDoesNotExposeCompleteUuid() {
        val id = ProviderId.newCustom(UUID.fromString("11111111-2222-3333-4444-555555555555"))
        assertFalse(id.toString().contains(id.value))
        assertTrue(id.toString().contains("custom:"))
    }

    @Test
    fun definitionTrimsFactoryInputAndContainsNoSecretProperty() {
        val definition = customDefinition(displayName = "  Mein Anbieter  ")
        assertEquals("Mein Anbieter", definition.displayName)
        val propertyNames = ProviderDefinition::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(propertyNames.any { it == "apikey" || it == "secret" })
    }

    @Test
    fun timeoutBoundsAreEnforced() {
        assertThrows(ProviderDefinitionValidationException::class.java) {
            customDefinition(timeoutMs = ProviderDefinition.MIN_TIMEOUT_MS - 1)
        }
        assertThrows(ProviderDefinitionValidationException::class.java) {
            customDefinition(timeoutMs = ProviderDefinition.MAX_TIMEOUT_MS + 1)
        }
        customDefinition(timeoutMs = ProviderDefinition.MIN_TIMEOUT_MS)
        customDefinition(timeoutMs = ProviderDefinition.MAX_TIMEOUT_MS)
    }

    @Test
    fun builtInFlagCannotContradictTechnicalId() {
        assertThrows(ProviderDefinitionValidationException::class.java) {
            customDefinition().copy(builtIn = true)
        }
    }

    @Test
    fun modelIdMustBeNonBlankAndBelongToStableProviderId() {
        val providerId = ProviderId.newCustom()
        val model = ProviderModelDefinition.create(providerId, " model-a ", source = ProviderModelSource.MANUAL)
        assertEquals("model-a", model.modelId)
        assertEquals(providerId, model.providerId)
        assertThrows(ProviderModelValidationException::class.java) {
            ProviderModelDefinition.create(providerId, " ", source = ProviderModelSource.MANUAL)
        }
    }

    private fun assertIdError(expected: ProviderIdValidationError, value: String) {
        val exception = assertThrows(ProviderIdValidationException::class.java) { ProviderId(value) }
        assertEquals(expected, exception.error)
    }
}

internal fun customDefinition(
    id: ProviderId = ProviderId.newCustom(),
    displayName: String = "Eigener Anbieter",
    baseUrl: String = "https://provider.example/v1/",
    timeoutMs: Long = ProviderDefinition.DEFAULT_TIMEOUT_MS,
    enabled: Boolean = true,
    defaultModelId: String? = null,
    hasSecret: Boolean = false
): ProviderDefinition = ProviderDefinition.create(
    id = id,
    displayName = displayName,
    connectionType = ProviderConnectionType.OPENAI_COMPATIBLE,
    baseUrl = baseUrl,
    authenticationType = ProviderAuthenticationType.BEARER,
    defaultModelId = defaultModelId,
    capabilities = ProviderCapabilities(streaming = true, modelDiscovery = false, tools = false, vision = false),
    timeoutMs = timeoutMs,
    enabled = enabled,
    builtIn = false,
    hasSecret = hasSecret
)
