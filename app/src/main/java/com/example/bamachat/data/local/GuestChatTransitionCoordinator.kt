package com.example.bamachat.data.local

import javax.inject.Inject
import javax.inject.Singleton

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

data class AccountTransitionResult(
    val accountScope: String,
    val cleanup: ScopedChatCleanupResult?
)

@Singleton
class GuestChatTransitionCoordinator @Inject constructor(
    private val scopeStore: ChatSessionScopeStore,
    private val cleaner: GuestScopeChatCleaner
) {
    suspend fun completeAuthenticatedTransition(uid: String): AccountTransitionResult {
        val pendingGuestScope = scopeStore.pendingGuestScope()
        if (pendingGuestScope == null) {
            return AccountTransitionResult(
                accountScope = scopeStore.activateAccount(uid),
                cleanup = null
            )
        }

        val cleanup = cleaner.clear(pendingGuestScope)
        scopeStore.completeAccountTransition(uid, cleanup.conversationIds)
        return AccountTransitionResult(
            accountScope = ChatOwnerScope.account(uid),
            cleanup = cleanup
        )
    }

}
