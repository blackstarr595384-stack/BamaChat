package com.example.bamachat.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface SettingsKey<T> {
    val key: String
    val default: T

    data class BoolKey(override val key: String, override val default: Boolean) : SettingsKey<Boolean>
    data class IntKey(override val key: String, override val default: Int) : SettingsKey<Int>
    data class FloatKey(override val key: String, override val default: Float) : SettingsKey<Float>
    data class StringKey(override val key: String, override val default: String) : SettingsKey<String>
    data class StringSetKey(override val key: String, override val default: Set<String>) : SettingsKey<Set<String>>
}

class SettingsDataStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: SettingsKey<T>): T {
        return when (key) {
            is SettingsKey.BoolKey -> prefs.getBoolean(key.key, key.default) as T
            is SettingsKey.IntKey -> prefs.getInt(key.key, key.default) as T
            is SettingsKey.FloatKey -> prefs.getFloat(key.key, key.default) as T
            is SettingsKey.StringKey -> (prefs.getString(key.key, key.default) ?: key.default) as T
            is SettingsKey.StringSetKey -> (prefs.getStringSet(key.key, key.default) ?: key.default) as T
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> set(key: SettingsKey<T>, value: T) {
        prefs.edit().apply {
            when (key) {
                is SettingsKey.BoolKey -> putBoolean(key.key, value as Boolean)
                is SettingsKey.IntKey -> putInt(key.key, value as Int)
                is SettingsKey.FloatKey -> putFloat(key.key, value as Float)
                is SettingsKey.StringKey -> putString(key.key, value as String)
                is SettingsKey.StringSetKey -> putStringSet(key.key, value as Set<String>)
            }
            apply()
        }
    }

    fun <T> observe(key: SettingsKey<T>): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key.key) {
                trySend(get(key))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get(key))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
