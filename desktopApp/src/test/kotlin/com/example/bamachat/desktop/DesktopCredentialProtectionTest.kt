package com.example.bamachat.desktop

import com.sun.jna.platform.win32.WinCrypt
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopCredentialProtectionTest {
    @Test
    fun windowsDpapiRoundTripUsesCurrentUserAndHidesEveryStoredSecret() {
        if (!SystemDesktopPlatformDetector.isWindows()) return
        withTemporarySettingsDirectory { settingsDirectory ->
            val nativeApi = RecordingWindowsDpapiNativeApi()
            val cipher = DesktopCredentialCipher(
                settingsDirectory = settingsDirectory,
                platformDetector = DesktopPlatformDetector { true },
                dpapiBridge = WindowsCurrentUserDpapiBridge(nativeApi)
            )
            val repository = DesktopSettingsRepository(
                settingsDirectory = settingsDirectory,
                credentialCipher = cipher
            )
            val expected = completeSettings()

            repository.save(expected)

            val storedProperties = readProperties(settingsDirectory)
            SECRET_PROPERTY_KEYS.forEach { key ->
                assertTrue(storedProperties.getProperty(key).startsWith(DPAPI_SECRET_PREFIX))
            }
            val storedText = settingsDirectory.resolve(SETTINGS_FILE_NAME).readText()
            TEST_SECRETS.forEach { secret -> assertFalse(storedText.contains(secret)) }
            val loaded = repository.load()
            assertSecretsEqual(expected, loaded)

            val first = cipher.protect(OPENROUTER_SECRET)
            val second = cipher.protect(OPENROUTER_SECRET)
            assertNotEquals(first, second)
            assertFalse(first.contains(OPENROUTER_SECRET))
            assertFalse(second.contains(OPENROUTER_SECRET))
            assertTrue(nativeApi.flags.isNotEmpty())
            nativeApi.flags.forEach { flags ->
                assertTrue(flags and WinCrypt.CRYPTPROTECT_UI_FORBIDDEN != 0)
                assertEquals(0, flags and WinCrypt.CRYPTPROTECT_LOCAL_MACHINE)
            }
        }
    }

    @Test
    fun legacyAesGcmLoadsAndMigratesOnceWithoutChangingOtherSettings() {
        withTemporarySettingsDirectory { settingsDirectory ->
            val legacyProtector = DesktopAesGcmSecretProtector(settingsDirectory)
            val properties = baseProperties().apply {
                setProperty(
                    KEY_OPENROUTER_API_KEY,
                    legacyProtector.protect(OPENROUTER_SECRET, LEGACY_AES_GCM_SECRET_PREFIX)
                )
                setProperty(
                    KEY_GOOGLE_CLIENT_SECRET,
                    legacyProtector.protect(OAUTH_SECRET, LEGACY_AES_GCM_SECRET_PREFIX)
                )
                setProperty(
                    KEY_AUTH_ID_TOKEN,
                    legacyProtector.protect(ID_TOKEN_SECRET, LEGACY_AES_GCM_SECRET_PREFIX)
                )
                setProperty(
                    KEY_AUTH_REFRESH_TOKEN,
                    legacyProtector.protect(REFRESH_TOKEN_SECRET, LEGACY_AES_GCM_SECRET_PREFIX)
                )
                setProperty("future_setting", "preserve-this-value")
                setProperty("encrypt_cloud_session", "false")
            }
            writeProperties(settingsDirectory, properties)
            val originalBytes = settingsDirectory.resolve(SETTINGS_FILE_NAME).readBytes()
            val repository = windowsRepository(settingsDirectory)

            val loaded = repository.load()

            assertSecretsEqual(completeSettings(), loaded)
            val migratedProperties = readProperties(settingsDirectory)
            SECRET_PROPERTY_KEYS.forEach { key ->
                assertTrue(migratedProperties.getProperty(key).startsWith(DPAPI_SECRET_PREFIX))
            }
            assertEquals("preserve-this-value", migratedProperties.getProperty("future_setting"))
            assertEquals("false", migratedProperties.getProperty("encrypt_cloud_session"))
            val recoveryFiles = migrationRecoveryFiles(settingsDirectory)
            assertEquals(1, recoveryFiles.size)
            assertContentEquals(originalBytes, recoveryFiles.single().readBytes())

            repository.load()

            assertEquals(1, migrationRecoveryFiles(settingsDirectory).size)
        }
    }

    @Test
    fun legacyPlaintextMigratesOnceAndRecoveryPreservesOriginalFile() {
        withTemporarySettingsDirectory { settingsDirectory ->
            val properties = baseProperties().apply {
                setProperty(KEY_OPENROUTER_API_KEY, OPENROUTER_SECRET)
                setProperty(KEY_GOOGLE_CLIENT_SECRET, OAUTH_SECRET)
                setProperty(KEY_AUTH_ID_TOKEN, ID_TOKEN_SECRET)
                setProperty(KEY_AUTH_REFRESH_TOKEN, REFRESH_TOKEN_SECRET)
            }
            writeProperties(settingsDirectory, properties)
            val originalBytes = settingsDirectory.resolve(SETTINGS_FILE_NAME).readBytes()
            val repository = windowsRepository(settingsDirectory)

            val loaded = repository.load()

            assertSecretsEqual(completeSettings(), loaded)
            val migratedText = settingsDirectory.resolve(SETTINGS_FILE_NAME).readText()
            TEST_SECRETS.forEach { secret -> assertFalse(migratedText.contains(secret)) }
            val recoveryFiles = migrationRecoveryFiles(settingsDirectory)
            assertEquals(1, recoveryFiles.size)
            assertContentEquals(originalBytes, recoveryFiles.single().readBytes())

            repository.load()

            assertEquals(1, migrationRecoveryFiles(settingsDirectory).size)
        }
    }

    @Test
    fun corruptOrForeignDpapiValuesFailClosedWithoutDamagingOtherSettings() {
        withTemporarySettingsDirectory { settingsDirectory ->
            val bridge = TestWindowsDpapiBridge()
            val cipher = windowsCipher(settingsDirectory, bridge)
            val properties = baseProperties().apply {
                setProperty(KEY_OPENROUTER_API_KEY, "$DPAPI_SECRET_PREFIX%%%")
                setProperty(KEY_GOOGLE_CLIENT_SECRET, cipher.protect(OAUTH_SECRET))
                setProperty(KEY_AUTH_ID_TOKEN, "$DPAPI_SECRET_PREFIX%%%")
                setProperty(KEY_AUTH_REFRESH_TOKEN, cipher.protect(REFRESH_TOKEN_SECRET))
                setProperty("provider", DesktopProvider.OPENROUTER.name)
                setProperty("openrouter_model", "fixture-model")
            }
            writeProperties(settingsDirectory, properties)
            val originalBytes = settingsDirectory.resolve(SETTINGS_FILE_NAME).readBytes()
            val repository = DesktopSettingsRepository(settingsDirectory, cipher)

            val loaded = repository.load()

            assertTrue(loaded.openRouterApiKey.isEmpty())
            assertTrue(loaded.googleOAuthClientSecret == OAUTH_SECRET)
            assertEquals(DesktopProvider.OPENROUTER, loaded.provider)
            assertEquals("fixture-model", loaded.openRouterModel)
            assertTrue(loaded.authUid.isEmpty())
            assertTrue(loaded.authEmail.isEmpty())
            assertTrue(loaded.authIdToken.isEmpty())
            assertTrue(loaded.authRefreshToken.isEmpty())
            assertContentEquals(
                originalBytes,
                settingsDirectory.resolve(SETTINGS_FILE_NAME).readBytes()
            )
            assertTrue(migrationRecoveryFiles(settingsDirectory).isEmpty())

            val foreignCipher = DesktopCredentialCipher(
                settingsDirectory = settingsDirectory,
                platformDetector = DesktopPlatformDetector { true },
                dpapiBridge = RejectingWindowsDpapiBridge
            )
            val foreignValue = DPAPI_SECRET_PREFIX + Base64.getEncoder()
                .encodeToString("foreign-value".toByteArray())
            assertIs<DesktopSecretReadResult.Unavailable>(foreignCipher.read(foreignValue))
        }
    }

    @Test
    fun failedAtomicMigrationKeepsLastValidSettingsFile() {
        withTemporarySettingsDirectory { settingsDirectory ->
            val properties = baseProperties().apply {
                setProperty(KEY_OPENROUTER_API_KEY, OPENROUTER_SECRET)
                setProperty(KEY_GOOGLE_CLIENT_SECRET, OAUTH_SECRET)
                setProperty(KEY_AUTH_ID_TOKEN, ID_TOKEN_SECRET)
                setProperty(KEY_AUTH_REFRESH_TOKEN, REFRESH_TOKEN_SECRET)
            }
            writeProperties(settingsDirectory, properties)
            val originalBytes = settingsDirectory.resolve(SETTINGS_FILE_NAME).readBytes()
            val repository = DesktopSettingsRepository(
                settingsDirectory = settingsDirectory,
                credentialCipher = windowsCipher(settingsDirectory),
                atomicWriter = DesktopAtomicStateWriter { _, _ ->
                    throw IOException("Injected atomic replace failure")
                }
            )

            val loaded = repository.load()

            assertSecretsEqual(completeSettings(), loaded)
            assertContentEquals(
                originalBytes,
                settingsDirectory.resolve(SETTINGS_FILE_NAME).readBytes()
            )
            assertEquals(1, migrationRecoveryFiles(settingsDirectory).size)
        }
    }

    @Test
    fun injectedNonWindowsPlatformUsesAesGcmFallback() {
        withTemporarySettingsDirectory { settingsDirectory ->
            val cipher = DesktopCredentialCipher(
                settingsDirectory = settingsDirectory,
                platformDetector = DesktopPlatformDetector { false },
                dpapiBridge = RejectingWindowsDpapiBridge
            )
            val repository = DesktopSettingsRepository(settingsDirectory, cipher)
            val expected = completeSettings()

            repository.save(expected)

            val storedProperties = readProperties(settingsDirectory)
            SECRET_PROPERTY_KEYS.forEach { key ->
                assertTrue(
                    storedProperties.getProperty(key).startsWith(AES_GCM_FALLBACK_SECRET_PREFIX)
                )
            }
            val storedText = settingsDirectory.resolve(SETTINGS_FILE_NAME).readText()
            TEST_SECRETS.forEach { secret -> assertFalse(storedText.contains(secret)) }
            assertSecretsEqual(expected, repository.load())
            assertTrue(Files.isRegularFile(settingsDirectory.resolve("session_salt.bin")))
            assertTrue(migrationRecoveryFiles(settingsDirectory).isEmpty())
        }
    }

    private fun windowsRepository(settingsDirectory: Path): DesktopSettingsRepository =
        DesktopSettingsRepository(
            settingsDirectory = settingsDirectory,
            credentialCipher = windowsCipher(settingsDirectory)
        )

    private fun windowsCipher(
        settingsDirectory: Path,
        bridge: WindowsDpapiBridge = TestWindowsDpapiBridge()
    ): DesktopCredentialCipher = DesktopCredentialCipher(
        settingsDirectory = settingsDirectory,
        platformDetector = DesktopPlatformDetector { true },
        dpapiBridge = bridge
    )

    private fun completeSettings(): DesktopUserSettings = DesktopUserSettings(
        provider = DesktopProvider.OPENROUTER,
        openRouterApiKey = OPENROUTER_SECRET,
        openRouterModel = "fixture-model",
        ollamaBaseUrl = "http://127.0.0.1:11434/",
        ollamaModel = "fixture-ollama-model",
        enabledExtensionIds = setOf("fixture-extension"),
        firebaseApiKey = "public-config-value",
        firebaseProjectId = "fixture-project",
        googleOAuthClientId = "fixture-client-id",
        googleOAuthClientSecret = OAUTH_SECRET,
        authEmail = "fixture-identity",
        authUid = "fixture-account-id",
        authIdToken = ID_TOKEN_SECRET,
        authRefreshToken = REFRESH_TOKEN_SECRET,
        authTokenExpiryEpochMs = Long.MAX_VALUE,
        encryptCloudSession = false
    )

    private fun baseProperties(): Properties = Properties().apply {
        setProperty("provider", DesktopProvider.OPENROUTER.name)
        setProperty("openrouter_model", "fixture-model")
        setProperty("ollama_base_url", "http://127.0.0.1:11434/")
        setProperty("ollama_model", "fixture-ollama-model")
        setProperty("enabled_extension_ids", "fixture-extension")
        setProperty("firebase_api_key", "public-config-value")
        setProperty("firebase_project_id", "fixture-project")
        setProperty("google_oauth_client_id", "fixture-client-id")
        setProperty("auth_email", "fixture-identity")
        setProperty("auth_uid", "fixture-account-id")
        setProperty("auth_token_expiry_epoch_ms", Long.MAX_VALUE.toString())
        setProperty("encrypt_cloud_session", "false")
    }

    private fun assertSecretsEqual(expected: DesktopUserSettings, actual: DesktopUserSettings) {
        assertTrue(actual.openRouterApiKey == expected.openRouterApiKey)
        assertTrue(actual.googleOAuthClientSecret == expected.googleOAuthClientSecret)
        assertTrue(actual.authIdToken == expected.authIdToken)
        assertTrue(actual.authRefreshToken == expected.authRefreshToken)
    }

    private fun writeProperties(settingsDirectory: Path, properties: Properties) {
        Files.createDirectories(settingsDirectory)
        Files.newOutputStream(settingsDirectory.resolve(SETTINGS_FILE_NAME)).use { output ->
            properties.store(output, "test fixture")
        }
    }

    private fun readProperties(settingsDirectory: Path): Properties = Properties().apply {
        Files.newInputStream(settingsDirectory.resolve(SETTINGS_FILE_NAME)).use { input ->
            load(input)
        }
    }

    private fun migrationRecoveryFiles(settingsDirectory: Path): List<Path> {
        if (!Files.isDirectory(settingsDirectory)) return emptyList()
        return Files.list(settingsDirectory).use { paths ->
            paths.filter { path ->
                path.fileName.toString().startsWith("$SETTINGS_FILE_NAME.recovery-")
            }.toList()
        }
    }

    private inline fun withTemporarySettingsDirectory(block: (Path) -> Unit) {
        val settingsDirectory = Files.createTempDirectory("bamachat-desktop-credential-test-")
            .toAbsolutePath()
            .normalize()
        try {
            block(settingsDirectory)
        } finally {
            Files.walk(settingsDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    Files.deleteIfExists(path)
                }
            }
        }
    }

    private class RecordingWindowsDpapiNativeApi : WindowsDpapiNativeApi {
        val flags = mutableListOf<Int>()

        override fun protect(data: ByteArray, flags: Int): ByteArray {
            this.flags += flags
            return JnaWindowsDpapiNativeApi.protect(data, flags)
        }

        override fun unprotect(data: ByteArray, flags: Int): ByteArray {
            this.flags += flags
            return JnaWindowsDpapiNativeApi.unprotect(data, flags)
        }
    }

    private class TestWindowsDpapiBridge : WindowsDpapiBridge {
        private val secureRandom = SecureRandom()

        override fun protectCurrentUser(data: ByteArray): ByteArray {
            val nonce = ByteArray(TEST_NONCE_BYTES).also(secureRandom::nextBytes)
            return ByteArray(nonce.size + data.size).also { protected ->
                System.arraycopy(nonce, 0, protected, 0, nonce.size)
                data.indices.forEach { index ->
                    protected[nonce.size + index] = (
                        data[index].toInt() xor nonce[index % nonce.size].toInt() xor TEST_MASK
                    ).toByte()
                }
            }
        }

        override fun unprotectCurrentUser(data: ByteArray): ByteArray {
            require(data.size > TEST_NONCE_BYTES)
            val plainSize = data.size - TEST_NONCE_BYTES
            return ByteArray(plainSize) { index ->
                (
                    data[TEST_NONCE_BYTES + index].toInt() xor
                        data[index % TEST_NONCE_BYTES].toInt() xor
                        TEST_MASK
                    ).toByte()
            }
        }

        private companion object {
            const val TEST_NONCE_BYTES = 16
            const val TEST_MASK = 0x5a
        }
    }

    private data object RejectingWindowsDpapiBridge : WindowsDpapiBridge {
        override fun protectCurrentUser(data: ByteArray): ByteArray {
            error("DPAPI must not be called")
        }

        override fun unprotectCurrentUser(data: ByteArray): ByteArray {
            error("DPAPI payload belongs to another user")
        }
    }

    private companion object {
        const val SETTINGS_FILE_NAME = "settings.properties"
        const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        const val KEY_GOOGLE_CLIENT_SECRET = "google_oauth_client_secret"
        const val KEY_AUTH_ID_TOKEN = "auth_id_token"
        const val KEY_AUTH_REFRESH_TOKEN = "auth_refresh_token"
        const val OPENROUTER_SECRET = "test-only-openrouter-secret-ß"
        const val OAUTH_SECRET = "test-only-oauth-secret-雪"
        const val ID_TOKEN_SECRET = "test-only-id-token-Δ"
        const val REFRESH_TOKEN_SECRET = "test-only-refresh-token-Ж"
        val SECRET_PROPERTY_KEYS = listOf(
            KEY_OPENROUTER_API_KEY,
            KEY_GOOGLE_CLIENT_SECRET,
            KEY_AUTH_ID_TOKEN,
            KEY_AUTH_REFRESH_TOKEN
        )
        val TEST_SECRETS = listOf(
            OPENROUTER_SECRET,
            OAUTH_SECRET,
            ID_TOKEN_SECRET,
            REFRESH_TOKEN_SECRET
        )
    }
}
