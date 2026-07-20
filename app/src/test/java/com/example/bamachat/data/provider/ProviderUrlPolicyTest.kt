package com.example.bamachat.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUrlPolicyTest {
    @Test
    fun publicHttpsIsAcceptedAndNormalized() {
        val result = ProviderUrlPolicy.validate("https://api.example.com/v1", false)
        assertEquals("https://api.example.com/v1/", (result as ProviderUrlValidationResult.Valid).normalizedUrl)
    }

    @Test
    fun publicHttpIsRejected() {
        assertInvalid("http://api.example.com/v1", ProviderUrlError.PUBLIC_HTTP_NOT_ALLOWED)
    }

    @Test
    fun localHttpTargetsRequireExplicitConfirmation() {
        listOf("http://localhost:11434", "http://192.168.1.10:11434", "http://provider.local:8080").forEach { url ->
            assertTrue(ProviderUrlPolicy.validate(url, false) is ProviderUrlValidationResult.RequiresLocalHttpConfirmation)
            assertTrue(ProviderUrlPolicy.validate(url, true) is ProviderUrlValidationResult.Valid)
        }
    }

    @Test
    fun loopbackPrivateAndLinkLocalRangesAreRecognizedWithoutDns() {
        listOf(
            "http://127.1.2.3:8080",
            "http://10.1.2.3:8080",
            "http://172.31.1.2:8080",
            "http://192.168.10.2:8080",
            "http://169.254.10.2:8080",
            "http://[::1]:8080",
            "http://[fe80::1]:8080"
        ).forEach { url -> assertTrue(url, ProviderUrlPolicy.validate(url, true) is ProviderUrlValidationResult.Valid) }
    }

    @Test
    fun userInfoFragmentQueryInvalidPortAndForeignSchemesAreRejected() {
        assertInvalid("https://name:password@example.com/v1", ProviderUrlError.USER_INFO_NOT_ALLOWED)
        assertInvalid("https://example.com/v1#part", ProviderUrlError.FRAGMENT_NOT_ALLOWED)
        assertInvalid("https://example.com/v1?mode=test", ProviderUrlError.QUERY_NOT_ALLOWED)
        assertInvalid("https://example.com:70000/v1", ProviderUrlError.INVALID_PORT)
        assertInvalid("file:///tmp/provider", ProviderUrlError.UNSUPPORTED_SCHEME)
        assertInvalid("javascript:alert(1)", ProviderUrlError.UNSUPPORTED_SCHEME)
        assertInvalid("ftp://example.com/provider", ProviderUrlError.UNSUPPORTED_SCHEME)
    }

    @Test
    fun validationHasNoNetworkDependency() {
        val parameterTypes = ProviderUrlPolicy::class.java.declaredMethods.flatMap { it.parameterTypes.toList() }
        assertTrue(parameterTypes.none { it.name.contains("okhttp") || it.name.contains("retrofit") })
    }

    private fun assertInvalid(url: String, error: ProviderUrlError) {
        val result = ProviderUrlPolicy.validate(url, false) as ProviderUrlValidationResult.Invalid
        assertEquals(error, result.error)
    }
}
