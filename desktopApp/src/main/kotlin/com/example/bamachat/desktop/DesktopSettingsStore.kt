package com.example.bamachat.desktop

import com.example.bamachat.shared.core.ExtensionRuntimeOrchestrator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    val provider: DesktopProvider = DesktopProvider.OLLAMA,
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

const val DEFAULT_OPENROUTER_MODEL = ""
const val OLD_STALE_OPENROUTER_MODEL = "google/gemma-3-12b-it:free"
const val DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434/"
const val DEFAULT_OLLAMA_MODEL = "llama3.2:latest"
val DEFAULT_ENABLED_EXTENSION_IDS: Set<String> = setOf(
    ExtensionRuntimeOrchestrator.EXT_RESEARCH_RADAR,
    ExtensionRuntimeOrchestrator.EXT_CODE_REVIEW_PRO,
    ExtensionRuntimeOrchestrator.EXT_WORKSPACE_ORCHESTRATOR
)

object DesktopSettingsStore {
    private val repository by lazy {
        DesktopSettingsRepository(
            settingsDirectory = DesktopDataDirectoryResolver.resolveSettingsDirectory()
        )
    }

    fun load(): DesktopUserSettings = repository.load()

    @Throws(IOException::class)
    fun save(settings: DesktopUserSettings) = repository.save(settings)
}

internal class DesktopSettingsRepository(
    settingsDirectory: Path,
    private val credentialCipher: DesktopCredentialCipher =
        DesktopCredentialCipher(settingsDirectory),
    private val atomicWriter: DesktopAtomicStateWriter = DesktopAtomicStateWriter(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val recoveryId: () -> String = { UUID.randomUUID().toString() }
) {
    private val settingsDirectory = settingsDirectory.toAbsolutePath().normalize()
    private val settingsFile = this.settingsDirectory.resolve(SETTINGS_FILE_NAME)
    private val lock = directoryLocks.computeIfAbsent(this.settingsDirectory.toString()) { Any() }

    fun load(): DesktopUserSettings = synchronized(lock) {
        if (!Files.isRegularFile(settingsFile)) return@synchronized DesktopUserSettings()
        val properties = Properties()
        try {
            Files.newInputStream(settingsFile).use { input -> properties.load(input) }
            val secretResults = SECRET_KEYS.associateWith { key ->
                credentialCipher.read(properties.getProperty(key).orEmpty().trim())
            }
            val loadedSettings = settingsFrom(properties, secretResults).sanitizeLoadedSession()
            if (secretResults.values.any { it.requiresMigration() }) {
                try {
                    migrateSecrets(properties, secretResults)
                } catch (_: Exception) {
                }
            }
            loadedSettings
        } catch (_: Exception) {
            DesktopUserSettings()
        }
    }

    @Throws(IOException::class)
    fun save(settings: DesktopUserSettings) = synchronized(lock) {
        val properties = Properties().apply {
            setProperty(KEY_PROVIDER, settings.provider.name)
            setProperty(KEY_OPENROUTER_KEY, protect(settings.openRouterApiKey))
            setProperty(KEY_OPENROUTER_MODEL, settings.openRouterModel.trim())
            setProperty(KEY_OLLAMA_BASE_URL, settings.ollamaBaseUrl.trim())
            setProperty(KEY_OLLAMA_MODEL, settings.ollamaModel.trim())
            setProperty(KEY_FIREBASE_API_KEY, settings.firebaseApiKey.trim())
            setProperty(KEY_FIREBASE_PROJECT_ID, settings.firebaseProjectId.trim())
            setProperty(KEY_GOOGLE_OAUTH_CLIENT_ID, settings.googleOAuthClientId.trim())
            setProperty(KEY_GOOGLE_OAUTH_CLIENT_SECRET, protect(settings.googleOAuthClientSecret))
            setProperty(
                KEY_ENABLED_EXTENSION_IDS,
                settings.enabledExtensionIds.sorted().joinToString(",")
            )
            setProperty(KEY_AUTH_EMAIL, settings.authEmail.trim())
            setProperty(KEY_AUTH_UID, settings.authUid.trim())
            setProperty(KEY_AUTH_ID_TOKEN, protect(settings.authIdToken))
            setProperty(KEY_AUTH_REFRESH_TOKEN, protect(settings.authRefreshToken))
            setProperty(KEY_AUTH_TOKEN_EXPIRY_EPOCH_MS, settings.authTokenExpiryEpochMs.toString())
            setProperty(KEY_ENCRYPT_CLOUD_SESSION, settings.encryptCloudSession.toString())
        }
        writeProperties(properties)
    }

    private fun settingsFrom(
        properties: Properties,
        secretResults: Map<String, DesktopSecretReadResult>
    ): DesktopUserSettings {
        val extensionIds = properties
            .getProperty(KEY_ENABLED_EXTENSION_IDS)
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        return DesktopUserSettings(
            provider = DesktopProvider.from(properties.getProperty(KEY_PROVIDER)),
            openRouterApiKey = secretResults.plainText(KEY_OPENROUTER_KEY),
            openRouterModel = properties.getProperty(KEY_OPENROUTER_MODEL)
                ?.takeIf(String::isNotBlank)
                ?: DEFAULT_OPENROUTER_MODEL,
            ollamaBaseUrl = properties.getProperty(KEY_OLLAMA_BASE_URL)
                ?.takeIf(String::isNotBlank)
                ?: DEFAULT_OLLAMA_BASE_URL,
            ollamaModel = properties.getProperty(KEY_OLLAMA_MODEL)
                ?.takeIf(String::isNotBlank)
                ?: DEFAULT_OLLAMA_MODEL,
            enabledExtensionIds = extensionIds.ifEmpty { DEFAULT_ENABLED_EXTENSION_IDS },
            firebaseApiKey = properties.getProperty(KEY_FIREBASE_API_KEY)
                ?.takeIf(String::isNotBlank)
                ?: DesktopFirebaseConfig.defaultApiKey(),
            firebaseProjectId = properties.getProperty(KEY_FIREBASE_PROJECT_ID)
                ?.takeIf(String::isNotBlank)
                ?: DesktopFirebaseConfig.defaultProjectId(),
            googleOAuthClientId = properties.getProperty(KEY_GOOGLE_OAUTH_CLIENT_ID)
                ?.takeIf(String::isNotBlank)
                ?: DesktopFirebaseConfig.defaultGoogleOAuthClientId(),
            googleOAuthClientSecret = secretResults.plainText(KEY_GOOGLE_OAUTH_CLIENT_SECRET),
            authEmail = properties.getProperty(KEY_AUTH_EMAIL).orEmpty().trim(),
            authUid = properties.getProperty(KEY_AUTH_UID).orEmpty().trim(),
            authIdToken = secretResults.plainText(KEY_AUTH_ID_TOKEN),
            authRefreshToken = secretResults.plainText(KEY_AUTH_REFRESH_TOKEN),
            authTokenExpiryEpochMs = properties
                .getProperty(KEY_AUTH_TOKEN_EXPIRY_EPOCH_MS)
                ?.toLongOrNull()
                ?: 0L,
            encryptCloudSession = properties
                .getProperty(KEY_ENCRYPT_CLOUD_SESSION)
                ?.trim()
                ?.toBooleanStrictOrNull()
                ?: false
        )
    }

    private fun migrateSecrets(
        originalProperties: Properties,
        secretResults: Map<String, DesktopSecretReadResult>
    ) {
        preserveMigrationRecoveryCopy()
        val migratedProperties = Properties().apply { putAll(originalProperties) }
        secretResults.forEach { (key, result) ->
            if (result is DesktopSecretReadResult.Available && result.requiresMigration) {
                migratedProperties.setProperty(key, credentialCipher.protect(result.plainText))
            }
        }
        writeProperties(migratedProperties)
    }

    private fun preserveMigrationRecoveryCopy(): Path {
        Files.createDirectories(settingsDirectory)
        val safeRecoveryId = recoveryId()
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .ifBlank { "migration" }
        val recoveryFile = settingsDirectory.resolve(
            "$SETTINGS_FILE_NAME.recovery-${clock()}-$safeRecoveryId"
        )
        return Files.copy(settingsFile, recoveryFile, StandardCopyOption.COPY_ATTRIBUTES)
    }

    private fun writeProperties(properties: Properties) {
        val bytes = ByteArrayOutputStream().use { output ->
            properties.store(output, "BamaChat Desktop settings")
            output.toByteArray()
        }
        try {
            atomicWriter.write(settingsFile, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun protect(value: String): String = credentialCipher.protect(value.trim())

    private fun Map<String, DesktopSecretReadResult>.plainText(key: String): String {
        return (get(key) as? DesktopSecretReadResult.Available)?.plainText.orEmpty()
    }

    private fun DesktopSecretReadResult.requiresMigration(): Boolean {
        return this is DesktopSecretReadResult.Available && requiresMigration
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

    private companion object {
        const val SETTINGS_FILE_NAME = "settings.properties"
        const val KEY_PROVIDER = "provider"
        const val KEY_OPENROUTER_KEY = "openrouter_api_key"
        const val KEY_OPENROUTER_MODEL = "openrouter_model"
        const val KEY_OLLAMA_BASE_URL = "ollama_base_url"
        const val KEY_OLLAMA_MODEL = "ollama_model"
        const val KEY_ENABLED_EXTENSION_IDS = "enabled_extension_ids"
        const val KEY_FIREBASE_API_KEY = "firebase_api_key"
        const val KEY_FIREBASE_PROJECT_ID = "firebase_project_id"
        const val KEY_GOOGLE_OAUTH_CLIENT_ID = "google_oauth_client_id"
        const val KEY_GOOGLE_OAUTH_CLIENT_SECRET = "google_oauth_client_secret"
        const val KEY_AUTH_EMAIL = "auth_email"
        const val KEY_AUTH_UID = "auth_uid"
        const val KEY_AUTH_ID_TOKEN = "auth_id_token"
        const val KEY_AUTH_REFRESH_TOKEN = "auth_refresh_token"
        const val KEY_AUTH_TOKEN_EXPIRY_EPOCH_MS = "auth_token_expiry_epoch_ms"
        const val KEY_ENCRYPT_CLOUD_SESSION = "encrypt_cloud_session"
        val SECRET_KEYS = listOf(
            KEY_OPENROUTER_KEY,
            KEY_GOOGLE_OAUTH_CLIENT_SECRET,
            KEY_AUTH_ID_TOKEN,
            KEY_AUTH_REFRESH_TOKEN
        )
        val directoryLocks = ConcurrentHashMap<String, Any>()
    }
}
