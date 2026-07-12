package com.example.bamachat.data.cloud

import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.util.AppTelemetry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatCloudSyncGateway(
    firestore: FirebaseFirestore? = null
) {
    private val firestore: FirebaseFirestore? = firestore
        ?: runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun userConversationsCollection(uid: String) =
        firestore?.collection("users")?.document(uid)
            ?.collection("chat_conversations")

    private fun userMessagesCollection(uid: String, conversationId: String) =
        userConversationsCollection(uid)?.document(conversationId)
            ?.collection("messages")

    suspend fun pushConversation(
        uid: String,
        conversation: ConversationEntity,
        workspaceName: String? = null,
        lastMessagePreview: String = ""
    ): Boolean {
        val db = firestore ?: return false
        if (uid.isBlank()) return false
        return try {
            val cloud = conversation.toCloudConversation(
                workspaceName = workspaceName,
                lastMessagePreview = lastMessagePreview
            )
            userConversationsCollection(uid)
                ?.document(conversation.id)
                ?.set(cloud.toFirestoreMap(), SetOptions.merge())
                ?.await()
            AppTelemetry.logEvent(
                "cloud_sync_push_conversation",
                "conversationId" to conversation.id
            )
            true
        } catch (e: Exception) {
            AppTelemetry.logError("cloud_sync_push_conversation_failed", e)
            false
        }
    }

    suspend fun pushMessage(
        uid: String,
        conversationId: String,
        message: ChatMessageEntity
    ): Boolean {
        val db = firestore ?: return false
        if (uid.isBlank()) return false
        return try {
            val cloud = message.toCloudMessage()
            userMessagesCollection(uid, conversationId)
                ?.document(message.id)
                ?.set(cloud.toFirestoreMap(), SetOptions.merge())
                ?.await()
            AppTelemetry.logEvent(
                "cloud_sync_push_message",
                "conversationId" to conversationId,
                "messageId" to message.id
            )
            true
        } catch (e: Exception) {
            AppTelemetry.logError("cloud_sync_push_message_failed", e)
            false
        }
    }

    suspend fun softDeleteConversation(
        uid: String,
        conversationId: String
    ): Boolean {
        val db = firestore ?: return false
        if (uid.isBlank()) return false
        return try {
            userConversationsCollection(uid)
                ?.document(conversationId)
                ?.set(
                    mapOf(
                        "deleted" to true,
                        "lastModifiedBy" to "android"
                    ),
                    SetOptions.merge()
                )
                ?.await()
            AppTelemetry.logEvent(
                "cloud_sync_soft_delete_conversation",
                "conversationId" to conversationId
            )
            true
        } catch (e: Exception) {
            AppTelemetry.logError("cloud_sync_soft_delete_conversation_failed", e)
            false
        }
    }

    fun pushConversationAsync(
        uid: String,
        conversation: ConversationEntity,
        workspaceName: String? = null,
        lastMessagePreview: String = ""
    ) {
        scope.launch {
            pushConversation(uid, conversation, workspaceName, lastMessagePreview)
        }
    }

    fun pushMessageAsync(
        uid: String,
        conversationId: String,
        message: ChatMessageEntity
    ) {
        scope.launch {
            pushMessage(uid, conversationId, message)
        }
    }

    fun softDeleteConversationAsync(
        uid: String,
        conversationId: String
    ) {
        scope.launch {
            softDeleteConversation(uid, conversationId)
        }
    }
}
