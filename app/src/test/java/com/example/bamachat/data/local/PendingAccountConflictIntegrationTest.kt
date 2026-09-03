package com.example.bamachat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.bamachat.data.cloud.AccountCloudOperationGate
import com.example.bamachat.data.cloud.AuthenticatedUidProvider
import com.example.bamachat.data.cloud.ChatCloudSyncGateway
import com.example.bamachat.data.cloud.ChatCloudWriter
import com.example.bamachat.data.cloud.CloudConversation
import com.example.bamachat.data.cloud.CloudMessage
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class PendingAccountConflictIntegrationTest {
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var scopeStore: ChatSessionScopeStore
    private lateinit var operationGate: AccountCloudOperationGate
    private lateinit var cleaner: CountingCleaner
    private lateinit var claimer: CountingClaimer
    private lateinit var workspaceStore: ConversationWorkspaceStore
    private lateinit var runner: AccountAuthTransitionRunner
    private lateinit var recovery: PendingAccountConflictRecovery

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(database.chatDao())
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        scopeStore = ChatSessionScopeStore(prefs)
        operationGate = AccountCloudOperationGate()
        cleaner = CountingCleaner(RoomGuestScopeChatCleaner(database.chatDao()))
        claimer = CountingClaimer(RoomLegacyScopeClaimer(database))
        workspaceStore = ConversationWorkspaceStore(prefs)
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore,
            cleaner,
            claimer,
            repository,
            workspaceStore,
            operationGate
        )
        runner = AccountAuthTransitionRunner(scopeStore, coordinator, operationGate)
        recovery = PendingAccountConflictRecovery(scopeStore)
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun pendingAndMatchingFirebaseUidResumeWithoutSignOutOrSecurityNotice() = runBlocking {
        createGuestChat("matching")
        scopeStore.prepareAccountTransition()
        scopeStore.beginAuthenticatedTransition(UID_A)
        val auth = FakeAuthSession(UID_A)

        runner.resumeAuthenticated(UID_A)

        assertEquals(0, auth.signOutCalls)
        assertEquals(ChatOwnerScope.account(UID_A), scopeStore.currentScope())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertFalse(scopeStore.consumeSecurityConflictNotice())
        assertEquals(1, cleaner.calls)
        assertEquals(1, claimer.calls)
    }

    @Test
    fun staleMatchingPendingUidAtNoneCannotSwitchForeignAccountOrClaimLegacy() = runBlocking {
        val accountA = scopeStore.activateAccount(UID_A)
        createScopedChat("account-a", accountA)
        createLegacyChat("stale")
        prefs.edit()
            .putString("chat_pending_account_uid", UID_B)
            .putString("chat_account_transition_phase", AccountTransitionPhase.NONE.name)
            .putBoolean("chat_account_transition_pending", false)
            .commit()
        val writer = CountingWriter()
        val gateway = ChatCloudSyncGateway(
            scopeStore,
            FakeAuthSession(UID_B),
            operationGate,
            writer
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { runner.resumeAuthenticated(UID_B) }
        }

        assertEquals(accountA, scopeStore.currentScope())
        assertEquals(AccountTransitionPhase.NONE, scopeStore.transitionPhase())
        assertEquals(UID_B, scopeStore.pendingAccountUid())
        assertEquals(0, cleaner.calls)
        assertEquals(0, claimer.calls)
        assertFalse(
            gateway.pushConversation(
                UID_B,
                ConversationEntity("blocked", "blocked", 1L, 1L, ownerScope = ChatOwnerScope.account(UID_B))
            )
        )
        assertEquals(0, writer.calls)
        assertNotNull(repository.getConversation("conversation-account-a", accountA))
        assertNotNull(
            database.chatDao().getConversationByIdAndScope(
                "conversation-stale",
                ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        assertEquals(0, database.chatDao().countConversationsForScope(ChatOwnerScope.account(UID_B)))
    }

    @Test
    fun corruptModernPhaseCannotFallbackToLegacyPendingOrMutateAccountState() = runBlocking {
        val accountA = scopeStore.activateAccount(UID_A)
        createScopedChat("account-a-corrupt", accountA)
        createLegacyChat("corrupt")
        val legacyWorkspaceKey = "conversation_workspace_name_conversation-corrupt"
        prefs.edit()
            .putString(legacyWorkspaceKey, "legacy-workspace")
            .putString("chat_pending_account_uid", UID_B)
            .putString("chat_account_transition_phase", "CORRUPT_OR_UNKNOWN")
            .putBoolean("chat_account_transition_pending", true)
            .commit()
        val writer = CountingWriter()
        val gateway = ChatCloudSyncGateway(
            scopeStore,
            FakeAuthSession(UID_B),
            operationGate,
            writer
        )

        assertThrows(PendingAccountUidConflictException::class.java) {
            runBlocking { runner.resumeAuthenticated(UID_B) }
        }

        val accountB = ChatOwnerScope.account(UID_B)
        assertEquals(AccountTransitionPhase.CORRUPT, scopeStore.transitionPhase())
        assertEquals(accountA, scopeStore.currentScope())
        assertEquals(UID_B, scopeStore.pendingAccountUid())
        assertEquals(0, cleaner.calls)
        assertEquals(0, claimer.calls)
        assertFalse(
            gateway.pushConversation(
                UID_B,
                ConversationEntity("blocked-corrupt", "blocked", 1L, 1L, ownerScope = accountB)
            )
        )
        assertEquals(0, writer.calls)
        assertNotNull(repository.getConversation("conversation-account-a-corrupt", accountA))
        assertNotNull(
            database.chatDao().getConversationByIdAndScope(
                "conversation-corrupt",
                ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        assertEquals(0, database.chatDao().countConversationsForScope(accountB))
        assertEquals("legacy-workspace", prefs.getString(legacyWorkspaceKey, null))
        assertFalse(prefs.contains(workspaceStore.scopedKey(accountB, "conversation-corrupt")))
    }

    @Test
    fun preparedMatchingUidAllowsAccountSwitchWithoutClaimingExistingAccountData() = runBlocking {
        val accountA = scopeStore.activateAccount(UID_A)
        createScopedChat("preserved-a", accountA)
        createLegacyChat("prepared")
        scopeStore.prepareAccountTransition()
        scopeStore.bindPreparedAccountUid(UID_B)

        runner.resumeAuthenticated(UID_B)

        val accountB = ChatOwnerScope.account(UID_B)
        assertEquals(accountB, scopeStore.currentScope())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertEquals(0, cleaner.calls)
        assertEquals(1, claimer.calls)
        assertNotNull(repository.getConversation("conversation-preserved-a", accountA))
        assertNotNull(repository.getConversation("conversation-prepared", accountB))
        assertEquals(1, database.chatDao().countConversationsForScope(accountA))
        assertEquals(1, database.chatDao().countConversationsForScope(accountB))
    }

    @Test
    fun pendingAndDifferentFirebaseUidSignsOutBeforeResetAndPreservesData() = runBlocking {
        val guest = createGuestChat("conflict")
        createLegacyChat("legacy")
        scopeStore.prepareAccountTransition()
        scopeStore.beginAuthenticatedTransition(UID_A)
        val auth = FakeAuthSession(UID_B)
        val writer = CountingWriter()
        val gateway = ChatCloudSyncGateway(
            scopeStore,
            auth,
            operationGate,
            writer
        )

        assertThrows(PendingAccountUidConflictException::class.java) {
            runBlocking { runner.resumeAuthenticated(UID_B) }
        }
        assertFalse(
            gateway.pushConversation(
                UID_B,
                ConversationEntity("blocked", "blocked", 1L, 1L, ownerScope = ChatOwnerScope.account(UID_B))
            )
        )
        val result = recovery.recoverUidConflict(auth::signOut, auth::currentUid)

        assertEquals(PendingAccountConflictRecoveryResult.RESET_AFTER_SIGN_OUT, result)
        assertEquals(1, auth.signOutCalls)
        assertEquals(0, cleaner.calls)
        assertEquals(0, claimer.calls)
        assertEquals(0, writer.calls)
        assertNotNull(repository.getConversation("conversation-conflict", guest))
        assertNotNull(
            database.chatDao().getConversationByIdAndScope(
                "conversation-legacy",
                ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        assertEquals(guest, scopeStore.currentScope())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertTrue(scopeStore.consumeSecurityConflictNotice())
    }

    @Test
    fun pendingWithNullFirebaseUserReturnsToSignedOutStateWithoutDeletingData() = runBlocking {
        val guest = createGuestChat("signed-out")
        scopeStore.prepareAccountTransition()
        scopeStore.beginAuthenticatedTransition(UID_A)

        val result = recovery.reconcileSignedOutState()

        assertEquals(PendingAccountConflictRecoveryResult.RESET_AFTER_SIGN_OUT, result)
        assertNotNull(repository.getConversation("conversation-signed-out", guest))
        assertEquals(0, cleaner.calls)
        assertEquals(0, claimer.calls)
        assertFalse(scopeStore.isAccountTransitionPending())
    }

    @Test
    fun repeatedAuthenticatedCallbacksAreIdempotent() = runBlocking {
        createGuestChat("repeated")
        scopeStore.prepareAccountTransition()
        scopeStore.beginAuthenticatedTransition(UID_A)

        runner.resumeAuthenticated(UID_A)
        runner.resumeAuthenticated(UID_A)

        assertEquals(1, cleaner.calls)
        assertEquals(1, claimer.calls)
        assertEquals(ChatOwnerScope.account(UID_A), scopeStore.currentScope())
        assertFalse(scopeStore.isAccountTransitionPending())
    }

    @Test
    fun signOutFailureKeepsConflictMetadataAndAllowsSafeRetry() = runBlocking {
        val guest = createGuestChat("retry")
        scopeStore.prepareAccountTransition()
        scopeStore.beginAuthenticatedTransition(UID_A)
        val auth = FakeAuthSession(UID_B, failSignOut = true)

        val failed = recovery.recoverUidConflict(auth::signOut, auth::currentUid)

        assertEquals(PendingAccountConflictRecoveryResult.SIGN_OUT_FAILED, failed)
        assertTrue(scopeStore.isAccountTransitionPending())
        assertEquals(UID_A, scopeStore.pendingAccountUid())
        assertEquals(guest, scopeStore.currentScope())
        assertFalse(scopeStore.isCloudSyncAllowed(UID_A))
        assertFalse(scopeStore.isCloudSyncAllowed(UID_B))

        auth.failSignOut = false
        val retried = recovery.recoverUidConflict(auth::signOut, auth::currentUid)

        assertEquals(PendingAccountConflictRecoveryResult.RESET_AFTER_SIGN_OUT, retried)
        assertFalse(scopeStore.isAccountTransitionPending())
        assertNotNull(repository.getConversation("conversation-retry", guest))
    }

    private suspend fun createGuestChat(suffix: String): String {
        val guest = scopeStore.startNewGuestSession()
        repository.createConversation("conversation-$suffix", ownerScope = guest)
        repository.saveMessage(
            "conversation-$suffix",
            ChatMessage("message-$suffix", "test", true, 1L),
            guest
        )
        return guest
    }

    private suspend fun createLegacyChat(suffix: String) {
        database.chatDao().insertConversation(
            ConversationEntity(
                "conversation-$suffix",
                "legacy",
                1L,
                1L,
                ownerScope = ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        database.chatDao().insertMessage(
            ChatMessageEntity(
                "message-$suffix",
                "conversation-$suffix",
                "test",
                true,
                1L,
                ownerScope = ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
    }

    private suspend fun createScopedChat(suffix: String, ownerScope: String) {
        repository.createConversation("conversation-$suffix", ownerScope = ownerScope)
        repository.saveMessage(
            "conversation-$suffix",
            ChatMessage("message-$suffix", "test", true, 1L),
            ownerScope
        )
    }

    private class FakeAuthSession(
        var uid: String?,
        var failSignOut: Boolean = false
    ) : AuthenticatedUidProvider {
        var signOutCalls = 0

        override fun currentUid(): String? = uid

        fun signOut() {
            signOutCalls++
            if (failSignOut) error("sign-out failed")
            uid = null
        }
    }

    private class CountingCleaner(
        private val delegate: GuestScopeChatCleaner
    ) : GuestScopeChatCleaner {
        var calls = 0
        override suspend fun clear(ownerScope: String): ScopedChatCleanupResult {
            calls++
            return delegate.clear(ownerScope)
        }
    }

    private class CountingClaimer(
        private val delegate: LegacyScopeClaimer
    ) : LegacyScopeClaimer {
        var calls = 0
        override suspend fun claim(uid: String): LegacyScopeClaimResult {
            calls++
            return delegate.claim(uid)
        }
    }

    private class CountingWriter : ChatCloudWriter {
        var calls = 0
        override suspend fun pushConversation(uid: String, conversation: CloudConversation) { calls++ }
        override suspend fun pushMessage(uid: String, conversationId: String, message: CloudMessage) { calls++ }
        override suspend fun softDeleteConversation(uid: String, conversationId: String) { calls++ }
    }

    private companion object {
        const val PREFS_NAME = "pending_account_conflict_integration_test"
        const val UID_A = "uid-a"
        const val UID_B = "uid-b"
    }
}
