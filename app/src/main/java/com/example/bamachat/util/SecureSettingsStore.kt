package com.example.bamachat.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureSettingsStore {
    private const val FILE_NAME = "secure_settings"
    private const val MASTER_KEY_ALIAS = "secure_settings_master_key"

    val secretKeys: Set<String> = setOf(
        "openrouter_api_key",
        "groq_api_key",
        "cerebras_api_key",
        "together_api_key",
        "gemini_api_key",
        "elevenlabs_api_key",
        "live_web_api_token",
        "photo_ai_cloud_api_token"
    )

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    fun getString(
        context: Context,
        legacyPrefs: SharedPreferences,
        key: String,
        defaultValue: String = ""
    ): String {
        val securePrefs = prefs(context)
        securePrefs.getString(key, null)?.let { return it }

        val legacyValue = legacyPrefs.getString(key, null)?.trim().orEmpty()
        if (legacyValue.isNotBlank()) {
            securePrefs.edit().putString(key, legacyValue).apply()
            legacyPrefs.edit().remove(key).apply()
            return legacyValue
        }

        return defaultValue
    }

    fun putString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun remove(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }

    fun clear(context: Context, keys: Collection<String> = secretKeys) {
        val editor = prefs(context).edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        encryptedPrefs?.let { return it }
        synchronized(this) {
            encryptedPrefs?.let { return it }
            val appContext = context.applicationContext
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                appContext,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            encryptedPrefs = created
            return created
        }
    }
}
