package com.example.bamachat.desktop

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal const val DPAPI_SECRET_PREFIX = "dpapi:v1:"
internal const val AES_GCM_FALLBACK_SECRET_PREFIX = "aesgcm:v1:"
internal const val LEGACY_AES_GCM_SECRET_PREFIX = "enc:v1:"

internal fun interface DesktopPlatformDetector {
    fun isWindows(): Boolean
}

internal object SystemDesktopPlatformDetector : DesktopPlatformDetector {
    override fun isWindows(): Boolean = System.getProperty("os.name")
        .orEmpty()
        .startsWith("Windows", ignoreCase = true)
}

internal interface WindowsDpapiNativeApi {
    fun protect(data: ByteArray, flags: Int): ByteArray

    fun unprotect(data: ByteArray, flags: Int): ByteArray
}

internal object JnaWindowsDpapiNativeApi : WindowsDpapiNativeApi {
    override fun protect(data: ByteArray, flags: Int): ByteArray =
        Crypt32Util.cryptProtectData(data, flags)

    override fun unprotect(data: ByteArray, flags: Int): ByteArray =
        Crypt32Util.cryptUnprotectData(data, flags)
}

internal interface WindowsDpapiBridge {
    fun protectCurrentUser(data: ByteArray): ByteArray

    fun unprotectCurrentUser(data: ByteArray): ByteArray
}

internal class WindowsCurrentUserDpapiBridge(
    private val nativeApi: WindowsDpapiNativeApi = JnaWindowsDpapiNativeApi
) : WindowsDpapiBridge {
    override fun protectCurrentUser(data: ByteArray): ByteArray =
        nativeApi.protect(data, CURRENT_USER_FLAGS)

    override fun unprotectCurrentUser(data: ByteArray): ByteArray =
        nativeApi.unprotect(data, CURRENT_USER_FLAGS)

    private companion object {
        const val CURRENT_USER_FLAGS = WinCrypt.CRYPTPROTECT_UI_FORBIDDEN
    }
}

internal sealed interface DesktopSecretReadResult {
    data class Available(
        val plainText: String,
        val requiresMigration: Boolean
    ) : DesktopSecretReadResult

    data object Missing : DesktopSecretReadResult

    data object Unavailable : DesktopSecretReadResult
}

internal class DesktopCredentialCipher(
    settingsDirectory: Path = DesktopDataDirectoryResolver.resolveSettingsDirectory(),
    private val platformDetector: DesktopPlatformDetector = SystemDesktopPlatformDetector,
    private val dpapiBridge: WindowsDpapiBridge = WindowsCurrentUserDpapiBridge(),
    private val aesGcmProtector: DesktopAesGcmSecretProtector =
        DesktopAesGcmSecretProtector(settingsDirectory)
) {
    fun protect(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        return if (platformDetector.isWindows()) {
            protectWithDpapi(plainText)
        } else {
            aesGcmProtector.protect(plainText, AES_GCM_FALLBACK_SECRET_PREFIX)
        }
    }

    fun read(storedValue: String): DesktopSecretReadResult {
        if (storedValue.isEmpty()) return DesktopSecretReadResult.Missing
        return when {
            storedValue.startsWith(DPAPI_SECRET_PREFIX) -> readDpapi(storedValue)
            storedValue.startsWith("dpapi:") -> DesktopSecretReadResult.Unavailable
            storedValue.startsWith(AES_GCM_FALLBACK_SECRET_PREFIX) -> readAesGcm(
                storedValue = storedValue,
                prefix = AES_GCM_FALLBACK_SECRET_PREFIX,
                requiresMigration = platformDetector.isWindows()
            )
            storedValue.startsWith("aesgcm:") -> DesktopSecretReadResult.Unavailable
            storedValue.startsWith(LEGACY_AES_GCM_SECRET_PREFIX) -> readAesGcm(
                storedValue = storedValue,
                prefix = LEGACY_AES_GCM_SECRET_PREFIX,
                requiresMigration = true
            )
            storedValue.startsWith("enc:") -> DesktopSecretReadResult.Unavailable
            else -> DesktopSecretReadResult.Available(
                plainText = storedValue,
                requiresMigration = true
            )
        }
    }

    private fun protectWithDpapi(plainText: String): String {
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        return try {
            val protectedBytes = dpapiBridge.protectCurrentUser(plainBytes)
            try {
                DPAPI_SECRET_PREFIX + Base64.getEncoder().encodeToString(protectedBytes)
            } finally {
                protectedBytes.fill(0)
            }
        } finally {
            plainBytes.fill(0)
        }
    }

    private fun readDpapi(storedValue: String): DesktopSecretReadResult {
        if (!platformDetector.isWindows()) return DesktopSecretReadResult.Unavailable
        return decodeAvailableSecret {
            val protectedBytes = decodePayload(storedValue, DPAPI_SECRET_PREFIX)
            try {
                val plainBytes = dpapiBridge.unprotectCurrentUser(protectedBytes)
                try {
                    decodeUtf8Strict(plainBytes)
                } finally {
                    plainBytes.fill(0)
                }
            } finally {
                protectedBytes.fill(0)
            }
        }
    }

    private fun readAesGcm(
        storedValue: String,
        prefix: String,
        requiresMigration: Boolean
    ): DesktopSecretReadResult = decodeAvailableSecret(requiresMigration) {
        aesGcmProtector.decrypt(storedValue, prefix)
    }

    private fun decodeAvailableSecret(
        requiresMigration: Boolean = false,
        decode: () -> String
    ): DesktopSecretReadResult = try {
        DesktopSecretReadResult.Available(
            plainText = decode(),
            requiresMigration = requiresMigration
        )
    } catch (_: Exception) {
        DesktopSecretReadResult.Unavailable
    }

    private fun decodePayload(storedValue: String, prefix: String): ByteArray {
        val encoded = storedValue.removePrefix(prefix)
        require(encoded.isNotEmpty()) { "Geschützter Desktop-Wert ist unvollständig." }
        return Base64.getDecoder().decode(encoded)
            .also { require(it.isNotEmpty()) { "Geschützter Desktop-Wert ist leer." } }
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}

internal class DesktopAesGcmSecretProtector(
    settingsDirectory: Path,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val atomicWriter: DesktopAtomicStateWriter = DesktopAtomicStateWriter()
) {
    private val settingsDirectory = settingsDirectory.toAbsolutePath().normalize()
    private val saltFile = this.settingsDirectory.resolve("session_salt.bin")
    private val lock = directoryLocks.computeIfAbsent(this.settingsDirectory.toString()) { Any() }

    fun protect(plainText: String, prefix: String = AES_GCM_FALLBACK_SECRET_PREFIX): String {
        require(prefix == AES_GCM_FALLBACK_SECRET_PREFIX || prefix == LEGACY_AES_GCM_SECRET_PREFIX) {
            "Unbekanntes AES-GCM-Format."
        }
        if (plainText.isEmpty()) return plainText
        return synchronized(lock) {
            val key = deriveKey()
            val plainBytes = plainText.toByteArray(Charsets.UTF_8)
            try {
                val iv = ByteArray(GCM_IV_BYTES).also(secureRandom::nextBytes)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, iv)
                )
                val cipherText = cipher.doFinal(plainBytes)
                val payload = ByteArray(iv.size + cipherText.size)
                System.arraycopy(iv, 0, payload, 0, iv.size)
                System.arraycopy(cipherText, 0, payload, iv.size, cipherText.size)
                try {
                    prefix + Base64.getEncoder().encodeToString(payload)
                } finally {
                    cipherText.fill(0)
                    payload.fill(0)
                }
            } finally {
                key.fill(0)
                plainBytes.fill(0)
            }
        }
    }

    fun decrypt(storedValue: String, prefix: String): String = synchronized(lock) {
        require(storedValue.startsWith(prefix)) { "Unbekanntes AES-GCM-Format." }
        val encoded = storedValue.removePrefix(prefix)
        require(encoded.isNotEmpty()) { "AES-GCM-Wert ist unvollständig." }
        val payload = Base64.getDecoder().decode(encoded)
        require(payload.size > GCM_IV_BYTES) { "AES-GCM-Wert ist unvollständig." }
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val cipherText = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val key = deriveKey()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val plainBytes = cipher.doFinal(cipherText)
            try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(plainBytes))
                    .toString()
            } finally {
                plainBytes.fill(0)
            }
        } finally {
            key.fill(0)
            payload.fill(0)
            iv.fill(0)
            cipherText.fill(0)
        }
    }

    private fun deriveKey(): ByteArray {
        val salt = getOrCreateSalt()
        val fingerprint = buildString {
            append(System.getProperty("user.name").orEmpty())
            append('|')
            append(System.getProperty("user.home").orEmpty())
            append('|')
            append(System.getProperty("os.name").orEmpty())
        }.toByteArray(Charsets.UTF_8)
        return try {
            MessageDigest.getInstance("SHA-256").run {
                update(salt)
                update(fingerprint)
                digest().copyOf(KEY_BYTES)
            }
        } finally {
            salt.fill(0)
            fingerprint.fill(0)
        }
    }

    private fun getOrCreateSalt(): ByteArray {
        if (Files.exists(saltFile)) {
            require(Files.isRegularFile(saltFile)) { "Desktop-Cipher-Salt ist keine Datei." }
            return Files.readAllBytes(saltFile).also { existing ->
                require(existing.size >= MINIMUM_SALT_BYTES) { "Desktop-Cipher-Salt ist beschädigt." }
            }
        }
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        atomicWriter.write(saltFile, salt)
        return salt.copyOf()
    }

    private companion object {
        const val KEY_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val MINIMUM_SALT_BYTES = 16
        const val SALT_BYTES = 32
        val directoryLocks = ConcurrentHashMap<String, Any>()
    }
}
