package com.example.bamachat.data.cloud

import android.content.SharedPreferences
import com.example.bamachat.data.local.ChatSessionScopeStore

class ChatSyncPolicy(
    private val prefs: SharedPreferences,
    private val scopeStore: ChatSessionScopeStore
) {
    fun preferenceKey(uid: String): String =
        "$KEY_PREFIX${uid.trim()}"

    fun isEnabledForUid(uid: String?): Boolean {
        val cleanUid = uid?.trim().orEmpty()
        if (cleanUid.isBlank()) return false
        if (!scopeStore.isCloudSyncAllowed(cleanUid)) return false
        return prefs.getBoolean(preferenceKey(cleanUid), false)
    }

    fun setEnabledForUid(uid: String, enabled: Boolean) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        prefs.edit()
            .remove(LEGACY_GLOBAL_KEY)
            .putBoolean(preferenceKey(cleanUid), enabled)
            .apply()
    }

    fun hasLegacyGlobalPreference(): Boolean =
        prefs.contains(LEGACY_GLOBAL_KEY)

    companion object {
        const val LEGACY_GLOBAL_KEY = "cloud_chat_sync_enabled"
        private const val KEY_PREFIX = "cloud_chat_sync_enabled_"
    }
}
