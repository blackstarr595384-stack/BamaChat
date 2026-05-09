package com.example.bamachat.util

import android.content.Context
import com.example.bamachat.data.local.ChatDatabase

class LocalDataSanitizer(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val chatDao = ChatDatabase.getDatabase(appContext).chatDao()

    suspend fun clearGuestSessionData(clearApiKeys: Boolean = false) {
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
            editor.remove("openrouter_api_key")
            editor.remove("groq_api_key")
            editor.remove("cerebras_api_key")
            editor.remove("together_api_key")
            editor.remove("gemini_api_key")
            editor.remove("elevenlabs_api_key")
            editor.remove("live_web_api_token")
            editor.remove("live_web_endpoint")
            editor.remove("live_web_allowed_domains")
        }
        editor.apply()
    }
}
