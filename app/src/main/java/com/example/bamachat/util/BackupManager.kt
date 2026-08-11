package com.example.bamachat.util

import com.example.bamachat.data.cloud.AuthenticatedUidProvider
import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ChatSessionScopeStore
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.data.repository.ScopedConversationSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class AccountChatBackup(
    val conversation: ConversationEntity,
    val messages: List<ChatMessageEntity>
)

interface ChatBackupCloudStore {
    suspend fun write(uid: String, backup: AccountChatBackup): String
    suspend fun read(uid: String, backupId: String): AccountChatBackup?
}

@Singleton
class FirestoreChatBackupCloudStore @Inject constructor() : ChatBackupCloudStore {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override suspend fun write(uid: String, backup: AccountChatBackup): String {
        val payload = mapOf(
            "conversation" to backup.conversation.toBackupMap(),
            "messages" to backup.messages.map { it.toBackupMap() },
            "backupDate" to Date()
        )
        return firestore.collection("users")
            .document(uid)
            .collection("chat_backups")
            .add(payload)
            .await()
            .id
    }

    override suspend fun read(uid: String, backupId: String): AccountChatBackup? {
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("chat_backups")
            .document(backupId)
            .get()
            .await()
        if (!snapshot.exists()) return null
        val conversation = (snapshot.get("conversation") as? Map<*, *>)?.toConversationEntity() ?: return null
        val messages = (snapshot.get("messages") as? List<*>)
            .orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.toMessageEntity() }
        return AccountChatBackup(conversation, messages)
    }

    private fun ConversationEntity.toBackupMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "personaName" to personaName,
        "ownerScope" to ownerScope
    )

    private fun ChatMessageEntity.toBackupMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "conversationId" to conversationId,
        "text" to text,
        "isUser" to isUser,
        "timestamp" to timestamp,
        "imageUrl" to imageUrl,
        "sourcesJson" to sourcesJson,
        "webFetchedAtIso" to webFetchedAtIso,
        "ownerScope" to ownerScope
    )

    private fun Map<*, *>.toConversationEntity(): ConversationEntity? {
        val id = this["id"] as? String ?: return null
        val ownerScope = this["ownerScope"] as? String ?: return null
        return ConversationEntity(
            id = id,
            title = this["title"] as? String ?: return null,
            createdAt = (this["createdAt"] as? Number)?.toLong() ?: return null,
            updatedAt = (this["updatedAt"] as? Number)?.toLong() ?: return null,
            personaName = this["personaName"] as? String ?: "ASSISTANT",
            ownerScope = ownerScope
        )
    }

    private fun Map<*, *>.toMessageEntity(): ChatMessageEntity? {
        val id = this["id"] as? String ?: return null
        val conversationId = this["conversationId"] as? String ?: return null
        val ownerScope = this["ownerScope"] as? String ?: return null
        return ChatMessageEntity(
            id = id,
            conversationId = conversationId,
            text = this["text"] as? String ?: return null,
            isUser = this["isUser"] as? Boolean ?: return null,
            timestamp = (this["timestamp"] as? Number)?.toLong() ?: return null,
            imageUrl = this["imageUrl"] as? String,
            sourcesJson = this["sourcesJson"] as? String,
            webFetchedAtIso = this["webFetchedAtIso"] as? String,
            ownerScope = ownerScope
        )
    }
}

@Singleton
class BackupManager @Inject constructor(
    private val repository: ChatRepository,
    private val scopeStore: ChatSessionScopeStore,
    private val uidProvider: AuthenticatedUidProvider,
    private val cloudStore: ChatBackupCloudStore
) {
    suspend fun backupActiveAccountConversation(conversationId: String): Result<String> = runCatching {
        val session = requireActiveAccountSession()
        val snapshot = repository.getAccountBackupSnapshot(conversationId, session.ownerScope)
        validateSnapshot(snapshot, session.ownerScope)
        requireActiveAccountSession(session.uid, session.ownerScope)
        cloudStore.write(session.uid, AccountChatBackup(snapshot.conversation, snapshot.messages))
    }.onFailure { AppTelemetry.logError("backup_to_cloud_failed", it) }

    suspend fun restoreActiveAccountBackup(backupId: String): Result<ScopedConversationSnapshot> = runCatching {
        val session = requireActiveAccountSession()
        val backup = cloudStore.read(session.uid, backupId) ?: error("Backup wurde nicht gefunden.")
        val snapshot = ScopedConversationSnapshot(backup.conversation, backup.messages)
        validateSnapshot(snapshot, session.ownerScope)
        requireActiveAccountSession(session.uid, session.ownerScope)
        repository.restoreAccountBackup(snapshot, session.ownerScope)
        snapshot
    }.onFailure { AppTelemetry.logError("restore_from_cloud_failed", it) }

    private fun validateSnapshot(snapshot: ScopedConversationSnapshot, ownerScope: String) {
        require(snapshot.conversation.ownerScope == ownerScope) { "Backup conversation owner mismatch" }
        require(snapshot.messages.all { it.ownerScope == ownerScope }) { "Backup message owner mismatch" }
        require(snapshot.messages.all { it.conversationId == snapshot.conversation.id }) {
            "Backup message conversation mismatch"
        }
    }

    private fun requireActiveAccountSession(
        expectedUid: String? = null,
        expectedScope: String? = null
    ): ActiveAccountSession {
        val uid = uidProvider.currentUid()?.trim()?.takeIf { it.isNotBlank() }
            ?: error("Kein authentifiziertes Konto aktiv.")
        val ownerScope = scopeStore.currentScope()
        check(scopeStore.isCloudSyncAllowed(uid)) { "Backup ist während eines Kontoübergangs gesperrt." }
        check(ChatOwnerScope.isAccountForUid(ownerScope, uid)) { "Backup benötigt den aktiven Account-Scope." }
        if (expectedUid != null) check(uid == expectedUid) { "Konto hat sich während des Backups geändert." }
        if (expectedScope != null) check(ownerScope == expectedScope) { "Scope hat sich während des Backups geändert." }
        return ActiveAccountSession(uid, ownerScope)
    }

    private data class ActiveAccountSession(val uid: String, val ownerScope: String)
}
