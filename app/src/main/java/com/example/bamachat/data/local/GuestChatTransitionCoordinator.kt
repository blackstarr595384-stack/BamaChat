package com.example.bamachat.data.local

import com.example.bamachat.data.cloud.AccountCloudOperationGate
import com.example.bamachat.data.repository.ChatRepository
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface GuestScopeChatCleaner {
    suspend fun clear(ownerScope: String): ScopedChatCleanupResult
}

@Singleton
class RoomGuestScopeChatCleaner @Inject constructor(
    private val chatDao: ChatDao
) : GuestScopeChatCleaner {
    override suspend fun clear(ownerScope: String): ScopedChatCleanupResult =
        chatDao.deleteChatDataForScope(ownerScope)
}

fun interface LegacyScopeClaimer {
    suspend fun claim(uid: String): LegacyScopeClaimResult
}

@Singleton
class RoomLegacyScopeClaimer @Inject constructor(
    private val database: ChatDatabase
) : LegacyScopeClaimer {
    override suspend fun claim(uid: String): LegacyScopeClaimResult = database.withTransaction {
        val accountScope = ChatOwnerScope.account(uid)
        val legacyScope = ChatOwnerScope.LEGACY_UNCLASSIFIED
        val dao = database.chatDao()
        val conversationIds = dao.getConversationIdsForScope(legacyScope)
        database.openHelper.writableDatabase.execSQL("PRAGMA defer_foreign_keys = ON")
        val claimedKnowledgeChunks = dao.claimLegacyKnowledgeChunks(accountScope, legacyScope)
        val claimedKnowledgeEdges = dao.countKnowledgeEdgesForScope(legacyScope)
        dao.mergeDuplicateLegacyKnowledgeEdges(accountScope, legacyScope)
        dao.deleteDuplicateLegacyKnowledgeEdges(accountScope, legacyScope)
        dao.claimLegacyKnowledgeEdges(accountScope, legacyScope)
        val claimedMessages = dao.claimLegacyMessages(accountScope, legacyScope)
        val claimedConversations = dao.claimLegacyConversations(accountScope, legacyScope)
        check(dao.countMessagesForScope(legacyScope) == 0) { "Legacy messages remain after claim" }
        check(dao.countConversationsForScope(legacyScope) == 0) { "Legacy conversations remain after claim" }
        check(dao.countKnowledgeChunksForScope(legacyScope) == 0) { "Legacy knowledge chunks remain after claim" }
        check(dao.countKnowledgeEdgesForScope(legacyScope) == 0) { "Legacy knowledge edges remain after claim" }
        LegacyScopeClaimResult(
            conversationIds,
            claimedConversations,
            claimedMessages,
            claimedKnowledgeChunks,
            claimedKnowledgeEdges
        )
    }
}

data class AccountTransitionResult(
    val accountScope: String,
    val cleanup: ScopedChatCleanupResult?,
    val legacyClaim: LegacyScopeClaimResult
)

@Singleton
class GuestChatTransitionCoordinator @Inject constructor(
    private val scopeStore: ChatSessionScopeStore,
    private val cleaner: GuestScopeChatCleaner,
    private val legacyClaimer: LegacyScopeClaimer,
    private val repository: ChatRepository,
    private val workspaceStore: ConversationWorkspaceStore,
    private val cloudOperationGate: AccountCloudOperationGate = AccountCloudOperationGate()
) {
    private val transitionMutex = Mutex()

    suspend fun completeAuthenticatedTransition(uid: String): AccountTransitionResult =
        transitionMutex.withLock {
            val accountScope = ChatOwnerScope.account(uid)
            val currentScope = scopeStore.currentScope()
            val preparedAccountSwitch =
                scopeStore.transitionPhase() == AccountTransitionPhase.PREPARED ||
                    scopeStore.pendingAccountUid() == uid.trim()
            check(
                !ChatOwnerScope.isAccount(currentScope) ||
                    currentScope == accountScope ||
                    preparedAccountSwitch
            ) {
                "Der aktive Kontobereich gehört nicht zur authentifizierten UID."
            }
            if (
                !scopeStore.isAccountTransitionPending() &&
                currentScope == accountScope &&
                repository.legacyConversationCount() == 0 &&
                repository.legacyMessageCount() == 0 &&
                repository.legacyKnowledgeChunkCount() == 0 &&
                repository.legacyKnowledgeEdgeCount() == 0
            ) {
                return@withLock AccountTransitionResult(
                    accountScope = accountScope,
                    cleanup = null,
                    legacyClaim = LegacyScopeClaimResult(emptyList(), 0, 0)
                )
            }
            cloudOperationGate.withTransitionStart {
                scopeStore.beginAuthenticatedTransition(uid)
            }

            var cleanup: ScopedChatCleanupResult? = null
            if (scopeStore.transitionPhase().ordinal < AccountTransitionPhase.GUEST_CLEANUP_COMPLETE.ordinal) {
                val pendingGuestScope = scopeStore.pendingGuestScope()
                if (pendingGuestScope != null) {
                    cleanup = cleaner.clear(pendingGuestScope)
                    workspaceStore.removeAllForScope(pendingGuestScope)
                }
                scopeStore.markTransitionPhase(uid, AccountTransitionPhase.GUEST_CLEANUP_COMPLETE)
            }

            val legacyClaim = if (
                scopeStore.transitionPhase().ordinal < AccountTransitionPhase.LEGACY_CLAIM_COMPLETE.ordinal
            ) {
                legacyClaimer.claim(uid).also {
                    scopeStore.markTransitionPhase(uid, AccountTransitionPhase.LEGACY_CLAIM_COMPLETE)
                }
            } else {
                LegacyScopeClaimResult(emptyList(), 0, 0)
            }

            if (scopeStore.transitionPhase().ordinal < AccountTransitionPhase.WORKSPACE_MIGRATION_COMPLETE.ordinal) {
                workspaceStore.migrateUnscopedBindings(repository.getAllConversationsForWorkspaceMigration())
                scopeStore.markTransitionPhase(uid, AccountTransitionPhase.WORKSPACE_MIGRATION_COMPLETE)
            }

            scopeStore.completeAccountTransition(uid)
            AccountTransitionResult(
                accountScope = accountScope,
                cleanup = cleanup,
                legacyClaim = legacyClaim
            )
        }

}
