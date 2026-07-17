package com.example.bamachat.data.repository

import com.example.bamachat.data.local.ChatDao
import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ChatMessageFtsEntity
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.local.KnowledgeChunkEntity
import com.example.bamachat.data.local.KnowledgeEdgeEntity
import com.example.bamachat.data.local.MessageFtsResult
import com.example.bamachat.data.local.PersonaFeedbackEntity
import com.example.bamachat.data.local.PersonaFeedbackStats
import com.example.bamachat.data.local.PersonaMemoryEntity
import com.example.bamachat.data.local.PersonaPromptVersionEntity
import com.example.bamachat.data.local.PersonaTrainingExampleEntity
import com.example.bamachat.data.local.UserMemoryFactEntity
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ChatSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository für Chat-Daten (Conversations + Messages).
 * Persistiert in Room. Provider-Aufrufe (OpenRouter, Ollama, Gemini) macht das ChatViewModel direkt.
 */
class ChatRepository(private val chatDao: ChatDao) {
    private val gson = Gson()

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

    suspend fun updateConversationPersonaName(id: String, personaName: String) {
        chatDao.updateConversationPersonaName(id, personaName)
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
                    imageUrl = e.imageUrl,
                    sources = parseSources(e.sourcesJson),
                    webFetchedAtIso = e.webFetchedAtIso
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
                imageUrl = message.imageUrl,
                sourcesJson = stringifySources(message.sources),
                webFetchedAtIso = message.webFetchedAtIso
            )
        )
        chatDao.insertMessageFts(
            ChatMessageFtsEntity(
                messageId = message.id,
                conversationId = conversationId,
                text = message.text,
                isUser = message.isUser,
                timestamp = message.timestamp
            )
        )
        if (touchConversation) {
            chatDao.touchConversation(conversationId, System.currentTimeMillis())
        }
    }

    suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessage(messageId)
        chatDao.deleteMessageFts(messageId)
    }

    suspend fun clearMessages(conversationId: String) {
        chatDao.deleteMessagesForConversation(conversationId)
        chatDao.deleteMessagesFtsForConversation(conversationId)
    }

    suspend fun searchMessages(query: String, limit: Int = 30): List<MessageFtsResult> {
        val ftsQuery = query.trim().lowercase()
            .replace(Regex("[^a-zA-ZäöüÄÖÜß0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .joinToString(" AND ")
        if (ftsQuery.isBlank()) return emptyList()
        return chatDao.searchMessagesFts(ftsQuery, limit)
    }

    // ===== Persona Memory =====
    suspend fun savePersonaMemory(
        personaName: String,
        memoryText: String,
        sourceMessageId: String? = null,
        keepLatest: Int = 10
    ) {
        val now = System.currentTimeMillis()
        chatDao.insertPersonaMemory(
            PersonaMemoryEntity(
                personaName = personaName,
                memoryText = memoryText,
                sourceMessageId = sourceMessageId,
                createdAt = now,
                updatedAt = now
            )
        )
        chatDao.trimPersonaMemory(personaName, keepLatest)
    }

    suspend fun getRecentPersonaMemory(personaName: String, limit: Int = 4): List<PersonaMemoryEntity> =
        chatDao.getRecentMemoryForPersona(personaName, limit)

    // ===== Persona Feedback =====
    suspend fun savePersonaFeedback(
        personaName: String,
        messageId: String,
        helpful: Boolean,
        note: String? = null
    ) {
        chatDao.upsertPersonaFeedback(
            PersonaFeedbackEntity(
                personaName = personaName,
                messageId = messageId,
                helpful = helpful,
                note = note,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getFeedbackForMessage(messageId: String): Boolean? =
        chatDao.getFeedbackForMessage(messageId)

    suspend fun getPersonaFeedbackStats(personaName: String): PersonaFeedbackStats =
        chatDao.getFeedbackStatsForPersona(personaName)

    suspend fun getPersonaAdaptationScore(personaName: String): Int {
        val stats = getPersonaFeedbackStats(personaName)
        val total = stats.helpfulCount + stats.unhelpfulCount
        if (total <= 0) return 50
        val ratio = (stats.helpfulCount - stats.unhelpfulCount).toFloat() / total.toFloat()
        return (50 + (ratio * 50f)).toInt().coerceIn(0, 100)
    }

    // ===== Prompt-Versionierung =====
    suspend fun savePromptVersion(
        personaName: String,
        promptText: String,
        source: String = "manual_edit",
        isRollbackPoint: Boolean = false
    ) {
        if (promptText.isBlank()) return
        chatDao.insertPromptVersion(
            PersonaPromptVersionEntity(
                personaName = personaName,
                promptText = promptText,
                source = source,
                createdAt = System.currentTimeMillis(),
                isRollbackPoint = isRollbackPoint
            )
        )
    }

    suspend fun getPromptVersions(personaName: String, limit: Int = 20): List<PersonaPromptVersionEntity> =
        chatDao.getPromptVersionsForPersona(personaName, limit)

    suspend fun getPromptVersionById(id: Long): PersonaPromptVersionEntity? =
        chatDao.getPromptVersionById(id)

    suspend fun getLatestPromptVersion(personaName: String): PersonaPromptVersionEntity? =
        chatDao.getLatestPromptVersionForPersona(personaName)

    // ===== User Memory Facts =====
    suspend fun saveUserMemoryFact(
        personaName: String,
        factText: String,
        confidence: Float = 0.65f,
        sourceMessageId: String? = null,
        keepLatest: Int = 60
    ) {
        if (factText.isBlank()) return
        val now = System.currentTimeMillis()
        chatDao.insertUserMemoryFact(
            UserMemoryFactEntity(
                personaName = personaName,
                factText = factText.trim(),
                confidence = confidence.coerceIn(0f, 1f),
                sourceMessageId = sourceMessageId,
                createdAt = now,
                updatedAt = now
            )
        )
        chatDao.trimUserMemoryFacts(personaName, keepLatest)
    }

    suspend fun getUserMemoryFacts(personaName: String, limit: Int = 8): List<UserMemoryFactEntity> =
        chatDao.getUserMemoryFactsForPersona(personaName, limit)

    // ===== RAG: Knowledge Chunks =====
    suspend fun saveKnowledgeChunk(
        sourceTitle: String,
        content: String,
        keywords: String,
        sourceType: String = "text"
    ) {
        if (content.isBlank()) return
        chatDao.insertKnowledgeChunk(
            KnowledgeChunkEntity(
                sourceTitle = sourceTitle.ifBlank { "Unbekannt" },
                content = content.trim(),
                keywords = keywords.trim(),
                sourceType = sourceType,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun searchKnowledge(queryToken: String, limit: Int = 5): List<KnowledgeChunkEntity> {
        val token = queryToken.trim().lowercase()
        if (token.isBlank()) return emptyList()
        return chatDao.searchKnowledgeChunks("%$token%", limit)
    }

    // ===== Knowledge Graph =====
    suspend fun saveKnowledgeEdge(fromConcept: String, relation: String, toConcept: String, weight: Float = 1f) {
        if (fromConcept.isBlank() || relation.isBlank() || toConcept.isBlank()) return
        chatDao.insertKnowledgeEdge(
            KnowledgeEdgeEntity(
                fromConcept = fromConcept.trim(),
                relation = relation.trim(),
                toConcept = toConcept.trim(),
                weight = weight,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getKnowledgeEdges(limit: Int = 12): List<KnowledgeEdgeEntity> =
        chatDao.getKnowledgeEdges(limit)

    // ===== Persona Training =====
    suspend fun savePersonaTrainingExample(
        personaName: String,
        userInput: String,
        idealResponse: String,
        source: String = "manual",
        keepLatest: Int = 100
    ) {
        if (userInput.isBlank() || idealResponse.isBlank()) return
        val now = System.currentTimeMillis()
        chatDao.insertPersonaTrainingExample(
            PersonaTrainingExampleEntity(
                personaName = personaName,
                userInput = userInput.trim(),
                idealResponse = idealResponse.trim(),
                source = source,
                createdAt = now,
                updatedAt = now
            )
        )
        chatDao.trimTrainingExamples(personaName, keepLatest)
    }

    suspend fun getPersonaTrainingExamples(personaName: String, limit: Int = 6): List<PersonaTrainingExampleEntity> =
        chatDao.getTrainingExamplesForPersona(personaName, limit)

    private fun stringifySources(sources: List<ChatSource>): String? {
        if (sources.isEmpty()) return null
        return runCatching { gson.toJson(sources) }.getOrNull()
    }

    private fun parseSources(raw: String?): List<ChatSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<ChatSource>>() {}.type
            gson.fromJson<List<ChatSource>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
