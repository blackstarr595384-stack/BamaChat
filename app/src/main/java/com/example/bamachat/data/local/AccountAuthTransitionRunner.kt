package com.example.bamachat.data.local

import com.example.bamachat.data.cloud.AccountCloudOperationGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AccountAuthProvider {
    GOOGLE,
    EMAIL,
    REGISTRATION,
    AUTH_STATE
}

@Singleton
class AccountAuthTransitionRunner @Inject constructor(
    private val scopeStore: ChatSessionScopeStore,
    private val coordinator: GuestChatTransitionCoordinator,
    private val cloudOperationGate: AccountCloudOperationGate = AccountCloudOperationGate()
) {
    private val mutex = Mutex()

    suspend fun authenticate(
        provider: AccountAuthProvider,
        authenticate: suspend () -> String
    ): AccountTransitionResult = mutex.withLock {
        cloudOperationGate.withTransitionStart { scopeStore.prepareAccountTransition() }
        try {
            val uid = authenticate().trim().also {
                require(it.isNotBlank()) { "Authentifizierung lieferte keine Firebase UID." }
            }
            coordinator.completeAuthenticatedTransition(uid)
        } catch (error: Throwable) {
            if (scopeStore.canCancelAccountTransition()) {
                scopeStore.cancelAccountTransition()
            }
            throw AccountAuthTransitionException(provider, error)
        }
    }

    suspend fun resumeAuthenticated(uid: String): AccountTransitionResult = mutex.withLock {
        coordinator.completeAuthenticatedTransition(uid)
    }

    suspend fun prepareAuthenticatedProcessResume(uid: String) {
        cloudOperationGate.withTransitionStart {
            scopeStore.prepareAccountTransition()
            scopeStore.beginAuthenticatedTransition(uid)
        }
    }
}

class AccountAuthTransitionException(
    val provider: AccountAuthProvider,
    cause: Throwable
) : IllegalStateException("Kontoübergang für $provider ist fehlgeschlagen.", cause)
