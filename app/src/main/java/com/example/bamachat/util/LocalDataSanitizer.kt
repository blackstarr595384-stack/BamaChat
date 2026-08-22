package com.example.bamachat.util

import android.content.Context
import android.content.SharedPreferences
import com.example.bamachat.data.local.ChatDao
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ScopedChatCleanupResult

class LocalDataSanitizer internal constructor(
    context: Context,
    private val prefs: SharedPreferences,
    private val chatDao: ChatDao
) {
    private val appContext = context.applicationContext

    constructor(context: Context) : this(
        context = context.applicationContext,
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE),
        chatDao = ChatDatabase.getDatabase(context.applicationContext).chatDao()
    )

    /**
     * Removes the selected guest's chats, knowledge, and message-derived memory and feedback rows.
     * Account, workspace, provider, API-key, prompt, and general settings data remain unchanged.
     */
    suspend fun clearGuestSessionData(ownerScope: String): ScopedChatCleanupResult {
        require(ChatOwnerScope.isGuest(ownerScope)) { "Only a valid guest scope can be cleared" }
        return chatDao.deleteChatDataForScope(ownerScope)
    }

    private suspend fun clearAllLocalData(clearApiKeys: Boolean) {
        chatDao.deleteAllMessages()
        chatDao.deleteAllConversations()
        chatDao.deleteAllPersonaMemory()
        chatDao.deleteAllPersonaFeedback()
        chatDao.deleteAllPromptVersions()
        chatDao.deleteAllUserMemoryFacts()
        chatDao.deleteAllKnowledgeChunks()
        chatDao.deleteAllKnowledgeEdges()
        chatDao.deleteAllPersonaTrainingExamples()

        val editor = prefs.edit()
        val allKeys = prefs.all.keys
        allKeys.forEach { key ->
            if (
                key == "current_conversation_id" ||
                key == "selected_persona" ||
                key == "custom_persona_prompt" ||
                key == "project_workspaces_json" ||
                key == "active_workspace_id" ||
                key == "active_workspace_name" ||
                key == "workspace_chat_filter_enabled" ||
                key == "cloud_persona_last_sync_at" ||
                key == "cloud_persona_last_sync_status" ||
                key == "usage_day" ||
                key == "usage_text_count" ||
                key == "usage_web_search_count" ||
                key == "usage_image_analysis_count" ||
                key == "usage_image_generation_count" ||
                key.startsWith("persona_prompt_override_") ||
                key.startsWith("persona_character_")
            ) {
                editor.remove(key)
            }
        }

        if (clearApiKeys) {
            SecureSettingsStore.clear(appContext)
            editor.remove("openrouter_api_key")
            editor.remove("groq_api_key")
            editor.remove("cerebras_api_key")
            editor.remove("together_api_key")
            editor.remove("gemini_api_key")
            editor.remove("opencode_api_key")
            editor.remove("opencode_endpoint")
            editor.remove("opencode_model")
            editor.remove("elevenlabs_api_key")
            editor.remove("cloud_voice_provider")
            editor.remove("piper_endpoint")
            editor.remove("piper_voice_name")
            editor.remove("live_web_api_token")
            editor.remove("live_web_endpoint")
            editor.remove("live_web_allowed_domains")
            editor.remove("mcp_remote_url")
            editor.remove("mcp_remote_token")
            editor.remove("photo_ai_cloud_api_token")
            editor.remove("photo_ai_cloud_endpoint")
        }
        editor.apply()
    }

    suspend fun clearAllAppData(clearApiKeys: Boolean = false) {
        clearAllLocalData(clearApiKeys = clearApiKeys)
        if (clearApiKeys) {
            SecureSettingsStore.clear(appContext)
        }
        prefs.edit().clear().apply()
    }
}
