package com.example.bamachat.service

import android.content.SharedPreferences
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ChatSessionScopeStore
import com.example.bamachat.data.local.ConversationWorkspaceStore
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.shared.core.WorkspaceNaming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

class ConversationService(
    private val repo: ChatRepository,
    private val prefs: SharedPreferences,
    private val scopeStore: ChatSessionScopeStore,
    private val workspaceStore: ConversationWorkspaceStore
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllConversations(): Flow<List<ConversationEntity>> =
        scopeStore.activeScope.flatMapLatest { ownerScope ->
            if (ChatOwnerScope.isWritable(ownerScope)) repo.getAllConversations(ownerScope)
            else flowOf(emptyList())
        }

    suspend fun createConversation(personaName: String, workspaceName: String): ConversationEntity {
        val id = UUID.randomUUID().toString()
        val title = newConversationTitle(workspaceName)
        val conv = repo.createConversation(id, title, personaName, writableOwnerScope())
        bindConversationToWorkspace(writableOwnerScope(), id, workspaceName)
        return conv
    }

    suspend fun switchConversation(id: String) {
        check(repo.getConversation(id, readableOwnerScope()) != null) {
            "Conversation is not visible in the active session"
        }
        prefs.edit().putString(currentConversationKey(), id).apply()
    }

    fun getCurrentConversationId(): String? = prefs.getString(currentConversationKey(), null)

    suspend fun rename(id: String, newTitle: String) {
        repo.renameConversation(id, writableOwnerScope(), newTitle.ifBlank { "Chat" })
    }

    suspend fun delete(id: String) {
        val ownerScope = writableOwnerScope()
        repo.deleteConversation(id, ownerScope)
        removeConversationWorkspaceBinding(ownerScope, id)
    }

    suspend fun touch(id: String) {
        repo.touchConversation(id, writableOwnerScope())
    }

    fun newConversationTitle(workspaceName: String): String {
        val ws = workspaceName.trim()
        return if (ws.isBlank()) "Neuer Chat" else "[$ws] Neuer Chat"
    }

    fun isPlaceholderTitle(title: String): Boolean =
        WorkspaceNaming.isPlaceholderConversationTitle(title)

    private fun bindConversationToWorkspace(ownerScope: String, id: String, name: String) {
        workspaceStore.bind(ownerScope, id, WorkspaceNaming.normalizeWorkspaceName(name))
    }

    fun resolveConversationWorkspaceName(id: String, title: String): String {
        val ownerScope = readableOwnerScope()
        val persisted = workspaceStore.resolve(ownerScope, id)?.trim().orEmpty()
        if (persisted.isNotBlank()) return persisted
        val inferred = WorkspaceNaming.workspaceTagFromTitle(title)
        if (!inferred.isNullOrBlank()) {
            bindConversationToWorkspace(ownerScope, id, inferred)
            return inferred
        }
        return "Standard"
    }

    private fun removeConversationWorkspaceBinding(ownerScope: String, id: String) {
        workspaceStore.remove(ownerScope, id)
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

    fun findLatestConversationForWorkspace(
        conversations: List<ConversationEntity>,
        workspaceName: String
    ): ConversationEntity? {
        val target = WorkspaceNaming.normalizeWorkspaceName(workspaceName)
        return conversations
            .filter { conv ->
                resolveConversationWorkspaceName(conv.id, conv.title)
                    .equals(target, ignoreCase = true)
            }
            .maxByOrNull { it.updatedAt }
    }

    fun isBoundToWorkspace(id: String, title: String): Boolean {
        val persisted = workspaceStore.resolve(readableOwnerScope(), id)?.trim().orEmpty()
        if (persisted.isNotBlank()) return true
        return WorkspaceNaming.workspaceTagFromTitle(title) != null
    }

    suspend fun createNormalConversation(personaName: String): ConversationEntity {
        val id = UUID.randomUUID().toString()
        val conv = repo.createConversation(id, "Neuer Chat", personaName, writableOwnerScope())
        return conv
    }

    fun findLatestConversationWithoutWorkspace(
        conversations: List<ConversationEntity>
    ): ConversationEntity? {
        return conversations
            .filter { conv -> !isBoundToWorkspace(conv.id, conv.title) }
            .maxByOrNull { it.updatedAt }
    }

    fun syncWorkspaceBindings(conversations: List<ConversationEntity>) {
        conversations.forEach { resolveConversationWorkspaceName(it.id, it.title) }
    }

    fun activeWorkspaceName(): String =
        WorkspaceNaming.normalizeWorkspaceName(prefs.getString("active_workspace_name", "Standard").orEmpty())

    fun findWorkspaceNameById(workspaceId: String): String? {
        val raw = prefs.getString("project_workspaces_json", null) ?: return null
        return try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("id") == workspaceId) return obj.getString("name")
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun currentOwnerScope(): String = readableOwnerScope()

    fun writableOwnerScope(): String = scopeStore.requireWritableActiveScope()

    fun hasWritableSession(): Boolean =
        ChatOwnerScope.isWritable(scopeStore.currentScope()) && !scopeStore.isAccountTransitionPending()

    private fun readableOwnerScope(): String = scopeStore.currentScope().also { ownerScope ->
        check(ChatOwnerScope.isWritable(ownerScope)) { "No readable chat session is active" }
    }

    private fun currentConversationKey(): String =
        ChatSessionScopeStore.currentConversationKey(readableOwnerScope())

}
