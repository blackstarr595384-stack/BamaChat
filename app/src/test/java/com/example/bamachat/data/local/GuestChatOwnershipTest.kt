package com.example.bamachat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.bamachat.data.cloud.isCloudSyncEligible
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.data.model.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class GuestChatOwnershipTest {
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var scopeStore: ChatSessionScopeStore

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
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun scopesIsolateGuestAndAccountsAndRejectMismatchedMessageOwner() = runBlocking {
        val guest = ChatOwnerScope.guest("guest-a")
        val accountA = ChatOwnerScope.account("uid-a")
        val accountB = ChatOwnerScope.account("uid-b")
        seedChat("guest-conversation", "guest-message", "guest body", guest)
        seedChat("account-a-conversation", "account-a-message", "account body", accountA)
        seedChat("account-b-conversation", "account-b-message", "other body", accountB)

        assertEquals(listOf("guest-conversation"), repository.getAllConversations(guest).first().map { it.id })
        assertEquals(listOf("account-a-conversation"), repository.getAllConversations(accountA).first().map { it.id })
        assertEquals(listOf("account-b-conversation"), repository.getAllConversations(accountB).first().map { it.id })
        assertEquals(1, repository.searchMessages("guest", guest).size)
        assertTrue(repository.searchMessages("guest", accountA).isEmpty())

        assertThrows(Exception::class.java) {
            runBlocking {
                database.chatDao().insertMessage(
                    message("mismatched", "guest-conversation", accountA, "blocked")
                )
            }
        }
        Unit
    }

    @Test
    fun successfulGoogleTransitionDeletesOnlyCurrentGuestScopeAndPreservesOtherData() = runBlocking {
        assertSelectiveSuccessfulTransition("google")
    }

    @Test
    fun successfulEmailTransitionDeletesOnlyCurrentGuestScopeAndPreservesOtherData() = runBlocking {
        assertSelectiveSuccessfulTransition("email")
    }

    @Test
    fun successfulRegistrationTransitionDeletesOnlyCurrentGuestScopeAndPreservesOtherData() = runBlocking {
        assertSelectiveSuccessfulTransition("registration")
    }

    @Test
    fun authCancellationAndFailureKeepGuestDataAndRestoreUsableGuestScope() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        seedChat("guest-conversation", "guest-message", "guest body", guest)

        scopeStore.beginAccountTransitionIfGuest()
        assertTrue(scopeStore.isAccountTransitionPending())
        assertFalse(scopeStore.isCloudSyncAllowed("uid-a"))
        scopeStore.cancelAccountTransition()

        assertFalse(scopeStore.isAccountTransitionPending())
        assertEquals(guest, scopeStore.currentScope())
        assertNotNull(repository.getConversation("guest-conversation", guest))
        assertEquals(1, repository.getMessages("guest-conversation", guest).first().size)
    }

    @Test
    fun leavingGuestSessionWithoutSuccessfulAuthenticationDoesNotDeleteGuestData() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        seedChat("guest-conversation", "guest-message", "guest body", guest)

        scopeStore.deactivateSession()

        assertNotNull(repository.getConversation("guest-conversation", guest))
        assertEquals(1, repository.getMessages("guest-conversation", guest).first().size)
    }

    @Test
    fun cleanupFailureRemainsFailSafeAcrossStoreRecreation() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        seedChat("guest-conversation", "guest-message", "guest body", guest)
        scopeStore.beginAccountTransitionIfGuest()
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore = scopeStore,
            cleaner = GuestScopeChatCleaner { error("cleanup failed") }
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.completeAuthenticatedTransition("uid-a") }
        }

        val restoredStore = ChatSessionScopeStore(prefs)
        assertTrue(restoredStore.isAccountTransitionPending())
        assertEquals(guest, restoredStore.currentScope())
        assertFalse(restoredStore.isCloudSyncAllowed("uid-a"))
        assertFalse(isCloudSyncEligible("uid-a", restoredStore.currentScope(), true))
        assertNotNull(repository.getConversation("guest-conversation", guest))
    }

    @Test
    fun successfulTransitionRunsCleanupExactlyOnce() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        scopeStore.beginAccountTransitionIfGuest()
        var calls = 0
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore = scopeStore,
            cleaner = GuestScopeChatCleaner {
                calls++
                ScopedChatCleanupResult(emptyList(), 0, 0, 0, 0)
            }
        )

        coordinator.completeAuthenticatedTransition("uid-a")
        coordinator.completeAuthenticatedTransition("uid-a")

        assertEquals(1, calls)
        assertEquals(ChatOwnerScope.account("uid-a"), scopeStore.currentScope())
        assertTrue(scopeStore.isCloudSyncAllowed("uid-a"))
    }

    private suspend fun assertSelectiveSuccessfulTransition(provider: String) {
        val guest = scopeStore.startNewGuestSession()
        val otherGuest = ChatOwnerScope.guest("other-$provider")
        val account = ChatOwnerScope.account("uid-$provider")
        seedChat("guest-$provider", "guest-message-$provider", "guest $provider", guest)
        seedChat("other-$provider", "other-message-$provider", "other $provider", otherGuest)
        seedChat("account-$provider", "account-message-$provider", "account $provider", account)
        insertLegacyChat("legacy-$provider", "legacy-message-$provider")
        prefs.edit()
            .putString("project_workspaces_json", "[]")
            .putString("openrouter_api_key", "preserved")
            .putBoolean("settings.simple_mode_enabled", true)
            .putString(ChatSessionScopeStore.currentConversationKey(guest), "guest-$provider")
            .putString(ChatSessionScopeStore.workspaceBindingKey("guest-$provider"), "Workspace")
            .commit()

        scopeStore.beginAccountTransitionIfGuest()
        assertFalse(scopeStore.isCloudSyncAllowed("uid-$provider"))
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao())
        )
        val result = coordinator.completeAuthenticatedTransition("uid-$provider")

        assertEquals(account, result.accountScope)
        assertEquals(1, result.cleanup?.deletedConversations)
        assertEquals(1, result.cleanup?.deletedMessages)
        assertEquals(1, result.cleanup?.deletedFtsRows)
        assertNull(repository.getConversation("guest-$provider", guest))
        assertNotNull(repository.getConversation("other-$provider", otherGuest))
        assertNotNull(repository.getConversation("account-$provider", account))
        assertEquals(1L, rawCount("conversations", "ownerScope", ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertEquals(1L, rawCount("chat_messages_fts", "conversation_id", "legacy-$provider"))
        assertTrue(repository.searchMessages("guest", guest).isEmpty())
        assertEquals(account, scopeStore.currentScope())
        assertTrue(scopeStore.isCloudSyncAllowed("uid-$provider"))
        assertEquals("[]", prefs.getString("project_workspaces_json", null))
        assertEquals("preserved", prefs.getString("openrouter_api_key", null))
        assertTrue(prefs.getBoolean("settings.simple_mode_enabled", false))
        assertFalse(prefs.contains(ChatSessionScopeStore.currentConversationKey(guest)))
        assertFalse(prefs.contains(ChatSessionScopeStore.workspaceBindingKey("guest-$provider")))
    }

    private suspend fun seedChat(conversationId: String, messageId: String, text: String, scope: String) {
        repository.createConversation(conversationId, ownerScope = scope)
        repository.saveMessage(
            conversationId = conversationId,
            message = ChatMessage(id = messageId, text = text, isUser = true, timestamp = 1L),
            ownerScope = scope
        )
    }

    private suspend fun insertLegacyChat(conversationId: String, messageId: String) {
        database.chatDao().insertConversation(
            ConversationEntity(conversationId, "Legacy", 1L, 1L, ownerScope = ChatOwnerScope.LEGACY_UNCLASSIFIED)
        )
        database.chatDao().insertMessage(
            message(messageId, conversationId, ChatOwnerScope.LEGACY_UNCLASSIFIED, "legacy body")
        )
        database.chatDao().insertMessageFts(
            ChatMessageFtsEntity(
                messageId = messageId,
                conversationId = conversationId,
                text = "legacy body",
                isUser = true,
                timestamp = 1L
            )
        )
    }

    private fun message(id: String, conversationId: String, scope: String, text: String) =
        ChatMessageEntity(
            id = id,
            conversationId = conversationId,
            text = text,
            isUser = true,
            timestamp = 1L,
            ownerScope = scope
        )

    private fun rawCount(table: String, column: String, value: String): Long =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM `$table` WHERE `$column` = ?", arrayOf(value))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }

    private companion object {
        const val PREFS_NAME = "guest_chat_ownership_test"
    }
}
