package com.example.bamachat.data.provider.chat

import android.content.SharedPreferences
import com.example.bamachat.data.provider.ProviderId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

@Singleton
class ActiveChatProviderSelectionStore @Inject constructor(
    private val preferences: SharedPreferences
) {
    companion object {
        internal const val KEY = "active_chat_provider_selection_v1"
        private const val VERSION = 1
    }

    private val _selection = MutableStateFlow(readSelection())
    val selection: StateFlow<ActiveChatProviderSelection> = _selection.asStateFlow()

    fun save(selection: ActiveChatProviderSelection) {
        val encoded = when (selection) {
            ActiveChatProviderSelection.Legacy -> JSONObject()
                .put("version", VERSION)
                .put("mode", "legacy")
            is ActiveChatProviderSelection.Custom -> JSONObject()
                .put("version", VERSION)
                .put("mode", "custom")
                .put("providerId", selection.providerId.value)
                .put("modelId", selection.modelId)
        }.toString()
        preferences.edit().putString(KEY, encoded).apply()
        _selection.value = selection
    }

    fun resetToLegacy() = save(ActiveChatProviderSelection.Legacy)

    private fun readSelection(): ActiveChatProviderSelection {
        val raw = preferences.getString(KEY, null) ?: return ActiveChatProviderSelection.Legacy
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version", -1) != VERSION) return@runCatching ActiveChatProviderSelection.Legacy
            when (json.optString("mode")) {
                "custom" -> ActiveChatProviderSelection.Custom(
                    providerId = ProviderId(json.getString("providerId")),
                    modelId = json.getString("modelId").trim()
                )
                else -> ActiveChatProviderSelection.Legacy
            }
        }.getOrDefault(ActiveChatProviderSelection.Legacy)
    }
}
