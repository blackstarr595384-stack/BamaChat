package com.example.bamachat.data.provider.chat

import android.content.Context
import com.example.bamachat.data.provider.ProviderId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActiveChatProviderSelectionStoreTest {
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("provider_selection_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @Test
    fun legacyIsSafeDefaultAndCanBeRestored() {
        val store = ActiveChatProviderSelectionStore(preferences)
        assertEquals(ActiveChatProviderSelection.Legacy, store.selection.value)

        store.save(customSelection())
        store.resetToLegacy()

        assertEquals(ActiveChatProviderSelection.Legacy, store.selection.value)
    }

    @Test
    fun customSelectionPersistsOnlyNonSensitiveIdentity() {
        val selection = customSelection()
        ActiveChatProviderSelectionStore(preferences).save(selection)

        assertEquals(selection, ActiveChatProviderSelectionStore(preferences).selection.value)
        val raw = preferences.getString(ActiveChatProviderSelectionStore.KEY, null).orEmpty()
        assertTrue(raw.contains(selection.providerId.value))
        assertTrue(raw.contains(selection.modelId))
        assertFalse(raw.contains("baseUrl", ignoreCase = true))
        assertFalse(raw.contains("apiKey", ignoreCase = true))
        assertFalse(raw.contains("authorization", ignoreCase = true))
    }

    @Test
    fun corruptOrUnknownVersionFallsBackToLegacy() {
        preferences.edit().putString(ActiveChatProviderSelectionStore.KEY, "not-json").commit()
        assertEquals(ActiveChatProviderSelection.Legacy, ActiveChatProviderSelectionStore(preferences).selection.value)

        preferences.edit().putString(
            ActiveChatProviderSelectionStore.KEY,
            "{\"version\":99,\"mode\":\"custom\"}"
        ).commit()
        assertEquals(ActiveChatProviderSelection.Legacy, ActiveChatProviderSelectionStore(preferences).selection.value)
    }

    private fun customSelection() = ActiveChatProviderSelection.Custom(
        ProviderId.newCustom(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        "test-model"
    )
}
