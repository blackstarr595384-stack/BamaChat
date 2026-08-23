package com.example.bamachat.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.bamachat.data.local.AccountAuthProvider
import com.example.bamachat.data.local.AccountAuthTransitionException
import com.example.bamachat.data.local.AccountAuthTransitionRunner
import com.example.bamachat.data.local.AccountTransitionPhase
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ChatSessionScopeStore
import com.example.bamachat.data.local.ConversationWorkspaceStore
import com.example.bamachat.data.local.GuestChatTransitionCoordinator
import com.example.bamachat.data.local.GuestScopeChatCleaner
import com.example.bamachat.data.local.RoomGuestScopeChatCleaner
import com.example.bamachat.data.local.RoomLegacyScopeClaimer
import com.example.bamachat.data.local.ScopedChatCleanupResult
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.flow.first
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
class AccountAuthTransitionRunnerTest {
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var scopeStore: ChatSessionScopeStore
    private lateinit var cleaner: CountingCleaner
    private lateinit var runner: AccountAuthTransitionRunner

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
        cleaner = CountingCleaner(RoomGuestScopeChatCleaner(database.chatDao()))
        runner = createRunner(scopeStore, cleaner)
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun googleSuccessUsesProductiveTransitionAndClearsGuest() = runBlocking {
        assertProviderSuccess(AccountAuthProvider.GOOGLE)
    }

    @Test
    fun emailSuccessUsesProductiveTransitionAndClearsGuest() = runBlocking {
        assertProviderSuccess(AccountAuthProvider.EMAIL)
    }

    @Test
    fun registrationSuccessUsesProductiveTransitionAndClearsGuest() = runBlocking {
        assertProviderSuccess(AccountAuthProvider.REGISTRATION)
    }

    @Test
    fun googleCancellationAndErrorKeepGuestDataAndCloseLoadingGate() = runBlocking {
        assertProviderFailure(AccountAuthProvider.GOOGLE)
    }

    @Test
    fun emailFailureKeepsGuestData() = runBlocking {
        assertProviderFailure(AccountAuthProvider.EMAIL)
    }

    @Test
    fun registrationFailureKeepsGuestData() = runBlocking {
        assertProviderFailure(AccountAuthProvider.REGISTRATION)
    }

    @Test
    fun processRestartWithPendingAuthenticatedUserResumesFailSafe() = runBlocking {
        val guest = createGuestChat("restart")
        scopeStore.prepareAccountTransition()
        scopeStore.beginAuthenticatedTransition("uid-restart")

        val restoredStore = ChatSessionScopeStore(prefs)
        val restoredRunner = createRunner(restoredStore)
        restoredRunner.resumeAuthenticated("uid-restart")

        assertEquals(ChatOwnerScope.account("uid-restart"), restoredStore.currentScope())
        assertFalse(restoredStore.isAccountTransitionPending())
        assertTrue(restoredStore.isCloudSyncAllowed("uid-restart"))
        assertTrue(repository.getMessages("conversation-restart", guest).first().isEmpty())
    }

    @Test
    fun processResumeRejectsForeignActiveAccountWithoutPersistedTransition() = runBlocking {
        val accountA = scopeStore.activateAccount("uid-a")

        assertThrows(com.example.bamachat.data.local.PendingAccountUidConflictException::class.java) {
            runBlocking { runner.prepareAuthenticatedProcessResume("uid-b") }
        }

        assertEquals(accountA, scopeStore.currentScope())
        assertEquals(com.example.bamachat.data.local.AccountTransitionPhase.NONE, scopeStore.transitionPhase())
        assertEquals(null, scopeStore.pendingAccountUid())
        assertEquals(0, cleaner.calls)
    }

    @Test
    fun missingModernPhaseKeyPreservesLegacyPendingCompatibility() = runBlocking {
        val guest = createGuestChat("legacy-pending")
        scopeStore.prepareAccountTransition()
        prefs.edit()
            .remove("chat_account_transition_phase")
            .remove("chat_pending_account_uid")
            .putBoolean("chat_account_transition_pending", true)
            .commit()

        assertEquals(AccountTransitionPhase.PREPARED, scopeStore.transitionPhase())

        runner.authenticate(AccountAuthProvider.EMAIL) { "uid-legacy-pending" }

        assertEquals(ChatOwnerScope.account("uid-legacy-pending"), scopeStore.currentScope())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertTrue(repository.getMessages("conversation-legacy-pending", guest).first().isEmpty())
        assertEquals(1, cleaner.calls)
    }

    private suspend fun assertProviderSuccess(provider: AccountAuthProvider) {
        val guest = createGuestChat(provider.name.lowercase())
        val result = runner.authenticate(provider) { "uid-${provider.name.lowercase()}" }

        assertEquals(ChatOwnerScope.account("uid-${provider.name.lowercase()}"), result.accountScope)
        assertEquals(1, result.cleanup?.deletedConversations)
        assertTrue(repository.getMessages("conversation-${provider.name.lowercase()}", guest).first().isEmpty())
        assertEquals(1, cleaner.calls)
        assertFalse(scopeStore.isAccountTransitionPending())
        assertTrue(scopeStore.isCloudSyncAllowed("uid-${provider.name.lowercase()}"))
    }

    private suspend fun assertProviderFailure(provider: AccountAuthProvider) {
        val suffix = provider.name.lowercase()
        val guest = createGuestChat("failure-$suffix")

        assertThrows(AccountAuthTransitionException::class.java) {
            runBlocking { runner.authenticate(provider) { error("auth failed") } }
        }

        assertEquals(guest, scopeStore.currentScope())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertNotNull(repository.getConversation("conversation-failure-$suffix", guest))
        assertEquals(1, repository.getMessages("conversation-failure-$suffix", guest).first().size)
        assertEquals(0, cleaner.calls)
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

    private fun createRunner(
        store: ChatSessionScopeStore,
        guestCleaner: GuestScopeChatCleaner = cleaner
    ): AccountAuthTransitionRunner {
        val coordinator = GuestChatTransitionCoordinator(
            store,
            guestCleaner,
            RoomLegacyScopeClaimer(database),
            repository,
            ConversationWorkspaceStore(prefs)
        )
        return AccountAuthTransitionRunner(store, coordinator)
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

    private companion object {
        const val PREFS_NAME = "account_auth_transition_runner_test"
    }
}
