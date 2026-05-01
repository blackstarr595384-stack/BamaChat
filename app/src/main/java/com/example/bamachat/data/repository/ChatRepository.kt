package com.example.bamachat.data.repository

import com.example.bamachat.data.local.ChatDao
import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository für Chat-Daten (Conversations + Messages).
 * Persistiert in Room. Provider-Aufrufe (OpenRouter, Ollama, Gemini) macht das ChatViewModel direkt.
 */
class ChatRepository(private val chatDao: ChatDao) {

    // ===== Conversations =====
    fun getAllConversations(): Flow<List<ConversationEntity>> =
        chatDao.getAllConversations()

    suspend fun getConversation(id: String): ConversationEntity? =
        chatDao.getConversationById(id)

    suspend fun createConversation(
        id: String,
        title: String = "Neuer Chat",
        personaName: String = "ASSISTANT"
    ): ConversationEntity {
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now,
            personaName = personaName
        )
        chatDao.insertConversation(conv)
        return conv
    }

    suspend fun renameConversation(id: String, newTitle: String) {
        chatDao.renameConversation(id, newTitle, System.currentTimeMillis())
    }

    suspend fun touchConversation(id: String) {
        chatDao.touchConversation(id, System.currentTimeMillis())
    }

    suspend fun deleteConversation(id: String) {
        chatDao.deleteConversation(id)
    }

    // ===== Messages =====
    fun getMessages(conversationId: String): Flow<List<ChatMessage>> =
        chatDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { e ->
                ChatMessage(
                    id = e.id,
                    text = e.text,
                    isUser = e.isUser,
                    timestamp = e.timestamp,
                    imageUrl = e.imageUrl
                )
            }
        }

    suspend fun saveMessage(
        conversationId: String,
        message: ChatMessage,
        touchConversation: Boolean = true
    ) {
        chatDao.insertMessage(
            ChatMessageEntity(
                id = message.id,
                conversationId = conversationId,
                text = message.text,
                isUser = message.isUser,
                timestamp = message.timestamp,
                imageUrl = message.imageUrl
            )
        )
        if (touchConversation) {
            chatDao.touchConversation(conversationId, System.currentTimeMillis())
        }
    }

    suspend fun clearMessages(conversationId: String) {
        chatDao.deleteMessagesForConversation(conversationId)
    }
}
