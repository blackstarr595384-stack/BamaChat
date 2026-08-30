package com.example.bamachat.desktop

import java.util.UUID

internal sealed interface DesktopOwnerSwitchResult {
    data class Activated(
        val state: DesktopLocalState,
        val recoveryCopyCreated: Boolean
    ) : DesktopOwnerSwitchResult

    data object Rejected : DesktopOwnerSwitchResult
}

internal class DesktopScopedStateSession(
    private val store: DesktopScopedStateStore = DesktopScopedStateStore(),
    initialAuthenticatedUid: String = "",
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private var activeOwnerScope = DesktopOwnerScope
        .accountFromAuthenticatedUid(initialAuthenticatedUid)
        ?: DesktopOwnerScope.guest()
    private val initialLoad = store.load(activeOwnerScope)

    var currentState: DesktopLocalState = initialLoad.state
        private set

    var lastLoadRecoveryCopyCreated: Boolean = initialLoad.recoveryCopyCreated
        private set

    val activeOwnerScopeId: String
        @Synchronized get() = activeOwnerScope.id

    @Synchronized
    fun switchToAuthenticatedOwner(authenticatedUid: String): DesktopOwnerSwitchResult {
        val nextScope = DesktopOwnerScope.accountFromAuthenticatedUid(authenticatedUid)
            ?: return DesktopOwnerSwitchResult.Rejected
        return activate(nextScope)
    }

    @Synchronized
    fun switchToGuest(): DesktopOwnerSwitchResult.Activated {
        return activate(DesktopOwnerScope.guest())
    }

    @Synchronized
    fun appendMessage(
        role: DesktopLocalMessageRole,
        text: String
    ): DesktopLocalState {
        require(text.isNotEmpty()) { "Eine persistierte Desktop-Nachricht darf nicht leer sein." }
        val now = clock().also {
            require(it >= 0L) { "Zeitwerte dürfen nicht negativ sein." }
        }
        val message = DesktopLocalMessage(
            id = requiredId(),
            role = role.name,
            text = text,
            createdAtEpochMs = now
        )
        val activeConversation = currentState.activeConversation()
        val updatedConversation = if (activeConversation == null) {
            DesktopLocalConversation(
                id = requiredId(),
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                messages = listOf(message)
            )
        } else {
            activeConversation.copy(
                updatedAtEpochMs = maxOf(now, activeConversation.updatedAtEpochMs),
                messages = activeConversation.messages + message
            )
        }
        val updatedConversations = if (activeConversation == null) {
            currentState.conversations + updatedConversation
        } else {
            currentState.conversations.map { conversation ->
                if (conversation.id == activeConversation.id) updatedConversation else conversation
            }
        }
        return persist(
            currentState.copy(
                activeConversationId = updatedConversation.id,
                conversations = updatedConversations
            )
        )
    }

    @Synchronized
    fun clearActiveConversation(): DesktopLocalState {
        val activeConversationId = currentState.activeConversationId ?: return currentState
        return persist(
            currentState.copy(
                activeConversationId = null,
                conversations = currentState.conversations.filterNot {
                    it.id == activeConversationId
                }
            )
        )
    }

    @Synchronized
    fun updateWorkspaceNotes(notes: String): DesktopLocalState {
        if (notes == currentState.workspace.notes) return currentState
        val now = clock().also {
            require(it >= 0L) { "Zeitwerte dürfen nicht negativ sein." }
        }
        return persist(
            currentState.copy(
                workspace = DesktopLocalWorkspace(
                    notes = notes,
                    updatedAtEpochMs = maxOf(now, currentState.workspace.updatedAtEpochMs)
                )
            )
        )
    }

    private fun activate(nextScope: DesktopOwnerScope): DesktopOwnerSwitchResult.Activated {
        if (nextScope == activeOwnerScope) {
            return DesktopOwnerSwitchResult.Activated(
                state = currentState,
                recoveryCopyCreated = false
            )
        }
        val loaded = store.load(nextScope)
        activeOwnerScope = nextScope
        currentState = loaded.state
        lastLoadRecoveryCopyCreated = loaded.recoveryCopyCreated
        return DesktopOwnerSwitchResult.Activated(
            state = loaded.state,
            recoveryCopyCreated = loaded.recoveryCopyCreated
        )
    }

    private fun persist(updatedState: DesktopLocalState): DesktopLocalState {
        store.save(activeOwnerScope, updatedState)
        currentState = updatedState
        return updatedState
    }

    private fun requiredId(): String {
        return newId().trim().also {
            require(it.isNotEmpty()) { "Persistierte Desktop-IDs dürfen nicht leer sein." }
        }
    }
}
