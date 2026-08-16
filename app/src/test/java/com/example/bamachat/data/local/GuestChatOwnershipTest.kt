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
    private lateinit var workspaceStore: ConversationWorkspaceStore

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
        workspaceStore = ConversationWorkspaceStore(prefs)
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
    fun crossScopeIdCollisionsAreRejectedWhileSameScopeRestoreCanUpdate() = runBlocking {
        val account = ChatOwnerScope.account("uid-a")
        val guest = ChatOwnerScope.guest("guest-a")
        seedChat("shared-id", "shared-message", "account body", account)
        seedChat("guest-conversation", "guest-message", "guest body", guest)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.createConversation("shared-id", ownerScope = guest) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                database.chatDao().upsertMessageInScope(
                    message("shared-message", "guest-conversation", guest, "blocked")
                )
            }
        }

        repository.restoreAccountBackup(
            com.example.bamachat.data.repository.ScopedConversationSnapshot(
                conversation = ConversationEntity("shared-id", "Updated", 1L, 2L, ownerScope = account),
                messages = listOf(message("shared-message", "shared-id", account, "updated"))
            ),
            account
        )

        assertEquals("Updated", repository.getConversation("shared-id", account)?.title)
        assertEquals("updated", repository.getMessages("shared-id", account).first().single().text)
    }

    @Test
    fun workspaceBindingsAreIsolatedByOwnerScope() {
        val accountA = ChatOwnerScope.account("uid-a")
        val accountB = ChatOwnerScope.account("uid-b")
        val guest = ChatOwnerScope.guest("guest-a")

        workspaceStore.bind(accountA, "same-conversation", "A")
        workspaceStore.bind(accountB, "same-conversation", "B")
        workspaceStore.bind(guest, "same-conversation", "Guest")

        assertEquals("A", workspaceStore.resolve(accountA, "same-conversation"))
        assertEquals("B", workspaceStore.resolve(accountB, "same-conversation"))
        assertEquals("Guest", workspaceStore.resolve(guest, "same-conversation"))
        workspaceStore.removeAllForScope(guest)
        assertNull(workspaceStore.resolve(guest, "same-conversation"))
        assertEquals("A", workspaceStore.resolve(accountA, "same-conversation"))
        assertEquals("B", workspaceStore.resolve(accountB, "same-conversation"))
    }

    @Test
    fun successfulTransitionDeletesCurrentGuestClaimsLegacyAndPreservesOtherData() = runBlocking {
        assertSelectiveSuccessfulTransition()
    }

    @Test
    fun authCancellationAndFailureKeepGuestDataAndRestoreUsableGuestScope() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        seedChat("guest-conversation", "guest-message", "guest body", guest)
        repository.saveKnowledgeChunk(guest, "Guest", "guest knowledge", "guest")

        scopeStore.beginAccountTransitionIfGuest()
        assertTrue(scopeStore.isAccountTransitionPending())
        assertFalse(scopeStore.isCloudSyncAllowed("uid-a"))
        scopeStore.cancelAccountTransition()

        assertFalse(scopeStore.isAccountTransitionPending())
        assertEquals(guest, scopeStore.currentScope())
        assertNotNull(repository.getConversation("guest-conversation", guest))
        assertEquals(1, repository.getMessages("guest-conversation", guest).first().size)
        assertEquals(1, repository.searchKnowledge(guest, "guest").size)
    }

    @Test
    fun repeatedGuestEntryReusesPersistedScopeAndCancelsPreparedTransition() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        seedChat("reused-guest", "reused-message", "still reachable", guest)
        repository.saveKnowledgeChunk(guest, "Guest", "reused knowledge", "reused")
        scopeStore.prepareAccountTransition()

        val reused = scopeStore.startNewGuestSession()

        assertEquals(guest, reused)
        assertEquals(guest, scopeStore.currentScope())
        assertEquals(AccountTransitionPhase.NONE, scopeStore.transitionPhase())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertNotNull(repository.getConversation("reused-guest", reused))
        assertEquals(1, repository.searchKnowledge(reused, "reused").size)
    }

    @Test
    fun successfulTransitionScopesKnowledgeCleansOnlyCurrentGuestAndClaimsLegacy() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        val otherGuest = ChatOwnerScope.guest("other-knowledge-session")
        val account = ChatOwnerScope.account("knowledge-account")
        repository.saveKnowledgeChunk(guest, "Guest", "current guest knowledge", "current")
        repository.saveKnowledgeChunk(otherGuest, "Other", "other guest knowledge", "other")
        repository.saveKnowledgeChunk(account, "Account", "account knowledge", "account")
        repository.saveKnowledgeEdge(guest, "shared", "relates", "concept", 0.4f)
        repository.saveKnowledgeEdge(otherGuest, "shared", "relates", "concept", 0.5f)
        repository.saveKnowledgeEdge(account, "shared", "relates", "concept", 0.6f)
        database.chatDao().insertKnowledgeChunk(
            KnowledgeChunkEntity(
                sourceTitle = "Legacy",
                content = "legacy knowledge",
                keywords = "legacy",
                createdAt = 1L,
                ownerScope = ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        database.chatDao().insertKnowledgeEdge(
            KnowledgeEdgeEntity(
                fromConcept = "shared",
                relation = "relates",
                toConcept = "concept",
                weight = 0.9f,
                updatedAt = 2L,
                ownerScope = ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.searchKnowledge(ChatOwnerScope.LEGACY_UNCLASSIFIED, "legacy") }
        }

        scopeStore.prepareAccountTransition()
        val result = GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            RoomLegacyScopeClaimer(database),
            repository,
            workspaceStore
        ).completeAuthenticatedTransition("knowledge-account")

        assertEquals(1, result.cleanup?.deletedKnowledgeChunks)
        assertEquals(1, result.cleanup?.deletedKnowledgeEdges)
        assertEquals(1, result.legacyClaim.claimedKnowledgeChunks)
        assertEquals(1, result.legacyClaim.claimedKnowledgeEdges)
        assertEquals(0, database.chatDao().countKnowledgeChunksForScope(guest))
        assertEquals(0, database.chatDao().countKnowledgeEdgesForScope(guest))
        assertEquals(1, database.chatDao().countKnowledgeChunksForScope(otherGuest))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(otherGuest))
        assertEquals(2, database.chatDao().countKnowledgeChunksForScope(account))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(account))
        assertEquals(0, database.chatDao().countKnowledgeChunksForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertEquals(0, database.chatDao().countKnowledgeEdgesForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertEquals(1, repository.searchKnowledge(account, "legacy").size)
        assertEquals(1, repository.getKnowledgeEdges(account).size)
        assertEquals(1, repository.searchKnowledge(otherGuest, "other").size)
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
            cleaner = GuestScopeChatCleaner { error("cleanup failed") },
            legacyClaimer = RoomLegacyScopeClaimer(database),
            repository = repository,
            workspaceStore = workspaceStore
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
            },
            legacyClaimer = LegacyScopeClaimer { LegacyScopeClaimResult(emptyList(), 0, 0) },
            repository = repository,
            workspaceStore = workspaceStore
        )

        coordinator.completeAuthenticatedTransition("uid-a")
        coordinator.completeAuthenticatedTransition("uid-a")

        assertEquals(1, calls)
        assertEquals(ChatOwnerScope.account("uid-a"), scopeStore.currentScope())
        assertTrue(scopeStore.isCloudSyncAllowed("uid-a"))
    }

    @Test
    fun restartAfterGuestCleanupDoesNotRepeatDestructiveStep() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        seedChat("guest-restart", "guest-message-restart", "guest body", guest)
        insertLegacyChat("legacy-restart", "legacy-message-restart")
        scopeStore.prepareAccountTransition()
        scopeStore.beginAuthenticatedTransition("uid-restart")
        RoomGuestScopeChatCleaner(database.chatDao()).clear(guest)
        scopeStore.markTransitionPhase("uid-restart", AccountTransitionPhase.GUEST_CLEANUP_COMPLETE)

        val restoredStore = ChatSessionScopeStore(prefs)
        val restoredCoordinator = GuestChatTransitionCoordinator(
            restoredStore,
            GuestScopeChatCleaner { error("cleanup must not repeat") },
            RoomLegacyScopeClaimer(database),
            repository,
            workspaceStore
        )
        restoredCoordinator.completeAuthenticatedTransition("uid-restart")

        assertEquals(ChatOwnerScope.account("uid-restart"), restoredStore.currentScope())
        assertNotNull(repository.getConversation("legacy-restart", ChatOwnerScope.account("uid-restart")))
    }

    @Test
    fun restartAfterLegacyClaimDoesNotRepeatClaim() = runBlocking {
        insertLegacyChat("legacy-claimed", "legacy-message-claimed")
        scopeStore.prepareAccountTransition()
        scopeStore.beginAuthenticatedTransition("uid-claimed")
        scopeStore.markTransitionPhase("uid-claimed", AccountTransitionPhase.GUEST_CLEANUP_COMPLETE)
        RoomLegacyScopeClaimer(database).claim("uid-claimed")
        scopeStore.markTransitionPhase("uid-claimed", AccountTransitionPhase.LEGACY_CLAIM_COMPLETE)

        val restoredStore = ChatSessionScopeStore(prefs)
        val restoredCoordinator = GuestChatTransitionCoordinator(
            restoredStore,
            GuestScopeChatCleaner { error("cleanup must not run") },
            LegacyScopeClaimer { error("legacy claim must not repeat") },
            repository,
            workspaceStore
        )
        restoredCoordinator.completeAuthenticatedTransition("uid-claimed")

        assertEquals(ChatOwnerScope.account("uid-claimed"), restoredStore.currentScope())
        assertEquals(0, repository.legacyConversationCount())
    }

    @Test
    fun legacyClaimFailureKeepsPersistentGateClosedUntilSafeResume() = runBlocking {
        insertLegacyChat("legacy-failure", "legacy-message-failure")
        scopeStore.prepareAccountTransition()
        val failingCoordinator = GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            LegacyScopeClaimer { error("legacy claim failed") },
            repository,
            workspaceStore
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { failingCoordinator.completeAuthenticatedTransition("uid-failure") }
        }

        assertEquals(AccountTransitionPhase.GUEST_CLEANUP_COMPLETE, scopeStore.transitionPhase())
        assertTrue(scopeStore.isAccountTransitionPending())
        assertFalse(scopeStore.isCloudSyncAllowed("uid-failure"))
        assertEquals(1, repository.legacyConversationCount())

        val restoredStore = ChatSessionScopeStore(prefs)
        GuestChatTransitionCoordinator(
            restoredStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            RoomLegacyScopeClaimer(database),
            repository,
            workspaceStore
        ).completeAuthenticatedTransition("uid-failure")

        assertEquals(ChatOwnerScope.account("uid-failure"), restoredStore.currentScope())
        assertTrue(restoredStore.isCloudSyncAllowed("uid-failure"))
    }

    private suspend fun assertSelectiveSuccessfulTransition() {
        val guest = scopeStore.startNewGuestSession()
        val otherGuest = ChatOwnerScope.guest("other")
        val account = ChatOwnerScope.account("uid-a")
        seedChat("guest-current", "guest-message-current", "guest current", guest)
        seedChat("guest-other", "guest-message-other", "guest other", otherGuest)
        seedChat("account-existing", "account-message-existing", "account existing", account)
        insertLegacyChat("legacy-chat", "legacy-message")
        prefs.edit()
            .putString("project_workspaces_json", "[]")
            .putString("openrouter_api_key", "preserved")
            .putBoolean("settings.simple_mode_enabled", true)
            .putString(ChatSessionScopeStore.currentConversationKey(guest), "guest-current")
            .putString("conversation_workspace_name_guest-current", "Guest Workspace")
            .putString("conversation_workspace_name_legacy-chat", "Legacy Workspace")
            .commit()

        scopeStore.beginAccountTransitionIfGuest()
        assertFalse(scopeStore.isCloudSyncAllowed("uid-a"))
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            RoomLegacyScopeClaimer(database),
            repository,
            workspaceStore
        )
        val result = coordinator.completeAuthenticatedTransition("uid-a")

        assertEquals(account, result.accountScope)
        assertEquals(1, result.cleanup?.deletedConversations)
        assertEquals(1, result.cleanup?.deletedMessages)
        assertEquals(1, result.cleanup?.deletedFtsRows)
        assertEquals(1, result.legacyClaim.claimedConversations)
        assertEquals(1, result.legacyClaim.claimedMessages)
        assertNull(repository.getConversation("guest-current", guest))
        assertNotNull(repository.getConversation("guest-other", otherGuest))
        assertNotNull(repository.getConversation("account-existing", account))
        assertNotNull(repository.getConversation("legacy-chat", account))
        assertEquals(0L, rawCount("conversations", "ownerScope", ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertEquals(0L, rawCount("chat_messages", "ownerScope", ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertEquals(1L, rawCount("chat_messages_fts", "conversation_id", "legacy-chat"))
        assertTrue(repository.searchMessages("guest", guest).isEmpty())
        assertEquals(1, repository.searchMessages("legacy", account).size)
        assertEquals(account, scopeStore.currentScope())
        assertTrue(scopeStore.isCloudSyncAllowed("uid-a"))
        assertEquals("[]", prefs.getString("project_workspaces_json", null))
        assertEquals("preserved", prefs.getString("openrouter_api_key", null))
        assertTrue(prefs.getBoolean("settings.simple_mode_enabled", false))
        assertFalse(prefs.contains(ChatSessionScopeStore.currentConversationKey(guest)))
        assertFalse(prefs.contains("conversation_workspace_name_guest-current"))
        assertFalse(prefs.contains("conversation_workspace_name_legacy-chat"))
        assertNull(workspaceStore.resolve(guest, "guest-current"))
        assertEquals("Legacy Workspace", workspaceStore.resolve(account, "legacy-chat"))
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
