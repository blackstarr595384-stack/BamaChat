package com.example.bamachat.data.local

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class PendingAccountUidConflictRecoveryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var store: ChatSessionScopeStore

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        store = ChatSessionScopeStore(prefs)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun pendingAccountAndMatchingFirebaseAccountResumeIdempotently() {
        store.prepareAccountTransition()
        assertEquals(AccountTransitionPhase.AUTHENTICATED, store.beginAuthenticatedTransition("uid-a"))
        assertEquals(AccountTransitionPhase.AUTHENTICATED, store.beginAuthenticatedTransition("uid-a"))
        assertTrue(store.isAccountTransitionPending())
        assertFalse(store.isCloudSyncAllowed("uid-a"))
    }

    @Test
    fun pendingAccountAndDifferentFirebaseAccountStayBlockedWithoutMutation() {
        val guest = store.startNewGuestSession()
        store.prepareAccountTransition()
        store.beginAuthenticatedTransition("uid-a")

        assertThrows(PendingAccountUidConflictException::class.java) {
            store.beginAuthenticatedTransition("uid-b")
        }

        assertEquals("uid-a", store.pendingAccountUid())
        assertEquals(guest, store.pendingGuestScope())
        assertTrue(store.isAccountTransitionPending())
        assertFalse(store.isCloudSyncAllowed("uid-a"))
        assertFalse(store.isCloudSyncAllowed("uid-b"))
    }

    @Test
    fun verifiedSignOutResetsConflictMetadataButPreservesGuestSession() {
        val guest = store.startNewGuestSession()
        store.prepareAccountTransition()
        store.beginAuthenticatedTransition("uid-a")

        store.resetConflictingTransitionAfterSignOut()

        assertEquals(guest, store.currentScope())
        assertFalse(store.isAccountTransitionPending())
        assertEquals(null, store.pendingAccountUid())
        assertTrue(store.consumeSecurityConflictNotice())
        assertFalse(store.consumeSecurityConflictNotice())
    }

    @Test
    fun verifiedSignOutWithoutGuestReturnsToNoSessionAndAllowsFreshAttempt() {
        store.activateAccount("uid-old")
        store.prepareAccountTransition()
        store.beginAuthenticatedTransition("uid-a")
        store.resetConflictingTransitionAfterSignOut()
        assertEquals(ChatOwnerScope.NO_ACTIVE_SESSION, store.currentScope())

        store.prepareAccountTransition()
        assertEquals(AccountTransitionPhase.AUTHENTICATED, store.beginAuthenticatedTransition("uid-b"))
        assertEquals("uid-b", store.pendingAccountUid())
        assertFalse(store.isCloudSyncAllowed("uid-b"))
    }

    @Test
    fun normalColdStartWithoutPendingRemainsInactive() {
        assertEquals(AccountTransitionPhase.NONE, store.transitionPhase())
        assertEquals(ChatOwnerScope.NO_ACTIVE_SESSION, store.reconcile(null, guestModeEnabled = false))
        assertFalse(store.isAccountTransitionPending())
    }

    private companion object {
        const val PREFS_NAME = "pending_account_uid_conflict_recovery_test"
    }
}
