package com.example.bamachat.data.local

import javax.inject.Inject
import javax.inject.Singleton

enum class PendingAccountConflictRecoveryResult {
    NO_PENDING_TRANSITION,
    RESET_AFTER_SIGN_OUT,
    SIGN_OUT_FAILED,
    SIGN_OUT_NOT_CONFIRMED,
    RESET_FAILED
}

@Singleton
class PendingAccountConflictRecovery @Inject constructor(
    private val scopeStore: ChatSessionScopeStore
) {
    fun recoverUidConflict(
        signOut: () -> Unit,
        currentUid: () -> String?
    ): PendingAccountConflictRecoveryResult {
        if (!scopeStore.isAccountTransitionPending()) {
            return PendingAccountConflictRecoveryResult.NO_PENDING_TRANSITION
        }
        if (runCatching(signOut).isFailure) {
            return PendingAccountConflictRecoveryResult.SIGN_OUT_FAILED
        }
        if (!currentUid().isNullOrBlank()) {
            return PendingAccountConflictRecoveryResult.SIGN_OUT_NOT_CONFIRMED
        }
        if (!scopeStore.isAccountTransitionPending()) {
            return PendingAccountConflictRecoveryResult.RESET_AFTER_SIGN_OUT
        }
        return resetPendingTransition()
    }

    fun reconcileSignedOutState(): PendingAccountConflictRecoveryResult {
        if (!scopeStore.isAccountTransitionPending()) {
            return PendingAccountConflictRecoveryResult.NO_PENDING_TRANSITION
        }
        return if (scopeStore.canCancelAccountTransition()) {
            if (runCatching { scopeStore.cancelAccountTransition() }.isSuccess) {
                PendingAccountConflictRecoveryResult.RESET_AFTER_SIGN_OUT
            } else {
                PendingAccountConflictRecoveryResult.RESET_FAILED
            }
        } else {
            resetPendingTransition()
        }
    }

    private fun resetPendingTransition(): PendingAccountConflictRecoveryResult =
        if (runCatching { scopeStore.resetConflictingTransitionAfterSignOut() }.isSuccess) {
            PendingAccountConflictRecoveryResult.RESET_AFTER_SIGN_OUT
        } else {
            PendingAccountConflictRecoveryResult.RESET_FAILED
        }
}
