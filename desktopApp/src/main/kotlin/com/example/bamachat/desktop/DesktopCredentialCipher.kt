package com.example.bamachat.desktop

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object DesktopCredentialCipher {
    private const val ENCRYPTED_PREFIX = "enc:v1:"
    private const val KEY_BYTES = 32
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val baseDir = File(System.getProperty("user.home"), ".bamachat-desktop")
    private val saltFile = File(baseDir, "session_salt.bin")

    fun isEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        val key = deriveKey()
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(cipherText, 0, payload, iv.size, cipherText.size)
        return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    fun decrypt(storedValue: String): String {
        if (storedValue.isEmpty()) return storedValue
        if (!isEncrypted(storedValue)) return storedValue
        val payload = Base64.getDecoder().decode(storedValue.removePrefix(ENCRYPTED_PREFIX))
        if (payload.size <= GCM_IV_BYTES) {
            throw IllegalStateException("Ungültiges verschlüsseltes Tokenformat.")
        }
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val cipherText = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val key = deriveKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherText)
        return plainBytes.toString(Charsets.UTF_8)
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
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(fingerprint)
        return digest.digest().copyOf(KEY_BYTES)
    }

    private fun getOrCreateSalt(): ByteArray {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        if (!saltFile.exists()) {
            val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
            saltFile.writeBytes(salt)
            return salt
        }
        val existing = saltFile.readBytes()
        if (existing.size >= 16) return existing
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        saltFile.writeBytes(salt)
        return salt
    }
}
