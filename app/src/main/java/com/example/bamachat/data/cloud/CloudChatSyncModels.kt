package com.example.bamachat.data.cloud

import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ConversationPersonaMetadata

private const val ANDROID_SOURCE = "android"
private const val MAX_PREVIEW_LENGTH = 120

data class CloudConversation(
    val id: String = "",
    val title: String = "",
    val personaName: String = ConversationPersonaMetadata.DEFAULT_PERSONA_DISPLAY_NAME,
    val workspaceName: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastMessagePreview: String = "",
    val lastModifiedBy: String = ANDROID_SOURCE,
    val deleted: Boolean = false,
    val version: Int = 1
)

data class CloudMessage(
    val id: String = "",
    val conversationId: String = "",
    val text: String = "",
    val isUser: Boolean = true,
    val timestamp: Long = 0L,
    val role: String = "USER",
    val imageUrl: String? = null,
    val lastModifiedBy: String = ANDROID_SOURCE,
    val deleted: Boolean = false,
    val version: Int = 1
)

fun ConversationEntity.toCloudConversation(
    workspaceName: String? = null,
    lastMessagePreview: String = ""
): CloudConversation = CloudConversation(
    id = id,
    title = title,
    personaName = personaName,
    workspaceName = workspaceName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessagePreview = lastMessagePreview.take(MAX_PREVIEW_LENGTH),
    lastModifiedBy = ANDROID_SOURCE,
    deleted = false,
    version = 1
)

fun ChatMessageEntity.toCloudMessage(): CloudMessage = CloudMessage(
    id = id,
    conversationId = conversationId,
    text = text,
    isUser = isUser,
    timestamp = timestamp,
    role = if (isUser) "USER" else "ASSISTANT",
    imageUrl = imageUrl,
    lastModifiedBy = ANDROID_SOURCE,
    deleted = false,
    version = 1
)

fun CloudConversation.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "personaName" to personaName,
    "workspaceName" to workspaceName,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "lastMessagePreview" to lastMessagePreview,
    "lastModifiedBy" to lastModifiedBy,
    "deleted" to deleted,
    "version" to version
)

fun CloudMessage.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "conversationId" to conversationId,
    "text" to text,
    "isUser" to isUser,
    "timestamp" to timestamp,
    "role" to role,
    "imageUrl" to imageUrl,
    "lastModifiedBy" to lastModifiedBy,
    "deleted" to deleted,
    "version" to version
)
