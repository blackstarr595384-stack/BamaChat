package com.example.bamachat.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.bamachat.data.auth.AccountAuthenticationCoordinator
import com.example.bamachat.data.auth.AccountAuthenticationGateway
import com.example.bamachat.data.local.AccountAuthTransitionException
import com.example.bamachat.data.local.AccountAuthTransitionRunner
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ChatSessionScopeStore
import com.example.bamachat.data.local.ConversationWorkspaceStore
import com.example.bamachat.data.local.GuestChatTransitionCoordinator
import com.example.bamachat.data.local.RoomGuestScopeChatCleaner
import com.example.bamachat.data.local.RoomLegacyScopeClaimer
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class AccountAuthenticationCoordinatorTest {
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var scopeStore: ChatSessionScopeStore
    private lateinit var gateway: RecordingAuthGateway
    private lateinit var coordinator: AccountAuthenticationCoordinator

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
        gateway = RecordingAuthGateway()
        val transitionCoordinator = GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            RoomLegacyScopeClaimer(database),
            repository,
            ConversationWorkspaceStore(prefs)
        )
        coordinator = AccountAuthenticationCoordinator(
            gateway,
            AccountAuthTransitionRunner(scopeStore, transitionCoordinator)
        )
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun googlePublicProviderPathCompletesScopedTransition() = runBlocking {
        val guest = createGuestChat("google")
        gateway.googleUid = "uid-google"

        coordinator.signInWithGoogle("redacted-token")

        assertEquals(listOf("google"), gateway.calls)
        assertSuccessfulTransition(guest, "google", "uid-google")
    }

    @Test
    fun emailPublicProviderPathCompletesScopedTransition() = runBlocking {
        val guest = createGuestChat("email")
        gateway.emailUid = "uid-email"

        coordinator.signInWithEmail("redacted", "redacted")

        assertEquals(listOf("email"), gateway.calls)
        assertSuccessfulTransition(guest, "email", "uid-email")
    }

    @Test
    fun registrationPublicProviderPathCompletesScopedTransition() = runBlocking {
        val guest = createGuestChat("registration")
        gateway.registrationUid = "uid-registration"

        coordinator.registerWithEmail("redacted", "redacted")

        assertEquals(listOf("registration"), gateway.calls)
        assertSuccessfulTransition(guest, "registration", "uid-registration")
    }

    @Test
    fun googleCancellationKeepsGuestAndClosesPreparedGate() = runBlocking {
        assertProviderFailure("google") { coordinator.signInWithGoogle("cancel") }
    }

    @Test
    fun emailFailureKeepsGuestAndClosesPreparedGate() = runBlocking {
        assertProviderFailure("email") { coordinator.signInWithEmail("redacted", "bad") }
    }

    @Test
    fun registrationFailureKeepsGuestAndClosesPreparedGate() = runBlocking {
        assertProviderFailure("registration") {
            coordinator.registerWithEmail("redacted", "bad")
        }
    }

    private suspend fun assertProviderFailure(
        suffix: String,
        operation: suspend () -> Unit
    ) {
        val guest = createGuestChat("failure-$suffix")
        gateway.failure = IllegalStateException("provider failed")

        assertThrows(AccountAuthTransitionException::class.java) {
            runBlocking { operation() }
        }

        assertEquals(guest, scopeStore.currentScope())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertNotNull(repository.getConversation("conversation-failure-$suffix", guest))
    }

    private suspend fun assertSuccessfulTransition(guest: String, suffix: String, uid: String) {
        assertEquals(ChatOwnerScope.account(uid), scopeStore.currentScope())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertEquals(0, repository.getMessages("conversation-$suffix", guest).first().size)
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

    private class RecordingAuthGateway : AccountAuthenticationGateway {
        val calls = mutableListOf<String>()
        var googleUid = ""
        var emailUid = ""
        var registrationUid = ""
        var failure: Throwable? = null

        override suspend fun signInWithGoogle(idToken: String): String = result("google", googleUid)
        override suspend fun signInWithEmail(email: String, password: String): String = result("email", emailUid)
        override suspend fun registerWithEmail(email: String, password: String): String =
            result("registration", registrationUid)

        private fun result(provider: String, uid: String): String {
            calls += provider
            failure?.let { throw it }
            return uid
        }
    }

    private companion object {
        const val PREFS_NAME = "account_authentication_coordinator_test"
    }
}
