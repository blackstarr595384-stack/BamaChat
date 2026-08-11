package com.example.bamachat.data.cloud

import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ConversationPersonaMetadata
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.service.ConversationService
import com.example.bamachat.util.AppTelemetry
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun isCloudSyncEligible(
    uid: String,
    ownerScope: String,
    policyEnabled: Boolean
): Boolean = policyEnabled && ChatOwnerScope.isAccountForUid(ownerScope, uid)

@Singleton
class AndroidChatSyncCoordinator @Inject constructor(
    private val policy: ChatSyncPolicy,
    private val repository: ChatRepository,
    private val conversationService: ConversationService,
    private val gateway: ChatCloudSyncGateway
) {
    private val _runtimeStatus = MutableStateFlow(ChatSyncRuntimeStatus())
    val runtimeStatus: StateFlow<ChatSyncRuntimeStatus> = _runtimeStatus.asStateFlow()

    fun isEnabledForUid(uid: String?): Boolean = policy.isEnabledForUid(uid)

    fun hasLegacyGlobalPreference(): Boolean = policy.hasLegacyGlobalPreference()

    fun setEnabledForUid(uid: String, enabled: Boolean) {
        policy.setEnabledForUid(uid, enabled)
        _runtimeStatus.value = ChatSyncRuntimeStatus(
            uid = uid,
            state = if (enabled) ChatSyncState.Active else ChatSyncState.LocalOnly
        )
        AppTelemetry.logEvent(
            if (enabled) "cloud_chat_sync_enabled" else "cloud_chat_sync_disabled"
        )
    }

    suspend fun syncMessageAfterLocalSave(
        conversationId: String,
        message: ChatMessage,
        activePersonaName: String?,
        ownerScope: String
    ): SyncResult {
        val uid = currentUidOrNull() ?: return SyncResult.Disabled
        if (!isCloudSyncEligible(uid, ownerScope, policy.isEnabledForUid(uid))) return SyncResult.Disabled
        _runtimeStatus.value = ChatSyncRuntimeStatus(uid, ChatSyncState.Pending)

        val conversation = repository.getConversation(conversationId, ownerScope) ?: return SyncResult.Pending
        val cloudConversation = conversation.withResolvedPersona(activePersonaName, ownerScope)
        val messageEntity = message.toEntity(conversationId, ownerScope)
        val preview = safePreview(message.text)

        val conversationPushed = gateway.pushConversation(
            uid = uid,
            conversation = cloudConversation,
            workspaceName = resolveWorkspaceName(cloudConversation.id, cloudConversation.title),
            lastMessagePreview = preview
        )
        val messagePushed = if (conversationPushed) {
            gateway.pushMessage(uid, conversationId, messageEntity)
        } else {
            false
        }

        return if (conversationPushed && messagePushed) {
            _runtimeStatus.value = ChatSyncRuntimeStatus(uid, ChatSyncState.Success)
            AppTelemetry.logEvent(
                "cloud_chat_message_sync_success",
                "messageId" to message.id,
                "conversationId" to conversationId
            )
            SyncResult.Success
        } else {
            _runtimeStatus.value = ChatSyncRuntimeStatus(uid, ChatSyncState.Failed)
            AppTelemetry.logEvent(
                "cloud_chat_message_sync_failure",
                "messageId" to message.id,
                "conversationId" to conversationId
            )
            SyncResult.Failed
        }
    }

    suspend fun syncConversationMetadataAfterLocalChange(
        conversationId: String,
        activePersonaName: String?,
        ownerScope: String,
        lastMessagePreview: String? = null
    ): SyncResult {
        val uid = currentUidOrNull() ?: return SyncResult.Disabled
        if (!isCloudSyncEligible(uid, ownerScope, policy.isEnabledForUid(uid))) return SyncResult.Disabled
        _runtimeStatus.value = ChatSyncRuntimeStatus(uid, ChatSyncState.Pending)

        val conversation = repository.getConversation(conversationId, ownerScope) ?: return SyncResult.Pending
        val cloudConversation = conversation.withResolvedPersona(activePersonaName, ownerScope)
        val preview = lastMessagePreview
            ?: repository.getMessages(conversationId, ownerScope).first().lastOrNull()?.text.orEmpty()
        val success = gateway.pushConversation(
            uid = uid,
            conversation = cloudConversation,
            workspaceName = resolveWorkspaceName(cloudConversation.id, cloudConversation.title),
            lastMessagePreview = safePreview(preview)
        )
        _runtimeStatus.value = ChatSyncRuntimeStatus(
            uid,
            if (success) ChatSyncState.Success else ChatSyncState.Failed
        )
        return if (success) SyncResult.Success else SyncResult.Failed
    }

    suspend fun softDeleteConversationAfterLocalDelete(
        conversationId: String,
        ownerScope: String
    ): SyncResult {
        val uid = currentUidOrNull() ?: return SyncResult.Disabled
        if (!isCloudSyncEligible(uid, ownerScope, policy.isEnabledForUid(uid))) return SyncResult.Disabled
        _runtimeStatus.value = ChatSyncRuntimeStatus(uid, ChatSyncState.Pending)

        val success = gateway.softDeleteConversation(uid, conversationId, ownerScope)
        _runtimeStatus.value = ChatSyncRuntimeStatus(
            uid,
            if (success) ChatSyncState.Success else ChatSyncState.Failed
        )
        return if (success) SyncResult.Success else SyncResult.Failed
    }

    private fun currentUidOrNull(): String? =
        runCatching { FirebaseAuth.getInstance().currentUser?.uid?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun resolveWorkspaceName(conversationId: String, title: String): String? =
        if (conversationService.isBoundToWorkspace(conversationId, title)) {
            conversationService.resolveConversationWorkspaceName(conversationId, title)
                .trim()
                .takeIf { it.isNotBlank() }
        } else {
            null
        }

    private suspend fun ConversationEntity.withResolvedPersona(
        activePersonaName: String?,
        ownerScope: String
    ): ConversationEntity {
        val resolvedPersonaName = ConversationPersonaMetadata.resolve(personaName, activePersonaName)
        if (resolvedPersonaName != personaName) {
            repository.updateConversationPersonaName(id, ownerScope, resolvedPersonaName)
        }
        return copy(personaName = resolvedPersonaName)
    }

    private fun ChatMessage.toEntity(
        conversationId: String,
        ownerScope: String
    ): ChatMessageEntity =
        ChatMessageEntity(
            id = id,
            conversationId = conversationId,
            text = text,
            isUser = isUser,
            timestamp = timestamp,
            imageUrl = imageUrl,
            sourcesJson = null,
            webFetchedAtIso = null,
            ownerScope = ownerScope
        )

    companion object {
        const val MAX_PREVIEW_LENGTH = 120

        fun safePreview(text: String): String =
            text.replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_PREVIEW_LENGTH)
    }

    enum class SyncResult {
        Disabled,
        Pending,
        Success,
        Failed
    }

    data class ChatSyncRuntimeStatus(
        val uid: String? = null,
        val state: ChatSyncState = ChatSyncState.LocalOnly
    )

    enum class ChatSyncState {
        LocalOnly,
        Active,
        Pending,
        Success,
        Failed
    }
}
