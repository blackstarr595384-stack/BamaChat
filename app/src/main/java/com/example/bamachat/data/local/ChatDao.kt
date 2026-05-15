package com.example.bamachat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class MessageFtsResult(
    val rowid: Long,
    val message_id: String,
    val conversation_id: String,
    val text: String,
    val is_user: Boolean,
    val timestamp: Long,
    val snippet: String
)

data class PersonaFeedbackStats(
    val helpfulCount: Int,
    val unhelpfulCount: Int
)

@Dao
interface ChatDao {
    // ===== Conversations =====
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    // ===== Messages =====
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    // ===== FTS4 Message Search =====
    @Query("SELECT rowid, message_id, conversation_id, text, is_user, timestamp, snippet(chat_messages_fts, '<b>', '</b>', '...', -1, 20) AS snippet FROM chat_messages_fts WHERE chat_messages_fts MATCH :query ORDER BY rank LIMIT :limit")
    suspend fun searchMessagesFts(query: String, limit: Int = 30): List<MessageFtsResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageFts(fts: ChatMessageFtsEntity)

    @Query("DELETE FROM chat_messages_fts WHERE message_id = :messageId")
    suspend fun deleteMessageFts(messageId: String)

    @Query("DELETE FROM chat_messages_fts WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesFtsForConversation(conversationId: String)

    @Query("DELETE FROM chat_messages_fts")
    suspend fun deleteAllMessagesFts()

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM persona_memory")
    suspend fun deleteAllPersonaMemory()

    @Query("DELETE FROM persona_feedback")
    suspend fun deleteAllPersonaFeedback()

    @Query("DELETE FROM persona_prompt_versions")
    suspend fun deleteAllPromptVersions()

    @Query("DELETE FROM user_memory_facts")
    suspend fun deleteAllUserMemoryFacts()

    @Query("DELETE FROM knowledge_chunks")
    suspend fun deleteAllKnowledgeChunks()

    @Query("DELETE FROM knowledge_edges")
    suspend fun deleteAllKnowledgeEdges()

    @Query("DELETE FROM persona_training_examples")
    suspend fun deleteAllPersonaTrainingExamples()

    // ===== Persona Memory =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonaMemory(memory: PersonaMemoryEntity)

    @Query(
        "SELECT * FROM persona_memory " +
            "WHERE personaName = :personaName " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun getRecentMemoryForPersona(personaName: String, limit: Int): List<PersonaMemoryEntity>

    @Query(
        "DELETE FROM persona_memory " +
            "WHERE personaName = :personaName AND id NOT IN (" +
            "SELECT id FROM persona_memory WHERE personaName = :personaName ORDER BY updatedAt DESC LIMIT :keep" +
            ")"
    )
    suspend fun trimPersonaMemory(personaName: String, keep: Int)

    // ===== Persona Feedback =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonaFeedback(feedback: PersonaFeedbackEntity)

    @Query("SELECT helpful FROM persona_feedback WHERE messageId = :messageId LIMIT 1")
    suspend fun getFeedbackForMessage(messageId: String): Boolean?

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN helpful = 1 THEN 1 ELSE 0 END), 0) AS helpfulCount, " +
            "COALESCE(SUM(CASE WHEN helpful = 0 THEN 1 ELSE 0 END), 0) AS unhelpfulCount " +
            "FROM persona_feedback WHERE personaName = :personaName"
    )
    suspend fun getFeedbackStatsForPersona(personaName: String): PersonaFeedbackStats

    // ===== Prompt Versionen =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromptVersion(version: PersonaPromptVersionEntity)

    @Query(
        "SELECT * FROM persona_prompt_versions " +
            "WHERE personaName = :personaName " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun getPromptVersionsForPersona(personaName: String, limit: Int): List<PersonaPromptVersionEntity>

    @Query("SELECT * FROM persona_prompt_versions WHERE id = :id LIMIT 1")
    suspend fun getPromptVersionById(id: Long): PersonaPromptVersionEntity?

    @Query(
        "SELECT * FROM persona_prompt_versions " +
            "WHERE personaName = :personaName ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatestPromptVersionForPersona(personaName: String): PersonaPromptVersionEntity?

    // ===== Persistent User Memory =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserMemoryFact(fact: UserMemoryFactEntity)

    @Query(
        "SELECT * FROM user_memory_facts " +
            "WHERE personaName IN (:personaName, 'GLOBAL') " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun getUserMemoryFactsForPersona(personaName: String, limit: Int): List<UserMemoryFactEntity>

    @Query(
        "DELETE FROM user_memory_facts " +
            "WHERE personaName = :personaName AND id NOT IN (" +
            "SELECT id FROM user_memory_facts WHERE personaName = :personaName ORDER BY updatedAt DESC LIMIT :keep" +
            ")"
    )
    suspend fun trimUserMemoryFacts(personaName: String, keep: Int)

    // ===== Local RAG / Knowledge Chunks =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeChunk(chunk: KnowledgeChunkEntity)

    @Query(
        "SELECT * FROM knowledge_chunks " +
            "WHERE lower(content) LIKE :query OR lower(keywords) LIKE :query OR lower(sourceTitle) LIKE :query " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun searchKnowledgeChunks(query: String, limit: Int): List<KnowledgeChunkEntity>

    // ===== Knowledge Graph =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeEdge(edge: KnowledgeEdgeEntity)

    @Query("SELECT * FROM knowledge_edges ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getKnowledgeEdges(limit: Int): List<KnowledgeEdgeEntity>

    // ===== Persona Training (Feature 5) =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonaTrainingExample(example: PersonaTrainingExampleEntity)

    @Query(
        "SELECT * FROM persona_training_examples " +
            "WHERE personaName = :personaName AND enabled = 1 " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun getTrainingExamplesForPersona(personaName: String, limit: Int): List<PersonaTrainingExampleEntity>

    @Query(
        "DELETE FROM persona_training_examples " +
            "WHERE personaName = :personaName AND id NOT IN (" +
            "SELECT id FROM persona_training_examples WHERE personaName = :personaName ORDER BY updatedAt DESC LIMIT :keep" +
            ")"
    )
    suspend fun trimTrainingExamples(personaName: String, keep: Int)
}
