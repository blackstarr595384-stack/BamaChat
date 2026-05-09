package com.example.bamachat.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val personaName: String = "ASSISTANT"
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val imageUrl: String? = null,
    val sourcesJson: String? = null,
    val webFetchedAtIso: String? = null
)

@Entity(
    tableName = "persona_memory",
    indices = [Index(value = ["personaName"]), Index(value = ["updatedAt"])]
)
data class PersonaMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaName: String,
    val memoryText: String,
    val sourceMessageId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "persona_feedback",
    indices = [Index(value = ["personaName"]), Index(value = ["messageId"], unique = true)]
)
data class PersonaFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaName: String,
    val messageId: String,
    val helpful: Boolean,
    val note: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "persona_prompt_versions",
    indices = [Index(value = ["personaName"]), Index(value = ["createdAt"])]
)
data class PersonaPromptVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaName: String,
    val promptText: String,
    val source: String = "manual_edit",
    val createdAt: Long,
    val isRollbackPoint: Boolean = false
)

@Entity(
    tableName = "user_memory_facts",
    indices = [Index(value = ["personaName"]), Index(value = ["updatedAt"])]
)
data class UserMemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaName: String,
    val factText: String,
    val confidence: Float = 0.6f,
    val sourceMessageId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "knowledge_chunks",
    indices = [Index(value = ["sourceTitle"]), Index(value = ["createdAt"])]
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceTitle: String,
    val content: String,
    val keywords: String,
    val sourceType: String = "text",
    val createdAt: Long
)

@Entity(
    tableName = "knowledge_edges",
    indices = [
        Index(value = ["fromConcept"]),
        Index(value = ["toConcept"]),
        Index(value = ["fromConcept", "relation", "toConcept"], unique = true)
    ]
)
data class KnowledgeEdgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromConcept: String,
    val relation: String,
    val toConcept: String,
    val weight: Float = 1.0f,
    val updatedAt: Long
)

@Entity(
    tableName = "persona_training_examples",
    indices = [Index(value = ["personaName"]), Index(value = ["updatedAt"])]
)
data class PersonaTrainingExampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaName: String,
    val userInput: String,
    val idealResponse: String,
    val source: String = "manual",
    val createdAt: Long,
    val updatedAt: Long,
    val enabled: Boolean = true
)
