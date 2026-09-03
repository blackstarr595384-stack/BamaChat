package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.EphemeralVoiceCredential
import com.example.bamachat.voice.RealtimeEphemeralCredentialProvider
import com.example.bamachat.voice.RealtimeVoiceSessionRequest
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

class RealtimeVoiceException(val failure: VoiceFailure) : Exception(failure.userMessage)

fun interface RealtimeAuthTokenProvider {
    suspend fun getToken(): Result<String>
}

data class RealtimeHttpResponse(
    val statusCode: Int,
    val body: String
)

fun interface RealtimeSessionHttpTransport {
    suspend fun post(url: String, bearerToken: String, body: String): RealtimeHttpResponse
}

class FirebaseRealtimeSessionCredentialProvider(
    private val sessionUrl: String,
    sessionEndUrl: String,
    private val authTokenProvider: RealtimeAuthTokenProvider,
    private val httpTransport: RealtimeSessionHttpTransport,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L }
) : RealtimeEphemeralCredentialProvider {
    private val resolvedSessionEndUrl = sessionEndUrl.trim().ifBlank {
        sessionUrl.trim().substringBeforeLast('/', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.plus("/voiceRealtimeSessionEnd")
            .orEmpty()
    }

    override val isConfigured: Boolean = isSafeHttpsEndpoint(sessionUrl) &&
        isSafeHttpsEndpoint(resolvedSessionEndUrl)

    override suspend fun requestCredential(
        request: RealtimeVoiceSessionRequest
    ): Result<EphemeralVoiceCredential> = runCatching {
        if (!isConfigured) throw RealtimeVoiceException(
            VoiceFailure(
                VoiceFailureCategory.UNSUPPORTED,
                "Für Live-Unterhaltung muss zuerst der sichere BamaVoice-Server eingerichtet werden.",
                recoverable = false
            )
        )
        val firebaseToken = authTokenProvider.getToken().getOrElse {
            throw RealtimeVoiceException(
                VoiceFailure(
                    VoiceFailureCategory.AUTHENTICATION_REQUIRED,
                    "Bitte melde dich an, um Live-Unterhaltung zu starten."
                )
            )
        }.trim()
        if (firebaseToken.isBlank()) throw RealtimeVoiceException(
            VoiceFailure(
                VoiceFailureCategory.AUTHENTICATION_REQUIRED,
                "Bitte melde dich an, um Live-Unterhaltung zu starten."
            )
        )

        val requestBody = JSONObject()
            .put("model", request.model)
            .put("voice", request.voice)
            .put("turnTaking", request.turnTaking.storageValue)
            .put("noiseReduction", request.noiseReduction)
            .put("interruptResponse", request.interruptResponse)
            .put("personaName", request.personaName.take(MAX_PERSONA_LENGTH))
            .toString()
        val response = httpTransport.post(sessionUrl.trim(), firebaseToken, requestBody)
        if (response.statusCode !in 200..299) throw safeBackendFailure(response)
        parseCredential(response.body, request)
    }.onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
    }

    override suspend fun releaseCredential(leaseId: String) {
        if (!isConfigured || leaseId.isBlank()) return
        val firebaseToken = authTokenProvider.getToken().getOrNull()?.trim().orEmpty()
        if (firebaseToken.isBlank()) return
        runCatching {
            httpTransport.post(
                resolvedSessionEndUrl,
                firebaseToken,
                JSONObject().put("leaseId", leaseId).toString()
            )
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
        }
    }

    private fun parseCredential(
        responseBody: String,
        request: RealtimeVoiceSessionRequest
    ): EphemeralVoiceCredential {
        val body = runCatching { JSONObject(responseBody) }.getOrElse {
            throw temporaryFailure()
        }
        val credential = EphemeralVoiceCredential(
            value = body.optString("clientSecret").trim(),
            expiresAtEpochSeconds = body.optLong("expiresAt"),
            model = body.optString("model").trim(),
            voice = body.optString("voice").trim(),
            leaseId = body.optString("leaseId").trim(),
            sessionExpiresAtEpochSeconds = body.optLong("sessionExpiresAt")
        )
        val valid = credential.value.isNotBlank() &&
            credential.expiresAtEpochSeconds > nowEpochSeconds() + MIN_CREDENTIAL_LIFETIME_SECONDS &&
            credential.sessionExpiresAtEpochSeconds > nowEpochSeconds() &&
            credential.model == request.model &&
            credential.voice == request.voice &&
            credential.leaseId.length in 16..128
        if (!valid) throw temporaryFailure()
        return credential
    }

    private fun safeBackendFailure(response: RealtimeHttpResponse): RealtimeVoiceException {
        val code = runCatching {
            JSONObject(response.body).optJSONObject("error")?.optString("code")
        }.getOrNull().orEmpty()
        val failure = when (code) {
            "AuthenticationRequired" -> VoiceFailure(
                VoiceFailureCategory.AUTHENTICATION_REQUIRED,
                "Die Anmeldung für Live-Unterhaltung ist abgelaufen. Bitte melde dich erneut an."
            )
            "PermissionDenied" -> VoiceFailure(
                VoiceFailureCategory.PERMISSION_DENIED,
                "Live-Unterhaltung wurde vom sicheren Server abgelehnt."
            )
            "RateLimited" -> VoiceFailure(
                VoiceFailureCategory.RATE_LIMITED,
                "Zu viele Live-Sitzungen. Bitte versuche es später erneut."
            )
            "MisconfiguredBackend" -> VoiceFailure(
                VoiceFailureCategory.UNSUPPORTED,
                "Der sichere BamaVoice-Server ist noch nicht vollständig konfiguriert.",
                recoverable = false
            )
            "ProviderUnavailable" -> VoiceFailure(
                VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                "OpenAI Realtime ist vorübergehend nicht verfügbar."
            )
            else -> VoiceFailure(
                if (response.statusCode == 429) VoiceFailureCategory.RATE_LIMITED
                else VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                if (response.statusCode == 429) "Zu viele Live-Sitzungen. Bitte versuche es später erneut."
                else "Die sichere Live-Verbindung konnte nicht vorbereitet werden."
            )
        }
        return RealtimeVoiceException(failure)
    }

    private fun temporaryFailure() = RealtimeVoiceException(
        VoiceFailure(
            VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
            "Die sichere Live-Verbindung konnte nicht vorbereitet werden."
        )
    )

    companion object {
        private const val MAX_PERSONA_LENGTH = 80
        private const val MIN_CREDENTIAL_LIFETIME_SECONDS = 5L

        fun create(
            firebaseAuth: FirebaseAuth,
            sessionUrl: String,
            sessionEndUrl: String,
            okHttpClient: OkHttpClient = defaultHttpClient()
        ): FirebaseRealtimeSessionCredentialProvider {
            val tokenProvider = RealtimeAuthTokenProvider {
                runCatching {
                    val user = firebaseAuth.currentUser
                        ?: throw IllegalStateException("authentication_required")
                    user.getIdToken(false).await().token
                        ?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("authentication_required")
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                }
            }
            val transport = RealtimeSessionHttpTransport { url, bearerToken, body ->
                executeCancellablePost(okHttpClient, url, bearerToken, body)
            }
            return FirebaseRealtimeSessionCredentialProvider(
                sessionUrl = sessionUrl,
                sessionEndUrl = sessionEndUrl,
                authTokenProvider = tokenProvider,
                httpTransport = transport
            )
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        private suspend fun executeCancellablePost(
            client: OkHttpClient,
            url: String,
            bearerToken: String,
            body: String
        ): RealtimeHttpResponse = suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $bearerToken")
                .header("Accept", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            RealtimeHttpResponse(
                                statusCode = it.code,
                                body = it.body?.string()?.take(MAX_RESPONSE_CHARS).orEmpty()
                            )
                        }
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }

        private fun isSafeHttpsEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RESPONSE_CHARS = 32_768
    }
}
