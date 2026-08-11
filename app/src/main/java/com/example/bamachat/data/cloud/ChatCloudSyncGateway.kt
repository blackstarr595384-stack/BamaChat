package com.example.bamachat.data.cloud

import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ChatSessionScopeStore
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.util.AppTelemetry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

internal fun canUploadConversation(uid: String, conversation: ConversationEntity): Boolean =
    uid.isNotBlank() && ChatOwnerScope.isAccountForUid(conversation.ownerScope, uid)

internal fun canUploadMessage(uid: String, message: ChatMessageEntity): Boolean =
    uid.isNotBlank() && ChatOwnerScope.isAccountForUid(message.ownerScope, uid)

class ChatCloudSyncGateway(
    private val scopeStore: ChatSessionScopeStore,
    private val uidProvider: AuthenticatedUidProvider,
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

    internal suspend fun pushConversation(
        uid: String,
        conversation: ConversationEntity,
        workspaceName: String? = null,
        lastMessagePreview: String = ""
    ): Boolean {
        if (!isDirectUploadAllowed(uid, conversation.ownerScope) || !canUploadConversation(uid, conversation)) {
            return false
        }
        val db = firestore ?: return false
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

    internal suspend fun pushMessage(
        uid: String,
        conversationId: String,
        message: ChatMessageEntity
    ): Boolean {
        if (!isDirectUploadAllowed(uid, message.ownerScope) || !canUploadMessage(uid, message)) return false
        val db = firestore ?: return false
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

    internal suspend fun softDeleteConversation(
        uid: String,
        conversationId: String,
        ownerScope: String
    ): Boolean {
        val db = firestore ?: return false
        if (!isDirectUploadAllowed(uid, ownerScope)) return false
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

    internal fun pushConversationAsync(
        uid: String,
        conversation: ConversationEntity,
        workspaceName: String? = null,
        lastMessagePreview: String = ""
    ) {
        scope.launch {
            pushConversation(uid, conversation, workspaceName, lastMessagePreview)
        }
    }

    internal fun pushMessageAsync(
        uid: String,
        conversationId: String,
        message: ChatMessageEntity
    ) {
        scope.launch {
            pushMessage(uid, conversationId, message)
        }
    }

    internal fun softDeleteConversationAsync(
        uid: String,
        conversationId: String,
        ownerScope: String
    ) {
        scope.launch {
            softDeleteConversation(uid, conversationId, ownerScope)
        }
    }

    internal fun isDirectUploadAllowed(uid: String, ownerScope: String): Boolean {
        val authenticatedUid = uidProvider.currentUid()?.trim().orEmpty()
        if (authenticatedUid.isBlank() || authenticatedUid != uid.trim()) return false
        if (!scopeStore.isCloudSyncAllowed(authenticatedUid)) return false
        return ChatOwnerScope.isAccountForUid(ownerScope, authenticatedUid)
    }
}
