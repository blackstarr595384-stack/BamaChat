package com.example.bamachat.data.cloud

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSyncPolicyTest {
    @Test
    fun syncIsDisabledWithoutUidAndByDefault() {
        val policy = ChatSyncPolicy(FakeSharedPreferences())

        assertFalse(policy.isEnabledForUid(null))
        assertFalse(policy.isEnabledForUid(""))
        assertFalse(policy.isEnabledForUid("uid-a"))
    }

    @Test
    fun perUserPreferenceDoesNotLeakBetweenAccounts() {
        val prefs = FakeSharedPreferences()
        val policy = ChatSyncPolicy(prefs)

        policy.setEnabledForUid("uid-a", true)

        assertTrue(policy.isEnabledForUid("uid-a"))
        assertFalse(policy.isEnabledForUid("uid-b"))
    }

    @Test
    fun legacyGlobalPreferenceIsDetectedButNotUsed() {
        val prefs = FakeSharedPreferences(
            mutableMapOf(ChatSyncPolicy.LEGACY_GLOBAL_KEY to true)
        )
        val policy = ChatSyncPolicy(prefs)

        assertTrue(policy.hasLegacyGlobalPreference())
        assertFalse(policy.isEnabledForUid("uid-a"))
    }

    @Test
    fun previewIsWhitespaceNormalizedAndTruncated() {
        val raw = "Hallo\n\nWelt " + "x".repeat(160)
        val preview = AndroidChatSyncCoordinator.safePreview(raw)

        assertEquals(AndroidChatSyncCoordinator.MAX_PREVIEW_LENGTH, preview.length)
        assertFalse(preview.contains("\n"))
    }
}

private class FakeSharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (values[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private class FakeEditor(
    private val values: MutableMap<String, Any?>
) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removals = mutableSetOf<String>()

    override fun putString(key: String?, value: String?): SharedPreferences.Editor = applyChange(key, value)
    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = applyChange(key, values)
    override fun putInt(key: String?, value: Int): SharedPreferences.Editor = applyChange(key, value)
    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = applyChange(key, value)
    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = applyChange(key, value)
    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = applyChange(key, value)
    override fun remove(key: String?): SharedPreferences.Editor {
        key?.let { removals += it }
        return this
    }
    override fun clear(): SharedPreferences.Editor {
        removals += values.keys
        return this
    }
    override fun commit(): Boolean {
        apply()
        return true
    }
    override fun apply() {
        removals.forEach(values::remove)
        values.putAll(pending)
    }

    private fun applyChange(key: String?, value: Any?): SharedPreferences.Editor {
        key?.let { pending[it] = value }
        return this
    }
}
