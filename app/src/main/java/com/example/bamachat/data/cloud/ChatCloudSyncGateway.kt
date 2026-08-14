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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

internal fun canUploadConversation(uid: String, conversation: ConversationEntity): Boolean =
    uid.isNotBlank() && ChatOwnerScope.isAccountForUid(conversation.ownerScope, uid)

internal fun canUploadMessage(uid: String, message: ChatMessageEntity): Boolean =
    uid.isNotBlank() && ChatOwnerScope.isAccountForUid(message.ownerScope, uid)

internal interface ChatCloudWriter {
    suspend fun pushConversation(uid: String, conversation: CloudConversation)
    suspend fun pushMessage(uid: String, conversationId: String, message: CloudMessage)
    suspend fun softDeleteConversation(uid: String, conversationId: String)
}

internal class FirestoreChatCloudWriter(
    private val firestore: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
) : ChatCloudWriter {
    override suspend fun pushConversation(uid: String, conversation: CloudConversation) {
        val db = firestore ?: error("Cloud-Synchronisierung ist nicht verfügbar.")
        db.collection("users").document(uid)
            .collection("chat_conversations").document(conversation.id)
            .set(conversation.toFirestoreMap(), SetOptions.merge())
            .await()
    }

    override suspend fun pushMessage(uid: String, conversationId: String, message: CloudMessage) {
        val db = firestore ?: error("Cloud-Synchronisierung ist nicht verfügbar.")
        db.collection("users").document(uid)
            .collection("chat_conversations").document(conversationId)
            .collection("messages").document(message.id)
            .set(message.toFirestoreMap(), SetOptions.merge())
            .await()
    }

    override suspend fun softDeleteConversation(uid: String, conversationId: String) {
        val db = firestore ?: error("Cloud-Synchronisierung ist nicht verfügbar.")
        db.collection("users").document(uid)
            .collection("chat_conversations").document(conversationId)
            .set(
                mapOf("deleted" to true, "lastModifiedBy" to "android"),
                SetOptions.merge()
            )
            .await()
    }
}

class ChatCloudSyncGateway internal constructor(
    private val scopeStore: ChatSessionScopeStore,
    private val uidProvider: AuthenticatedUidProvider,
    private val cloudOperationGate: AccountCloudOperationGate,
    private val writer: ChatCloudWriter,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    internal suspend fun pushConversation(
        uid: String,
        conversation: ConversationEntity,
        workspaceName: String? = null,
        lastMessagePreview: String = ""
    ): Boolean = runCloudWrite(
        event = "cloud_sync_push_conversation",
        failureEvent = "cloud_sync_push_conversation_failed",
        uid = uid,
        ownerScope = conversation.ownerScope,
        entityAllowed = canUploadConversation(uid, conversation)
    ) {
        writer.pushConversation(
            uid,
            conversation.toCloudConversation(workspaceName, lastMessagePreview)
        )
    }

    internal suspend fun pushMessage(
        uid: String,
        conversationId: String,
        message: ChatMessageEntity
    ): Boolean = runCloudWrite(
        event = "cloud_sync_push_message",
        failureEvent = "cloud_sync_push_message_failed",
        uid = uid,
        ownerScope = message.ownerScope,
        entityAllowed = canUploadMessage(uid, message)
    ) {
        writer.pushMessage(uid, conversationId, message.toCloudMessage())
    }

    internal suspend fun softDeleteConversation(
        uid: String,
        conversationId: String,
        ownerScope: String
    ): Boolean = runCloudWrite(
        event = "cloud_sync_soft_delete_conversation",
        failureEvent = "cloud_sync_soft_delete_conversation_failed",
        uid = uid,
        ownerScope = ownerScope,
        entityAllowed = ChatOwnerScope.isAccountForUid(ownerScope, uid)
    ) {
        writer.softDeleteConversation(uid, conversationId)
    }

    internal fun pushConversationAsync(
        uid: String,
        conversation: ConversationEntity,
        workspaceName: String? = null,
        lastMessagePreview: String = ""
    ): Job = coroutineScope.launch {
        pushConversation(uid, conversation, workspaceName, lastMessagePreview)
    }

    internal fun pushMessageAsync(
        uid: String,
        conversationId: String,
        message: ChatMessageEntity
    ): Job = coroutineScope.launch {
        pushMessage(uid, conversationId, message)
    }

    internal fun softDeleteConversationAsync(
        uid: String,
        conversationId: String,
        ownerScope: String
    ): Job = coroutineScope.launch {
        softDeleteConversation(uid, conversationId, ownerScope)
    }

    internal fun isDirectUploadAllowed(uid: String, ownerScope: String): Boolean {
        val authenticatedUid = uidProvider.currentUid()?.trim().orEmpty()
        if (authenticatedUid.isBlank() || authenticatedUid != uid.trim()) return false
        if (!scopeStore.isCloudSyncAllowed(authenticatedUid)) return false
        return ChatOwnerScope.isAccountForUid(ownerScope, authenticatedUid)
    }

    private suspend fun runCloudWrite(
        event: String,
        failureEvent: String,
        uid: String,
        ownerScope: String,
        entityAllowed: Boolean,
        operation: suspend () -> Unit
    ): Boolean = cloudOperationGate.withCloudOperation {
        if (!entityAllowed || !isDirectUploadAllowed(uid, ownerScope)) return@withCloudOperation false
        try {
            operation()
            AppTelemetry.logEvent(event)
            true
        } catch (error: Exception) {
            AppTelemetry.logError(failureEvent, error)
            false
        }
    }
}
