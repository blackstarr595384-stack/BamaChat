package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.RealtimeTurnTaking
import com.example.bamachat.voice.RealtimeVoiceSessionRequest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSessionCredentialProviderTest {
    @Test
    fun authenticatedRequestContainsOnlyAllowlistedClientConfiguration() = runBlocking {
        var capturedUrl = ""
        var capturedToken = ""
        var capturedBody = ""
        val provider = provider(
            transport = RealtimeSessionHttpTransport { url, token, body ->
                capturedUrl = url
                capturedToken = token
                capturedBody = body
                successfulResponse()
            }
        )

        val result = provider.requestCredential(request())

        assertTrue(result.isSuccess)
        assertEquals(SESSION_URL, capturedUrl)
        assertEquals(FIREBASE_TOKEN, capturedToken)
        val body = JSONObject(capturedBody)
        assertEquals("gpt-realtime", body.getString("model"))
        assertEquals("marin", body.getString("voice"))
        assertEquals("semantic", body.getString("turnTaking"))
        assertTrue(body.getBoolean("interruptResponse"))
        assertFalse(body.has("uid"))
        assertFalse(capturedBody.contains(FIREBASE_TOKEN))
    }

    @Test
    fun malformedOrExpiredCredentialIsRejected() = runBlocking {
        val provider = provider(
            transport = RealtimeSessionHttpTransport { _, _, _ ->
                RealtimeHttpResponse(
                    200,
                    credentialJson(expiresAt = NOW_SECONDS + 4)
                )
            }
        )

        val result = provider.requestCredential(request())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RealtimeVoiceException)
    }

    @Test
    fun missingAuthenticationMapsToSafeAuthenticationFailure() = runBlocking {
        val provider = FirebaseRealtimeSessionCredentialProvider(
            sessionUrl = SESSION_URL,
            sessionEndUrl = SESSION_END_URL,
            authTokenProvider = RealtimeAuthTokenProvider { Result.failure(IllegalStateException("raw-token-error")) },
            httpTransport = RealtimeSessionHttpTransport { _, _, _ -> error("transport must not run") },
            nowEpochSeconds = { NOW_SECONDS }
        )

        val result = provider.requestCredential(request())

        val failure = (result.exceptionOrNull() as RealtimeVoiceException).failure
        assertEquals("Bitte melde dich an, um Live-Unterhaltung zu starten.", failure.userMessage)
        assertFalse(failure.userMessage.contains("raw-token-error"))
    }

    @Test
    fun releasingCredentialUsesAuthenticatedSiblingEndpoint() = runBlocking {
        val calls = mutableListOf<Triple<String, String, String>>()
        val provider = provider(
            sessionEndUrl = "",
            transport = RealtimeSessionHttpTransport { url, token, body ->
                calls += Triple(url, token, body)
                RealtimeHttpResponse(204, "")
            }
        )

        provider.releaseCredential(LEASE_ID)

        assertEquals(1, calls.size)
        assertEquals(SESSION_END_URL, calls.single().first)
        assertEquals(FIREBASE_TOKEN, calls.single().second)
        assertEquals(LEASE_ID, JSONObject(calls.single().third).getString("leaseId"))
    }

    private fun provider(
        sessionEndUrl: String = SESSION_END_URL,
        transport: RealtimeSessionHttpTransport
    ) = FirebaseRealtimeSessionCredentialProvider(
        sessionUrl = SESSION_URL,
        sessionEndUrl = sessionEndUrl,
        authTokenProvider = RealtimeAuthTokenProvider { Result.success(FIREBASE_TOKEN) },
        httpTransport = transport,
        nowEpochSeconds = { NOW_SECONDS }
    )

    private fun request() = RealtimeVoiceSessionRequest(
        provider = "openai",
        model = "gpt-realtime",
        voice = "marin",
        languageTag = "de-DE",
        personaName = "BamaChat",
        turnTaking = RealtimeTurnTaking.SEMANTIC,
        noiseReduction = "near_field"
    )

    private fun successfulResponse() = RealtimeHttpResponse(200, credentialJson())

    private fun credentialJson(expiresAt: Long = NOW_SECONDS + 90) = JSONObject()
        .put("clientSecret", "short-lived-client-secret")
        .put("expiresAt", expiresAt)
        .put("model", "gpt-realtime")
        .put("voice", "marin")
        .put("leaseId", LEASE_ID)
        .put("sessionExpiresAt", NOW_SECONDS + 900)
        .toString()

    companion object {
        private const val SESSION_URL = "https://example.test/voiceRealtimeSession"
        private const val SESSION_END_URL = "https://example.test/voiceRealtimeSessionEnd"
        private const val FIREBASE_TOKEN = "firebase-id-token-for-test"
        private const val LEASE_ID = "lease-1234567890123456"
        private const val NOW_SECONDS = 1_000L
    }
}
