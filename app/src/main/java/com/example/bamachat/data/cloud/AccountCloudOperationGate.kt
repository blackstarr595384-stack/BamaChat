package com.example.bamachat.data.cloud

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AccountCloudOperationLease internal constructor(internal val token: Any)

@Singleton
class AccountCloudOperationGate @Inject constructor() {
    private val mutex = Mutex()
    private val leaseToken = Any()

    internal suspend fun <T> withCloudOperation(
        operation: suspend (AccountCloudOperationLease) -> T
    ): T = mutex.withLock {
        operation(AccountCloudOperationLease(leaseToken))
    }

    suspend fun <T> withTransitionStart(operation: suspend () -> T): T =
        mutex.withLock { operation() }

    internal fun requireValidLease(lease: AccountCloudOperationLease) {
        check(lease.token === leaseToken) { "Cloud-Operation besitzt keinen gültigen Lease." }
    }
}
