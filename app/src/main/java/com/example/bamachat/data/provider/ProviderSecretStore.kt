package com.example.bamachat.data.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface ProviderSecretStorage {
    fun put(providerId: ProviderId, secret: String)
    fun get(providerId: ProviderId): String?
    fun contains(providerId: ProviderId): Boolean
    fun remove(providerId: ProviderId)
    fun clearCustomSecrets()
}

internal interface ProviderSecretBackend {
    fun put(alias: String, value: String)
    fun get(alias: String): String?
    fun contains(alias: String): Boolean
    fun remove(alias: String)
    fun aliases(): Set<String>
}

class ProviderSecretStoreException(message: String) : IllegalStateException(message)

@Singleton
class ProviderSecretStore internal constructor(
    private val backend: ProviderSecretBackend
) : ProviderSecretStorage {
    @Inject
    constructor(@ApplicationContext context: Context) : this(EncryptedProviderSecretBackend(context))

    override fun put(providerId: ProviderId, secret: String) {
        requireCustom(providerId)
        val clean = secret.trim()
        if (clean.isEmpty()) throw ProviderSecretStoreException("Der Anbieter-Schlüssel darf nicht leer sein.")
        runCatching { backend.put(alias(providerId), clean) }
            .getOrElse { throw ProviderSecretStoreException("Der Anbieter-Schlüssel konnte nicht sicher gespeichert werden.") }
    }

    override fun get(providerId: ProviderId): String? {
        requireCustom(providerId)
        return runCatching { backend.get(alias(providerId)) }
            .getOrElse { throw ProviderSecretStoreException("Der Anbieter-Schlüssel konnte nicht sicher geladen werden.") }
    }

    override fun contains(providerId: ProviderId): Boolean {
        requireCustom(providerId)
        return runCatching { backend.contains(alias(providerId)) }
            .getOrElse { throw ProviderSecretStoreException("Der Schlüsselstatus konnte nicht sicher geprüft werden.") }
    }

    override fun remove(providerId: ProviderId) {
        requireCustom(providerId)
        runCatching { backend.remove(alias(providerId)) }
            .getOrElse { throw ProviderSecretStoreException("Der Anbieter-Schlüssel konnte nicht sicher gelöscht werden.") }
    }

    override fun clearCustomSecrets() {
        runCatching {
            backend.aliases().filter { it.startsWith(ALIAS_PREFIX) }.forEach(backend::remove)
        }.getOrElse { throw ProviderSecretStoreException("Benutzerdefinierte Anbieter-Schlüssel konnten nicht sicher gelöscht werden.") }
    }

    override fun toString(): String = "ProviderSecretStore(encrypted=true)"

    internal fun aliasForTesting(providerId: ProviderId): String = alias(providerId)

    private fun requireCustom(providerId: ProviderId) {
        if (!providerId.isCustom) {
            throw ProviderSecretStoreException("Dieser Speicher ist ausschließlich für benutzerdefinierte Anbieter vorgesehen.")
        }
    }

    private fun alias(providerId: ProviderId): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(providerId.value.toByteArray(Charsets.UTF_8))
        return ALIAS_PREFIX + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val ALIAS_PREFIX = "custom_provider_secret_"
    }
}

private class EncryptedProviderSecretBackend(context: Context) : ProviderSecretBackend {
    private val preferences: SharedPreferences

    init {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        preferences = EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun put(alias: String, value: String) {
        if (!preferences.edit().putString(alias, value).commit()) {
            throw IllegalStateException("Encrypted preference write failed")
        }
    }

    override fun get(alias: String): String? = preferences.getString(alias, null)

    override fun contains(alias: String): Boolean = preferences.contains(alias)

    override fun remove(alias: String) {
        if (!preferences.edit().remove(alias).commit()) {
            throw IllegalStateException("Encrypted preference removal failed")
        }
    }

    override fun aliases(): Set<String> = preferences.all.keys

    private companion object {
        const val FILE_NAME = "custom_provider_secrets"
        const val MASTER_KEY_ALIAS = "custom_provider_secrets_master_key"
    }
}
