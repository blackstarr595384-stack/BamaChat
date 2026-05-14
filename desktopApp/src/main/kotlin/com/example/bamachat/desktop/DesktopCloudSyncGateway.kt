package com.example.bamachat.desktop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

data class CloudWorkspaceSnapshot(
    val text: String,
    val updatedAt: String?,
    val updatedBy: String?
)

class CloudSessionExpiredException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class DesktopCloudSyncGateway(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build(),
    private val gson: Gson = Gson(),
    private val googleOAuthGateway: DesktopGoogleOAuthGateway = DesktopGoogleOAuthGateway()
) {
    suspend fun signInWithEmailPassword(
        settings: DesktopUserSettings,
        email: String,
        password: String
    ): DesktopUserSettings = withContext(Dispatchers.IO) {
        val apiKey = ensureApiKey(settings.firebaseApiKey)
        val body = gson.toJson(
            mapOf(
                "email" to email.trim(),
                "password" to password,
                "returnSecureToken" to true
            )
        )
        val response = httpPostJson(
            url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey",
            body = body
        )
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Firebase Login fehlgeschlagen: ${extractFirebaseError(response.body())}")
        }
        return@withContext applyAuthResponse(settings, response.body())
    }

    suspend fun signUpWithEmailPassword(
        settings: DesktopUserSettings,
        email: String,
        password: String
    ): DesktopUserSettings = withContext(Dispatchers.IO) {
        val apiKey = ensureApiKey(settings.firebaseApiKey)
        val body = gson.toJson(
            mapOf(
                "email" to email.trim(),
                "password" to password,
                "returnSecureToken" to true
            )
        )
        val response = httpPostJson(
            url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey",
            body = body
        )
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Firebase Registrierung fehlgeschlagen: ${extractFirebaseError(response.body())}")
        }
        return@withContext applyAuthResponse(settings, response.body())
    }

    suspend fun signInWithGoogleOAuthBrowser(
        settings: DesktopUserSettings
    ): DesktopUserSettings = withContext(Dispatchers.IO) {
        val idToken = googleOAuthGateway.fetchGoogleIdToken(
            clientId = settings.googleOAuthClientId,
            clientSecret = settings.googleOAuthClientSecret
        )
        return@withContext signInWithGoogleIdToken(settings, idToken)
    }

    suspend fun signInWithGoogleIdToken(
        settings: DesktopUserSettings,
        googleIdToken: String
    ): DesktopUserSettings = withContext(Dispatchers.IO) {
        val apiKey = ensureApiKey(settings.firebaseApiKey)
        val postBody = "id_token=${urlEncode(googleIdToken.trim())}&providerId=google.com"
        val body = gson.toJson(
            mapOf(
                "postBody" to postBody,
                "requestUri" to "http://localhost",
                "returnIdpCredential" to true,
                "returnSecureToken" to true
            )
        )
        val response = httpPostJson(
            url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey",
            body = body
        )
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Firebase Google-Login fehlgeschlagen: ${extractFirebaseError(response.body())}")
        }
        return@withContext applyAuthResponse(settings, response.body())
    }

    suspend fun refreshAuthTokenIfNeeded(settings: DesktopUserSettings): DesktopUserSettings =
        withContext(Dispatchers.IO) {
            ensureAuthenticated(settings).let { current ->
                val now = System.currentTimeMillis()
                val refreshMarginMs = 45_000L
                if (current.authTokenExpiryEpochMs - now > refreshMarginMs && current.authIdToken.isNotBlank()) {
                    return@withContext current
                }
                val apiKey = ensureApiKey(current.firebaseApiKey)
                val refreshToken = current.authRefreshToken.trim()
                if (refreshToken.isBlank()) {
                    throw CloudSessionExpiredException("Cloud-Session abgelaufen: Refresh-Token fehlt. Bitte erneut anmelden.")
                }
                val formBody = buildString {
                    append("grant_type=refresh_token")
                    append("&refresh_token=")
                    append(URLEncoder.encode(refreshToken, StandardCharsets.UTF_8))
                }
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("https://securetoken.googleapis.com/v1/token?key=$apiKey"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                    .build()
                val response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                )
                if (response.statusCode() !in 200..299) {
                    val firebaseError = extractFirebaseError(response.body())
                    if (isSessionExpiredFirebaseError(firebaseError)) {
                        throw CloudSessionExpiredException(
                            "Cloud-Session abgelaufen. Bitte erneut anmelden. ($firebaseError)"
                        )
                    }
                    throw IllegalStateException("Token-Refresh fehlgeschlagen: $firebaseError")
                }
                val root = JsonParser.parseString(response.body()).asJsonObject
                val idToken = root.getString("id_token")
                    ?: throw IllegalStateException("Token-Refresh lieferte keinen id_token.")
                val newRefreshToken = root.getString("refresh_token")
                    ?: throw IllegalStateException("Token-Refresh lieferte keinen refresh_token.")
                val uid = root.getString("user_id")
                    ?: throw IllegalStateException("Token-Refresh lieferte keinen user_id.")
                val expiresInSeconds = root.getString("expires_in")?.toLongOrNull() ?: 3600L
                return@withContext current.copy(
                    authUid = uid,
                    authIdToken = idToken,
                    authRefreshToken = newRefreshToken,
                    authTokenExpiryEpochMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
                )
            }
        }

    suspend fun pushWorkspaceSnapshot(
        settings: DesktopUserSettings,
        text: String
    ): DesktopUserSettings = withContext(Dispatchers.IO) {
        var session = refreshAuthTokenIfNeeded(settings)
        session = pushWorkspaceWithSession(session, text)
        return@withContext session
    }

    suspend fun pullWorkspaceSnapshot(
        settings: DesktopUserSettings
    ): Pair<DesktopUserSettings, CloudWorkspaceSnapshot?> = withContext(Dispatchers.IO) {
        var session = refreshAuthTokenIfNeeded(settings)
        val first = fetchWorkspaceWithSession(session)
        if (first.statusCode() == 401) {
            session = forceRefreshSession(session)
            val retry = fetchWorkspaceWithSession(session)
            if (retry.statusCode() == 401) {
                throw CloudSessionExpiredException("Cloud-Session abgelaufen. Bitte erneut anmelden.")
            }
            return@withContext session to parseWorkspaceDocumentResponse(retry)
        }
        return@withContext session to parseWorkspaceDocumentResponse(first)
    }

    fun signOut(settings: DesktopUserSettings): DesktopUserSettings = settings.clearCloudSession()

    private suspend fun pushWorkspaceWithSession(
        session: DesktopUserSettings,
        text: String
    ): DesktopUserSettings {
        val response = patchWorkspaceWithSession(session, text)
        if (response.statusCode() == 401) {
            val refreshed = forceRefreshSession(session)
            val retry = patchWorkspaceWithSession(refreshed, text)
            if (retry.statusCode() == 401) {
                throw CloudSessionExpiredException("Cloud-Session abgelaufen. Bitte erneut anmelden.")
            }
            ensureFirestoreWriteSuccess(retry)
            return refreshed
        }
        ensureFirestoreWriteSuccess(response)
        return session
    }

    private fun patchWorkspaceWithSession(
        session: DesktopUserSettings,
        text: String
    ): HttpResponse<String> {
        val projectId = ensureProjectId(session.firebaseProjectId)
        val uid = ensureAuthenticated(session).authUid
        val timestamp = Instant.now().toString()
        val body = gson.toJson(
            mapOf(
                "fields" to mapOf(
                    "desktop_workspace_note" to mapOf("stringValue" to text),
                    "desktop_workspace_updated_at" to mapOf("timestampValue" to timestamp),
                    "desktop_workspace_updated_by" to mapOf(
                        "stringValue" to session.authEmail.ifBlank { "desktop" }
                    )
                )
            )
        )

        val updateMask = "updateMask.fieldPaths=desktop_workspace_note" +
            "&updateMask.fieldPaths=desktop_workspace_updated_at" +
            "&updateMask.fieldPaths=desktop_workspace_updated_by"
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/users/$uid?$updateMask"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(40))
            .header("Authorization", "Bearer ${session.authIdToken}")
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    }

    private fun ensureFirestoreWriteSuccess(response: HttpResponse<String>) {
        if (response.statusCode() in 200..299) return
        if (response.statusCode() == 401) {
            throw CloudSessionExpiredException("Cloud-Session abgelaufen. Bitte erneut anmelden.")
        }
        throw IllegalStateException("Cloud-Sync fehlgeschlagen: ${extractFirestoreError(response.body())}")
    }

    private fun fetchWorkspaceWithSession(session: DesktopUserSettings): HttpResponse<String> {
        val projectId = ensureProjectId(session.firebaseProjectId)
        val uid = ensureAuthenticated(session).authUid
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/users/$uid"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer ${session.authIdToken}")
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    }

    private fun parseWorkspaceDocumentResponse(response: HttpResponse<String>): CloudWorkspaceSnapshot? {
        if (response.statusCode() == 404) return null
        if (response.statusCode() == 401) {
            throw CloudSessionExpiredException("Cloud-Session abgelaufen. Bitte erneut anmelden.")
        }
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Cloud-Laden fehlgeschlagen: ${extractFirestoreError(response.body())}")
        }
        val root = JsonParser.parseString(response.body()).asJsonObject
        val fields = root.getAsJsonObject("fields") ?: return null
        val text = fields.getAsJsonObject("desktop_workspace_note")
            ?.getString("stringValue")
            .orEmpty()
        val updatedAt = fields.getAsJsonObject("desktop_workspace_updated_at")
            ?.getString("timestampValue")
        val updatedBy = fields.getAsJsonObject("desktop_workspace_updated_by")
            ?.getString("stringValue")
        if (text.isBlank() && updatedAt == null && updatedBy == null) return null
        return CloudWorkspaceSnapshot(
            text = text,
            updatedAt = updatedAt,
            updatedBy = updatedBy
        )
    }

    private fun applyAuthResponse(settings: DesktopUserSettings, body: String): DesktopUserSettings {
        val root = JsonParser.parseString(body).asJsonObject
        val idToken = root.getString("idToken")
            ?: throw IllegalStateException("Firebase Auth Antwort ohne idToken.")
        val refreshToken = root.getString("refreshToken")
            ?: throw IllegalStateException("Firebase Auth Antwort ohne refreshToken.")
        val uid = root.getString("localId")
            ?: throw IllegalStateException("Firebase Auth Antwort ohne localId.")
        val email = root.getString("email").orEmpty()
        val expiresInSeconds = root.getString("expiresIn")?.toLongOrNull() ?: 3600L
        return settings.copy(
            authEmail = email,
            authUid = uid,
            authIdToken = idToken,
            authRefreshToken = refreshToken,
            authTokenExpiryEpochMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        )
    }

    private fun ensureApiKey(apiKey: String): String {
        val value = apiKey.trim()
        if (value.isBlank()) {
            throw IllegalStateException("Firebase API-Key fehlt. Bitte in den Settings setzen.")
        }
        return value
    }

    private fun ensureProjectId(projectId: String): String {
        val value = projectId.trim()
        if (value.isBlank()) {
            throw IllegalStateException("Firebase Project-ID fehlt. Bitte in den Settings setzen.")
        }
        return value
    }

    private fun ensureAuthenticated(settings: DesktopUserSettings): DesktopUserSettings {
        if (settings.authUid.isBlank()) {
            throw IllegalStateException("Nicht angemeldet. Bitte zuerst in den Desktop-Settings einloggen.")
        }
        if (settings.authIdToken.isBlank() || settings.authRefreshToken.isBlank()) {
            throw CloudSessionExpiredException("Cloud-Session unvollständig. Bitte erneut anmelden.")
        }
        return settings
    }

    private suspend fun forceRefreshSession(session: DesktopUserSettings): DesktopUserSettings {
        return try {
            refreshAuthTokenIfNeeded(session.copy(authTokenExpiryEpochMs = 0L))
        } catch (expired: CloudSessionExpiredException) {
            throw expired
        } catch (t: Throwable) {
            throw CloudSessionExpiredException(
                "Cloud-Session konnte nicht erneuert werden. Bitte erneut anmelden.",
                t
            )
        }
    }

    private fun isSessionExpiredFirebaseError(firebaseError: String): Boolean {
        val normalized = firebaseError.uppercase()
        return normalized.contains("INVALID_REFRESH_TOKEN") ||
            normalized.contains("TOKEN_EXPIRED") ||
            normalized.contains("USER_DISABLED") ||
            normalized.contains("USER_NOT_FOUND") ||
            normalized.contains("INVALID_GRANT") ||
            normalized.contains("CREDENTIAL_TOO_OLD_LOGIN_AGAIN")
    }

    private fun extractFirebaseError(body: String): String {
        return runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            val errorObject = root.getAsJsonObject("error")
            when {
                errorObject == null -> body.take(220)
                errorObject.has("message") -> errorObject.getString("message").orEmpty()
                else -> errorObject.toString()
            }
        }.getOrElse { body.take(220) }
    }

    private fun extractFirestoreError(body: String): String {
        return runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            val errorObject = root.getAsJsonObject("error")
            when {
                errorObject == null -> body.take(220)
                errorObject.has("message") -> errorObject.getString("message").orEmpty()
                else -> errorObject.toString()
            }
        }.getOrElse { body.take(220) }
    }

    private fun httpPostJson(url: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    private fun JsonObject.getString(key: String): String? {
        if (!has(key)) return null
        val value = get(key)
        if (value.isJsonNull) return null
        return value.asString
    }
}
