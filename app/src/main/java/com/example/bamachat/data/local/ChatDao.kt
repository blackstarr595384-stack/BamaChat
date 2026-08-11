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

data class ScopedChatCleanupResult(
    val conversationIds: List<String>,
    val deletedMessages: Int,
    val deletedConversations: Int,
    val deletedFtsRows: Int,
    val deletedLinkedReferences: Int
)

@Dao
interface ChatDao {
    // ===== Conversations =====
    @Query("SELECT * FROM conversations WHERE ownerScope = :ownerScope ORDER BY updatedAt DESC")
    fun getConversationsForScope(ownerScope: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id AND ownerScope = :ownerScope LIMIT 1")
    suspend fun getConversationByIdAndScope(id: String, ownerScope: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id AND ownerScope = :ownerScope")
    suspend fun renameConversation(id: String, ownerScope: String, title: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id AND ownerScope = :ownerScope")
    suspend fun touchConversation(id: String, ownerScope: String, updatedAt: Long): Int

    @Query("UPDATE conversations SET personaName = :personaName WHERE id = :id AND ownerScope = :ownerScope")
    suspend fun updateConversationPersonaName(id: String, ownerScope: String, personaName: String): Int

    @Query("DELETE FROM conversations WHERE id = :id AND ownerScope = :ownerScope")
    suspend fun deleteConversation(id: String, ownerScope: String): Int

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    // ===== Messages =====
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId AND ownerScope = :ownerScope ORDER BY timestamp ASC")
    fun getMessagesForConversationAndScope(conversationId: String, ownerScope: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId AND ownerScope = :ownerScope LIMIT 1")
    suspend fun getMessageByIdAndScope(messageId: String, ownerScope: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :messageId AND ownerScope = :ownerScope")
    suspend fun deleteMessage(messageId: String, ownerScope: String): Int

    // ===== FTS4 Message Search =====
    @Query("SELECT f.rowid, f.message_id, f.conversation_id, f.text, f.is_user, f.timestamp, snippet(chat_messages_fts, '<b>', '</b>', '...', -1, 20) AS snippet FROM chat_messages_fts AS f INNER JOIN chat_messages AS m ON m.id = f.message_id AND m.conversationId = f.conversation_id WHERE chat_messages_fts MATCH :query AND m.ownerScope = :ownerScope ORDER BY f.rowid DESC LIMIT :limit")
    suspend fun searchMessagesFtsForScope(query: String, ownerScope: String, limit: Int = 30): List<MessageFtsResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageFts(fts: ChatMessageFtsEntity)

    @Query("DELETE FROM chat_messages_fts WHERE message_id = :messageId")
    suspend fun deleteMessageFts(messageId: String)

    @Query("DELETE FROM chat_messages_fts WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesFtsForConversation(conversationId: String)

    @Query("DELETE FROM chat_messages_fts")
    suspend fun deleteAllMessagesFts()

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId AND ownerScope = :ownerScope")
    suspend fun deleteMessagesForConversation(conversationId: String, ownerScope: String): Int

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("SELECT id FROM conversations WHERE ownerScope = :ownerScope")
    suspend fun getConversationIdsForScope(ownerScope: String): List<String>

    @Query("DELETE FROM chat_messages_fts WHERE conversation_id IN (SELECT id FROM conversations WHERE ownerScope = :ownerScope)")
    suspend fun deleteMessageFtsForScope(ownerScope: String): Int

    @Query("DELETE FROM chat_messages WHERE ownerScope = :ownerScope")
    suspend fun deleteMessagesForScope(ownerScope: String): Int

    @Query("DELETE FROM conversations WHERE ownerScope = :ownerScope")
    suspend fun deleteConversationsForScope(ownerScope: String): Int

    @Query("DELETE FROM persona_memory WHERE sourceMessageId IN (SELECT id FROM chat_messages WHERE ownerScope = :ownerScope)")
    suspend fun deletePersonaMemoryForMessageScope(ownerScope: String): Int

    @Query("DELETE FROM persona_feedback WHERE messageId IN (SELECT id FROM chat_messages WHERE ownerScope = :ownerScope)")
    suspend fun deletePersonaFeedbackForMessageScope(ownerScope: String): Int

    @Query("DELETE FROM user_memory_facts WHERE sourceMessageId IN (SELECT id FROM chat_messages WHERE ownerScope = :ownerScope)")
    suspend fun deleteUserMemoryFactsForMessageScope(ownerScope: String): Int

    @Transaction
    suspend fun deleteChatDataForScope(ownerScope: String): ScopedChatCleanupResult {
        require(ChatOwnerScope.isGuest(ownerScope)) { "Only guest scopes can be selectively cleared" }
        val conversationIds = getConversationIdsForScope(ownerScope)
        val deletedLinkedReferences =
            deletePersonaMemoryForMessageScope(ownerScope) +
                deletePersonaFeedbackForMessageScope(ownerScope) +
                deleteUserMemoryFactsForMessageScope(ownerScope)
        val deletedFtsRows = deleteMessageFtsForScope(ownerScope)
        val deletedMessages = deleteMessagesForScope(ownerScope)
        val deletedConversations = deleteConversationsForScope(ownerScope)
        return ScopedChatCleanupResult(
            conversationIds = conversationIds,
            deletedMessages = deletedMessages,
            deletedConversations = deletedConversations,
            deletedFtsRows = deletedFtsRows,
            deletedLinkedReferences = deletedLinkedReferences
        )
    }

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
