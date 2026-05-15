package com.example.bamachat.service

import android.content.SharedPreferences
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.shared.core.WorkspaceNaming
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationService(
    private val repo: ChatRepository,
    private val prefs: SharedPreferences
) {
    fun getAllConversations(): Flow<List<ConversationEntity>> = repo.getAllConversations()

    suspend fun createConversation(personaName: String, workspaceName: String): ConversationEntity {
        val id = UUID.randomUUID().toString()
        val title = newConversationTitle(workspaceName)
        val conv = repo.createConversation(id, title, personaName)
        bindConversationToWorkspace(id, workspaceName)
        return conv
    }

    suspend fun switchConversation(id: String) {
        prefs.edit().putString(CURRENT_CONVERSATION_KEY, id).apply()
    }

    fun getCurrentConversationId(): String? = prefs.getString(CURRENT_CONVERSATION_KEY, null)

    suspend fun rename(id: String, newTitle: String) {
        repo.renameConversation(id, newTitle.ifBlank { "Chat" })
    }

    suspend fun delete(id: String) {
        repo.deleteConversation(id)
        removeConversationWorkspaceBinding(id)
    }

    suspend fun touch(id: String) {
        repo.touchConversation(id)
    }

    fun newConversationTitle(workspaceName: String): String {
        val ws = workspaceName.trim()
        return if (ws.isBlank()) "Neuer Chat" else "[$ws] Neuer Chat"
    }

    fun isPlaceholderTitle(title: String): Boolean =
        WorkspaceNaming.isPlaceholderConversationTitle(title)

    private fun conversationWorkspaceKey(id: String): String =
        "conversation_workspace_name_$id"

    private fun bindConversationToWorkspace(id: String, name: String) {
        prefs.edit().putString(conversationWorkspaceKey(id), WorkspaceNaming.normalizeWorkspaceName(name)).apply()
    }

    fun resolveConversationWorkspaceName(id: String, title: String): String {
        val persisted = prefs.getString(conversationWorkspaceKey(id), "")?.trim().orEmpty()
        if (persisted.isNotBlank()) return persisted
        val inferred = WorkspaceNaming.workspaceTagFromTitle(title)
        if (!inferred.isNullOrBlank()) {
            bindConversationToWorkspace(id, inferred)
            return inferred
        }
        return "Standard"
    }

    private fun removeConversationWorkspaceBinding(id: String) {
        prefs.edit().remove(conversationWorkspaceKey(id)).apply()
    }

    fun getConversationsForWorkspace(
        conversations: List<ConversationEntity>,
        activeWorkspaceName: String,
        onlyActiveWorkspace: Boolean
    ): List<ConversationEntity> {
        if (!onlyActiveWorkspace) return conversations
        val target = WorkspaceNaming.normalizeWorkspaceName(activeWorkspaceName)
        return conversations.filter { conv ->
            resolveConversationWorkspaceName(conv.id, conv.title)
                .equals(target, ignoreCase = true)
        }
    }

    fun syncWorkspaceBindings(conversations: List<ConversationEntity>) {
        conversations.forEach { resolveConversationWorkspaceName(it.id, it.title) }
    }

    fun activeWorkspaceName(): String =
        WorkspaceNaming.normalizeWorkspaceName(prefs.getString("active_workspace_name", "Standard").orEmpty())

    private companion object {
        private const val CURRENT_CONVERSATION_KEY = "current_conversation_id"
    }
}
