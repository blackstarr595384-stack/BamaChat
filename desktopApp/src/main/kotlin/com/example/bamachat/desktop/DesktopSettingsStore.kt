package com.example.bamachat.desktop

import com.example.bamachat.shared.core.ExtensionRuntimeOrchestrator
import java.io.File
import java.io.IOException
import java.util.Properties

enum class DesktopProvider {
    OPENROUTER,
    OLLAMA;

    companion object {
        fun from(raw: String?): DesktopProvider {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == normalized } ?: OPENROUTER
        }
    }
}

data class DesktopUserSettings(
    val provider: DesktopProvider = DesktopProvider.OPENROUTER,
    val openRouterApiKey: String = "",
    val openRouterModel: String = DEFAULT_OPENROUTER_MODEL,
    val ollamaBaseUrl: String = DEFAULT_OLLAMA_BASE_URL,
    val ollamaModel: String = DEFAULT_OLLAMA_MODEL,
    val enabledExtensionIds: Set<String> = DEFAULT_ENABLED_EXTENSION_IDS,
    val firebaseApiKey: String = DesktopFirebaseConfig.defaultApiKey(),
    val firebaseProjectId: String = DesktopFirebaseConfig.defaultProjectId(),
    val googleOAuthClientId: String = DesktopFirebaseConfig.defaultGoogleOAuthClientId(),
    val googleOAuthClientSecret: String = "",
    val authEmail: String = "",
    val authUid: String = "",
    val authIdToken: String = "",
    val authRefreshToken: String = "",
    val authTokenExpiryEpochMs: Long = 0L,
    val encryptCloudSession: Boolean = false
) {
    fun isCloudSignedIn(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return authUid.isNotBlank() &&
            authIdToken.isNotBlank() &&
            authTokenExpiryEpochMs > nowEpochMs
    }

    fun clearCloudSession(): DesktopUserSettings = copy(
        authEmail = "",
        authUid = "",
        authIdToken = "",
        authRefreshToken = "",
        authTokenExpiryEpochMs = 0L
    )
}

const val DEFAULT_OPENROUTER_MODEL = "google/gemma-3-12b-it:free"
const val DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434/"
const val DEFAULT_OLLAMA_MODEL = "llama3.2:latest"
val DEFAULT_ENABLED_EXTENSION_IDS: Set<String> = setOf(
    ExtensionRuntimeOrchestrator.EXT_RESEARCH_RADAR,
    ExtensionRuntimeOrchestrator.EXT_CODE_REVIEW_PRO,
    ExtensionRuntimeOrchestrator.EXT_WORKSPACE_ORCHESTRATOR
)

object DesktopSettingsStore {
    private const val KEY_PROVIDER = "provider"
    private const val KEY_OPENROUTER_KEY = "openrouter_api_key"
    private const val KEY_OPENROUTER_MODEL = "openrouter_model"
    private const val KEY_OLLAMA_BASE_URL = "ollama_base_url"
    private const val KEY_OLLAMA_MODEL = "ollama_model"
    private const val KEY_ENABLED_EXTENSION_IDS = "enabled_extension_ids"
    private const val KEY_FIREBASE_API_KEY = "firebase_api_key"
    private const val KEY_FIREBASE_PROJECT_ID = "firebase_project_id"
    private const val KEY_GOOGLE_OAUTH_CLIENT_ID = "google_oauth_client_id"
    private const val KEY_GOOGLE_OAUTH_CLIENT_SECRET = "google_oauth_client_secret"
    private const val KEY_AUTH_EMAIL = "auth_email"
    private const val KEY_AUTH_UID = "auth_uid"
    private const val KEY_AUTH_ID_TOKEN = "auth_id_token"
    private const val KEY_AUTH_REFRESH_TOKEN = "auth_refresh_token"
    private const val KEY_AUTH_TOKEN_EXPIRY_EPOCH_MS = "auth_token_expiry_epoch_ms"
    private const val KEY_ENCRYPT_CLOUD_SESSION = "encrypt_cloud_session"

    private val baseDir = File(System.getProperty("user.home"), ".bamachat-desktop")
    private val settingsFile = File(baseDir, "settings.properties")

    fun load(): DesktopUserSettings {
        if (!settingsFile.exists()) return DesktopUserSettings()
        val properties = Properties()
        return runCatching {
            settingsFile.inputStream().use { properties.load(it) }
            val extensionIds = properties
                .getProperty(KEY_ENABLED_EXTENSION_IDS)
                .orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            val encryptCloudSession = properties
                .getProperty(KEY_ENCRYPT_CLOUD_SESSION)
                ?.trim()
                ?.toBooleanStrictOrNull()
                ?: false
            val authIdToken = decodeStoredToken(
                storedValue = properties.getProperty(KEY_AUTH_ID_TOKEN).orEmpty().trim()
            )
            val authRefreshToken = decodeStoredToken(
                storedValue = properties.getProperty(KEY_AUTH_REFRESH_TOKEN).orEmpty().trim()
            )
            DesktopUserSettings(
                provider = DesktopProvider.from(properties.getProperty(KEY_PROVIDER)),
                openRouterApiKey = properties.getProperty(KEY_OPENROUTER_KEY).orEmpty().trim(),
                openRouterModel = properties.getProperty(KEY_OPENROUTER_MODEL)
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_OPENROUTER_MODEL,
                ollamaBaseUrl = properties.getProperty(KEY_OLLAMA_BASE_URL)
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_OLLAMA_BASE_URL,
                ollamaModel = properties.getProperty(KEY_OLLAMA_MODEL)
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_OLLAMA_MODEL,
                enabledExtensionIds = extensionIds.ifEmpty { DEFAULT_ENABLED_EXTENSION_IDS },
                firebaseApiKey = properties.getProperty(KEY_FIREBASE_API_KEY)
                    ?.takeIf { it.isNotBlank() }
                    ?: DesktopFirebaseConfig.defaultApiKey(),
                firebaseProjectId = properties.getProperty(KEY_FIREBASE_PROJECT_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: DesktopFirebaseConfig.defaultProjectId(),
                googleOAuthClientId = properties.getProperty(KEY_GOOGLE_OAUTH_CLIENT_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: DesktopFirebaseConfig.defaultGoogleOAuthClientId(),
                googleOAuthClientSecret = properties.getProperty(KEY_GOOGLE_OAUTH_CLIENT_SECRET)
                    .orEmpty()
                    .trim(),
                authEmail = properties.getProperty(KEY_AUTH_EMAIL).orEmpty().trim(),
                authUid = properties.getProperty(KEY_AUTH_UID).orEmpty().trim(),
                authIdToken = authIdToken,
                authRefreshToken = authRefreshToken,
                authTokenExpiryEpochMs = properties
                    .getProperty(KEY_AUTH_TOKEN_EXPIRY_EPOCH_MS)
                    ?.toLongOrNull()
                    ?: 0L,
                encryptCloudSession = encryptCloudSession
            ).sanitizeLoadedSession()
        }.getOrElse { DesktopUserSettings() }
    }

    @Throws(IOException::class)
    fun save(settings: DesktopUserSettings) {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val authIdTokenToStore = encodeTokenForSave(
            plainText = settings.authIdToken.trim(),
            encryptionEnabled = settings.encryptCloudSession
        )
        val authRefreshTokenToStore = encodeTokenForSave(
            plainText = settings.authRefreshToken.trim(),
            encryptionEnabled = settings.encryptCloudSession
        )
        val properties = Properties().apply {
            setProperty(KEY_PROVIDER, settings.provider.name)
            setProperty(KEY_OPENROUTER_KEY, settings.openRouterApiKey.trim())
            setProperty(KEY_OPENROUTER_MODEL, settings.openRouterModel.trim())
            setProperty(KEY_OLLAMA_BASE_URL, settings.ollamaBaseUrl.trim())
            setProperty(KEY_OLLAMA_MODEL, settings.ollamaModel.trim())
            setProperty(KEY_FIREBASE_API_KEY, settings.firebaseApiKey.trim())
            setProperty(KEY_FIREBASE_PROJECT_ID, settings.firebaseProjectId.trim())
            setProperty(KEY_GOOGLE_OAUTH_CLIENT_ID, settings.googleOAuthClientId.trim())
            setProperty(KEY_GOOGLE_OAUTH_CLIENT_SECRET, settings.googleOAuthClientSecret.trim())
            setProperty(
                KEY_ENABLED_EXTENSION_IDS,
                settings.enabledExtensionIds.sorted().joinToString(",")
            )
            setProperty(KEY_AUTH_EMAIL, settings.authEmail.trim())
            setProperty(KEY_AUTH_UID, settings.authUid.trim())
            setProperty(KEY_AUTH_ID_TOKEN, authIdTokenToStore)
            setProperty(KEY_AUTH_REFRESH_TOKEN, authRefreshTokenToStore)
            setProperty(KEY_AUTH_TOKEN_EXPIRY_EPOCH_MS, settings.authTokenExpiryEpochMs.toString())
            setProperty(KEY_ENCRYPT_CLOUD_SESSION, settings.encryptCloudSession.toString())
        }
        settingsFile.outputStream().use { output ->
            properties.store(output, "BamaChat Desktop settings")
        }
    }

    private fun encodeTokenForSave(plainText: String, encryptionEnabled: Boolean): String {
        if (!encryptionEnabled || plainText.isBlank()) return plainText
        return DesktopCredentialCipher.encrypt(plainText)
    }

    private fun decodeStoredToken(storedValue: String): String {
        if (storedValue.isBlank()) return storedValue
        if (!DesktopCredentialCipher.isEncrypted(storedValue)) {
            return storedValue
        }
        return runCatching {
            DesktopCredentialCipher.decrypt(storedValue)
        }.getOrElse {
            ""
        }
    }

    private fun DesktopUserSettings.sanitizeLoadedSession(): DesktopUserSettings {
        val hasAnyCloudSessionData = authEmail.isNotBlank() ||
            authUid.isNotBlank() ||
            authIdToken.isNotBlank() ||
            authRefreshToken.isNotBlank() ||
            authTokenExpiryEpochMs > 0L
        if (!hasAnyCloudSessionData) return this
        val hasCompleteSession = authUid.isNotBlank() &&
            authIdToken.isNotBlank() &&
            authRefreshToken.isNotBlank()
        return if (hasCompleteSession) this else clearCloudSession()
    }
}
