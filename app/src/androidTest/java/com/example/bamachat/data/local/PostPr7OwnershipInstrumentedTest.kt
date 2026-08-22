package com.example.bamachat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.util.LocalDataSanitizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostPr7OwnershipInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository
    private lateinit var scopeStore: ChatSessionScopeStore
    private lateinit var workspaceStore: ConversationWorkspaceStore
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
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
        context.deleteSharedPreferences(PREFS_NAME)
    }

    @Test
    fun repeatedGuestEntryReusesScopeAndKeepsChatAndKnowledgeReachable() = runBlocking {
        val firstScope = scopeStore.startNewGuestSession()
        repository.createConversation("device-guest", ownerScope = firstScope)
        repository.saveMessage(
            "device-guest",
            ChatMessage("device-message", "device guest text", true, 1L),
            firstScope
        )
        repository.saveKnowledgeChunk(firstScope, "Device guest", "device guest knowledge", "device")
        scopeStore.prepareAccountTransition()

        val repeatedScope = scopeStore.startNewGuestSession()

        assertEquals(firstScope, repeatedScope)
        assertEquals(AccountTransitionPhase.NONE, scopeStore.transitionPhase())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertNotNull(repository.getConversation("device-guest", repeatedScope))
        assertEquals(1, repository.getMessages("device-guest", repeatedScope).first().size)
        assertEquals(1, repository.searchKnowledge(repeatedScope, "device").size)
    }

    @Test
    fun cancellationKeepsGuestDataAndSuccessfulTransitionRemovesOnlyGuestScope() = runBlocking {
        val guestScope = scopeStore.startNewGuestSession()
        val accountScope = ChatOwnerScope.account("device-account")
        repository.createConversation("device-transition", ownerScope = guestScope)
        repository.saveMessage(
            "device-transition",
            ChatMessage("device-transition-message", "guest transition text", true, 2L),
            guestScope
        )
        repository.saveKnowledgeChunk(guestScope, "Guest", "guest transition knowledge", "transition")
        repository.saveKnowledgeEdge(guestScope, "guest", "relates", "transition")
        repository.saveKnowledgeChunk(accountScope, "Account", "account retained knowledge", "retained")
        repository.saveKnowledgeEdge(accountScope, "account", "relates", "retained")

        scopeStore.prepareAccountTransition()
        scopeStore.cancelAccountTransition()
        assertNotNull(repository.getConversation("device-transition", guestScope))
        assertEquals(1, repository.searchKnowledge(guestScope, "transition").size)

        scopeStore.prepareAccountTransition()
        scopeStore.bindPreparedAccountUid("device-account")
        GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            RoomLegacyScopeClaimer(database),
            repository,
            workspaceStore
        ).completeAuthenticatedTransition("device-account")

        assertNull(repository.getConversation("device-transition", guestScope))
        assertEquals(0, database.chatDao().countKnowledgeChunksForScope(guestScope))
        assertEquals(0, database.chatDao().countKnowledgeEdgesForScope(guestScope))
        assertEquals(1, database.chatDao().countKnowledgeChunksForScope(accountScope))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(accountScope))
        assertEquals(1, repository.searchKnowledge(accountScope, "retained").size)
    }

    @Test
    fun localDataSanitizerDeletesTargetGuestDerivedDataAndPreservesOtherScopesAndSettings() = runBlocking {
        val targetGuest = ChatOwnerScope.guest("device-cleanup-target")
        val otherGuest = ChatOwnerScope.guest("device-cleanup-other")
        val account = ChatOwnerScope.account("device-cleanup-account")
        seedScopedData(targetGuest, "target")
        seedScopedData(otherGuest, "other")
        seedScopedData(account, "account")
        database.chatDao().insertPromptVersion(
            PersonaPromptVersionEntity(
                personaName = "DEVICE_PERSONA",
                promptText = "retained device prompt",
                createdAt = 10L
            )
        )
        prefs.edit()
            .putString("project_workspaces_json", "device-workspace")
            .putString("active_workspace_id", "device-workspace-id")
            .putString("ai_provider", "device-provider")
            .putString("openrouter_api_key", "device-test-key-sentinel")
            .putBoolean("settings.simple_mode_enabled", true)
            .commit()

        val result = LocalDataSanitizer(context, prefs, database.chatDao())
            .clearGuestSessionData(targetGuest)

        assertEquals(1, result.deletedConversations)
        assertEquals(1, result.deletedMessages)
        assertEquals(3, result.deletedDerivedMemoryAndFeedbackRows)
        assertNull(repository.getConversation("device-target", targetGuest))
        assertEquals(0, database.chatDao().countKnowledgeChunksForScope(targetGuest))
        assertEquals(0, database.chatDao().countKnowledgeEdgesForScope(targetGuest))
        assertEquals(0L, rawCount("persona_memory", "sourceMessageId", "device-message-target"))
        assertEquals(0L, rawCount("persona_feedback", "messageId", "device-message-target"))
        assertEquals(0L, rawCount("user_memory_facts", "sourceMessageId", "device-message-target"))
        assertNotNull(repository.getConversation("device-other", otherGuest))
        assertNotNull(repository.getConversation("device-account", account))
        assertEquals(1, database.chatDao().countKnowledgeChunksForScope(otherGuest))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(otherGuest))
        assertEquals(1, database.chatDao().countKnowledgeChunksForScope(account))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(account))
        assertDerivedDataPresent("other")
        assertDerivedDataPresent("account")
        assertNotNull(database.chatDao().getLatestPromptVersionForPersona("DEVICE_PERSONA"))
        assertEquals("device-workspace", prefs.getString("project_workspaces_json", null))
        assertEquals("device-workspace-id", prefs.getString("active_workspace_id", null))
        assertEquals("device-provider", prefs.getString("ai_provider", null))
        assertEquals("device-test-key-sentinel", prefs.getString("openrouter_api_key", null))
        assertEquals(true, prefs.getBoolean("settings.simple_mode_enabled", false))
    }

    @Test
    fun stalePendingUidAtNoneIsRejectedAndPreparedMatchingUidCanSwitchAccounts() = runBlocking {
        val accountA = scopeStore.activateAccount("device-owner-a")
        repository.createConversation("device-account-a", ownerScope = accountA)
        database.chatDao().insertConversation(
            ConversationEntity(
                id = "device-legacy",
                title = "Legacy",
                createdAt = 1L,
                updatedAt = 1L,
                ownerScope = ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        prefs.edit()
            .putString("chat_pending_account_uid", "device-owner-b")
            .putString("chat_account_transition_phase", AccountTransitionPhase.NONE.name)
            .putBoolean("chat_account_transition_pending", false)
            .commit()
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            RoomLegacyScopeClaimer(database),
            repository,
            workspaceStore
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.completeAuthenticatedTransition("device-owner-b") }
        }
        assertEquals(accountA, scopeStore.currentScope())
        assertNotNull(repository.getConversation("device-account-a", accountA))
        assertNotNull(
            database.chatDao().getConversationByIdAndScope(
                "device-legacy",
                ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )

        scopeStore.prepareAccountTransition()
        scopeStore.bindPreparedAccountUid("device-owner-b")
        coordinator.completeAuthenticatedTransition("device-owner-b")

        val accountB = ChatOwnerScope.account("device-owner-b")
        assertEquals(accountB, scopeStore.currentScope())
        assertNotNull(repository.getConversation("device-account-a", accountA))
        assertNotNull(repository.getConversation("device-legacy", accountB))
    }

    @Test
    fun corruptModernPhaseCannotFallbackToLegacyPendingOrMutateScopes() = runBlocking {
        val accountA = scopeStore.activateAccount("device-corrupt-owner-a")
        repository.createConversation("device-corrupt-account-a", ownerScope = accountA)
        database.chatDao().insertConversation(
            ConversationEntity(
                id = "device-corrupt-legacy",
                title = "Legacy",
                createdAt = 1L,
                updatedAt = 1L,
                ownerScope = ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        prefs.edit()
            .putString("chat_pending_account_uid", "device-corrupt-owner-b")
            .putString("chat_account_transition_phase", "CORRUPT_OR_UNKNOWN")
            .putBoolean("chat_account_transition_pending", true)
            .commit()
        var cleanerCalls = 0
        var claimerCalls = 0
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore,
            GuestScopeChatCleaner {
                cleanerCalls++
                ScopedChatCleanupResult(emptyList(), 0, 0, 0, 0)
            },
            LegacyScopeClaimer {
                claimerCalls++
                LegacyScopeClaimResult(emptyList(), 0, 0)
            },
            repository,
            workspaceStore
        )

        assertThrows(PendingAccountUidConflictException::class.java) {
            runBlocking { coordinator.completeAuthenticatedTransition("device-corrupt-owner-b") }
        }

        assertEquals(AccountTransitionPhase.CORRUPT, scopeStore.transitionPhase())
        assertEquals(accountA, scopeStore.currentScope())
        assertEquals(0, cleanerCalls)
        assertEquals(0, claimerCalls)
        assertNotNull(repository.getConversation("device-corrupt-account-a", accountA))
        assertNotNull(
            database.chatDao().getConversationByIdAndScope(
                "device-corrupt-legacy",
                ChatOwnerScope.LEGACY_UNCLASSIFIED
            )
        )
        assertEquals(
            0,
            database.chatDao().countConversationsForScope(ChatOwnerScope.account("device-corrupt-owner-b"))
        )
    }

    private suspend fun seedScopedData(ownerScope: String, suffix: String) {
        val conversationId = "device-$suffix"
        val messageId = "device-message-$suffix"
        repository.createConversation(conversationId, ownerScope = ownerScope)
        repository.saveMessage(
            conversationId,
            ChatMessage(messageId, "device $suffix", true, 10L),
            ownerScope
        )
        repository.saveKnowledgeChunk(ownerScope, "Device $suffix", "device $suffix knowledge", suffix)
        repository.saveKnowledgeEdge(ownerScope, suffix, "relates", "device-$suffix-target")
        database.chatDao().insertPersonaMemory(
            PersonaMemoryEntity(
                personaName = "DEVICE-$suffix",
                memoryText = "device $suffix memory",
                sourceMessageId = messageId,
                createdAt = 10L,
                updatedAt = 10L
            )
        )
        database.chatDao().upsertPersonaFeedback(
            PersonaFeedbackEntity(
                personaName = "DEVICE-$suffix",
                messageId = messageId,
                helpful = true,
                createdAt = 10L
            )
        )
        database.chatDao().insertUserMemoryFact(
            UserMemoryFactEntity(
                personaName = "DEVICE-$suffix",
                factText = "device $suffix fact",
                sourceMessageId = messageId,
                createdAt = 10L,
                updatedAt = 10L
            )
        )
    }

    private fun assertDerivedDataPresent(suffix: String) {
        assertEquals(1L, rawCount("persona_memory", "sourceMessageId", "device-message-$suffix"))
        assertEquals(1L, rawCount("persona_feedback", "messageId", "device-message-$suffix"))
        assertEquals(1L, rawCount("user_memory_facts", "sourceMessageId", "device-message-$suffix"))
    }

    private fun rawCount(table: String, column: String, value: String): Long =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM `$table` WHERE `$column` = ?", arrayOf(value))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }

    private companion object {
        const val PREFS_NAME = "post_pr7_ownership_instrumented_test_preferences"
    }
}
